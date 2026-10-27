package com.phantom.mesh.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a local user account in the PhantomMesh system.
 *
 * Stores the bcrypt-hashed password locally for offline auth validation,
 * and a secondary "decoy" password hash that triggers the data-destruction
 * duress login flow when entered.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "username")
    val username: String,

    /** Bcrypt hash of the real password (stored for offline login fallback) */
    @ColumnInfo(name = "password_hash")
    val passwordHash: String,

    /**
     * Bcrypt hash of the "duress" decoy password.
     * When entered, triggers immediate local data wipe + dummy state login.
     * Null if the user hasn't configured a decoy password yet.
     */
    @ColumnInfo(name = "decoy_password_hash")
    val decoyPasswordHash: String? = null,

    /** Display alias shown in mesh topology and chat headers */
    @ColumnInfo(name = "display_name")
    val displayName: String? = null,

    /** Firebase custom token (ephemeral, refreshed on cloud auth) */
    @ColumnInfo(name = "firebase_token")
    val firebaseToken: String? = null,

    /** Nearby Connections endpoint ID when active in mesh */
    @ColumnInfo(name = "mesh_endpoint_id")
    val meshEndpointId: String? = null,

    /** ISO-8601 timestamp of last activity for the 30-day cloud cron wipe */
    @ColumnInfo(name = "last_active")
    val lastActive: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** Optional security recovery question selected by the user */
    @ColumnInfo(name = "security_question")
    val securityQuestion: String? = null,

    /** PBKDF2/SHA-256 hash of the user's secret answer for password recovery */
    @ColumnInfo(name = "security_answer_hash")
    val securityAnswerHash: String? = null
)
