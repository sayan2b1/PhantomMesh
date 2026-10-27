package com.phantom.mesh.calling

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ultra-low-latency (<5ms) Peer-to-Peer LAN VoIP Engine for PhantomMesh.
 *
 * Automatically activated when both peers are on the same Wi-Fi network:
 * - Direct UDP streaming between local device IP addresses (zero internet consumption).
 * - MTU-safe chunking (<= 1200 bytes) eliminating Wi-Fi router IP fragmentation packet loss.
 * - G.711 mu-law audio compression (800 bytes / chunk) fitting inside a single MTU frame.
 * - Out-of-order and stale frame rejection for continuous real-time video playback.
 */
class LanVoipEngine(
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onVideoReceived: (Bitmap) -> Unit
) {
    companion object {
        private const val TAG = "LanVoipEngine"
        const val DEFAULT_PORT = 19888
        private const val TYPE_AUDIO: Byte = 1
        private const val TYPE_VIDEO_CHUNK: Byte = 2
        private const val TYPE_PING: Byte = 3
        private const val TYPE_PONG: Byte = 4

        private const val MAX_CHUNK_PAYLOAD = 1180 // Safe MTU size (< 1500 bytes on all Wi-Fi routers)
        private const val RECV_BUF_SIZE = 65507
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var socket: DatagramSocket? = null
    private var receiverJob: Job? = null

    private var targetAddress: InetAddress? = null
    private var targetPort: Int = DEFAULT_PORT

    private val isRunning = AtomicBoolean(false)
    private var audioSeq = 0
    private var videoSeq = 0

    // Frame assembly buffer for multi-chunk video frames: seq -> Map<chunkIndex, chunkData>
    private class InFlightFrame(val totalChunks: Int, val timestamp: Long = System.currentTimeMillis()) {
        val chunks = ConcurrentHashMap<Int, ByteArray>()
    }
    private val inFlightFrames = ConcurrentHashMap<Int, InFlightFrame>()
    private var lastRenderedVideoSeq = -1

    fun start(localPort: Int = DEFAULT_PORT, peerIp: String, peerPort: Int = DEFAULT_PORT) {
        if (isRunning.getAndSet(true)) return

        try {
            targetAddress = InetAddress.getByName(peerIp)
            targetPort = peerPort

            // Bind to local UDP port with socket reuse and expanded buffers
            socket = DatagramSocket(localPort).apply {
                reuseAddress = true
                sendBufferSize = 256 * 1024
                receiveBufferSize = 256 * 1024
            }

            Log.i(TAG, "LanVoipEngine bound to UDP port $localPort, targeting $peerIp:$peerPort")

            // Send initial ping to punch local router firewall/NAT if needed
            sendControlPacket(TYPE_PING)

            // Start UDP receiver loop
            receiverJob = scope.launch {
                val recvBuf = ByteArray(RECV_BUF_SIZE)
                val packet = DatagramPacket(recvBuf, recvBuf.size)

                while (isActive && isRunning.get()) {
                    try {
                        socket?.receive(packet)
                        val length = packet.length
                        if (length < 5) continue

                        val type = recvBuf[0]

                        when (type) {
                            TYPE_AUDIO -> {
                                // Header: [TYPE(1), SEQ(4), G711_AUDIO...]
                                val payloadLength = length - 5
                                if (payloadLength > 0) {
                                    val muLaw = ByteArray(payloadLength)
                                    System.arraycopy(recvBuf, 5, muLaw, 0, payloadLength)
                                    val pcm = AudioVoipEngine.G711.muLawToLinear(muLaw)
                                    onAudioReceived(pcm)
                                }
                            }
                            TYPE_VIDEO_CHUNK -> {
                                // Header: [TYPE(1), SEQ(4), CHUNK_IDX(2), TOTAL_CHUNKS(2), DATA...]
                                if (length >= 9) {
                                    val bb = ByteBuffer.wrap(recvBuf, 1, 8)
                                    val seq = bb.int
                                    val chunkIdx = bb.short.toInt() and 0xFFFF
                                    val totalChunks = bb.short.toInt() and 0xFFFF
                                    val chunkPayloadLen = length - 9

                                    if (seq > lastRenderedVideoSeq || (lastRenderedVideoSeq - seq > 2000)) {
                                        val chunkBytes = ByteArray(chunkPayloadLen)
                                        System.arraycopy(recvBuf, 9, chunkBytes, 0, chunkPayloadLen)

                                        handleVideoChunk(seq, chunkIdx, totalChunks, chunkBytes)
                                    }
                                }
                            }
                            TYPE_PING -> {
                                sendControlPacket(TYPE_PONG)
                            }
                            TYPE_PONG -> {
                                Log.i(TAG, "LAN P2P connection verified with $peerIp")
                            }
                        }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            Log.d(TAG, "UDP receive loop error: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start LanVoipEngine: ${e.message}", e)
            stop()
        }
    }

    private fun handleVideoChunk(seq: Int, chunkIdx: Int, totalChunks: Int, data: ByteArray) {
        if (totalChunks <= 1) {
            // Single chunk frame — decode immediately
            lastRenderedVideoSeq = seq
            inFlightFrames.clear()
            decodeAndEmitVideo(data)
            return
        }

        // Multi-chunk frame assembly
        val frame = inFlightFrames.computeIfAbsent(seq) { InFlightFrame(totalChunks) }
        frame.chunks[chunkIdx] = data

        if (frame.chunks.size == totalChunks) {
            // All chunks received for this frame!
            inFlightFrames.remove(seq)
            lastRenderedVideoSeq = seq

            // Prune older stale frames
            val now = System.currentTimeMillis()
            inFlightFrames.entries.removeIf { it.key < seq || now - it.value.timestamp > 1000L }

            // Reassemble complete JPEG payload
            var totalBytes = 0
            for (i in 0 until totalChunks) {
                totalBytes += frame.chunks[i]?.size ?: 0
            }
            val fullJpeg = ByteArray(totalBytes)
            var offset = 0
            for (i in 0 until totalChunks) {
                val part = frame.chunks[i] ?: return
                System.arraycopy(part, 0, fullJpeg, offset, part.size)
                offset += part.size
            }

            decodeAndEmitVideo(fullJpeg)
        }
    }

    private fun decodeAndEmitVideo(jpegBytes: ByteArray) {
        try {
            val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            if (bmp != null) {
                onVideoReceived(bmp)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error decoding LAN video frame: ${e.message}")
        }
    }

    fun sendAudio(pcmData: ByteArray) {
        if (!isRunning.get() || socket == null || targetAddress == null) return
        scope.launch {
            try {
                // Compress PCM to G.711 mu-law (halves payload to ~800 bytes, safe within 1500 MTU)
                val muLaw = AudioVoipEngine.G711.linearToMuLaw(pcmData)
                val packetBuf = ByteArray(5 + muLaw.size)
                packetBuf[0] = TYPE_AUDIO
                ByteBuffer.wrap(packetBuf, 1, 4).putInt(audioSeq++)
                System.arraycopy(muLaw, 0, packetBuf, 5, muLaw.size)

                val packet = DatagramPacket(packetBuf, packetBuf.size, targetAddress, targetPort)
                socket?.send(packet)
            } catch (e: Exception) {
                Log.d(TAG, "LAN audio send error: ${e.message}")
            }
        }
    }

    fun sendVideo(jpegBytes: ByteArray) {
        if (!isRunning.get() || socket == null || targetAddress == null) return
        scope.launch {
            try {
                val seq = videoSeq++
                val totalLength = jpegBytes.size
                val totalChunks = (totalLength + MAX_CHUNK_PAYLOAD - 1) / MAX_CHUNK_PAYLOAD

                for (chunkIdx in 0 until totalChunks) {
                    val offset = chunkIdx * MAX_CHUNK_PAYLOAD
                    val chunkSize = minOf(MAX_CHUNK_PAYLOAD, totalLength - offset)

                    val packetBuf = ByteArray(9 + chunkSize)
                    packetBuf[0] = TYPE_VIDEO_CHUNK
                    val bb = ByteBuffer.wrap(packetBuf, 1, 8)
                    bb.putInt(seq)
                    bb.putShort(chunkIdx.toShort())
                    bb.putShort(totalChunks.toShort())

                    System.arraycopy(jpegBytes, offset, packetBuf, 9, chunkSize)

                    val packet = DatagramPacket(packetBuf, packetBuf.size, targetAddress, targetPort)
                    socket?.send(packet)
                }
            } catch (e: Exception) {
                Log.d(TAG, "LAN video send error: ${e.message}")
            }
        }
    }

    private fun sendControlPacket(type: Byte) {
        scope.launch {
            try {
                val buf = ByteArray(5)
                buf[0] = type
                ByteBuffer.wrap(buf, 1, 4).putInt(0)
                val packet = DatagramPacket(buf, buf.size, targetAddress, targetPort)
                socket?.send(packet)
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        receiverJob?.cancel()
        receiverJob = null

        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null

        targetAddress = null
        inFlightFrames.clear()
        Log.i(TAG, "LanVoipEngine stopped and UDP socket closed")
    }
}
