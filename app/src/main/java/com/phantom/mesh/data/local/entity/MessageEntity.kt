package com.phantom.mesh.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Core message entity supporting the dual-network architecture.
 *
 * Each message carries routing metadata for the 3-hop mesh:
 * - [hopCount]: incremented at each mesh relay, dropped at 3
 * - [deliveryStatus]: drives the hop-state read receipt UI
 *     → "saved"     = 1 gray check  (written to local Room DB)
 *     → "mesh_sent" = 1 blue check  (handed off to mesh router)
 *     → "delivered"  = 2 blue checks (target peer ACK received)
 * - [transportUsed]: records which network actually delivered ("cloud" / "mesh")
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chat_id", "timestamp"]),
        Index(value = ["sender_id"]),
        Index(value = ["delivery_status"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "chat_id")
    val chatId: String,

    @ColumnInfo(name = "sender_id")
    val senderId: String,

    /** Target recipient user ID (used for mesh routing decisions) */
    @ColumnInfo(name = "target_id")
    val targetId: String,

    /** Message body text (null for media-only messages) */
    @ColumnInfo(name = "content")
    val content: String? = null,

    /** "text", "image", "video", "file", "ack", "drop_zone" */
    @ColumnInfo(name = "data_type")
    val dataType: String = "text",

    /** Local file URI for media attachments (persisted to Room after download) */
    @ColumnInfo(name = "media_local_uri")
    val mediaLocalUri: String? = null,

    /** Firebase Storage download URL (ephemeral — auto-deletes after 15 days) */
    @ColumnInfo(name = "media_cloud_url")
    val mediaCloudUrl: String? = null,

    /** Media file size in bytes (for priority queue ordering) */
    @ColumnInfo(name = "media_size_bytes")
    val mediaSizeBytes: Long = 0L,

    /** MIME type for media payloads */
    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    /** "saved", "mesh_sent", "delivered", "read" */
    @ColumnInfo(name = "delivery_status")
    val deliveryStatus: String = "saved",

    /** Mesh hop counter — packet dropped when this reaches 3 */
    @ColumnInfo(name = "hop_count")
    val hopCount: Int = 0,

    /** "cloud" or "mesh" — which transport layer delivered this message */
    @ColumnInfo(name = "transport_used")
    val transportUsed: String = "cloud",

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    /** Flag indicating this is a forwarded mesh packet, not originated locally */
    @ColumnInfo(name = "is_forwarded")
    val isForwarded: Boolean = false
)
