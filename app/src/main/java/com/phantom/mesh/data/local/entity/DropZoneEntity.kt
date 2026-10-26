package com.phantom.mesh.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Drop Zone entity for proximity-based file sharing.
 *
 * A "drop" is a file pinned by a user for local mesh broadcast.
 * Nearby peers can pull the file metadata + payload via Nearby Connections.
 * Each drop has a TTL — once expired, the file is automatically purged from
 * ALL local Room databases that received it.
 *
 * The [broadcastRadius] conceptually maps to how many hops the drop
 * metadata travels (capped at 3 per the mesh routing rules).
 */
@Entity(
    tableName = "drop_zones",
    indices = [
        Index(value = ["expires_at"]),
        Index(value = ["owner_id"])
    ]
)
data class DropZoneEntity(
    @PrimaryKey
    @ColumnInfo(name = "drop_id")
    val dropId: String,

    /** User ID of the peer who originally pinned this file */
    @ColumnInfo(name = "owner_id")
    val ownerId: String,

    /** Human-readable filename */
    @ColumnInfo(name = "file_name")
    val fileName: String,

    /** MIME type (e.g., "image/jpeg", "application/pdf") */
    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    /** File size in bytes */
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,

    /** Local file path on this device (null if not yet pulled) */
    @ColumnInfo(name = "local_file_path")
    val localFilePath: String? = null,

    /** SHA-256 hash for integrity verification across mesh hops */
    @ColumnInfo(name = "file_hash")
    val fileHash: String? = null,

    /** Whether the full file payload has been downloaded to this device */
    @ColumnInfo(name = "is_downloaded")
    val isDownloaded: Boolean = false,

    /** Max mesh hops for metadata broadcast (1–3) */
    @ColumnInfo(name = "broadcast_radius")
    val broadcastRadius: Int = 1,

    /** Absolute epoch millis when this drop expires and self-destructs */
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
