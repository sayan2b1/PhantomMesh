package com.phantom.mesh.data.local.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.phantom.mesh.data.local.dao.ChatDao
import com.phantom.mesh.data.local.dao.MessageDao
import com.phantom.mesh.data.local.entity.ChatEntity
import com.phantom.mesh.data.local.entity.MessageEntity
import com.phantom.mesh.data.local.preferences.ContactAliasManager
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.data.worker.LocalBackupWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class BackupFrequency(val key: String, val title: String, val intervalDays: Long) {
    DAILY("DAILY", "Daily", 1),
    WEEKLY("WEEKLY", "Weekly", 7),
    BI_WEEKLY("BI_WEEKLY", "Bi-Weekly", 14),
    MANUAL("MANUAL", "Whenever I tap on backup", 0);

    companion object {
        fun fromKey(key: String?): BackupFrequency {
            return entries.find { it.key.equals(key, ignoreCase = true) } ?: MANUAL
        }
    }
}

data class LocalChatBackupPayload(
    val version: Int = 1,
    val app: String = "PhantomMesh",
    val backupTimestamp: Long = System.currentTimeMillis(),
    val userId: String?,
    val username: String?,
    val aliases: Map<String, String> = emptyMap(),
    val chats: List<ChatEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList()
)

data class BackupMetadata(
    val fileName: String,
    val filePath: String,
    val timestamp: Long,
    val sizeBytes: Long,
    val chatCount: Int,
    val messageCount: Int
)

data class BackupResult(
    val success: Boolean,
    val message: String,
    val backupFile: File? = null,
    val chatCount: Int = 0,
    val messageCount: Int = 0,
    val sizeBytes: Long = 0L,
    val timestamp: Long = 0L
)

data class RestoreResult(
    val success: Boolean,
    val message: String,
    val chatsRestored: Int = 0,
    val messagesRestored: Int = 0,
    val aliasesRestored: Int = 0
)

/**
 * Signal-style local chat backup and restore manager.
 * Stores backups in persistent public Downloads/PhantomMesh_Backups and app-specific directories
 * so data survives uninstalls and server-side message expiry.
 */
@Singleton
class LocalChatBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val sessionManager: SessionManager,
    private val contactAliasManager: ContactAliasManager
) {
    companion object {
        private const val TAG = "LocalChatBackupManager"
        private const val BACKUP_DIR_NAME = "PhantomMesh_Backups"
        const val PRIMARY_BACKUP_FILE_NAME = "phantom_chat_backup.json"
        const val WORK_NAME = "phantom_local_chat_backup_worker"
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    }

    /**
     * Executes an immediate full backup of all local chats, messages, and contact aliases.
     * Appends to and rewrites the single primary backup file without creating extra files.
     */
    suspend fun performBackup(): BackupResult = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val appBackupDir = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }
            val appFile = File(appBackupDir, PRIMARY_BACKUP_FILE_NAME)

            // 1. Read existing backup if present to append & preserve historical records
            var previousPayload: LocalChatBackupPayload? = null
            if (appFile.exists() && appFile.length() > 0) {
                try {
                    previousPayload = gson.fromJson(appFile.reader(Charsets.UTF_8), LocalChatBackupPayload::class.java)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not parse existing app backup file: ${e.message}")
                }
            }

            if (previousPayload == null) {
                // Check if public downloads backup exists
                val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), BACKUP_DIR_NAME)
                val pubFile = File(pubDir, PRIMARY_BACKUP_FILE_NAME)
                if (pubFile.exists() && pubFile.length() > 0) {
                    try {
                        previousPayload = gson.fromJson(pubFile.reader(Charsets.UTF_8), LocalChatBackupPayload::class.java)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not parse existing public backup file: ${e.message}")
                    }
                }
            }

            val currentChats = chatDao.getAllChats()
            val currentMessages = messageDao.getAllMessages()
            val currentAliases = contactAliasManager.getAllAliases()
            val currentUserId = sessionManager.getUserId()
            val currentUsername = sessionManager.getUsername()

            // Merge chats (append/update by chatId)
            val mergedChatsMap = (previousPayload?.chats ?: emptyList())
                .associateBy { it.chatId }
                .toMutableMap()
            for (chat in currentChats) {
                mergedChatsMap[chat.chatId] = chat
            }
            val mergedChats = mergedChatsMap.values.toList()

            // Merge messages (append by messageId, preserve historical messages)
            val mergedMessagesMap = (previousPayload?.messages ?: emptyList())
                .associateBy { it.messageId }
                .toMutableMap()
            for (msg in currentMessages) {
                mergedMessagesMap[msg.messageId] = msg
            }
            val mergedMessages = mergedMessagesMap.values.sortedBy { it.timestamp }

            // Merge aliases
            val mergedAliases = (previousPayload?.aliases ?: emptyMap()) + currentAliases

            val payload = LocalChatBackupPayload(
                version = 1,
                app = "PhantomMesh",
                backupTimestamp = now,
                userId = currentUserId ?: previousPayload?.userId,
                username = currentUsername ?: previousPayload?.username,
                aliases = mergedAliases,
                chats = mergedChats,
                messages = mergedMessages
            )

            val jsonString = gson.toJson(payload)
            val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)
            val sizeBytes = jsonBytes.size.toLong()

            // 2. Overwrite in app-specific storage
            FileOutputStream(appFile, false).use { it.write(jsonBytes) }

            // 3. Overwrite in public Downloads/PhantomMesh_Backups/phantom_chat_backup.json
            var savedPublicPath: String? = null
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val projection = arrayOf(MediaStore.Downloads._ID)
                    val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
                    val selectionArgs = arrayOf(PRIMARY_BACKUP_FILE_NAME, "Download/$BACKUP_DIR_NAME%")
                    val queryUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    var existingUri: Uri? = null

                    context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                            existingUri = android.content.ContentUris.withAppendedId(queryUri, id)
                        }
                    }

                    val targetUri = existingUri ?: run {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, PRIMARY_BACKUP_FILE_NAME)
                            put(MediaStore.Downloads.MIME_TYPE, "application/json")
                            put(MediaStore.Downloads.RELATIVE_PATH, "Download/$BACKUP_DIR_NAME")
                        }
                        context.contentResolver.insert(queryUri, contentValues)
                    }

                    if (targetUri != null) {
                        context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                            out.write(jsonBytes)
                        }
                        savedPublicPath = "Download/$BACKUP_DIR_NAME/$PRIMARY_BACKUP_FILE_NAME"
                    }
                } else {
                    val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), BACKUP_DIR_NAME).apply { mkdirs() }
                    val pubFile = File(pubDir, PRIMARY_BACKUP_FILE_NAME)
                    FileOutputStream(pubFile, false).use { it.write(jsonBytes) }
                    savedPublicPath = pubFile.absolutePath
                }
            } catch (e: Exception) {
                Log.w(TAG, "Public downloads copy warning: ${e.message}")
            }

            // 4. Remove any older legacy timestamped backup files to avoid duplicate clutter
            try {
                appBackupDir.listFiles { _, name -> name.startsWith("phantom_backup_") && name.endsWith(".json") }
                    ?.forEach { it.delete() }
            } catch (_: Exception) {}

            val finalDisplayPath = savedPublicPath ?: appFile.absolutePath
            sessionManager.recordBackupSuccess(now, sizeBytes, mergedMessages.size, finalDisplayPath)

            Log.i(TAG, "Chat backup rewritten: ${mergedChats.size} chats, ${mergedMessages.size} messages ($sizeBytes bytes)")
            BackupResult(
                success = true,
                message = "Backup updated successfully! (${mergedMessages.size} messages, $sizeBytes bytes)",
                backupFile = appFile,
                chatCount = mergedChats.size,
                messageCount = mergedMessages.size,
                sizeBytes = sizeBytes,
                timestamp = now
            )
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed: ${e.message}", e)
            BackupResult(
                success = false,
                message = "Backup failed: ${e.message}"
            )
        }
    }

    /**
     * Restores chats and messages from a local File.
     */
    suspend fun restoreFromFile(file: File): RestoreResult = withContext(Dispatchers.IO) {
        try {
            FileInputStream(file).use { input ->
                restoreFromInputStream(input)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore from file ${file.absolutePath}", e)
            RestoreResult(success = false, message = "Restore failed: ${e.message}")
        }
    }

    /**
     * Restores chats and messages from a content URI (picked via system document picker).
     */
    suspend fun restoreFromUri(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                restoreFromInputStream(input)
            } ?: RestoreResult(success = false, message = "Could not open selected file")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore from URI $uri", e)
            RestoreResult(success = false, message = "Restore failed: ${e.message}")
        }
    }

    private suspend fun restoreFromInputStream(inputStream: InputStream): RestoreResult {
        val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val payload = gson.fromJson(jsonString, LocalChatBackupPayload::class.java)

        if (payload == null || (payload.chats.isEmpty() && payload.messages.isEmpty())) {
            return RestoreResult(success = false, message = "The selected backup contains no valid chat data")
        }

        // Restore chats
        if (payload.chats.isNotEmpty()) {
            chatDao.insertChats(payload.chats)
        }

        // Restore messages
        if (payload.messages.isNotEmpty()) {
            messageDao.insertMessages(payload.messages)
        }

        // Restore contact aliases
        if (payload.aliases.isNotEmpty()) {
            contactAliasManager.restoreAliases(payload.aliases)
        }

        Log.i(TAG, "Restore successful: ${payload.chats.size} chats, ${payload.messages.size} messages")
        return RestoreResult(
            success = true,
            message = "Successfully restored ${payload.chats.size} chats and ${payload.messages.size} messages!",
            chatsRestored = payload.chats.size,
            messagesRestored = payload.messages.size,
            aliasesRestored = payload.aliases.size
        )
    }

    /**
     * Discovers all available backup files located in device storage.
     */
    fun listAvailableBackups(): List<BackupMetadata> {
        val results = mutableListOf<BackupMetadata>()
        val searchDirs = listOfNotNull(
            File(context.getExternalFilesDir(null), "backups"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), BACKUP_DIR_NAME),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), BACKUP_DIR_NAME)
        )

        val seenNames = mutableSetOf<String>()

        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles { _, name -> (name == PRIMARY_BACKUP_FILE_NAME || name.startsWith("phantom_backup_")) && name.endsWith(".json") }?.forEach { file ->
                    if (seenNames.add(file.name)) {
                        try {
                            val payload = gson.fromJson(file.reader(), LocalChatBackupPayload::class.java)
                            results.add(
                                BackupMetadata(
                                    fileName = file.name,
                                    filePath = file.absolutePath,
                                    timestamp = payload?.backupTimestamp ?: file.lastModified(),
                                    sizeBytes = file.length(),
                                    chatCount = payload?.chats?.size ?: 0,
                                    messageCount = payload?.messages?.size ?: 0
                                )
                            )
                        } catch (_: Exception) {
                            results.add(
                                BackupMetadata(
                                    fileName = file.name,
                                    filePath = file.absolutePath,
                                    timestamp = file.lastModified(),
                                    sizeBytes = file.length(),
                                    chatCount = 0,
                                    messageCount = 0
                                )
                            )
                        }
                    }
                }
            }
        }

        return results.sortedByDescending { it.timestamp }
    }

    /**
     * Schedules or cancels periodic WorkManager auto-backup based on chosen frequency.
     */
    fun scheduleAutoBackup(frequency: BackupFrequency) {
        sessionManager.setAutoBackupFrequency(frequency.key)

        val workManager = WorkManager.getInstance(context)
        if (frequency == BackupFrequency.MANUAL) {
            workManager.cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Cancelled periodic chat auto-backup")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<LocalBackupWorker>(frequency.intervalDays, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Log.i(TAG, "Scheduled periodic chat auto-backup with interval: ${frequency.title} (${frequency.intervalDays} days)")
    }

    /**
     * Re-enqueues periodic auto-backup work if configured in preferences, e.g. on application startup.
     */
    fun ensureScheduledFromPreferences() {
        val frequency = BackupFrequency.fromKey(sessionManager.getAutoBackupFrequency())
        if (frequency != BackupFrequency.MANUAL) {
            scheduleAutoBackup(frequency)
        }
    }
}
