package com.phantom.mesh.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a chat conversation (1:1 or group).
 *
 * Each chat tracks participants and the last message preview for the
 * conversation list UI. The [transportType] field records whether the
 * conversation is currently routed over cloud or mesh.
 */
@Entity(
    tableName = "chats",
    indices = [
        Index(value = ["last_message_timestamp"]),
        Index(value = ["is_archived"])
    ]
)
data class ChatEntity(
    @PrimaryKey
    @ColumnInfo(name = "chat_id")
    val chatId: String,

    /** Comma-separated participant user IDs (sorted for deterministic chat ID generation) */
    @ColumnInfo(name = "participants")
    val participants: String,

    /** Human-readable chat title (peer's display name for 1:1, custom for group) */
    @ColumnInfo(name = "chat_title")
    val chatTitle: String? = null,

    /** Snippet of the last message for conversation list preview */
    @ColumnInfo(name = "last_message_preview")
    val lastMessagePreview: String? = null,

    @ColumnInfo(name = "last_message_timestamp")
    val lastMessageTimestamp: Long = 0L,

    /** Count of messages not yet read by the local user */
    @ColumnInfo(name = "unread_count")
    val unreadCount: Int = 0,

    /** Active transport: "cloud", "mesh", or "offline" */
    @ColumnInfo(name = "transport_type")
    val transportType: String = "cloud",

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "is_muted")
    val isMuted: Boolean = false,

    /** Unsent draft message text (WhatsApp-style) */
    @ColumnInfo(name = "draft_message")
    val draftMessage: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
