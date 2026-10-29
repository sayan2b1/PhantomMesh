package com.phantom.mesh.util

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.mesh.MeshConnectionManager
import com.phantom.mesh.mesh.MeshPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Coordinates typing and voice recording indicators across both Mesh and Cloud.
 *
 * Outgoing:
 * - Rate-limits typing pulses (max 1 pulse per 2.5s while user types).
 * - Debounces user typing: automatically reverts to IDLE 3.5s after the last keystroke.
 * - Dispatches voice recording state while user holds or uses the voice recorder.
 * - Cleans up to IDLE when messages are sent or user leaves the chat screen.
 *
 * Incoming:
 * - Tracks peer typing and recording states.
 * - Automatically expires peer typing after 5s and recording after 35s if no follow-up arrived.
 */
@Singleton
class TypingStatusManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val meshConnectionManagerProvider: Provider<MeshConnectionManager>
) {
    companion object {
        private const val TAG = "TypingStatusManager"
    }

    enum class TypingState {
        IDLE,
        TYPING,
        RECORDING
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Map of chatId -> TypingState
    private val _peerTypingStates = MutableStateFlow<Map<String, TypingState>>(emptyMap())
    val peerTypingStates: StateFlow<Map<String, TypingState>> = _peerTypingStates.asStateFlow()

    // Automatic expiration jobs for incoming peer indicators
    private val peerTimeoutJobs = ConcurrentHashMap<String, Job>()

    // Local outgoing debounce job
    private var localDebounceJob: Job? = null
    private var lastOutgoingTypingPulseMs = 0L

    /**
     * Returns a reactive flow for a specific chat's peer typing state.
     */
    fun getPeerTypingState(chatId: String): Flow<TypingState> {
        return _peerTypingStates.map { it[chatId] ?: TypingState.IDLE }
    }

    /**
     * Updates an incoming peer's typing or recording state.
     * Automatically registers an auto-revert timer to IDLE.
     */
    fun setPeerTypingState(chatId: String, peerIdOrUsername: String, state: TypingState) {
        if (chatId.isBlank()) return

        val current = _peerTypingStates.value[chatId] ?: TypingState.IDLE
        if (current != state) {
            _peerTypingStates.value = _peerTypingStates.value + (chatId to state)
        }

        peerTimeoutJobs[chatId]?.cancel()

        when (state) {
            TypingState.TYPING -> {
                peerTimeoutJobs[chatId] = scope.launch {
                    delay(5000)
                    _peerTypingStates.value = _peerTypingStates.value + (chatId to TypingState.IDLE)
                }
            }
            TypingState.RECORDING -> {
                peerTimeoutJobs[chatId] = scope.launch {
                    delay(35000)
                    _peerTypingStates.value = _peerTypingStates.value + (chatId to TypingState.IDLE)
                }
            }
            TypingState.IDLE -> {
                peerTimeoutJobs.remove(chatId)
            }
        }
    }

    /**
     * Called when the local user types in the input bar.
     * Emits a typing pulse every 2.5s and sets a 3.5s debounce to automatically send IDLE.
     */
    fun setUserTyping(chatId: String, targetId: String) {
        val now = System.currentTimeMillis()
        if (now - lastOutgoingTypingPulseMs > 2500L) {
            lastOutgoingTypingPulseMs = now
            dispatchOutgoingStatus(chatId, targetId, TypingState.TYPING)
        }

        localDebounceJob?.cancel()
        localDebounceJob = scope.launch {
            delay(3500)
            setUserIdle(chatId, targetId)
        }
    }

    /**
     * Called when the local user starts recording a voice note.
     */
    fun setUserRecording(chatId: String, targetId: String) {
        localDebounceJob?.cancel()
        localDebounceJob = null
        lastOutgoingTypingPulseMs = 0L
        dispatchOutgoingStatus(chatId, targetId, TypingState.RECORDING)
    }

    /**
     * Called when the local user stops/cancels recording, sends a message, or leaves the chat.
     */
    fun setUserIdle(chatId: String, targetId: String) {
        localDebounceJob?.cancel()
        localDebounceJob = null
        lastOutgoingTypingPulseMs = 0L
        dispatchOutgoingStatus(chatId, targetId, TypingState.IDLE)
    }

    private fun dispatchOutgoingStatus(chatId: String, targetId: String, state: TypingState) {
        scope.launch {
            try {
                val myId = sessionManager.getUserId() ?: ""
                val myName = sessionManager.getUsername() ?: myId
                val statusStr = when (state) {
                    TypingState.TYPING -> "typing"
                    TypingState.RECORDING -> "recording"
                    TypingState.IDLE -> "idle"
                }

                // 1. Dispatch over Cloud (Firestore)
                val firestore = FirebaseFirestore.getInstance()
                val cloudPayload = mapOf(
                    "typing_${myName.lowercase()}" to statusStr,
                    "typing_${myId.lowercase()}" to statusStr,
                    "typingTime_${myName.lowercase()}" to System.currentTimeMillis(),
                    "typingTime_${myId.lowercase()}" to System.currentTimeMillis()
                )
                firestore.collection("chats")
                    .document(chatId)
                    .set(cloudPayload, SetOptions.merge())
                    .addOnFailureListener {
                        // Suppress cloud errors if operating purely in offline mesh
                    }

                // 2. Dispatch over Mesh (Nearby Connections)
                val cleanTarget = targetId.split(",")
                    .map { it.trim() }
                    .firstOrNull { it.isNotBlank() && !it.equals(myId, ignoreCase = true) && !it.equals(myName, ignoreCase = true) }
                    ?: targetId.trim()

                val meshPacket = MeshPacket(
                    packetId = "typ_${UUID.randomUUID().toString().take(8)}",
                    senderId = myName,
                    targetId = cleanTarget,
                    data = statusStr,
                    dataType = "typing_status"
                )
                meshConnectionManagerProvider.get().sendPacket(meshPacket)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to dispatch typing status: ${e.message}")
            }
        }
    }
}

