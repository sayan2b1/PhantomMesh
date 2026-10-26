package com.phantom.mesh.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.phantom.mesh.data.local.dao.ChatDao
import com.phantom.mesh.data.local.dao.DropZoneDao
import com.phantom.mesh.data.local.dao.MeshQueueDao
import com.phantom.mesh.data.local.dao.MessageDao
import com.phantom.mesh.data.local.dao.UserDao
import com.phantom.mesh.data.local.entity.ChatEntity
import com.phantom.mesh.data.local.entity.DropZoneEntity
import com.phantom.mesh.data.local.entity.MeshQueueEntity
import com.phantom.mesh.data.local.entity.MessageEntity
import com.phantom.mesh.data.local.entity.UserEntity

/**
 * PhantomMesh Room database — the single source of truth for all local data.
 *
 * Schema includes:
 * - [UserEntity]: accounts with dual-password (real + decoy/duress)
 * - [ChatEntity]: conversations with transport-layer awareness
 * - [MessageEntity]: messages with full mesh routing metadata
 * - [DropZoneEntity]: proximity-shared files with TTL expiry
 * - [MeshQueueEntity]: persistent priority queue for mesh packet relay
 *
 * SECURITY NOTE: This database is the target of the duress login wipe.
 * The [DuressWipeManager] calls each DAO's nuke method to destroy all
 * local data when the decoy password is entered.
 */
@Database(
    entities = [
        UserEntity::class,
        ChatEntity::class,
        MessageEntity::class,
        DropZoneEntity::class,
        MeshQueueEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PhantomMeshDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun dropZoneDao(): DropZoneDao
    abstract fun meshQueueDao(): MeshQueueDao

    companion object {
        const val DATABASE_NAME = "phantom_mesh_db"
    }
}
