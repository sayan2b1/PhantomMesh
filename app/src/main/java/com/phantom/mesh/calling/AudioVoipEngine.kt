package com.phantom.mesh.calling

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ultra-low-latency VoIP Audio Engine for PhantomMesh.
 *
 * Implements full-duplex PCM 16kHz audio capture and streaming playback:
 * - Records 16-bit PCM mono at 16000 Hz in ~20ms frames (640 bytes).
 * - Plays incoming remote PCM audio packets with USAGE_VOICE_COMMUNICATION.
 * - Hardware acoustic echo suppression and communication mode routing.
 */
class AudioVoipEngine(
    private val context: Context,
    private val onAudioChunkReady: (chunk: ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "AudioVoipEngine"
        private const val SAMPLE_RATE = 8000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val READ_BUF_SIZE = 640 // Read in 40ms blocks
        private const val CHUNK_SIZE = 4800   // ~300ms chunks (4800 bytes = 2400 samples at 8kHz)
    }

    private val audioScope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private val isRunning = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
    }

    fun setSpeakerOn(speakerOn: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speakerDevice = audioManager?.availableCommunicationDevices?.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speakerOn && speakerDevice != null) {
                    audioManager?.setCommunicationDevice(speakerDevice)
                } else {
                    audioManager?.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.isSpeakerphoneOn = speakerOn
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to toggle speakerphone: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning.getAndSet(true)) return

        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

            // 1. Initialize AudioTrack for real-time playback
            val outMinBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)
            val outBufferSize = maxOf(outMinBufferSize, CHUNK_SIZE * 2)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val formatOut = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_OUT)
                .setEncoding(AUDIO_FORMAT)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(formatOut)
                .setBufferSizeInBytes(outBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            Log.i(TAG, "AudioTrack initialized and playing at $SAMPLE_RATE Hz")

            // 2. Check record audio permission before capturing
            val hasMicPerm = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasMicPerm) {
                Log.w(TAG, "RECORD_AUDIO permission not granted, skipping capture")
                return
            }

            val inMinBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
            val inBufferSize = maxOf(inMinBufferSize, CHUNK_SIZE * 2)

            // Multi-source fallback (VOICE_COMMUNICATION -> MIC -> DEFAULT) for max hardware compatibility
            var record: AudioRecord? = null
            val audioSources = intArrayOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT
            )
            for (source in audioSources) {
                try {
                    val candidate = AudioRecord(
                        source,
                        SAMPLE_RATE,
                        CHANNEL_IN,
                        AUDIO_FORMAT,
                        inBufferSize
                    )
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        record = candidate
                        Log.i(TAG, "AudioRecord initialized using audio source: $source")
                        break
                    } else {
                        candidate.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AudioSource $source failed: ${e.message}")
                }
            }

            if (record == null) {
                Log.e(TAG, "AudioRecord failed to initialize across all audio sources")
                return
            }

            audioRecord = record
            audioRecord?.startRecording()
            Log.i(TAG, "AudioRecord started capture at $SAMPLE_RATE Hz")

            // 3. Audio capture loop with 300ms chunking
            recordingJob = audioScope.launch {
                val readBuf = ByteArray(READ_BUF_SIZE)
                val chunkStream = java.io.ByteArrayOutputStream(CHUNK_SIZE)
                while (isActive && isRunning.get()) {
                    val read = audioRecord?.read(readBuf, 0, READ_BUF_SIZE) ?: -1
                    if (read > 0) {
                        if (!isMuted.get()) {
                            chunkStream.write(readBuf, 0, read)
                            if (chunkStream.size() >= CHUNK_SIZE) {
                                val chunk = chunkStream.toByteArray()
                                chunkStream.reset()
                                onAudioChunkReady(chunk)
                            }
                        } else {
                            chunkStream.reset()
                        }
                    } else {
                        kotlinx.coroutines.delay(10)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioVoipEngine: ${e.message}", e)
        }
    }

    /**
     * Feeds incoming PCM audio from remote peer into the AudioTrack.
     */
    fun playRemoteAudio(pcmData: ByteArray) {
        if (!isRunning.get() || pcmData.isEmpty()) return
        audioScope.launch {
            try {
                audioTrack?.write(pcmData, 0, pcmData.size)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write PCM buffer to AudioTrack: ${e.message}")
            }
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack: ${e.message}")
        }
        audioTrack = null

        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting AudioManager mode: ${e.message}")
        }

        Log.i(TAG, "AudioVoipEngine stopped and resources released")
    }
}
