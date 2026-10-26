package com.phantom.mesh.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a pending mesh packet in the priority transfer queue.
 *
 * Text payloads are assigned [priority] = 0 (highest) and broadcast instantly.
 * Media payloads are assigned [priority] = 1+ and queued until a stable
 * Wi-Fi Direct link is confirmed with the next hop.
 *
 * Packets are persisted to survive process death — if the app restarts mid-transfer,
 * the queue is replayed from Room.
 */
@Entity(
    tableName = "mesh_queue",
    indices = [
        Index(value = ["priority", "created_at"]),
        Index(value = ["next_hop_endpoint_id"]),
        Index(value = ["status"])
    ]
)
data class MeshQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "packet_id")
    val packetId: String,

    /** Serialized JSON of the MeshPacket (senderId, targetId, data, dataType, hopCount) */
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    /** 0 = text (instant), 1 = small media, 2 = large media */
    @ColumnInfo(name = "priority")
    val priority: Int = 0,

    /** Nearby Connections endpoint ID of the next relay node */
    @ColumnInfo(name = "next_hop_endpoint_id")
    val nextHopEndpointId: String? = null,

    /** "pending", "in_progress", "sent", "failed" */
    @ColumnInfo(name = "status")
    val status: String = "pending",

    /** Number of send retries attempted */
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
