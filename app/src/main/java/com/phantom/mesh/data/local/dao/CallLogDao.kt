package com.phantom.mesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.phantom.mesh.data.local.entity.CallLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntity)

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun observeAllCallLogs(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    suspend fun getAllCallLogs(): List<CallLogEntity>

    @Query("DELETE FROM call_logs WHERE call_id = :callId")
    suspend fun deleteCallLog(callId: String)

    @Query("DELETE FROM call_logs")
    suspend fun clearAllCallLogs()

    // ── DURESS: Nuclear data wipe ──────────────────────────────
    @Query("DELETE FROM call_logs")
    suspend fun nukeAllCallLogs()
}

