package com.phantom.mesh.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a call log entry in the persistent call history.
 */
@Entity(
    tableName = "call_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["peer_username"])
    ]
)
data class CallLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "call_id")
    val callId: String,

    @ColumnInfo(name = "peer_username")
    val peerUsername: String,

    @ColumnInfo(name = "peer_address")
    val peerAddress: String = "",

    /** "VOICE" or "VIDEO" */
    @ColumnInfo(name = "call_type")
    val callType: String = "VOICE",

    /** "INCOMING", "OUTGOING", "MISSED" */
    @ColumnInfo(name = "direction")
    val direction: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int = 0,

    /** "COMPLETED", "MISSED", "REJECTED", "FAILED", "CANCELLED" */
    @ColumnInfo(name = "status")
    val status: String = "COMPLETED"
)

