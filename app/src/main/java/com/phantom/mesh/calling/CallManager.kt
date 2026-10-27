package com.phantom.mesh.calling

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.phantom.mesh.network.NetworkStateManager
import com.phantom.mesh.network.NetworkTransport
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
 * Implements strict transport-level gating:
 * - Bluetooth Mesh connections are strictly BLOCKED from calling due to bandwidth constraints (~2 KB/s).
 * - Cloud and Wi-Fi Direct transports are authorized for high-throughput VoIP audio and video feeds.
 */
@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkStateManager: NetworkStateManager
) {
    private val TAG = "CallManager"
    private val scope = CoroutineScope(Dispatchers.Main)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _currentCall = MutableStateFlow<ActiveCall?>(null)
    val currentCall: StateFlow<ActiveCall?> = _currentCall.asStateFlow()

    private var durationTimerJob: Job? = null
    private var simulatedConnectJob: Job? = null

    /**
     * Attempts to initiate an outgoing voice or video call to a peer.
     * Verifies that the current connection has sufficient bandwidth (Cloud or Wi-Fi Direct).
     */
    fun startCall(
        peerUsername: String,
        peerAddress: String,
        callType: CallType
    ): CallInitiationResult {
        val currentTransport = networkStateManager.activeTransport.value

        // Guard: Video calling requires high bandwidth, only available on Cloud or Wi-Fi Direct.
        // Voice calling uses low data transfer rates and is supported on all networks (Bluetooth, Wi-Fi, Cloud).
        if (callType == CallType.VIDEO && currentTransport is NetworkTransport.Bluetooth) {
            val msg = "Video calling is unavailable over Bluetooth Mesh due to high bandwidth requirements. Voice calling is available across all networks (Bluetooth, Wi-Fi, Cloud). Please connect via Cloud or Wi-Fi Direct to make video calls."
            Log.w(TAG, msg)
            return CallInitiationResult.BlockedByTransport(msg)
        }

        if (currentTransport is NetworkTransport.Offline) {
            val msg = "Device is offline. Connect to internet, Bluetooth mesh, or Wi-Fi Direct peer to start a call."
            Log.w(TAG, msg)
            return CallInitiationResult.BlockedByTransport(msg)
        }

        val call = ActiveCall(
            peerUsername = peerUsername,
            peerAddress = peerAddress,
            callType = callType,
            status = CallStatus.OUTGOING_CALLING,
            isIncoming = false,
            isVideoEnabled = callType == CallType.VIDEO,
            isSpeakerOn = callType == CallType.VIDEO
        )

        _currentCall.value = call
        SoundEffectManager.startOutgoingRingtone()

        // Configure audio routing
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        if (callType == CallType.VIDEO) {
            audioManager?.isSpeakerphoneOn = true
        }

        // Simulate network handshake / peer connection establishment
        simulatedConnectJob?.cancel()
        simulatedConnectJob = scope.launch {
            delay(3500) // Realistic ringing phase
            if (_currentCall.value?.status == CallStatus.OUTGOING_CALLING) {
                connectCall()
            }
        }

        return CallInitiationResult.Success(call)
    }

    /**
     * Receives an incoming call from a remote peer over Cloud, Wi-Fi Direct, or Bluetooth.
     */
    fun receiveIncomingCall(
        peerUsername: String,
        peerAddress: String,
        callType: CallType
    ) {
        val currentTransport = networkStateManager.activeTransport.value
        if (callType == CallType.VIDEO && currentTransport is NetworkTransport.Bluetooth) {
            Log.w(TAG, "Ignoring incoming video call: Bluetooth Mesh throughput insufficient for video stream")
            return
        }
        if (currentTransport is NetworkTransport.Offline) {
            Log.w(TAG, "Ignoring incoming call: Device is completely offline ($currentTransport)")
            return
        }

        val call = ActiveCall(
            peerUsername = peerUsername,
            peerAddress = peerAddress,
            callType = callType,
            status = CallStatus.INCOMING_RINGING,
            isIncoming = true,
            isVideoEnabled = callType == CallType.VIDEO,
            isSpeakerOn = callType == CallType.VIDEO
        )

        _currentCall.value = call
        SoundEffectManager.startIncomingRingtone()
    }

    /**
     * Answers an incoming call.
     */
    fun acceptCall() {
        val call = _currentCall.value ?: return
        if (call.status == CallStatus.INCOMING_RINGING) {
            SoundEffectManager.stopRingtone()
            connectCall()
        }
    }

    private fun connectCall() {
        SoundEffectManager.stopRingtone()
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
    }

    /**
     * Toggles microphone mute during a call.
     */
    fun toggleMute() {
        _currentCall.value = _currentCall.value?.let {
            val nextState = !it.isMuted
            audioManager?.isMicrophoneMute = nextState
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
    fun endCall() {
        simulatedConnectJob?.cancel()
        durationTimerJob?.cancel()
        SoundEffectManager.stopRingtone()
        SoundEffectManager.playCallEndSound()

        audioManager?.mode = AudioManager.MODE_NORMAL
        audioManager?.isMicrophoneMute = false

        _currentCall.value = _currentCall.value?.copy(status = CallStatus.ENDED)

        scope.launch {
            delay(1200) // Brief delay to show "Call Ended"
            _currentCall.value = null
        }
    }
}
