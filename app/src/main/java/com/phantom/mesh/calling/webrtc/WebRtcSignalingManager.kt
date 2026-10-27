package com.phantom.mesh.calling.webrtc

import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.mesh.MeshConnectionManager
import com.phantom.mesh.mesh.MeshPacket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Provider

/**
 * Handles WebRTC Session Description (SDP) and ICE candidate exchange
 * across Firebase Firestore and Bluetooth Mesh.
 *
 * Cost: Zero database streaming cost (~3 total writes per call).
 * Latency: Connects within 1-2 network round trips.
 */
class WebRtcSignalingManager(
    private val callId: String,
    private val isCaller: Boolean,
    private val peerAddress: String,
    private val webRtcClient: WebRtcClient,
    private val meshConnectionManagerProvider: Provider<MeshConnectionManager>,
    private val sessionManager: SessionManager
) : WebRtcClient.Listener {

    companion object {
        private const val TAG = "WebRtcSignaling"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val gson = Gson()
    private val isRunning = AtomicBoolean(false)

    private var callDocListener: ListenerRegistration? = null
    private var candidatesListener: ListenerRegistration? = null

    fun start() {
        if (isRunning.getAndSet(true)) return
        webRtcClient.listener = this

        val myRole = if (isCaller) "caller" else "callee"
        val peerCandidatesCollection = if (isCaller) "calleeCandidates" else "callerCandidates"

        // 1. Listen to Call Document for remote SDP (Offer if callee, Answer if caller)
        try {
            callDocListener = firestore.collection("calls").document(callId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                    if (isCaller) {
                        // Caller waits for Callee's answer
                        val answerMap = snapshot.get("answer") as? Map<*, *>
                        val answer = RtcSessionDescription.fromMap(answerMap)
                        if (answer != null) {
                            webRtcClient.onRemoteSessionDescriptionReceived(answer)
                        }
                    } else {
                        // Callee waits for Caller's offer
                        val offerMap = snapshot.get("offer") as? Map<*, *>
                        val offer = RtcSessionDescription.fromMap(offerMap)
                        if (offer != null) {
                            webRtcClient.onRemoteSessionDescriptionReceived(offer)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed listening to call document: ${e.message}")
        }

        // 2. Listen to Remote Peer ICE Candidates
        try {
            candidatesListener = firestore.collection("calls").document(callId)
                .collection(peerCandidatesCollection)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    for (change in snapshot.documentChanges) {
                        if (change.type == DocumentChange.Type.ADDED) {
                            val candidate = RtcIceCandidate.fromMap(change.document.data)
                            if (candidate != null) {
                                webRtcClient.addIceCandidate(candidate)
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed listening to remote ICE candidates: ${e.message}")
        }
    }

    override fun onLocalSdpCreated(desc: RtcSessionDescription) {
        val fieldName = if (desc.type.equals("OFFER", ignoreCase = true)) "offer" else "answer"

        // 1. Upload SDP Offer / Answer to Cloud Firestore
        try {
            firestore.collection("calls").document(callId)
                .set(mapOf(fieldName to desc.toMap()), SetOptions.merge())
                .addOnSuccessListener {
                    Log.i(TAG, "WebRTC local $fieldName published to Cloud Firestore")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed writing $fieldName to Firestore: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error saving local SDP: ${e.message}")
        }

        // 2. Forward SDP Offer / Answer over Bluetooth Mesh for offline P2P
        try {
            val meshPacket = MeshPacket(
                packetId = UUID.randomUUID().toString(),
                senderId = sessionManager.getUserId() ?: "local",
                targetId = peerAddress,
                data = gson.toJson(mapOf(
                    "callId" to callId,
                    "type" to desc.type,
                    "sdp" to desc.sdp
                )),
                dataType = "webrtc_sdp"
            )
            meshConnectionManagerProvider.get().sendPacket(meshPacket)
        } catch (_: Exception) {}
    }

    override fun onIceCandidateGenerated(candidate: RtcIceCandidate) {
        val myCandidatesCollection = if (isCaller) "callerCandidates" else "calleeCandidates"

        // 1. Upload ICE Candidate to Cloud Firestore
        try {
            firestore.collection("calls").document(callId)
                .collection(myCandidatesCollection)
                .add(candidate.toMap())
        } catch (e: Exception) {
            Log.w(TAG, "Failed adding ICE candidate to Firestore: ${e.message}")
        }

        // 2. Forward ICE Candidate over Bluetooth Mesh for offline P2P
        try {
            val meshPacket = MeshPacket(
                packetId = UUID.randomUUID().toString(),
                senderId = sessionManager.getUserId() ?: "local",
                targetId = peerAddress,
                data = gson.toJson(mapOf(
                    "callId" to callId,
                    "candidate" to candidate.toMap()
                )),
                dataType = "webrtc_ice"
            )
            meshConnectionManagerProvider.get().sendPacket(meshPacket)
        } catch (_: Exception) {}
    }

    fun handleIncomingMeshPacket(packet: MeshPacket) {
        try {
            when (packet.dataType) {
                "webrtc_sdp" -> {
                    val map = gson.fromJson(packet.data, Map::class.java)
                    val incomingCallId = map["callId"] as? String
                    if (incomingCallId == callId) {
                        val type = map["type"] as? String ?: return
                        val sdp = map["sdp"] as? String ?: return
                        webRtcClient.onRemoteSessionDescriptionReceived(RtcSessionDescription(type, sdp))
                    }
                }
                "webrtc_ice" -> {
                    val map = gson.fromJson(packet.data, Map::class.java)
                    val incomingCallId = map["callId"] as? String
                    if (incomingCallId == callId) {
                        val candMap = map["candidate"] as? Map<*, *>
                        val candidate = RtcIceCandidate.fromMap(candMap)
                        if (candidate != null) {
                            webRtcClient.addIceCandidate(candidate)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling mesh WebRTC packet: ${e.message}")
        }
    }

    override fun onRemoteVideoTrackReceived(track: org.webrtc.VideoTrack) {
        Log.i(TAG, "Remote WebRTC VideoTrack received: ${track.id()}")
    }

    override fun onConnectionStateChanged(state: org.webrtc.PeerConnection.PeerConnectionState) {
        Log.i(TAG, "WebRTC connection state: $state")
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        try {
            callDocListener?.remove()
        } catch (_: Exception) {}
        callDocListener = null

        try {
            candidatesListener?.remove()
        } catch (_: Exception) {}
        candidatesListener = null

        Log.i(TAG, "WebRtcSignalingManager stopped")
    }
}
