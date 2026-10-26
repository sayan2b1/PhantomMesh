package com.phantom.mesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.phantom.mesh.data.local.entity.DropZoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DropZoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrop(drop: DropZoneEntity)

    @Update
    suspend fun updateDrop(drop: DropZoneEntity)

    @Query("SELECT * FROM drop_zones WHERE drop_id = :dropId LIMIT 1")
    suspend fun getDropById(dropId: String): DropZoneEntity?

    /** All active (non-expired) drops, reactive stream for the Drop Zone UI */
    @Query("""
        SELECT * FROM drop_zones 
        WHERE expires_at > :now 
        ORDER BY created_at DESC
    """)
    fun observeActiveDrops(now: Long = System.currentTimeMillis()): Flow<List<DropZoneEntity>>

    /** Drops owned by the local user (for managing pinned files) */
    @Query("SELECT * FROM drop_zones WHERE owner_id = :ownerId ORDER BY created_at DESC")
    fun observeMyDrops(ownerId: String): Flow<List<DropZoneEntity>>

    /** Drops available from peers (not yet downloaded) */
    @Query("""
        SELECT * FROM drop_zones 
        WHERE owner_id != :localUserId 
          AND is_downloaded = 0 
          AND expires_at > :now
        ORDER BY created_at DESC
    """)
    fun observeAvailablePeerDrops(
        localUserId: String,
        now: Long = System.currentTimeMillis()
    ): Flow<List<DropZoneEntity>>

    @Query("UPDATE drop_zones SET is_downloaded = 1, local_file_path = :path WHERE drop_id = :dropId")
    suspend fun markDownloaded(dropId: String, path: String)

    /** TTL enforcement — purge all expired drops and their local files */
    @Query("DELETE FROM drop_zones WHERE expires_at <= :now")
    suspend fun purgeExpiredDrops(now: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM drop_zones WHERE drop_id = :dropId")
    suspend fun deleteDrop(dropId: String)

    // ── DURESS: Nuclear data wipe ──────────────────────────────
    @Query("DELETE FROM drop_zones")
    suspend fun nukeAllDrops()
}
