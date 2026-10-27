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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ultra-low-latency VoIP Audio Engine for PhantomMesh.
 *
 * Implements full-duplex PCM 8kHz audio capture and streaming playback:
 * - Records 16-bit PCM mono at 8000 Hz in ~300ms chunks (4800 bytes).
 * - Plays incoming remote PCM audio packets with USAGE_VOICE_COMMUNICATION.
 * - Hardware acoustic echo suppression and communication mode routing.
 * - Thread-safe serialized playback channel eliminating native AudioTrack concurrency crashes.
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
        private const val READ_BUF_SIZE = 320 // Read in 20ms blocks
        private const val CHUNK_SIZE = 1600   // ~100ms chunks (1600 bytes = 800 samples at 8kHz) for ultra-low latency
    }

    object G711 {
        private const val BIAS = 0x84
        private const val CLIP = 32635

        fun encode(pcm: ByteArray): ByteArray {
            val out = ByteArray(pcm.size / 2)
            var outIdx = 0
            for (i in 0 until pcm.size - 1 step 2) {
                val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
                var sign = (sample shr 8) and 0x80
                var p = sample
                if (sign != 0) p = -p
                if (p > CLIP) p = CLIP
                p += BIAS
                var exp = 7
                var mask = 0x4000
                while (exp > 0 && (p and mask) == 0) {
                    exp--
                    mask = mask shr 1
                }
                val mantissa = (p shr (exp + 3)) and 0x0F
                val muLaw = sign or (exp shl 4) or mantissa
                out[outIdx++] = (muLaw xor 0xFF).toByte()
            }
            return out
        }

        fun decode(muLaw: ByteArray): ByteArray {
            val out = ByteArray(muLaw.size * 2)
            var outIdx = 0
            for (b in muLaw) {
                val mu = (b.toInt() xor 0xFF) and 0xFF
                val sign = mu and 0x80
                val exp = (mu shr 4) and 0x07
                val mantissa = mu and 0x0F
                var sample = ((mantissa shl 3) + BIAS) shl exp
                sample -= BIAS
                val finalSample = if (sign != 0) -sample else sample
                out[outIdx++] = (finalSample and 0xFF).toByte()
                out[outIdx++] = (finalSample shr 8).toByte()
            }
            return out
        }

        fun linearToMuLaw(pcm: ByteArray): ByteArray = encode(pcm)
        fun muLawToLinear(muLaw: ByteArray): ByteArray = decode(muLaw)
    }

    private val audioScope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null

    // Serialized ultra-low-latency playback queue (capacity = 8 for smooth cellular jitter handling)
    private val playbackChannel = Channel<ByteArray>(capacity = 8)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private val isRunning = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)
    private var currentSpeakerOn: Boolean = true

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
    }

    fun setSpeakerOn(speakerOn: Boolean) {
        currentSpeakerOn = speakerOn
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (speakerOn) {
                    val speakerDevice = audioManager?.availableCommunicationDevices?.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        audioManager?.setCommunicationDevice(speakerDevice)
                    }
                } else {
                    val earpieceDevice = audioManager?.availableCommunicationDevices?.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    if (earpieceDevice != null) {
                        audioManager?.setCommunicationDevice(earpieceDevice)
                    } else {
                        audioManager?.clearCommunicationDevice()
                    }
                }
            }

            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = speakerOn

            // Boost call stream volume to ensure crystal clear audibility on both earpiece and speaker
            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) ?: 1
            val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_VOICE_CALL) ?: 0
            if (currentVol < (maxVol * 0.75).toInt()) {
                audioManager?.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVol * 0.9).toInt(), 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to toggle speakerphone: ${e.message}")
        }
    }

    private fun applyDigitalGain(buffer: ByteArray, bytesRead: Int, gain: Float = 1.8f) {
        for (i in 0 until bytesRead - 1 step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val boosted = (sample.toShort() * gain).toInt().coerceIn(-32767, 32767)
            buffer[i] = (boosted and 0xFF).toByte()
            buffer[i + 1] = (boosted shr 8).toByte()
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning.getAndSet(true)) return

        try {
            try {
                audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            } catch (e: Exception) {
                Log.w(TAG, "Failed setting audio mode to MODE_IN_COMMUNICATION: ${e.message}")
            }

            // Apply speaker routing immediately after setting MODE_IN_COMMUNICATION to avoid OEM reset
            setSpeakerOn(currentSpeakerOn)

            // 1. Initialize AudioTrack for real-time playback
            val outMinBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)
            val outBufferSize = maxOf(outMinBufferSize, CHUNK_SIZE * 4)

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

            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.setVolume(AudioTrack.getMaxVolume())
                audioTrack?.play()
                Log.i(TAG, "AudioTrack initialized and playing at $SAMPLE_RATE Hz")
            } else {
                Log.e(TAG, "AudioTrack failed initialization with state: ${audioTrack?.state}")
            }

            // 2. Start dedicated single-thread playback consumer to serialize AudioTrack.write()
            playbackJob = audioScope.launch {
                for (chunk in playbackChannel) {
                    if (!isActive || !isRunning.get()) break
                    val track = audioTrack ?: continue
                    try {
                        if (track.state == AudioTrack.STATE_INITIALIZED && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            track.write(chunk, 0, chunk.size)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing PCM buffer to AudioTrack: ${e.message}")
                    }
                }
            }

            // 3. Check record audio permission before capturing
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

            // 4. Audio capture loop with 100ms chunking and digital gain boost
            recordingJob = audioScope.launch {
                val readBuf = ByteArray(READ_BUF_SIZE)
                val chunkStream = java.io.ByteArrayOutputStream(CHUNK_SIZE)
                while (isActive && isRunning.get()) {
                    val recordInst = audioRecord
                    if (recordInst == null || recordInst.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        kotlinx.coroutines.delay(20)
                        continue
                    }
                    val read = try {
                        recordInst.read(readBuf, 0, READ_BUF_SIZE)
                    } catch (e: Exception) {
                        -1
                    }
                    if (read > 0) {
                        if (!isMuted.get()) {
                            applyDigitalGain(readBuf, read, 1.8f)
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
     * Feeds incoming PCM audio from remote peer into the serialized AudioTrack queue.
     * Completely non-blocking and safe from concurrent native write collisions.
     */
    fun playRemoteAudio(pcmData: ByteArray) {
        if (!isRunning.get() || pcmData.isEmpty()) return
        try {
            val result = playbackChannel.trySend(pcmData)
            if (result.isFailure) {
                // Buffer is full (network burst): drop the oldest packet and enqueue the newest
                playbackChannel.tryReceive()
                playbackChannel.trySend(pcmData)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Dropping audio frame: ${e.message}")
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        recordingJob?.cancel()
        recordingJob = null

        playbackJob?.cancel()
        playbackJob = null

        // Drain any pending audio chunks
        while (playbackChannel.tryReceive().isSuccess) {}

        try {
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null

        try {
            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.stop()
            }
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack: ${e.message}")
        }
        audioTrack = null

        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager?.clearCommunicationDevice()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resetting AudioManager mode: ${e.message}")
        }

        Log.i(TAG, "AudioVoipEngine stopped and resources released")
    }
}
