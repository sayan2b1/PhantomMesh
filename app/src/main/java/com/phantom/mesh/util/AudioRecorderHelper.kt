package com.phantom.mesh.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
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
import java.util.UUID

/**
 * Handles capturing crystal-clear AAC/MPEG-4 voice messages.
 * Emits live duration and normalized amplitude (0.0 to 1.0) for real-time waveform animations.
 */
class AudioRecorderHelper {

    companion object {
        private const val TAG = "AudioRecorderHelper"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recordStartTime: Long = 0L
    private var tickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    fun startRecording(context: Context): Boolean {
        if (_isRecording.value) return false

        return try {
            val voiceDir = File(context.filesDir, "voice_notes").apply { mkdirs() }
            val outputFile = File(voiceDir, "voice_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.m4a")

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            currentOutputFile = outputFile
            recordStartTime = System.currentTimeMillis()
            _isRecording.value = true
            _recordingDurationMs.value = 0L
            _currentAmplitude.value = 0f

            tickerJob = scope.launch {
                while (isActive && _isRecording.value) {
                    delay(80)
                    _recordingDurationMs.value = System.currentTimeMillis() - recordStartTime
                    try {
                        val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                        val normalized = (maxAmp / 32767f).coerceIn(0f, 1f)
                        _currentAmplitude.value = normalized
                    } catch (ignored: Exception) {}
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording: ${e.message}", e)
            cleanup()
            false
        }
    }

    /**
     * Stops recording and returns the recorded file along with its duration in milliseconds.
     */
    fun stopRecording(): Pair<File, Long>? {
        if (!_isRecording.value) return null

        val duration = System.currentTimeMillis() - recordStartTime
        val file = currentOutputFile

        cleanup()

        return if (file != null && file.exists() && file.length() > 0 && duration >= 600) {
            Pair(file, duration)
        } else {
            file?.delete()
            null
        }
    }

    /**
     * Cancels recording and deletes the temporary file.
     */
    fun cancelRecording() {
        val file = currentOutputFile
        cleanup()
        try {
            file?.delete()
        } catch (ignored: Exception) {}
    }

    private fun cleanup() {
        tickerJob?.cancel()
        tickerJob = null
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (ignored: Exception) {}
                release()
            }
        } catch (ignored: Exception) {}
        mediaRecorder = null
        _isRecording.value = false
        _recordingDurationMs.value = 0L
        _currentAmplitude.value = 0f
    }
}
