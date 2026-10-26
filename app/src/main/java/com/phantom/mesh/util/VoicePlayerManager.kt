package com.phantom.mesh.util

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Singleton or reusable manager for playing voice messages.
 * Automatically resolves local files or inline data URIs and emits playback progress.
 */
class VoicePlayerManager {

    companion object {
        private const val TAG = "VoicePlayerManager"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _activePlayingMessageId = MutableStateFlow<String?>(null)
    val activePlayingMessageId: StateFlow<String?> = _activePlayingMessageId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(0)
    val totalDurationMs: StateFlow<Int> = _totalDurationMs.asStateFlow()

    fun togglePlay(context: Context, messageId: String, localUri: String?, cloudUrl: String?) {
        if (_activePlayingMessageId.value == messageId && _isPlaying.value) {
            pause()
            return
        }

        if (_activePlayingMessageId.value == messageId && mediaPlayer != null && !_isPlaying.value) {
            resume()
            return
        }

        stop()

        val resolvedFile = resolveAudioSource(context, messageId, localUri, cloudUrl)
        if (resolvedFile == null || !resolvedFile.exists()) {
            Log.w(TAG, "Audio file could not be resolved for message $messageId")
            return
        }

        try {
            val player = MediaPlayer().apply {
                setDataSource(resolvedFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    stop()
                }
            }

            mediaPlayer = player
            _activePlayingMessageId.value = messageId
            _totalDurationMs.value = player.duration.coerceAtLeast(1)
            player.start()
            _isPlaying.value = true

            startProgressTracker()
        } catch (e: Exception) {
            Log.e(TAG, "Failed playing voice message: ${e.message}", e)
            stop()
        }
    }

    private fun resume() {
        try {
            mediaPlayer?.start()
            _isPlaying.value = true
            startProgressTracker()
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming player: ${e.message}", e)
            stop()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (ignored: Exception) {}
        _isPlaying.value = false
        progressJob?.cancel()
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (ignored: Exception) {}
        mediaPlayer = null
        _activePlayingMessageId.value = null
        _isPlaying.value = false
        _playbackProgress.value = 0f
        _currentPositionMs.value = 0
        _totalDurationMs.value = 0
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                delay(60)
                try {
                    val player = mediaPlayer ?: break
                    if (player.isPlaying) {
                        val current = player.currentPosition
                        val total = player.duration.coerceAtLeast(1)
                        _currentPositionMs.value = current
                        _totalDurationMs.value = total
                        _playbackProgress.value = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    }
                } catch (ignored: Exception) {}
            }
        }
    }

    private fun resolveAudioSource(context: Context, messageId: String, localUri: String?, cloudUrl: String?): File? {
        if (!localUri.isNullOrBlank()) {
            val f = File(localUri)
            if (f.exists() && f.length() > 0) return f
        }

        val voiceDir = File(context.filesDir, "voice_notes").apply { mkdirs() }
        val targetFile = File(voiceDir, "${messageId}.m4a")
        if (targetFile.exists() && targetFile.length() > 0) return targetFile

        if (!cloudUrl.isNullOrBlank() && cloudUrl.contains("base64,")) {
            try {
                val b64 = cloudUrl.substringAfter("base64,")
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                if (bytes.isNotEmpty()) {
                    targetFile.writeBytes(bytes)
                    return targetFile
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed decoding base64 audio: ${e.message}", e)
            }
        }
        return null
    }
}
