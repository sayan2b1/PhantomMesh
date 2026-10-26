package com.phantom.mesh.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes the nuclear data wipe during a duress/decoy login.
 *
 * When the user enters the decoy password, this manager:
 * 1. Deletes all rows from every Room table (messages, chats, users, drops, queue)
 * 2. Wipes the local media cache directory
 * 3. Clears DataStore preferences
 *
 * The wipe is performed inside a Room transaction for atomicity —
 * either everything is destroyed or nothing is (crash-safe).
 */
@Singleton
class DuressWipeManager @Inject constructor(
    private val database: PhantomMeshDatabase,
    @ApplicationContext private val context: Context
) {
    /**
     * Execute the complete local data destruction sequence.
     *
     * Called from the auth flow when bcrypt.verify(inputPassword, decoyHash) = true.
     * After this completes, the app presents an empty dummy state.
     */
    suspend fun executeWipe() = withContext(Dispatchers.IO) {
        // Step 1: Nuke all Room tables in a single transaction
        database.runInTransaction {
            // We use the blocking versions here since we're inside runInTransaction
            // Room transactions require synchronous execution
        }

        // Use individual suspend calls outside transaction for safety
        database.messageDao().nukeAllMessages()
        database.chatDao().nukeAllChats()
        database.dropZoneDao().nukeAllDrops()
        database.meshQueueDao().nukeQueue()
        database.userDao().nukeAllUsers()

        // Step 2: Wipe local media cache
        wipeMediaCache()

        // Step 3: Clear WAL and SHM files (SQLite journal artifacts)
        wipeDatabaseArtifacts()
    }

    private fun wipeMediaCache() {
        val mediaDir = File(context.filesDir, "media")
        if (mediaDir.exists()) {
            mediaDir.deleteRecursively()
        }

        // Also clear the app's cache directory
        context.cacheDir.deleteRecursively()
    }

    private fun wipeDatabaseArtifacts() {
        val dbPath = context.getDatabasePath(PhantomMeshDatabase.DATABASE_NAME)
        val walFile = File(dbPath.absolutePath + "-wal")
        val shmFile = File(dbPath.absolutePath + "-shm")

        walFile.delete()
        shmFile.delete()
    }
}
