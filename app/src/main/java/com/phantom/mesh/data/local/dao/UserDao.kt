package com.phantom.mesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.phantom.mesh.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE username = :username COLLATE NOCASE LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE user_id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE user_id = :userId LIMIT 1")
    fun observeUser(userId: String): Flow<UserEntity?>

    @Query("UPDATE users SET decoy_password_hash = :hash WHERE user_id = :userId")
    suspend fun setDecoyPassword(userId: String, hash: String)

    @Query("UPDATE users SET password_hash = :hash WHERE user_id = :userId")
    suspend fun updatePasswordHash(userId: String, hash: String)

    @Query("UPDATE users SET password_hash = :hash WHERE username = :username COLLATE NOCASE")
    suspend fun updatePasswordHashByUsername(username: String, hash: String)

    @Query("UPDATE users SET security_question = :question, security_answer_hash = :answerHash WHERE user_id = :userId")
    suspend fun updateSecurityQuestion(userId: String, question: String, answerHash: String)

    @Query("UPDATE users SET username = :newUsername, display_name = :newUsername WHERE user_id = :userId")
    suspend fun updateUsername(userId: String, newUsername: String)

    @Query("UPDATE users SET last_active = :timestamp WHERE user_id = :userId")
    suspend fun updateLastActive(userId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE users SET firebase_token = :token WHERE user_id = :userId")
    suspend fun updateFirebaseToken(userId: String, token: String?)

    @Query("UPDATE users SET mesh_endpoint_id = :endpointId WHERE user_id = :userId")
    suspend fun updateMeshEndpointId(userId: String, endpointId: String?)

    // ── DURESS: Nuclear data wipe ──────────────────────────────
    /** Called during duress login — obliterates ALL local data */
    @Query("DELETE FROM users")
    suspend fun nukeAllUsers()
}
