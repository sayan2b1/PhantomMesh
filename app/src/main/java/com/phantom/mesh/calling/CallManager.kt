package com.phantom.mesh.calling

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.phantom.mesh.calling.webrtc.WebRtcClient
import com.phantom.mesh.calling.webrtc.WebRtcSignalingManager
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.mesh.MeshConnectionManager
import com.phantom.mesh.mesh.MeshPacket
import com.phantom.mesh.network.LocalNetworkDetector
import com.phantom.mesh.network.NetworkProfile
import com.phantom.mesh.network.NetworkStateManager
import com.phantom.mesh.network.NetworkTransport
import com.phantom.mesh.util.NotificationHelper
import com.phantom.mesh.util.SoundEffectManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

enum class CallType {
    VOICE,
    VIDEO
}

enum class CallStatus {
    IDLE,
    OUTGOING_CALLING,
    INCOMING_RINGING,
    CONNECTED,
    ENDED
}

enum class CallTransportMode {
    WEBRTC_DIRECT,   // High-fidelity WebRTC P2P (UDP/STUN, 48kHz Opus, hardware VP8/H.264)
    SAME_WIFI_LAN,   // Direct P2P UDP over local Wi-Fi router (<5ms latency)
    CLOUD_RELAY,     // Real-time media streaming via Firebase (Cross-network: Wi-Fi to Cellular)
    BLUETOOTH_MESH   // Offline Nearby Connections mesh P2P
}

data class ActiveCall(
    val callId: String = UUID.randomUUID().toString(),
    val peerUsername: String,
    val peerAddress: String,
    val callType: CallType,
    val status: CallStatus,
    val isIncoming: Boolean,
    val startTimeMs: Long = 0L,
    val durationSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val remoteVideoBitmap: Bitmap? = null,
    val transportMode: CallTransportMode = CallTransportMode.WEBRTC_DIRECT
)

sealed class CallInitiationResult {
    data class Success(val call: ActiveCall) : CallInitiationResult()
    data class BlockedByTransport(val reason: String) : CallInitiationResult()
    data class Error(val message: String) : CallInitiationResult()
}

/**
 * Enterprise-grade VoIP & Video Calling Engine for PhantomMesh.
 *
 * Implements dual-channel signaling across both Cloud (Firestore) and Mesh (Bluetooth/Wi-Fi Direct):
 * - Signals incoming calls across networks so the recipient device rings immediately.
 * - Full-duplex PCM VoIP audio engine for live voice communication.
 * - Transport-aware routing: low-rate voice calls supported across all networks.
 */
@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkStateManager: NetworkStateManager,
    private val sessionManager: SessionManager,
    private val meshConnectionManagerProvider: Provider<MeshConnectionManager>,
    private val notificationHelperProvider: Provider<NotificationHelper>
) {
    private val TAG = "CallManager"
    private val scope = CoroutineScope(Dispatchers.Main)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val gson = Gson()

    private val _currentCall = MutableStateFlow<ActiveCall?>(null)
    val currentCall: StateFlow<ActiveCall?> = _currentCall.asStateFlow()

    private val _webRtcClientFlow = MutableStateFlow<WebRtcClient?>(null)
    val webRtcClientFlow: StateFlow<WebRtcClient?> = _webRtcClientFlow.asStateFlow()

    var webRtcClient: WebRtcClient?
        get() = _webRtcClientFlow.value
        private set(value) {
            _webRtcClientFlow.value = value
        }
    private var webRtcSignaling: WebRtcSignalingManager? = null

    private var myNetworkProfile: NetworkProfile = LocalNetworkDetector.getCurrentProfile(context)
    private var peerNetworkProfile: NetworkProfile? = null
    private var lanVoipEngine: LanVoipEngine? = null

    private var durationTimerJob: Job? = null
    private var audioVoipEngine: AudioVoipEngine? = null

    private val incomingCallListeners = mutableListOf<ListenerRegistration>()
    private var activeCallDocListener: ListenerRegistration? = null
    private var cloudAudioListener: ListenerRegistration? = null
    private var cloudVideoListener: ListenerRegistration? = null
    private var legacyCloudStreamListener: ListenerRegistration? = null

    private var isCloudAudioWriteInFlight = false
    private var pendingAudioB64: String? = null
    private var audioStreamSeq = 0L

    private var isCloudVideoWriteInFlight = false
    private var pendingVideoB64: String? = null
    private var videoStreamSeq = 0L

    init {
        // 1. Listen for incoming calling packets on the Mesh (Bluetooth / Wi-Fi Direct)
        scope.launch {
            meshConnectionManagerProvider.get().incomingPackets.collect { packet ->
                handleIncomingMeshPacket(packet)
            }
        }

        // 2. Start cloud signaling listener whenever session is valid or identity updates
        scope.launch {
            sessionManager.usernameFlow.collect { username ->
                if (!username.isNullOrBlank()) {
                    startCloudCallListener(username)
                }
            }
        }
        scope.launch {
            sessionManager.userIdFlow.collect { userId ->
                if (!userId.isNullOrBlank()) {
                    startCloudCallListener()
                }
            }
        }
    }

    /**
     * Starts listening for incoming call documents in Firestore across username and userId.
     */
    fun startCloudCallListener(username: String? = null) {
        val myUsername = (username ?: sessionManager.getUsername() ?: "").trim().lowercase().removePrefix("@")
        val myUserId = (sessionManager.getUserId() ?: "").trim().lowercase()

        incomingCallListeners.forEach { it.remove() }
        incomingCallListeners.clear()

        val firestore = FirebaseFirestore.getInstance()
        val targetQueries = mutableListOf<Query>()

        if (myUsername.isNotBlank()) {
            targetQueries.add(firestore.collection("calls").whereEqualTo("targetUsername", myUsername).whereEqualTo("status", "ringing"))
            targetQueries.add(firestore.collection("calls").whereEqualTo("targetId", myUsername).whereEqualTo("status", "ringing"))
        }
        if (myUserId.isNotBlank() && myUserId != myUsername) {
            targetQueries.add(firestore.collection("calls").whereEqualTo("targetId", myUserId).whereEqualTo("status", "ringing"))
            targetQueries.add(firestore.collection("calls").whereEqualTo("targetUsername", myUserId).whereEqualTo("status", "ringing"))
        }

        targetQueries.forEach { query ->
            try {
                val reg = query.addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    for (docChange in snapshot.documentChanges) {
                        val doc = docChange.document
                        val callId = doc.getString("callId") ?: doc.id

                        if (docChange.type == DocumentChange.Type.ADDED || docChange.type == DocumentChange.Type.MODIFIED) {
                            val callerUsername = doc.getString("callerUsername") ?: "Contact"
                            val callerId = doc.getString("callerId") ?: ""
                            val callTypeStr = doc.getString("callType") ?: "VOICE"
                            val callType = if (callTypeStr == "VIDEO") CallType.VIDEO else CallType.VOICE
                            val timestamp = doc.getLong("timestamp") ?: 0L
                            val ageMs = Math.abs(System.currentTimeMillis() - timestamp)

                            val callerNetMap = doc.get("callerNet") as? Map<*, *>
                            val peerNet = NetworkProfile.fromMap(callerNetMap)

                            // Accept calls initiated within 2 minutes (tolerates clock skew)
                            if (ageMs < 120_000L || timestamp == 0L) {
                                if (_currentCall.value == null || _currentCall.value?.status == CallStatus.ENDED) {
                                    receiveIncomingCall(callerUsername, callerId, callId, callType, "cloud", peerNet)
                                }
                            }
                        } else if (docChange.type == DocumentChange.Type.REMOVED) {
                            val current = _currentCall.value
                            if (current != null && current.callId == callId && current.status == CallStatus.INCOMING_RINGING) {
                                val docStatus = docChange.document.getString("status")
                                if (docStatus == "cancelled" || docStatus == "ended" || docStatus == "rejected") {
                                    onCallEndedRemote("Caller Cancelled")
                                }
                            }
                        }
                    }
                }
                incomingCallListeners.add(reg)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register call snapshot listener: ${e.message}")
            }
        }
    }

    /**
     * Listens continuously to the call document for status changes:
     * - "accepted": connects the outgoing call
     * - "rejected" or "ended": terminates and cleans up immediately
     */
    private fun listenToCallDocument(callId: String) {
        activeCallDocListener?.remove()
        try {
            activeCallDocListener = FirebaseFirestore.getInstance().collection("calls").document(callId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val status = snapshot.getString("status")
                    val current = _currentCall.value ?: return@addSnapshotListener
                    if (current.callId != callId) return@addSnapshotListener

                    when (status) {
                        "accepted" -> {
                            val calleeNetMap = snapshot.get("calleeNet") as? Map<*, *>
                            if (calleeNetMap != null) {
                                peerNetworkProfile = NetworkProfile.fromMap(calleeNetMap)
                            }
                            if (current.status == CallStatus.OUTGOING_CALLING) {
                                connectCall()
                            }
                        }
                        "rejected" -> {
                            if (current.status != CallStatus.ENDED) {
                                onCallEndedRemote("Call Declined")
                            }
                        }
                        "ended" -> {
                            if (current.status != CallStatus.ENDED) {
                                onCallEndedRemote("Call Ended")
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach call document listener: ${e.message}")
        }
    }

    /**
     * Attempts to initiate an outgoing voice or video call to a peer.
     * Transmits call offer across both Cloud (Firestore) and Mesh (Nearby Connections).
     */
    fun startCall(
        peerUsername: String,
        peerAddress: String,
        callType: CallType
    ): CallInitiationResult {
        val currentTransport = networkStateManager.activeTransport.value
        if (currentTransport is NetworkTransport.Offline && !meshConnectionManagerProvider.get().hasActivePeers() && !networkStateManager.isInternetAvailable.value) {
            val msg = "Device is offline. Connect to internet or Bluetooth mesh peer to start a call."
            Log.w(TAG, msg)
            return CallInitiationResult.BlockedByTransport(msg)
        }

        val myUsername = sessionManager.getUsername() ?: "User"
        val myUserId = sessionManager.getUserId() ?: ""
        val myCleanName = myUsername.trim().lowercase().removePrefix("@")
        val myCleanId = myUserId.trim().lowercase()

        // Normalize peer address and username, stripping self and comma-separated tokens
        val addressTokens = peerAddress.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val nameTokens = peerUsername.split(",").map { it.trim() }.filter { it.isNotBlank() }

        val resolvedPeerToken = (addressTokens + nameTokens).firstOrNull { token ->
            val clean = token.lowercase().removePrefix("@")
            clean != myCleanName && clean != myCleanId && !clean.startsWith("peer ") && clean != "peer"
        } ?: addressTokens.firstOrNull() ?: peerAddress

        val cleanTargetRaw = resolvedPeerToken
            .removePrefix("Peer ")
            .removePrefix("Peer-")
            .removePrefix("PEER: ")
            .removePrefix("@")
            .trim()
            .lowercase()
        val cleanTargetAddress = cleanTargetRaw.substringBefore("#").trim()

        val cleanDisplayName = (nameTokens.firstOrNull { !it.equals(myUsername, ignoreCase = true) } ?: resolvedPeerToken)
            .removePrefix("Peer ")
            .removePrefix("Peer-")
            .removePrefix("PEER: ")
            .trim()
            .ifBlank { cleanTargetAddress }

        myNetworkProfile = LocalNetworkDetector.getCurrentProfile(context)

        val callId = UUID.randomUUID().toString()
        val initialMode = if (networkStateManager.isInternetAvailable.value) CallTransportMode.WEBRTC_DIRECT else CallTransportMode.BLUETOOTH_MESH

        val call = ActiveCall(
            callId = callId,
            peerUsername = cleanDisplayName,
            peerAddress = cleanTargetAddress,
            callType = callType,
            status = CallStatus.OUTGOING_CALLING,
            isIncoming = false,
            isVideoEnabled = callType == CallType.VIDEO,
            isSpeakerOn = true,
            transportMode = initialMode
        )

        _currentCall.value = call
        SoundEffectManager.startOutgoingRingtone()

        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

        // Initialize WebRTC for outgoing call
        try {
            webRtcClient?.close()
            val rtcClient = WebRtcClient(context)
            webRtcClient = rtcClient

            webRtcSignaling?.stop()
            val signaling = WebRtcSignalingManager(
                callId = callId,
                isCaller = true,
                peerAddress = cleanTargetAddress,
                webRtcClient = rtcClient,
                meshConnectionManagerProvider = meshConnectionManagerProvider,
                sessionManager = sessionManager
            )
            webRtcSignaling = signaling
            signaling.start()
            rtcClient.start(isCaller = true, isVideo = callType == CallType.VIDEO)
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing WebRTC for outgoing call: ${e.message}", e)
        }

        // 1. Signal call offer over Cloud Firestore
        try {
            val callData = hashMapOf(
                "callId" to callId,
                "callerId" to myUserId,
                "callerUsername" to myUsername,
                "targetId" to cleanTargetAddress,
                "targetUsername" to cleanTargetAddress,
                "targetRaw" to cleanTargetRaw,
                "callType" to callType.name,
                "status" to "ringing",
                "callerNet" to myNetworkProfile.toMap(),
                "timestamp" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance().collection("calls").document(callId).set(callData)
            listenToCallDocument(callId)
        } catch (e: Exception) {
            Log.w(TAG, "Cloud signaling write failed: ${e.message}")
        }

        // 2. Signal call offer over Mesh (Nearby Connections P2P)
        try {
            val meshPacket = MeshPacket(
                packetId = UUID.randomUUID().toString(),
                senderId = myUserId.ifBlank { myUsername },
                targetId = cleanTargetAddress,
                data = gson.toJson(mapOf(
                    "callId" to callId,
                    "callerUsername" to myUsername,
                    "callType" to callType.name,
                    "callerNet" to myNetworkProfile.toMap()
                )),
                dataType = "call_offer"
            )
            meshConnectionManagerProvider.get().sendPacket(meshPacket)

            if (cleanTargetRaw != cleanTargetAddress) {
                val altPacket = meshPacket.copy(
                    packetId = UUID.randomUUID().toString(),
                    targetId = cleanTargetRaw
                )
                meshConnectionManagerProvider.get().sendPacket(altPacket)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Mesh signaling offer failed: ${e.message}")
        }

        return CallInitiationResult.Success(call)
    }

    /**
     * Receives an incoming call from a remote peer.
     */
    fun receiveIncomingCall(
        peerUsername: String,
        peerAddress: String,
        callId: String,
        callType: CallType,
        transport: String = "mesh",
        peerNet: NetworkProfile? = null
    ) {
        if (_currentCall.value != null && _currentCall.value?.status != CallStatus.ENDED) {
            Log.w(TAG, "Already in a call, ignoring incoming: $callId")
            return
        }

        myNetworkProfile = LocalNetworkDetector.getCurrentProfile(context)
        peerNetworkProfile = peerNet

        val isSameWifi = peerNet != null && LocalNetworkDetector.isSameWifiNetwork(myNetworkProfile, peerNet)
        val mode = if (networkStateManager.isInternetAvailable.value) {
            CallTransportMode.WEBRTC_DIRECT
        } else if (isSameWifi) {
            CallTransportMode.SAME_WIFI_LAN
        } else {
            CallTransportMode.BLUETOOTH_MESH
        }

        val call = ActiveCall(
            callId = callId,
            peerUsername = peerUsername,
            peerAddress = peerAddress,
            callType = callType,
            status = CallStatus.INCOMING_RINGING,
            isIncoming = true,
            isVideoEnabled = callType == CallType.VIDEO,
            isSpeakerOn = true,
            transportMode = mode
        )

        _currentCall.value = call
        SoundEffectManager.startIncomingRingtone()

        notificationHelperProvider.get().showIncomingCallNotification(
            peerUsername,
            if (callType == CallType.VIDEO) "Video" else "Voice"
        )

        // Listen continuously for caller cancelling, ending, or status updates
        listenToCallDocument(callId)
    }

    /**
     * Answers an incoming call.
     */
    fun acceptCall() {
        val call = _currentCall.value ?: return
        if (call.status == CallStatus.INCOMING_RINGING) {
            notificationHelperProvider.get().cancelIncomingCallNotification()
            SoundEffectManager.stopRingtone()

            // Initialize WebRTC for incoming call
            try {
                webRtcClient?.close()
                val rtcClient = WebRtcClient(context)
                webRtcClient = rtcClient

                webRtcSignaling?.stop()
                val signaling = WebRtcSignalingManager(
                    callId = call.callId,
                    isCaller = false,
                    peerAddress = call.peerAddress,
                    webRtcClient = rtcClient,
                    meshConnectionManagerProvider = meshConnectionManagerProvider,
                    sessionManager = sessionManager
                )
                webRtcSignaling = signaling
                signaling.start()
                rtcClient.start(isCaller = false, isVideo = call.callType == CallType.VIDEO)
            } catch (e: Exception) {
                Log.e(TAG, "Failed initializing WebRTC for incoming call: ${e.message}", e)
            }

            // Immediately transition local call status to CONNECTED so it cannot be falsely cancelled
            val now = System.currentTimeMillis()
            _currentCall.value = call.copy(
                status = CallStatus.CONNECTED,
                startTimeMs = now,
                durationSeconds = 0L
            )

            myNetworkProfile = LocalNetworkDetector.getCurrentProfile(context)

            // Update Cloud status to accepted with callee network profile
            try {
                FirebaseFirestore.getInstance().collection("calls").document(call.callId)
                    .set(mapOf(
                        "status" to "accepted",
                        "calleeNet" to myNetworkProfile.toMap()
                    ), SetOptions.merge())
            } catch (e: Exception) {
                Log.w(TAG, "Cloud update to accepted failed: ${e.message}")
            }

            // Signal acceptance over Mesh with callee network profile
            try {
                val answerData = mapOf(
                    "callId" to call.callId,
                    "calleeNet" to myNetworkProfile.toMap()
                )
                val answerPacket = MeshPacket(
                    packetId = UUID.randomUUID().toString(),
                    senderId = sessionManager.getUserId() ?: "local",
                    targetId = call.peerAddress,
                    data = gson.toJson(answerData),
                    dataType = "call_answer"
                )
                meshConnectionManagerProvider.get().sendPacket(answerPacket)
            } catch (e: Exception) {
                Log.w(TAG, "Mesh answer packet failed: ${e.message}")
            }

            connectCall()
        }
    }

    private fun connectCall() {
        SoundEffectManager.stopRingtone()
        notificationHelperProvider.get().cancelIncomingCallNotification()

        myNetworkProfile = LocalNetworkDetector.getCurrentProfile(context)
        val peerNet = peerNetworkProfile
        val isSameWifi = peerNet != null && LocalNetworkDetector.isSameWifiNetwork(myNetworkProfile, peerNet)
        val mode = if (networkStateManager.isInternetAvailable.value) {
            CallTransportMode.WEBRTC_DIRECT
        } else if (isSameWifi) {
            CallTransportMode.SAME_WIFI_LAN
        } else {
            CallTransportMode.BLUETOOTH_MESH
        }

        Log.i(TAG, "Connecting call $mode. isSameWifi=$isSameWifi, myNet=$myNetworkProfile, peerNet=$peerNet")

        val now = System.currentTimeMillis()
        _currentCall.value = _currentCall.value?.copy(
            status = CallStatus.CONNECTED,
            startTimeMs = now,
            durationSeconds = 0L,
            transportMode = mode
        )

        // Start call duration ticker
        durationTimerJob?.cancel()
        durationTimerJob = scope.launch {
            while (isActive && _currentCall.value?.status == CallStatus.CONNECTED) {
                delay(1000)
                _currentCall.value = _currentCall.value?.let { current ->
                    current.copy(durationSeconds = current.durationSeconds + 1)
                }
            }
        }

        // WebRTC P2P direct UDP media handles all audio/video when active
        if (webRtcClient != null) {
            Log.i(TAG, "WebRTC active: hardware audio & video streaming directly via P2P UDP")
            return
        }

        // Mode 1: SAME_WIFI_LAN direct P2P UDP engine (<5ms, zero data)
        if (mode == CallTransportMode.SAME_WIFI_LAN && peerNet != null && peerNet.localIp.isNotBlank()) {
            lanVoipEngine?.stop()
            lanVoipEngine = LanVoipEngine(
                onAudioReceived = { pcm ->
                    audioVoipEngine?.playRemoteAudio(pcm)
                },
                onVideoReceived = { bmp ->
                    if (_currentCall.value?.status == CallStatus.CONNECTED) {
                        _currentCall.value = _currentCall.value?.copy(remoteVideoBitmap = bmp)
                    }
                }
            )
            lanVoipEngine?.start(peerIp = peerNet.localIp)
        }

        // Mode 2: CLOUD_RELAY via Firebase Firestore (Cross-network: Wi-Fi to Mobile Data & LAN fallback)
        if (mode == CallTransportMode.CLOUD_RELAY || networkStateManager.isInternetAvailable.value) {
            val current = _currentCall.value ?: return
            startCloudMediaListener(current)
        }

        val current = _currentCall.value ?: return
        startAudioStream(current, mode)

        if (current.callType == CallType.VIDEO && current.isVideoEnabled) {
            startVideoStream(current, mode)
        }
    }

    private fun startAudioStream(call: ActiveCall, mode: CallTransportMode) {
        audioVoipEngine?.stop()
        val myRole = if (call.isIncoming) "callee" else "caller"

        audioVoipEngine = AudioVoipEngine(context) { pcmChunk ->
            val active = _currentCall.value
            if (active?.status == CallStatus.CONNECTED && !active.isMuted) {
                // 1. LAN Direct P2P (<5ms latency)
                if (mode == CallTransportMode.SAME_WIFI_LAN) {
                    lanVoipEngine?.sendAudio(pcmChunk)
                }

                // 2. Cloud Relay (Dedicated low-latency audio stream via G.711 mu-law)
                if (mode == CallTransportMode.CLOUD_RELAY || (mode == CallTransportMode.SAME_WIFI_LAN && networkStateManager.isInternetAvailable.value)) {
                    val muLaw = AudioVoipEngine.G711.linearToMuLaw(pcmChunk)
                    val b64 = Base64.encodeToString(muLaw, Base64.NO_WRAP)
                    sendCloudAudio(call.callId, myRole, b64)
                }

                // 3. Bluetooth Mesh P2P
                if (mode == CallTransportMode.BLUETOOTH_MESH || meshConnectionManagerProvider.get().hasActivePeers()) {
                    try {
                        val b64 = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
                        val packet = MeshPacket(
                            packetId = UUID.randomUUID().toString(),
                            senderId = sessionManager.getUserId() ?: "local",
                            targetId = call.peerAddress,
                            data = b64,
                            dataType = "call_audio"
                        )
                        meshConnectionManagerProvider.get().sendPacket(packet)
                    } catch (_: Exception) {}
                }
            }
        }
        audioVoipEngine?.setMuted(call.isMuted)
        audioVoipEngine?.setSpeakerOn(call.isSpeakerOn)
        audioVoipEngine?.start()
    }

    private fun startVideoStream(call: ActiveCall, mode: CallTransportMode) {
        val myRole = if (call.isIncoming) "callee" else "caller"

        CameraVideoEngine.start(
            context = context,
            surfaceTexture = null,
            frontCamera = call.isFrontCamera
        ) { jpegBytes ->
            val active = _currentCall.value
            if (active?.status == CallStatus.CONNECTED && active.isVideoEnabled) {
                // 1. LAN Direct P2P (<5ms latency, MTU-safe chunks)
                if (mode == CallTransportMode.SAME_WIFI_LAN) {
                    lanVoipEngine?.sendVideo(jpegBytes)
                }

                // 2. Cloud Relay (Dedicated video stream document)
                if (mode == CallTransportMode.CLOUD_RELAY || (mode == CallTransportMode.SAME_WIFI_LAN && networkStateManager.isInternetAvailable.value)) {
                    val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                    sendCloudVideo(call.callId, myRole, b64)
                }

                // 3. Bluetooth Mesh P2P
                if (mode == CallTransportMode.BLUETOOTH_MESH || meshConnectionManagerProvider.get().hasActivePeers()) {
                    try {
                        val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                        val packet = MeshPacket(
                            packetId = UUID.randomUUID().toString(),
                            senderId = sessionManager.getUserId() ?: "local",
                            targetId = call.peerAddress,
                            data = b64,
                            dataType = "call_video"
                        )
                        meshConnectionManagerProvider.get().sendPacket(packet)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    @Synchronized
    private fun sendCloudAudio(callId: String, role: String, audioB64: String) {
        pendingAudioB64 = audioB64
        flushPendingCloudAudio(callId, role)
    }

    @Synchronized
    private fun flushPendingCloudAudio(callId: String, role: String) {
        if (isCloudAudioWriteInFlight) return
        val audioToSend = pendingAudioB64 ?: return
        pendingAudioB64 = null
        isCloudAudioWriteInFlight = true

        val now = System.currentTimeMillis()
        val data = hashMapOf<String, Any>(
            "seq" to audioStreamSeq++,
            "ts" to now,
            "audio" to audioToSend
        )

        try {
            FirebaseFirestore.getInstance().collection("calls").document(callId)
                .collection("stream").document("${role}_audio")
                .set(data)
                .addOnCompleteListener {
                    synchronized(this@CallManager) {
                        isCloudAudioWriteInFlight = false
                        if (pendingAudioB64 != null) {
                            flushPendingCloudAudio(callId, role)
                        }
                    }
                }
        } catch (e: Exception) {
            synchronized(this@CallManager) {
                isCloudAudioWriteInFlight = false
            }
            Log.d(TAG, "Cloud audio write error: ${e.message}")
        }
    }

    @Synchronized
    private fun sendCloudVideo(callId: String, role: String, videoB64: String) {
        pendingVideoB64 = videoB64
        flushPendingCloudVideo(callId, role)
    }

    @Synchronized
    private fun flushPendingCloudVideo(callId: String, role: String) {
        if (isCloudVideoWriteInFlight) return
        val videoToSend = pendingVideoB64 ?: return
        pendingVideoB64 = null
        isCloudVideoWriteInFlight = true

        val now = System.currentTimeMillis()
        val data = hashMapOf<String, Any>(
            "seq" to videoStreamSeq++,
            "ts" to now,
            "video" to videoToSend
        )

        try {
            FirebaseFirestore.getInstance().collection("calls").document(callId)
                .collection("stream").document("${role}_video")
                .set(data)
                .addOnCompleteListener {
                    synchronized(this@CallManager) {
                        isCloudVideoWriteInFlight = false
                        if (pendingVideoB64 != null) {
                            flushPendingCloudVideo(callId, role)
                        }
                    }
                }
        } catch (e: Exception) {
            synchronized(this@CallManager) {
                isCloudVideoWriteInFlight = false
            }
            Log.d(TAG, "Cloud video write error: ${e.message}")
        }
    }

    private fun startCloudMediaListener(call: ActiveCall) {
        cloudAudioListener?.remove()
        cloudVideoListener?.remove()
        legacyCloudStreamListener?.remove()

        val peerRole = if (call.isIncoming) "caller" else "callee"
        val firestore = FirebaseFirestore.getInstance()
        var lastAudioSeq = -1L
        var lastVideoSeq = -1L

        // 1. Dedicated Cloud Audio Stream Listener (Ultra-fast 800-byte updates, never blocked by video)
        try {
            cloudAudioListener = firestore.collection("calls").document(call.callId)
                .collection("stream").document("${peerRole}_audio")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    if (_currentCall.value?.status != CallStatus.CONNECTED) return@addSnapshotListener

                    val seq = snapshot.getLong("seq") ?: -1L
                    if (seq > lastAudioSeq || (lastAudioSeq - seq > 2000)) {
                        lastAudioSeq = seq
                        val audioB64 = snapshot.getString("audio")
                        if (!audioB64.isNullOrBlank()) {
                            try {
                                val muLaw = Base64.decode(audioB64, Base64.DEFAULT)
                                val pcm = AudioVoipEngine.G711.muLawToLinear(muLaw)
                                audioVoipEngine?.playRemoteAudio(pcm)
                            } catch (e: Exception) {
                                Log.d(TAG, "Error playing cloud audio: ${e.message}")
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach cloud audio listener: ${e.message}")
        }

        // 2. Dedicated Cloud Video Stream Listener (Independent frame delivery)
        if (call.callType == CallType.VIDEO) {
            try {
                cloudVideoListener = firestore.collection("calls").document(call.callId)
                    .collection("stream").document("${peerRole}_video")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                        if (_currentCall.value?.status != CallStatus.CONNECTED) return@addSnapshotListener

                        val seq = snapshot.getLong("seq") ?: -1L
                        if (seq > lastVideoSeq || (lastVideoSeq - seq > 2000)) {
                            lastVideoSeq = seq
                            val videoB64 = snapshot.getString("video")
                            if (!videoB64.isNullOrBlank()) {
                                try {
                                    val jpegBytes = Base64.decode(videoB64, Base64.DEFAULT)
                                    val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                                    if (bmp != null && _currentCall.value?.status == CallStatus.CONNECTED) {
                                        _currentCall.value = _currentCall.value?.copy(remoteVideoBitmap = bmp)
                                    }
                                } catch (e: Exception) {
                                    Log.d(TAG, "Error decoding cloud video: ${e.message}")
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to attach cloud video listener: ${e.message}")
            }
        }

        // 3. Fallback listener for legacy stream document
        try {
            legacyCloudStreamListener = firestore.collection("calls").document(call.callId)
                .collection("stream").document(peerRole)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    if (_currentCall.value?.status != CallStatus.CONNECTED) return@addSnapshotListener

                    val audioB64 = snapshot.getString("audio")
                    if (!audioB64.isNullOrBlank()) {
                        try {
                            val muLaw = Base64.decode(audioB64, Base64.DEFAULT)
                            val pcm = AudioVoipEngine.G711.muLawToLinear(muLaw)
                            audioVoipEngine?.playRemoteAudio(pcm)
                        } catch (_: Exception) {}
                    }

                    val videoB64 = snapshot.getString("video")
                    if (!videoB64.isNullOrBlank() && call.callType == CallType.VIDEO) {
                        try {
                            val jpegBytes = Base64.decode(videoB64, Base64.DEFAULT)
                            val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                            if (bmp != null && _currentCall.value?.status == CallStatus.CONNECTED) {
                                _currentCall.value = _currentCall.value?.copy(remoteVideoBitmap = bmp)
                            }
                        } catch (_: Exception) {}
                    }
                }
        } catch (_: Exception) {}
    }

    private fun handleIncomingMeshPacket(packet: MeshPacket) {
        when (packet.dataType) {
            "call_offer" -> {
                try {
                    val map = gson.fromJson(packet.data, Map::class.java)
                    val callId = map["callId"] as? String ?: UUID.randomUUID().toString()
                    val callerUsername = map["callerUsername"] as? String ?: packet.senderId
                    val callTypeStr = map["callType"] as? String ?: "VOICE"
                    val callType = if (callTypeStr == "VIDEO") CallType.VIDEO else CallType.VOICE
                    val callerNetMap = map["callerNet"] as? Map<*, *>
                    val peerNet = NetworkProfile.fromMap(callerNetMap)

                    receiveIncomingCall(
                        peerUsername = callerUsername,
                        peerAddress = packet.senderId,
                        callId = callId,
                        callType = callType,
                        transport = "mesh",
                        peerNet = peerNet
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed parsing call_offer packet: ${e.message}")
                }
            }
            "call_answer" -> {
                try {
                    val map = gson.fromJson(packet.data, Map::class.java)
                    val incomingCallId = map["callId"] as? String ?: packet.data
                    val calleeNetMap = map["calleeNet"] as? Map<*, *>
                    if (calleeNetMap != null) {
                        peerNetworkProfile = NetworkProfile.fromMap(calleeNetMap)
                    }
                    if (_currentCall.value?.callId == incomingCallId && _currentCall.value?.status == CallStatus.OUTGOING_CALLING) {
                        connectCall()
                    }
                } catch (_: Exception) {
                    if (_currentCall.value?.callId == packet.data && _currentCall.value?.status == CallStatus.OUTGOING_CALLING) {
                        connectCall()
                    }
                }
            }
            "call_reject", "call_end" -> {
                val current = _currentCall.value
                if (current != null && current.status != CallStatus.ENDED) {
                    val pData = packet.data.trim()
                    val pSender = packet.senderId.trim().lowercase().substringBefore("#")
                    val callPeer = current.peerAddress.trim().lowercase().substringBefore("#")
                    val callUser = current.peerUsername.trim().lowercase().substringBefore("#")

                    if (pData == current.callId || pData.isBlank() || pData == "broadcast" ||
                        pSender == callPeer || pSender == callUser || pData == callPeer || pData == callUser) {
                        onCallEndedRemote(if (packet.dataType == "call_reject") "Call Declined" else "Call Ended")
                    }
                }
            }
            "webrtc_sdp", "webrtc_ice" -> {
                webRtcSignaling?.handleIncomingMeshPacket(packet)
            }
            "call_audio" -> {
                if (_currentCall.value?.status == CallStatus.CONNECTED) {
                    try {
                        val pcmBytes = Base64.decode(packet.data, Base64.DEFAULT)
                        audioVoipEngine?.playRemoteAudio(pcmBytes)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed playing incoming mesh audio chunk: ${e.message}")
                    }
                }
            }
            "call_video" -> {
                if (_currentCall.value?.status == CallStatus.CONNECTED) {
                    try {
                        val rawBytes = Base64.decode(packet.data, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                        if (bmp != null && _currentCall.value?.status == CallStatus.CONNECTED) {
                            _currentCall.value = _currentCall.value?.copy(remoteVideoBitmap = bmp)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed decoding incoming mesh video frame: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Toggles microphone mute during a call.
     */
    fun toggleMute() {
        _currentCall.value = _currentCall.value?.let {
            val nextState = !it.isMuted
            audioManager?.isMicrophoneMute = nextState
            audioVoipEngine?.setMuted(nextState)
            webRtcClient?.setMuted(nextState)
            it.copy(isMuted = nextState)
        }
    }

    /**
     * Toggles speakerphone on/off.
     */
    fun toggleSpeaker() {
        _currentCall.value = _currentCall.value?.let {
            val nextState = !it.isSpeakerOn
            audioManager?.isSpeakerphoneOn = nextState
            audioVoipEngine?.setSpeakerOn(nextState)
            webRtcClient?.setSpeakerOn(nextState)
            it.copy(isSpeakerOn = nextState)
        }
    }

    /**
     * Toggles local video stream on/off.
     */
    fun toggleVideo() {
        _currentCall.value = _currentCall.value?.let { current ->
            val nextEnabled = !current.isVideoEnabled
            if (webRtcClient != null) {
                webRtcClient?.setVideoEnabled(nextEnabled)
            } else {
                if (!nextEnabled) {
                    CameraVideoEngine.stop()
                } else {
                    startVideoStream(current, current.transportMode)
                }
            }
            current.copy(isVideoEnabled = nextEnabled)
        }
    }

    /**
     * Switches between front and back camera.
     */
    fun flipCamera() {
        _currentCall.value = _currentCall.value?.let { current ->
            val nextFront = !current.isFrontCamera
            if (webRtcClient != null) {
                webRtcClient?.switchCamera()
            } else {
                CameraVideoEngine.switchCamera(context, nextFront)
            }
            current.copy(isFrontCamera = nextFront)
        }
    }

    /**
     * Terminates or declines the active call.
     */
    fun endCall(notifyRemote: Boolean = true) {
        val call = _currentCall.value
        durationTimerJob?.cancel()
        durationTimerJob = null

        SoundEffectManager.stopRingtone()
        SoundEffectManager.playCallEndSound()
        notificationHelperProvider.get().cancelIncomingCallNotification()

        try {
            webRtcSignaling?.stop()
        } catch (_: Exception) {}
        webRtcSignaling = null

        try {
            webRtcClient?.close()
        } catch (_: Exception) {}
        webRtcClient = null

        try {
            audioVoipEngine?.stop()
        } catch (_: Exception) {}
        audioVoipEngine = null

        try {
            CameraVideoEngine.stop()
        } catch (_: Exception) {}

        try {
            lanVoipEngine?.stop()
        } catch (_: Exception) {}
        lanVoipEngine = null

        try {
            cloudAudioListener?.remove()
        } catch (_: Exception) {}
        cloudAudioListener = null

        try {
            cloudVideoListener?.remove()
        } catch (_: Exception) {}
        cloudVideoListener = null

        try {
            legacyCloudStreamListener?.remove()
        } catch (_: Exception) {}
        legacyCloudStreamListener = null

        isCloudAudioWriteInFlight = false
        pendingAudioB64 = null
        isCloudVideoWriteInFlight = false
        pendingVideoB64 = null

        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isMicrophoneMute = false
            audioManager?.isSpeakerphoneOn = false
        } catch (_: Exception) {}

        try {
            activeCallDocListener?.remove()
        } catch (_: Exception) {}
        activeCallDocListener = null

        if (call != null && notifyRemote) {
            val finalStatus = if (call.status == CallStatus.INCOMING_RINGING) "rejected" else "ended"
            try {
                FirebaseFirestore.getInstance().collection("calls").document(call.callId)
                    .set(mapOf("status" to finalStatus), SetOptions.merge())
            } catch (_: Exception) {}

            try {
                val endType = if (call.status == CallStatus.INCOMING_RINGING) "call_reject" else "call_end"
                val endPacket = MeshPacket(
                    packetId = UUID.randomUUID().toString(),
                    senderId = sessionManager.getUserId() ?: "local",
                    targetId = call.peerAddress,
                    data = call.callId,
                    dataType = endType
                )
                meshConnectionManagerProvider.get().sendPacket(endPacket)

                // Flood broadcast to ensure remote peer terminates immediately
                val broadcastPacket = endPacket.copy(
                    packetId = UUID.randomUUID().toString(),
                    targetId = "broadcast"
                )
                meshConnectionManagerProvider.get().sendPacket(broadcastPacket)
            } catch (_: Exception) {}
        }

        _currentCall.value = _currentCall.value?.copy(status = CallStatus.ENDED, remoteVideoBitmap = null)

        scope.launch {
            delay(1200)
            _currentCall.value = null
        }
    }

    private fun onCallEndedRemote(reason: String) {
        durationTimerJob?.cancel()
        durationTimerJob = null

        SoundEffectManager.stopRingtone()
        SoundEffectManager.playCallEndSound()
        notificationHelperProvider.get().cancelIncomingCallNotification()

        try {
            webRtcSignaling?.stop()
        } catch (_: Exception) {}
        webRtcSignaling = null

        try {
            webRtcClient?.close()
        } catch (_: Exception) {}
        webRtcClient = null

        try {
            audioVoipEngine?.stop()
        } catch (_: Exception) {}
        audioVoipEngine = null

        try {
            CameraVideoEngine.stop()
        } catch (_: Exception) {}

        try {
            lanVoipEngine?.stop()
        } catch (_: Exception) {}
        lanVoipEngine = null

        try {
            cloudAudioListener?.remove()
        } catch (_: Exception) {}
        cloudAudioListener = null

        try {
            cloudVideoListener?.remove()
        } catch (_: Exception) {}
        cloudVideoListener = null

        try {
            legacyCloudStreamListener?.remove()
        } catch (_: Exception) {}
        legacyCloudStreamListener = null

        isCloudAudioWriteInFlight = false
        pendingAudioB64 = null
        isCloudVideoWriteInFlight = false
        pendingVideoB64 = null

        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isMicrophoneMute = false
            audioManager?.isSpeakerphoneOn = false
        } catch (_: Exception) {}

        try {
            activeCallDocListener?.remove()
        } catch (_: Exception) {}
        activeCallDocListener = null

        _currentCall.value = _currentCall.value?.copy(status = CallStatus.ENDED, remoteVideoBitmap = null)

        scope.launch {
            delay(1200)
            _currentCall.value = null
        }
    }
}
