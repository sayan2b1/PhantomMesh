package com.phantom.mesh.mesh

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * The wire-format packet for all mesh network communication.
 *
 * Every payload traversing the Nearby Connections mesh carries this structure.
 * The [hopCount] is incremented at each relay node and the packet is DROPPED
 * when it reaches [MAX_HOPS] (3) to prevent infinite loops.
 *
 * ## Packet Types
 * - `"text"`      → Chat message (priority 0 — instant broadcast)
 * - `"image"`     → Image attachment (priority 1 — queued for Wi-Fi Direct)
 * - `"video"`     → Video attachment (priority 2 — queued for Wi-Fi Direct)
 * - `"file"`      → Generic file (priority 1)
 * - `"ack"`       → Delivery acknowledgment (bounced back to sender for read receipts)
 * - `"drop_meta"` → Drop Zone metadata broadcast
 * - `"drop_pull"` → Drop Zone file pull request
 * - `"routing"`   → Routing table advertisement
 */
data class MeshPacket(
    @SerializedName("packet_id")
    val packetId: String,

    @SerializedName("sender_id")
    val senderId: String,

    @SerializedName("target_id")
    val targetId: String,

    @SerializedName("data")
    val data: String,

    @SerializedName("data_type")
    val dataType: String = "text",

    @SerializedName("hop_count")
    val hopCount: Int = 0,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    /** Original message ID (for ACK packets to reference) */
    @SerializedName("ref_message_id")
    val refMessageId: String? = null,

    /** File size hint for priority queue ordering (0 for text) */
    @SerializedName("payload_size")
    val payloadSize: Long = 0L
) {
    companion object {
        const val MAX_HOPS = 3

        private val gson = Gson()

        fun fromJson(json: String): MeshPacket = gson.fromJson(json, MeshPacket::class.java)
    }

    fun toJson(): String = gson.toJson(this)

    /** Increment hop and return new packet (immutable) */
    fun incrementHop(): MeshPacket = copy(hopCount = hopCount + 1)

    /** Check if this packet has exceeded the max hop limit */
    fun isExpired(): Boolean = hopCount >= MAX_HOPS

    /** Determine transfer priority: text/voice/ack/control = 0 (instant), media = 1+ */
    fun priority(): Int = when (dataType) {
        "text", "ack", "routing", "identity", "identity_ack", "delete_msg", "drop_meta", "drop_pull", "voice" -> 0
        "image", "file" -> 1
        "video" -> 2
        else -> 0
    }

    /** Create an ACK response packet that travels back to the original sender */
    fun createAck(): MeshPacket = MeshPacket(
        packetId = "${packetId}_ack",
        senderId = targetId,    // ACK comes FROM the target
        targetId = senderId,    // ACK goes TO the original sender
        data = "ack",
        dataType = "ack",
        hopCount = 0,
        refMessageId = packetId
    )
}
