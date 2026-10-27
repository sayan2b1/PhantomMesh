package com.phantom.mesh.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * High-performance sound effects engine utilizing synthetic PCM AudioTrack generation.
 *
 * Provides zero-dependency, ultra-low-latency audio feedback:
 * - WhatsApp-style sent message sound (subtle high pop)
 * - WhatsApp-style incoming message chime
 * - Outgoing and incoming calling ringtones
 * - Call ended tone
 */
object SoundEffectManager {
    private const val TAG = "SoundEffectManager"
    private const val SAMPLE_RATE = 44100

    private val audioScope = CoroutineScope(Dispatchers.Default)
    private var ringtoneJob: Job? = null

    /**
     * Plays a subtle, snappy "sent" pop sound (similar to WhatsApp message sent).
     */
    fun playMessageSentSound() {
        audioScope.launch {
            try {
                val durationMs = 60
                val totalSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(totalSamples)

                for (i in 0 until totalSamples) {
                    val progress = i.toDouble() / totalSamples
                    // Pitch sweep from 1500Hz up to 2200Hz with fast decay envelope
                    val freq = 1500.0 + (700.0 * progress)
                    val envelope = 1.0 - progress
                    val sample = sin(2.0 * PI * freq * i / SAMPLE_RATE) * envelope * 0.4
                    buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }

                playPcmBuffer(buffer, SAMPLE_RATE)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play sent sound: ${e.message}")
            }
        }
    }

    /**
     * Plays a crisp, melodic incoming message chime (dual tone A5 -> E6).
     */
    fun playMessageReceivedSound() {
        audioScope.launch {
            try {
                val durationMs = 180
                val totalSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
                val half = totalSamples / 2
                val buffer = ShortArray(totalSamples)

                // Note 1: 900 Hz with fast attack and decay
                for (i in 0 until half) {
                    val progress = i.toDouble() / half
                    val envelope = (1.0 - progress) * (if (i < 100) i / 100.0 else 1.0)
                    val sample = sin(2.0 * PI * 900.0 * i / SAMPLE_RATE) * envelope * 0.5
                    buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }

                // Note 2: 1350 Hz chime
                for (i in half until totalSamples) {
                    val rel = i - half
                    val progress = rel.toDouble() / half
                    val envelope = (1.0 - progress) * (if (rel < 100) rel / 100.0 else 1.0)
                    val sample = sin(2.0 * PI * 1350.0 * rel / SAMPLE_RATE) * envelope * 0.55
                    buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }

                playPcmBuffer(buffer, SAMPLE_RATE)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play received sound: ${e.message}")
            }
        }
    }

    /**
     * Loops an outgoing calling ringback tone (intermittent beep beep).
     */
    fun startOutgoingRingtone() {
        stopRingtone()
        ringtoneJob = audioScope.launch {
            try {
                val beepSamples = (SAMPLE_RATE * 0.4).toInt() // 400ms tone
                val buffer = ShortArray(beepSamples)
                for (i in 0 until beepSamples) {
                    val env = if (i < 200) i / 200.0 else if (i > beepSamples - 200) (beepSamples - i) / 200.0 else 1.0
                    val sample = (sin(2.0 * PI * 440.0 * i / SAMPLE_RATE) + sin(2.0 * PI * 480.0 * i / SAMPLE_RATE)) * 0.25 * env
                    buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }

                while (isActive) {
                    playPcmBuffer(buffer, SAMPLE_RATE)
                    delay(2500)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Outgoing ringtone stopped: ${e.message}")
            }
        }
    }

    /**
     * Loops an incoming call ringtone (melodic cyber trill).
     */
    fun startIncomingRingtone() {
        stopRingtone()
        ringtoneJob = audioScope.launch {
            try {
                val toneDurationMs = 800
                val totalSamples = (SAMPLE_RATE * (toneDurationMs / 1000.0)).toInt()
                val buffer = ShortArray(totalSamples)

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / SAMPLE_RATE
                    // Rapid vibrato frequency modulation between 650Hz and 850Hz
                    val modFreq = 750.0 + 100.0 * sin(2.0 * PI * 8.0 * t)
                    val env = if (i < 400) i / 400.0 else if (i > totalSamples - 400) (totalSamples - i) / 400.0 else 1.0
                    val sample = sin(2.0 * PI * modFreq * t) * 0.45 * env
                    buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }

                while (isActive) {
                    playPcmBuffer(buffer, SAMPLE_RATE)
                    delay(1500)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Incoming ringtone stopped: ${e.message}")
            }
        }
    }

    fun stopRingtone() {
        ringtoneJob?.cancel()
        ringtoneJob = null
    }

    /**
     * Plays a short descending tone indicating call disconnected.
     */
    fun playCallEndSound() {
        stopRingtone()
        audioScope.launch {
            try {
                val durationMs = 250
                val totalSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(totalSamples)

                for (i in 0 until totalSamples) {
                    val progress = i.toDouble() / totalSamples
                    val freq = 480.0 - (200.0 * progress)
                    val envelope = 1.0 - progress
                    val sample = sin(2.0 * PI * freq * i / SAMPLE_RATE) * envelope * 0.4
                    buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }

                playPcmBuffer(buffer, SAMPLE_RATE)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play call end sound: ${e.message}")
            }
        }
    }

    private fun playPcmBuffer(buffer: ShortArray, sampleRate: Int) {
        var track: AudioTrack? = null
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufSize, buffer.size * 2)

            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()

            val playTimeMs = (buffer.size * 1000L / sampleRate) + 50L
            Thread.sleep(playTimeMs)
        } catch (e: Exception) {
            Log.d(TAG, "AudioTrack playback error: ${e.message}")
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (_: Exception) {}
        }
    }
}
