package com.phantom.mesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.phantom.mesh.data.local.entity.MeshQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeshQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(packet: MeshQueueEntity)

    @Update
    suspend fun updatePacket(packet: MeshQueueEntity)

    /**
     * Dequeue the highest-priority pending packet.
     * Priority 0 (text) always goes first; within the same priority,
     * older packets go first (FIFO).
     */
    @Query("""
        SELECT * FROM mesh_queue 
        WHERE status = 'pending' 
        ORDER BY priority ASC, created_at ASC 
        LIMIT 1
    """)
    suspend fun dequeueNext(): MeshQueueEntity?

    /** All pending packets for a specific next-hop endpoint */
    @Query("""
        SELECT * FROM mesh_queue 
        WHERE next_hop_endpoint_id = :endpointId 
          AND status = 'pending'
        ORDER BY priority ASC, created_at ASC
    """)
    suspend fun getPendingForEndpoint(endpointId: String): List<MeshQueueEntity>

    /** Reactive stream of queue depth (for UI indicators) */
    @Query("SELECT COUNT(*) FROM mesh_queue WHERE status = 'pending'")
    fun observeQueueDepth(): Flow<Int>

    @Query("UPDATE mesh_queue SET status = :status WHERE packet_id = :packetId")
    suspend fun updateStatus(packetId: String, status: String)

    @Query("UPDATE mesh_queue SET retry_count = retry_count + 1 WHERE packet_id = :packetId")
    suspend fun incrementRetry(packetId: String)

    /** Clean up sent or failed packets older than the retention window */
    @Query("""
        DELETE FROM mesh_queue 
        WHERE status IN ('sent', 'failed') 
          AND created_at < :cutoff
    """)
    suspend fun purgeOldPackets(cutoff: Long)

    @Query("DELETE FROM mesh_queue WHERE packet_id = :packetId")
    suspend fun deletePacket(packetId: String)

    // ── DURESS: Nuclear data wipe ──────────────────────────────
    @Query("DELETE FROM mesh_queue")
    suspend fun nukeQueue()
}
