package com.phantom.mesh.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
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
 * Singleton manager for playing voice notes and audio messages.
 * Guarantees that only ONE voice message can EVER play at any given time.
 * If playback is triggered for another voice message, any active or buffering
 * playback is immediately stopped and released before starting the newly selected message.
 */
object VoicePlayerManager {

    private const val TAG = "VoicePlayerManager"
    private val lock = Any()

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
        synchronized(lock) {
            // If the same message is currently playing -> pause it
            if (_activePlayingMessageId.value == messageId && _isPlaying.value) {
                pause()
                return
            }

            // If the same message was paused -> resume it
            if (_activePlayingMessageId.value == messageId && mediaPlayer != null && !_isPlaying.value) {
                resume()
                return
            }

            // A different voice message was selected:
            // Immediately stop, reset and release the previous player
            stopInternal()

            _activePlayingMessageId.value = messageId

            val resolvedFile = resolveAudioSource(context, messageId, localUri, cloudUrl)
            if (resolvedFile != null && resolvedFile.exists()) {
                try {
                    val player = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(resolvedFile.absolutePath)
                        prepare()
                        setOnCompletionListener {
                            stop()
                        }
                        setOnErrorListener { _, _, _ ->
                            stop()
                            true
                        }
                    }

                    mediaPlayer = player
                    _totalDurationMs.value = player.duration.coerceAtLeast(1)
                    requestAudioFocus(context)
                    player.start()
                    _isPlaying.value = true
                    startProgressTracker()
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Failed playing local audio for $messageId: ${e.message}", e)
                    stopInternal()
                }
            }

            if (cloudUrl?.startsWith("http") == true) {
                try {
                    val targetMessageId = messageId
                    val player = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(cloudUrl)
                        setOnPreparedListener { mp ->
                            synchronized(lock) {
                                // Strictly ensure user didn't switch to another voice message while buffering
                                if (_activePlayingMessageId.value != targetMessageId) {
                                    try {
                                        mp.setOnPreparedListener(null)
                                        mp.setOnCompletionListener(null)
                                        mp.setOnErrorListener(null)
                                        mp.reset()
                                        mp.release()
                                    } catch (_: Exception) {}
                                    return@setOnPreparedListener
                                }
                                try {
                                    requestAudioFocus(context)
                                    mp.start()
                                    _isPlaying.value = true
                                    _totalDurationMs.value = mp.duration.coerceAtLeast(1)
                                    startProgressTracker()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error starting remote stream for $targetMessageId: ${e.message}", e)
                                    stopInternal()
                                }
                            }
                        }
                        setOnCompletionListener {
                            stop()
                        }
                        setOnErrorListener { mp, _, _ ->
                            synchronized(lock) {
                                try {
                                    mp.reset()
                                    mp.release()
                                } catch (_: Exception) {}
                                if (_activePlayingMessageId.value == targetMessageId) {
                                    stopInternal()
                                }
                            }
                            true
                        }
                        prepareAsync()
                    }
                    mediaPlayer = player
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Failed streaming remote audio for $messageId: ${e.message}", e)
                    stopInternal()
                }
            }

            Log.w(TAG, "Audio source could not be resolved for message $messageId")
            stopInternal()
        }
    }

    private fun resume() {
        synchronized(lock) {
            try {
                mediaPlayer?.start()
                _isPlaying.value = true
                startProgressTracker()
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming player: ${e.message}", e)
                stopInternal()
            }
        }
    }

    fun pause() {
        synchronized(lock) {
            try {
                mediaPlayer?.pause()
            } catch (ignored: Exception) {}
            _isPlaying.value = false
            progressJob?.cancel()
        }
    }

    private fun stopInternal() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.apply {
                setOnPreparedListener(null)
                setOnCompletionListener(null)
                setOnErrorListener(null)
                try {
                    if (isPlaying) stop()
                } catch (_: Exception) {}
                try {
                    reset()
                } catch (_: Exception) {}
                try {
                    release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        _activePlayingMessageId.value = null
        _isPlaying.value = false
        _playbackProgress.value = 0f
        _currentPositionMs.value = 0
        _totalDurationMs.value = 0
    }

    fun stop() {
        synchronized(lock) {
            stopInternal()
        }
    }

    private fun requestAudioFocus(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (ignored: Exception) {}
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
