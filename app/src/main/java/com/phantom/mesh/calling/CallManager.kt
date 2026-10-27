package com.phantom.mesh.calling

import android.content.Context
import android.media.AudioManager
import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.mesh.MeshConnectionManager
import com.phantom.mesh.mesh.MeshPacket
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
    val isFrontCamera: Boolean = true
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

    private var activeCallTransport: String = "mesh"
    private var durationTimerJob: Job? = null
    private var audioVoipEngine: AudioVoipEngine? = null

    private val incomingCallListeners = mutableListOf<ListenerRegistration>()
    private var activeCallDocListener: ListenerRegistration? = null

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

                            // Accept calls initiated within 2 minutes (tolerates clock skew)
                            if (ageMs < 120_000L || timestamp == 0L) {
                                if (_currentCall.value == null) {
                                    receiveIncomingCall(callerUsername, callerId, callId, callType, "cloud")
                                }
                            }
                        } else if (docChange.type == DocumentChange.Type.REMOVED) {
                            if (_currentCall.value?.callId == callId && _currentCall.value?.status == CallStatus.INCOMING_RINGING) {
                                onCallEndedRemote("Caller Cancelled")
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
     * Attempts to initiate an outgoing voice or video call to a peer.
     * Transmits call offer across both Cloud (Firestore) and Mesh (Nearby Connections).
     */
    fun startCall(
        peerUsername: String,
        peerAddress: String,
        callType: CallType
    ): CallInitiationResult {
        val currentTransport = networkStateManager.activeTransport.value

        if (callType == CallType.VIDEO && currentTransport is NetworkTransport.Bluetooth) {
            val msg = "Video calling is unavailable over Bluetooth Mesh due to bandwidth limits. Voice calling is available across all networks (Bluetooth, Wi-Fi, Cloud)."
            Log.w(TAG, msg)
            return CallInitiationResult.BlockedByTransport(msg)
        }

        if (currentTransport is NetworkTransport.Offline) {
            val msg = "Device is offline. Connect to internet, Bluetooth mesh, or Wi-Fi Direct peer to start a call."
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

        val cleanTargetAddress = resolvedPeerToken
            .removePrefix("Peer ")
            .removePrefix("Peer-")
            .removePrefix("PEER: ")
            .removePrefix("@")
            .trim()
            .lowercase()

        val cleanDisplayName = (nameTokens.firstOrNull { !it.equals(myUsername, ignoreCase = true) } ?: resolvedPeerToken)
            .removePrefix("Peer ")
            .removePrefix("Peer-")
            .removePrefix("PEER: ")
            .trim()
            .ifBlank { cleanTargetAddress }

        val callId = UUID.randomUUID().toString()
        val call = ActiveCall(
            callId = callId,
            peerUsername = cleanDisplayName,
            peerAddress = cleanTargetAddress,
            callType = callType,
            status = CallStatus.OUTGOING_CALLING,
            isIncoming = false,
            isVideoEnabled = callType == CallType.VIDEO,
            isSpeakerOn = callType == CallType.VIDEO
        )

        _currentCall.value = call
        activeCallTransport = if (networkStateManager.isInternetAvailable.value) "cloud" else "mesh"
        SoundEffectManager.startOutgoingRingtone()

        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        if (callType == CallType.VIDEO) {
            audioManager?.isSpeakerphoneOn = true
        }

        // 1. Signal call offer over Cloud Firestore
        try {
            val callData = hashMapOf(
                "callId" to callId,
                "callerId" to myUserId,
                "callerUsername" to myUsername,
                "targetId" to cleanTargetAddress,
                "targetUsername" to cleanTargetAddress,
                "callType" to callType.name,
                "status" to "ringing",
                "timestamp" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance().collection("calls").document(callId).set(callData)

            // Listen for remote answer/rejection on this call document
            activeCallDocListener?.remove()
            activeCallDocListener = FirebaseFirestore.getInstance().collection("calls").document(callId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val status = snapshot.getString("status")
                    if (status == "accepted" && _currentCall.value?.status == CallStatus.OUTGOING_CALLING) {
                        connectCall()
                    } else if (status == "rejected" || status == "ended") {
                        if (_currentCall.value != null && _currentCall.value?.status != CallStatus.ENDED) {
                            onCallEndedRemote(if (status == "rejected") "Call Declined" else "Call Ended")
                        }
                    }
                }
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
                    "callType" to callType.name
                )),
                dataType = "call_offer"
            )
            meshConnectionManagerProvider.get().sendPacket(meshPacket)
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
        transport: String = "mesh"
    ) {
        if (_currentCall.value != null) {
            Log.w(TAG, "Already in a call, ignoring incoming: $callId")
            return
        }

        val call = ActiveCall(
            callId = callId,
            peerUsername = peerUsername,
            peerAddress = peerAddress,
            callType = callType,
            status = CallStatus.INCOMING_RINGING,
            isIncoming = true,
            isVideoEnabled = callType == CallType.VIDEO,
            isSpeakerOn = callType == CallType.VIDEO
        )

        _currentCall.value = call
        activeCallTransport = transport
        SoundEffectManager.startIncomingRingtone()

        notificationHelperProvider.get().showIncomingCallNotification(
            peerUsername,
            if (callType == CallType.VIDEO) "Video" else "Voice"
        )

        // Listen for caller hang up before answer
        if (transport == "cloud") {
            activeCallDocListener?.remove()
            activeCallDocListener = FirebaseFirestore.getInstance().collection("calls").document(callId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val status = snapshot.getString("status")
                    if (status == "ended" && _currentCall.value?.status == CallStatus.INCOMING_RINGING) {
                        onCallEndedRemote("Caller Hung Up")
                    }
                }
        }
    }

    /**
     * Answers an incoming call.
     */
    fun acceptCall() {
        val call = _currentCall.value ?: return
        if (call.status == CallStatus.INCOMING_RINGING) {
            notificationHelperProvider.get().cancelIncomingCallNotification()
            SoundEffectManager.stopRingtone()

            // Update Cloud status to accepted
            try {
                FirebaseFirestore.getInstance().collection("calls").document(call.callId)
                    .set(mapOf("status" to "accepted"), SetOptions.merge())
            } catch (e: Exception) {
                Log.w(TAG, "Cloud update to accepted failed: ${e.message}")
            }

            // Signal acceptance over Mesh
            try {
                val answerPacket = MeshPacket(
                    packetId = UUID.randomUUID().toString(),
                    senderId = sessionManager.getUserId() ?: "local",
                    targetId = call.peerAddress,
                    data = call.callId,
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

        val now = System.currentTimeMillis()
        _currentCall.value = _currentCall.value?.copy(
            status = CallStatus.CONNECTED,
            startTimeMs = now,
            durationSeconds = 0L
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

        // Start full-duplex VoIP audio capture & playback
        startVoipAudio()
    }

    private fun startVoipAudio() {
        audioVoipEngine?.stop()
        audioVoipEngine = AudioVoipEngine(context) { pcmChunk ->
            val call = _currentCall.value
            if (call?.status == CallStatus.CONNECTED) {
                val b64 = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
                val packet = MeshPacket(
                    packetId = UUID.randomUUID().toString(),
                    senderId = sessionManager.getUserId() ?: "local",
                    targetId = call.peerAddress,
                    data = b64,
                    dataType = "call_audio"
                )
                meshConnectionManagerProvider.get().sendPacket(packet)
            }
        }
        audioVoipEngine?.setMuted(_currentCall.value?.isMuted == true)
        audioVoipEngine?.setSpeakerOn(_currentCall.value?.isSpeakerOn == true)
        audioVoipEngine?.start()
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

                    receiveIncomingCall(
                        peerUsername = callerUsername,
                        peerAddress = packet.senderId,
                        callId = callId,
                        callType = callType,
                        transport = "mesh"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed parsing call_offer packet: ${e.message}")
                }
            }
            "call_answer" -> {
                if (_currentCall.value?.callId == packet.data && _currentCall.value?.status == CallStatus.OUTGOING_CALLING) {
                    connectCall()
                }
            }
            "call_reject" -> {
                if (_currentCall.value?.callId == packet.data) {
                    onCallEndedRemote("Call Declined")
                }
            }
            "call_end" -> {
                if (_currentCall.value?.callId == packet.data) {
                    onCallEndedRemote("Call Ended")
                }
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
            it.copy(isSpeakerOn = nextState)
        }
    }

    /**
     * Toggles local video stream on/off.
     */
    fun toggleVideo() {
        _currentCall.value = _currentCall.value?.let {
            it.copy(isVideoEnabled = !it.isVideoEnabled)
        }
    }

    /**
     * Switches between front and back camera.
     */
    fun flipCamera() {
        _currentCall.value = _currentCall.value?.let {
            it.copy(isFrontCamera = !it.isFrontCamera)
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

        audioVoipEngine?.stop()
        audioVoipEngine = null

        audioManager?.mode = AudioManager.MODE_NORMAL
        audioManager?.isMicrophoneMute = false
        audioManager?.isSpeakerphoneOn = false

        activeCallDocListener?.remove()
        activeCallDocListener = null

        if (call != null && notifyRemote) {
            val finalStatus = if (call.status == CallStatus.INCOMING_RINGING) "rejected" else "ended"
            try {
                FirebaseFirestore.getInstance().collection("calls").document(call.callId)
                    .set(mapOf("status" to finalStatus), SetOptions.merge())
            } catch (_: Exception) {}

            try {
                val endPacket = MeshPacket(
                    packetId = UUID.randomUUID().toString(),
                    senderId = sessionManager.getUserId() ?: "local",
                    targetId = call.peerAddress,
                    data = call.callId,
                    dataType = if (call.status == CallStatus.INCOMING_RINGING) "call_reject" else "call_end"
                )
                meshConnectionManagerProvider.get().sendPacket(endPacket)
            } catch (_: Exception) {}
        }

        _currentCall.value = _currentCall.value?.copy(status = CallStatus.ENDED)

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

        audioVoipEngine?.stop()
        audioVoipEngine = null

        audioManager?.mode = AudioManager.MODE_NORMAL
        audioManager?.isMicrophoneMute = false
        audioManager?.isSpeakerphoneOn = false

        activeCallDocListener?.remove()
        activeCallDocListener = null

        _currentCall.value = _currentCall.value?.copy(status = CallStatus.ENDED)

        scope.launch {
            delay(1200)
            _currentCall.value = null
        }
    }
}
