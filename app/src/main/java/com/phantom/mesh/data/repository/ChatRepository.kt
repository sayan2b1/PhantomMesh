package com.phantom.mesh.data.repository

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.phantom.mesh.data.local.dao.ChatDao
import com.phantom.mesh.data.local.dao.MeshQueueDao
import com.phantom.mesh.data.local.dao.MessageDao
import com.phantom.mesh.data.local.entity.ChatEntity
import com.phantom.mesh.data.local.entity.MessageEntity
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.mesh.MeshConnectionManager
import com.phantom.mesh.mesh.MeshPacket
import com.phantom.mesh.mesh.MeshRoutingTable
import com.phantom.mesh.network.NetworkStateManager
import com.phantom.mesh.util.HardwareStateUtil
import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val meshQueueDao: MeshQueueDao,
    private val routingTable: MeshRoutingTable,
    private val sessionManager: SessionManager,
    private val networkStateManager: NetworkStateManager,
    private val meshConnectionManager: MeshConnectionManager
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val exceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("ChatRepository", "Unhandled error in ChatRepository: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    private var firestoreMessagesListener: ListenerRegistration? = null
    private var cloudListenerStarted = false

    init {
        // Observe incoming mesh packets and persist them directly to Room
        scope.launch {
            meshConnectionManager.incomingPackets.collect { packet ->
                handleIncomingMeshPacket(packet)
            }
        }

        // Listen for ACK packets bounced back from target peer
        scope.launch {
            meshConnectionManager.incomingAcks.collect { ackPacket ->
                ackPacket.refMessageId?.let { originalMessageId ->
                    // Two blue checks = delivered
                    messageDao.updateDeliveryStatus(originalMessageId, "delivered")
                }
            }
        }

        // Auto-start cloud listener if we already have a valid session
        scope.launch {
            val userId = sessionManager.getUserId()
            val username = sessionManager.getUsername()
            if (userId != null && sessionManager.isSessionValid()) {
                android.util.Log.i("ChatRepository", "Auto-starting cloud listener from init (session valid)")
                startCloudListener(userId, username)
            }
        }
    }

    // ── Reactive Room Flows ────────────────────────────────────────────────

    fun observeActiveChats(): Flow<List<ChatEntity>> = chatDao.observeActiveChats()

    fun observeMessages(chatId: String): Flow<List<MessageEntity>> = messageDao.observeMessages(chatId)

    // ── Sending Messages (Dual-Network Router) ─────────────────────────────

    /**
     * Sends a text message through the dual-network router.
     *
     * 1. Writes immediately to Room DB with status 'saved' (1 gray check).
     * 2. Checks active network:
     *    - If online: pushes to Firestore. Once saved to cloud, marks as 'mesh_sent' / 'delivered'.
     *    - If mesh/offline: hands off to Nearby Connections mesh router.
     *      Status updates to 'mesh_sent' (1 blue check).
     *      Target peer will bounce an ACK packet back -> updates to 'delivered' (2 blue checks).
     */
    suspend fun sendTextMessage(chatId: String, targetId: String, content: String): MessageEntity = withContext(Dispatchers.IO) {
        val currentUserId = sessionManager.getUserId() ?: "anon_sender"
        val messageId = "msg_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val isOnline = networkStateManager.isInternetAvailable.value

        // Step 1: Ensure parent chat entry exists in Room
        try {
            ensureChatExists(chatId, targetId, content, now, if (isOnline) "cloud" else "mesh")
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Failed to ensureChatExists: ${e.message}", e)
        }

        // Step 2: Write directly to Room DB (1 gray check: 'saved')
        val message = MessageEntity(
            messageId = messageId,
            chatId = chatId,
            senderId = currentUserId,
            targetId = targetId,
            content = content,
            dataType = "text",
            deliveryStatus = "saved",
            hopCount = 0,
            transportUsed = if (isOnline) "cloud" else "mesh",
            timestamp = now
        )
        try {
            messageDao.insertMessage(message)
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Failed to insert message into Room: ${e.message}", e)
        }

        // Step 3: Route through active network layer (Dual-Network Router)
        try {
            if (isOnline) {
                routeViaCloud(message)
            }
            // Transmit via mesh if offline or if local peers are connected
            if (!isOnline || meshConnectionManager.hasActivePeers()) {
                routeViaMesh(message)
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Failed to route message: ${e.message}", e)
        }

        return@withContext message
    }

    /**
     * Sends a media message (image/video/file).
     * Online: uploads ephemeral media to Firebase Storage + metadata to Firestore.
     * Mesh: queues in persistent MeshQueueDao (Priority Queue) to send over Wi-Fi Direct.
     */
    suspend fun sendMediaMessage(
        chatId: String,
        targetId: String,
        localFile: File,
        mimeType: String,
        dataType: String = "image"
    ): MessageEntity = withContext(Dispatchers.IO) {
        val currentUserId = sessionManager.getUserId() ?: "anon_sender"
        val messageId = "media_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val isOnline = networkStateManager.isInternetAvailable.value

        // Ensure parent chat entry exists FIRST
        ensureChatExists(chatId, targetId, "[Attachment: ${localFile.name}]", now, if (isOnline) "cloud" else "mesh")

        val message = MessageEntity(
            messageId = messageId,
            chatId = chatId,
            senderId = currentUserId,
            targetId = targetId,
            content = localFile.name,
            dataType = dataType,
            mediaLocalUri = localFile.absolutePath,
            mediaSizeBytes = localFile.length(),
            mimeType = mimeType,
            deliveryStatus = "saved",
            hopCount = 0,
            transportUsed = if (isOnline) "cloud" else "mesh",
            timestamp = now
        )
        try {
            messageDao.insertMessage(message)
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Failed to insert media message into Room: ${e.message}", e)
        }

        if (isOnline) {
            var cloudUrl: String? = null
            // 1. Attempt Firebase Storage upload
            try {
                val storageRef = storage.reference.child("ephemeral_media/$chatId/$messageId/${localFile.name}")
                val uploadTask = storageRef.putFile(android.net.Uri.fromFile(localFile)).await()
                cloudUrl = uploadTask.storage.downloadUrl.await().toString()
            } catch (e: Exception) {
                android.util.Log.w("ChatRepository", "Storage upload not available (${e.message}), trying inline data fallback")
            }

            // 2. Resilient fallback: If Firebase Storage is not provisioned, encode image as data URI directly in Firestore document
            if (cloudUrl == null && localFile.length() < 750 * 1024) {
                try {
                    val bytes = localFile.readBytes()
                    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    cloudUrl = "data:$mimeType;base64,$b64"
                    android.util.Log.i("ChatRepository", "Image encoded as inline data URI (${bytes.size} bytes)")
                } catch (e: Exception) {
                    android.util.Log.e("ChatRepository", "Failed encoding media to base64: ${e.message}", e)
                }
            }

            if (cloudUrl != null) {
                val updatedMessage = message.copy(mediaCloudUrl = cloudUrl)
                messageDao.updateMessage(updatedMessage)
                routeViaCloud(updatedMessage)
            } else {
                routeViaMesh(message)
            }

            // Also broadcast to nearby mesh peers if connected
            if (meshConnectionManager.hasActivePeers()) {
                routeViaMesh(message)
            }
        } else {
            // Offline: Enqueue to Priority Queue for Wi-Fi Direct
            routeViaMesh(message)
        }

        return@withContext message
    }

    /**
     * Sends an encrypted AAC voice note message over cloud or offline mesh.
     */
    suspend fun sendVoiceMessage(
        chatId: String,
        targetId: String,
        localFile: File,
        durationMs: Long
    ): MessageEntity = withContext(Dispatchers.IO) {
        val currentUserId = sessionManager.getUserId() ?: "anon_sender"
        val messageId = "voice_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val isOnline = networkStateManager.isInternetAvailable.value

        val seconds = (durationMs / 1000).coerceAtLeast(1)
        val durationFormatted = String.format("%d:%02d", seconds / 60, seconds % 60)
        val preview = "🎤 Voice message ($durationFormatted)"

        ensureChatExists(chatId, targetId, preview, now, if (isOnline) "cloud" else "mesh")

        val message = MessageEntity(
            messageId = messageId,
            chatId = chatId,
            senderId = currentUserId,
            targetId = targetId,
            content = preview,
            dataType = "voice",
            mediaLocalUri = localFile.absolutePath,
            mediaSizeBytes = localFile.length(),
            mimeType = "audio/mp4",
            deliveryStatus = "saved",
            hopCount = 0,
            transportUsed = if (isOnline) "cloud" else "mesh",
            timestamp = now
        )
        try {
            messageDao.insertMessage(message)
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Failed to insert voice message into Room: ${e.message}", e)
        }

        if (isOnline) {
            var cloudUrl: String? = null
            try {
                val storageRef = storage.reference.child("ephemeral_voice/$chatId/$messageId/${localFile.name}")
                val uploadTask = storageRef.putFile(android.net.Uri.fromFile(localFile)).await()
                cloudUrl = uploadTask.storage.downloadUrl.await().toString()
            } catch (e: Exception) {
                android.util.Log.w("ChatRepository", "Storage upload not available for voice, using inline base64 fallback")
            }

            if (cloudUrl == null && localFile.length() < 750 * 1024) {
                try {
                    val bytes = localFile.readBytes()
                    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    cloudUrl = "data:audio/mp4;base64,$b64"
                } catch (e: Exception) {
                    android.util.Log.e("ChatRepository", "Failed encoding voice to base64: ${e.message}", e)
                }
            }

            if (cloudUrl != null) {
                val updatedMessage = message.copy(mediaCloudUrl = cloudUrl)
                messageDao.updateMessage(updatedMessage)
                routeViaCloud(updatedMessage)
            } else {
                routeViaMesh(message)
            }

            if (meshConnectionManager.hasActivePeers()) {
                routeViaMesh(message)
            }
        } else {
            routeViaMesh(message)
        }

        return@withContext message
    }

    private suspend fun deleteLocalMediaFiles(messageId: String, localUri: String?) {
        try {
            val cleanUuid = messageId.removePrefix("media_").removePrefix("msg_").removePrefix("voice_").removePrefix("video_").trim()
            val shortId = if (messageId.length >= 8) messageId.take(8) else messageId
            val shortUuid = if (cleanUuid.length >= 8) cleanUuid.take(8) else cleanUuid

            val tokens = listOfNotNull(
                messageId,
                shortId,
                cleanUuid.ifBlank { null },
                shortUuid.ifBlank { null }
            ).filter { it.length >= 4 }.distinct()

            // 1. Delete explicit localUri if present
            if (!localUri.isNullOrBlank()) {
                val f = File(localUri)
                if (f.exists()) f.delete()
            }

            // 2. Delete matching files in received_media
            val mediaDir = File(context.filesDir, "received_media")
            if (mediaDir.exists() && mediaDir.isDirectory) {
                mediaDir.listFiles()?.forEach { file ->
                    if (tokens.any { token -> file.name.contains(token, ignoreCase = true) }) {
                        file.delete()
                    }
                }
            }

            // 3. Delete matching files in voice_notes
            val voiceDir = File(context.filesDir, "voice_notes")
            if (voiceDir.exists() && voiceDir.isDirectory) {
                voiceDir.listFiles()?.forEach { file ->
                    if (tokens.any { token -> file.name.contains(token, ignoreCase = true) }) {
                        file.delete()
                    }
                }
            }

            // 4. Delete matching files in root filesDir
            context.filesDir.listFiles()?.forEach { file ->
                if (file.isFile && tokens.any { token -> file.name.contains(token, ignoreCase = true) }) {
                    file.delete()
                }
            }

            // 5. Delete temporary cache files in cacheDir
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && tokens.any { token -> file.name.contains(token, ignoreCase = true) }) {
                    file.delete()
                }
            }

            // 6. Delete temporary files in externalCacheDir
            context.externalCacheDir?.listFiles()?.forEach { file ->
                if (file.isFile && tokens.any { token -> file.name.contains(token, ignoreCase = true) }) {
                    file.delete()
                }
            }

            // 7. Purge from persistent Mesh priority queue
            try {
                meshQueueDao.deletePacket(messageId)
                if (cleanUuid.isNotBlank()) {
                    meshQueueDao.deletePacket(cleanUuid)
                }
            } catch (ignored: Exception) {}

            // 8. Evict from Coil image cache so in-memory/disk cached bitmaps disappear
            try {
                coil.Coil.imageLoader(context).memoryCache?.clear()
                coil.Coil.imageLoader(context).diskCache?.clear()
            } catch (ignored: Exception) {}

            // 9. Purge from public device gallery & media folders via MediaStore ContentResolver & filesystem sweep
            HardwareStateUtil.deleteFromMediaStore(context, messageId)
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Error in deleteLocalMediaFiles: ${e.message}", e)
        }
    }

    /**
     * Deletes a message for everyone (WhatsApp style).
     * 1. Replaces content with "🚫 This message was deleted" locally and deletes media file.
     * 2. If online: updates Firestore message document and parent chat, deletes cloud storage file.
     * 3. Over mesh: broadcasts a delete_msg MeshPacket to target AND broadcast so nearby peers update and purge their local copy.
     */
    suspend fun deleteMessageForEveryone(chatId: String, messageId: String, targetId: String) = withContext(Dispatchers.IO) {
        val deletedNotice = "🚫 This message was deleted"

        // 1. Mark as deleted locally in Room DB and purge local media
        val existing = messageDao.getMessageById(messageId)
        deleteLocalMediaFiles(messageId, existing?.mediaLocalUri)
        HardwareStateUtil.deleteFromMediaStore(context, messageId)
        messageDao.markMessageAsDeleted(messageId, deletedNotice)

        // Update chat list preview
        try {
            chatDao.updateLastMessage(chatId, deletedNotice, System.currentTimeMillis(), "cloud")
        } catch (ignored: Exception) {}

        // 2. If online, update Firestore
        if (networkStateManager.isInternetAvailable.value) {
            try {
                firestore.collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .document(messageId)
                    .update(
                        mapOf(
                            "content" to deletedNotice,
                            "mediaCloudUrl" to null,
                            "mediaLocalUri" to null,
                            "dataType" to "deleted",
                            "mimeType" to null,
                            "isDeleted" to true
                        )
                    ).await()

                firestore.collection("chats")
                    .document(chatId)
                    .update("lastMessage", deletedNotice)

                existing?.mediaCloudUrl?.let { cloudUrl ->
                    if (cloudUrl.startsWith("http")) {
                        try {
                            storage.getReferenceFromUrl(cloudUrl).delete().await()
                        } catch (ignored: Exception) {}
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ChatRepository", "Failed to update deleted message in Firestore: ${e.message}")
            }
        }

        // 3. Always dispatch deletion through mesh network
        try {
            val currentUsername = sessionManager.getUsername() ?: sessionManager.getUserId() ?: "anon"
            val packet = MeshPacket(
                packetId = "del_${UUID.randomUUID().toString().take(8)}",
                senderId = currentUsername,
                targetId = targetId.trim().lowercase(),
                data = messageId,
                dataType = "delete_msg",
                refMessageId = messageId,
                hopCount = 0
            )
            meshConnectionManager.sendPacket(packet)

            // Also broadcast delete command to purge all relay / peer caches
            val broadcastPacket = packet.copy(
                packetId = "del_bc_${UUID.randomUUID().toString().take(8)}",
                targetId = "broadcast"
            )
            meshConnectionManager.sendPacket(broadcastPacket)
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Failed to send delete_msg packet into mesh: ${e.message}", e)
        }
    }

    /**
     * Deletes a message locally only ("Delete for Me").
     */
    suspend fun deleteMessageForMe(chatId: String, messageId: String) = withContext(Dispatchers.IO) {
        val existing = messageDao.getMessageById(messageId)
        deleteLocalMediaFiles(messageId, existing?.mediaLocalUri)
        HardwareStateUtil.deleteFromMediaStore(context, messageId)
        messageDao.deleteMessageById(messageId)
    }

    private suspend fun routeViaCloud(message: MessageEntity) {
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                val token = sessionManager.getCustomToken()
                if (!token.isNullOrBlank()) {
                    try {
                        auth.signInWithCustomToken(token).await()
                    } catch (e: Exception) {
                        android.util.Log.w("ChatRepository", "Could not restore auth token: ${e.message}")
                    }
                }
            }

            val currentUsername = sessionManager.getUsername() ?: message.senderId
            val targetUsername = message.targetId.trim().lowercase()
            val docData = hashMapOf(
                "messageId" to message.messageId,
                "chatId" to message.chatId,
                "senderId" to message.senderId,
                "senderUsername" to currentUsername,
                "targetId" to targetUsername,
                "targetUsername" to targetUsername,
                "content" to message.content,
                "dataType" to message.dataType,
                "mediaCloudUrl" to message.mediaCloudUrl,
                "mimeType" to (message.mimeType ?: "image/jpeg"),
                "mediaSizeBytes" to message.mediaSizeBytes,
                "timestamp" to message.timestamp
            )

            android.util.Log.d("ChatRepository", "Writing message to Firestore: chatId=${message.chatId}, targetId=$targetUsername, messageId=${message.messageId}")

            firestore.collection("chats")
                .document(message.chatId)
                .collection("messages")
                .document(message.messageId)
                .set(docData)
                .await()

            // Also update parent chat metadata so recipient can discover the conversation
            val parentChatData = hashMapOf(
                "chatId" to message.chatId,
                "participants" to listOf(currentUsername.trim().lowercase(), targetUsername),
                "lastMessage" to (if (message.dataType == "image") "[Image Attachment]" else message.content),
                "lastSender" to currentUsername,
                "updatedAt" to message.timestamp
            )
            try {
                firestore.collection("chats")
                    .document(message.chatId)
                    .set(parentChatData, SetOptions.merge())
            } catch (e: Exception) {
                android.util.Log.w("ChatRepository", "Could not update parent chat: ${e.message}")
            }

            android.util.Log.d("ChatRepository", "Message written to Firestore successfully: ${message.messageId}")

            // Cloud handed off -> 1 blue check
            messageDao.updateDeliveryStatus(message.messageId, "mesh_sent")
        } catch (e: Exception) {
            // Network glitch -> fallback to mesh
            android.util.Log.e("ChatRepository", "Cloud routing failed, fallback to mesh: ${e.message}", e)
            routeViaMesh(message)
        }
    }

    private suspend fun routeViaMesh(message: MessageEntity) {
        try {
            val currentUsername = sessionManager.getUsername() ?: message.senderId
            val packetData = if (message.dataType == "image") {
                if (!message.mediaCloudUrl.isNullOrBlank()) {
                    message.mediaCloudUrl
                } else if (!message.mediaLocalUri.isNullOrBlank()) {
                    val file = File(message.mediaLocalUri)
                    if (file.exists()) {
                        val bytes = if (file.length() > 750 * 1024) {
                            try {
                                val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                val bos = java.io.ByteArrayOutputStream()
                                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, bos)
                                bmp.recycle()
                                bos.toByteArray()
                            } catch (e: Exception) {
                                file.readBytes()
                            }
                        } else {
                            file.readBytes()
                        }
                        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        "data:${message.mimeType ?: "image/jpeg"};base64,$b64"
                    } else {
                        message.content ?: ""
                    }
                } else {
                    message.content ?: ""
                }
            } else if (message.dataType == "voice") {
                if (!message.mediaLocalUri.isNullOrBlank()) {
                    val file = File(message.mediaLocalUri)
                    if (file.exists()) {
                        val b64 = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                        "data:audio/mp4;base64,$b64"
                    } else {
                        message.content ?: ""
                    }
                } else {
                    message.content ?: ""
                }
            } else if (message.dataType == "video") {
                if (!message.mediaLocalUri.isNullOrBlank()) {
                    val file = File(message.mediaLocalUri)
                    if (file.exists()) {
                        val b64 = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                        "data:${message.mimeType ?: "video/mp4"};base64,$b64"
                    } else {
                        message.content ?: ""
                    }
                } else {
                    message.content ?: ""
                }
            } else {
                message.content ?: ""
            }

            val packet = MeshPacket(
                packetId = message.messageId,
                senderId = currentUsername,
                targetId = message.targetId.trim().lowercase(),
                data = packetData ?: "",
                dataType = message.dataType,
                hopCount = 0,
                payloadSize = message.mediaSizeBytes
            )

            // Hand off to Nearby Connections mesh router (1 blue check: 'mesh_sent')
            meshConnectionManager.sendPacket(packet)
            messageDao.updateDeliveryStatus(message.messageId, "mesh_sent")
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Mesh routing failed: ${e.message}", e)
        }
    }

    // ── Incoming Packet Handlers ───────────────────────────────────────────

    private suspend fun handleIncomingMeshPacket(packet: MeshPacket) {
        // 1. Handle remote delete command IMMEDIATELY
        if (packet.dataType == "delete_msg") {
            val deletedMessageId = packet.data.trim().ifBlank { packet.refMessageId?.trim() ?: "" }
            if (deletedMessageId.isNotBlank()) {
                val cleanUuid = deletedMessageId.removePrefix("media_").removePrefix("msg_").removePrefix("voice_").removePrefix("video_").trim()
                val existing = messageDao.getMessageById(deletedMessageId) ?: messageDao.getMessageByIdOrToken(deletedMessageId, cleanUuid)

                deleteLocalMediaFiles(deletedMessageId, existing?.mediaLocalUri)
                HardwareStateUtil.deleteFromMediaStore(context, deletedMessageId)
                if (cleanUuid.isNotBlank()) {
                    HardwareStateUtil.deleteFromMediaStore(context, cleanUuid)
                }

                messageDao.markMessageAsDeleted(deletedMessageId, "🚫 This message was deleted")
                if (cleanUuid.isNotBlank()) {
                    messageDao.markMessageAsDeletedByToken(deletedMessageId, cleanUuid, "🚫 This message was deleted")
                }

                val targetChatId = existing?.chatId
                if (targetChatId != null) {
                    chatDao.updateLastMessage(targetChatId, "🚫 This message was deleted", packet.timestamp, "mesh")
                }
                android.util.Log.i("ChatRepository", "Purged deleted message $deletedMessageId via mesh")
            }
            return
        }

        val currentUserId = sessionManager.getUserId() ?: meshConnectionManager.getLocalUserId() ?: "anon_recipient"
        val currentUsername = sessionManager.getUsername() ?: meshConnectionManager.getLocalDisplayName()

        val myIdentity = currentUsername ?: currentUserId
        val senderIdentity = packet.senderId

        // Resolve or create appropriate conversation ID
        val potentialChatId = generateChatId(senderIdentity, myIdentity)
        val fallbackChatId = generateChatId(senderIdentity, packet.targetId)
        val existingChat = chatDao.getChatById(potentialChatId) ?: chatDao.getChatById(fallbackChatId)
        val chatId = existingChat?.chatId ?: potentialChatId

        val isImage = packet.dataType == "image" || packet.data.startsWith("data:image")
        val isVoice = packet.dataType == "voice" || packet.data.startsWith("data:audio")
        val isVideo = packet.dataType == "video" || packet.data.startsWith("data:video")
        var localSavedPath: String? = null
        var cloudSource: String? = null

        if (isImage && packet.data.startsWith("data:")) {
            cloudSource = packet.data
            try {
                val b64 = packet.data.substringAfter("base64,")
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val mediaDir = File(context.filesDir, "received_media").apply { mkdirs() }
                val localFile = File(mediaDir, "${packet.packetId}.jpg")
                localFile.writeBytes(bytes)
                localSavedPath = localFile.absolutePath
                android.util.Log.i("ChatRepository", "Saved incoming mesh image to: $localSavedPath (${bytes.size} bytes)")
            } catch (e: Exception) {
                android.util.Log.e("ChatRepository", "Failed saving incoming mesh image: ${e.message}", e)
            }
        } else if (isVoice && packet.data.startsWith("data:")) {
            cloudSource = packet.data
            try {
                val b64 = packet.data.substringAfter("base64,")
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val voiceDir = File(context.filesDir, "voice_notes").apply { mkdirs() }
                val localFile = File(voiceDir, "${packet.packetId}.m4a")
                localFile.writeBytes(bytes)
                localSavedPath = localFile.absolutePath
                android.util.Log.i("ChatRepository", "Saved incoming mesh voice note to: $localSavedPath (${bytes.size} bytes)")
            } catch (e: Exception) {
                android.util.Log.e("ChatRepository", "Failed saving incoming mesh voice note: ${e.message}", e)
            }
        } else if (isVideo && packet.data.startsWith("data:")) {
            cloudSource = packet.data
            try {
                val b64 = packet.data.substringAfter("base64,")
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val mediaDir = File(context.filesDir, "received_media").apply { mkdirs() }
                val localFile = File(mediaDir, "${packet.packetId}.mp4")
                localFile.writeBytes(bytes)
                localSavedPath = localFile.absolutePath
                android.util.Log.i("ChatRepository", "Saved incoming mesh video to: $localSavedPath (${bytes.size} bytes)")
            } catch (e: Exception) {
                android.util.Log.e("ChatRepository", "Failed saving incoming mesh video: ${e.message}", e)
            }
        }

        val displayPreview = if (isImage) "[Image Attachment]" else if (isVoice) "🎤 Voice message" else if (isVideo) "[Video Attachment]" else packet.data
        ensureChatExists(chatId, senderIdentity, displayPreview, packet.timestamp, "mesh")

        val incomingMessage = MessageEntity(
            messageId = packet.packetId,
            chatId = chatId,
            senderId = packet.senderId,
            targetId = currentUserId,
            content = if (isImage) "attachment_${packet.packetId.take(8)}.jpg" else if (isVoice) "🎤 Voice message" else if (isVideo) "attachment_${packet.packetId.take(8)}.mp4" else packet.data,
            dataType = if (isImage) "image" else if (isVoice) "voice" else if (isVideo) "video" else packet.dataType,
            mediaLocalUri = localSavedPath,
            mediaCloudUrl = cloudSource,
            mediaSizeBytes = packet.payloadSize,
            mimeType = if (isImage) "image/jpeg" else if (isVoice) "audio/mp4" else if (isVideo) "video/mp4" else null,
            deliveryStatus = "delivered", // Already delivered to this target device
            hopCount = packet.hopCount,
            transportUsed = "mesh",
            timestamp = packet.timestamp
        )

        try {
            messageDao.insertMessage(incomingMessage)
            chatDao.incrementUnread(chatId)
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Failed to insert incoming mesh message: ${e.message}", e)
        }
    }

    /**
     * Starts listening for Firestore incoming messages for the active user.
     * Uses documentChanges (ADDED) for real-time delivery instead of re-scanning
     * the entire snapshot each time.
     *
     * Falls back to per-chat document listeners if the collectionGroup index
     * is missing (FAILED_PRECONDITION).
     */
    fun startCloudListener(currentUserId: String, currentUsername: String? = null) {
        if (cloudListenerStarted) {
            android.util.Log.d("ChatRepository", "Cloud listener already active, skipping duplicate start")
            return
        }

        val targets = listOfNotNull(
            currentUserId,
            currentUsername?.trim()?.lowercase(),
            currentUsername?.trim()
        ).filter { it.isNotBlank() }.distinct()

        if (targets.isEmpty()) {
            android.util.Log.w("ChatRepository", "No targets for cloud listener — skipping")
            return
        }

        android.util.Log.i("ChatRepository", "Starting cloud listener for targets: $targets")

        firestoreMessagesListener?.remove()
        cloudListenerStarted = true

        // Primary strategy: collectionGroup listener with whereIn on targetId
        firestoreMessagesListener = firestore.collectionGroup("messages")
            .whereIn("targetId", targets.take(10)) // Firestore whereIn max = 10
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    android.util.Log.e("ChatRepository", "Firestore cloud listener error: ${error.message}", error)

                    // If FAILED_PRECONDITION, the collection group index is missing.
                    // Fall back to polling approach.
                    if (error.message?.contains("FAILED_PRECONDITION", ignoreCase = true) == true ||
                        error.message?.contains("requires an index", ignoreCase = true) == true) {
                        android.util.Log.w("ChatRepository", "Collection group index missing! Falling back to polling.")
                        cloudListenerStarted = false
                        startPollingFallback(currentUserId, currentUsername)
                    }
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                scope.launch {
                    try {
                        // Process only ADDED documents for real-time delivery
                        for (change in snapshots.documentChanges) {
                            val doc = change.document
                            val messageId = doc.getString("messageId") ?: continue
                            val senderId = doc.getString("senderId") ?: continue

                            val isDeleted = doc.getBoolean("isDeleted") == true || doc.getString("content") == "🚫 This message was deleted"
                            if (isDeleted) {
                                val existing = messageDao.getMessageById(messageId)
                                deleteLocalMediaFiles(messageId, existing?.mediaLocalUri)
                                HardwareStateUtil.deleteFromMediaStore(context, messageId)
                                messageDao.markMessageAsDeleted(messageId, "🚫 This message was deleted")
                                val currentChatId = doc.getString("chatId") ?: existing?.chatId
                                if (currentChatId != null) {
                                    chatDao.updateLastMessage(currentChatId, "🚫 This message was deleted", System.currentTimeMillis(), "cloud")
                                }
                                continue
                            }

                            if (change.type != DocumentChange.Type.ADDED) continue

                            // Skip messages sent by ourselves
                            if (senderId == currentUserId) continue

                            // Skip if already in local DB
                            if (messageDao.getMessageById(messageId) != null) continue

                            val chatId = doc.getString("chatId") ?: continue
                            val content = doc.getString("content")
                            val dataType = doc.getString("dataType") ?: "text"
                            val mimeType = doc.getString("mimeType") ?: if (dataType == "image") "image/jpeg" else null
                            val mediaSizeBytes = doc.getLong("mediaSizeBytes") ?: 0L
                            val mediaCloudUrl = doc.getString("mediaCloudUrl")
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                            val senderUsername = doc.getString("senderUsername") ?: senderId
                            val displayPeer = if (senderUsername.isNotBlank()) senderUsername else senderId

                            var localSavedUri: String? = null
                            if (!mediaCloudUrl.isNullOrBlank() && mediaCloudUrl.startsWith("data:image")) {
                                try {
                                    val b64 = mediaCloudUrl.substringAfter("base64,")
                                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                    val mediaDir = File(context.filesDir, "received_media").apply { mkdirs() }
                                    val ext = if (mimeType?.contains("png") == true) "png" else "jpg"
                                    val localFile = File(mediaDir, "${messageId}.$ext")
                                    localFile.writeBytes(bytes)
                                    localSavedUri = localFile.absolutePath
                                    android.util.Log.i("ChatRepository", "Saved received cloud image to: $localSavedUri (${bytes.size} bytes)")
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatRepository", "Error decoding cloud base64 image: ${e.message}", e)
                                }
                            } else if (!mediaCloudUrl.isNullOrBlank() && mediaCloudUrl.startsWith("data:audio")) {
                                try {
                                    val b64 = mediaCloudUrl.substringAfter("base64,")
                                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                    val voiceDir = File(context.filesDir, "voice_notes").apply { mkdirs() }
                                    val localFile = File(voiceDir, "${messageId}.m4a")
                                    localFile.writeBytes(bytes)
                                    localSavedUri = localFile.absolutePath
                                    android.util.Log.i("ChatRepository", "Saved received cloud voice note to: $localSavedUri (${bytes.size} bytes)")
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatRepository", "Error decoding cloud voice note: ${e.message}", e)
                                }
                            }

                            val isVoice = dataType == "voice" || (!mediaCloudUrl.isNullOrBlank() && mediaCloudUrl.startsWith("data:audio"))
                            val isMedia = dataType == "image" || (!mediaCloudUrl.isNullOrBlank() && mediaCloudUrl.startsWith("data:image"))
                            val previewText = if (isVoice) (content ?: "🎤 Voice message") else if (isMedia) "[Image Attachment]" else (content ?: "[File]")

                            android.util.Log.d("ChatRepository", "Cloud message received: id=$messageId from=$displayPeer chatId=$chatId")

                            try {
                                ensureChatExists(chatId, displayPeer, previewText, timestamp, "cloud")
                            } catch (e: Exception) {
                                android.util.Log.e("ChatRepository", "Failed ensureChatExists in cloud listener: ${e.message}", e)
                            }

                            val msg = MessageEntity(
                                messageId = messageId,
                                chatId = chatId,
                                senderId = senderId,
                                targetId = currentUserId,
                                content = content,
                                dataType = if (isMedia) "image" else dataType,
                                mediaLocalUri = localSavedUri,
                                mediaCloudUrl = mediaCloudUrl,
                                mediaSizeBytes = mediaSizeBytes,
                                mimeType = mimeType,
                                deliveryStatus = "delivered",
                                hopCount = 0,
                                transportUsed = "cloud",
                                timestamp = timestamp
                            )
                            try {
                                messageDao.insertMessage(msg)
                                chatDao.incrementUnread(chatId)
                            } catch (e: Exception) {
                                android.util.Log.e("ChatRepository", "Failed to insert cloud message: ${e.message}", e)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ChatRepository", "Error processing cloud messages: ${e.message}", e)
                    }
                }
            }
    }

    /**
     * Fallback polling: Periodically queries each known chat's messages subcollection
     * directly (no collectionGroup index required).
     */
    private fun startPollingFallback(currentUserId: String, currentUsername: String?) {
        android.util.Log.i("ChatRepository", "Starting polling fallback for message sync")
        scope.launch {
            while (true) {
                try {
                    // Step 1: Discover any conversations where current user is a participant
                    val myCleanName = currentUsername?.trim()?.lowercase()
                    if (!myCleanName.isNullOrBlank()) {
                        try {
                            val userChatsSnapshot = firestore.collection("chats")
                                .whereArrayContains("participants", myCleanName)
                                .get()
                                .await()
                            for (chatDoc in userChatsSnapshot.documents) {
                                val cid = chatDoc.getString("chatId") ?: chatDoc.id
                                val participantsList = chatDoc.get("participants") as? List<*>
                                val peerName = participantsList?.firstOrNull { it?.toString()?.lowercase() != myCleanName }?.toString() ?: "Peer"
                                ensureChatExists(
                                    cid,
                                    peerName,
                                    chatDoc.getString("lastMessage") ?: "",
                                    chatDoc.getLong("updatedAt") ?: System.currentTimeMillis(),
                                    "cloud"
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("ChatRepository", "Could not fetch participant chats: ${e.message}")
                        }
                    }

                    // Step 2: Poll each known chat's messages subcollection
                    val allChats = chatDao.getAllChats()
                    for (chat in allChats) {
                        try {
                            val snapshot = firestore.collection("chats")
                                .document(chat.chatId)
                                .collection("messages")
                                .get()
                                .await()

                            for (doc in snapshot.documents) {
                                val messageId = doc.getString("messageId") ?: continue
                                val senderId = doc.getString("senderId") ?: continue
                                val isDeleted = doc.getBoolean("isDeleted") == true || doc.getString("content") == "🚫 This message was deleted"

                                if (isDeleted) {
                                    val existing = messageDao.getMessageById(messageId)
                                    deleteLocalMediaFiles(messageId, existing?.mediaLocalUri)
                                    HardwareStateUtil.deleteFromMediaStore(context, messageId)
                                    messageDao.markMessageAsDeleted(messageId, "🚫 This message was deleted")
                                    val currentChatId = doc.getString("chatId") ?: chat.chatId
                                    chatDao.updateLastMessage(currentChatId, "🚫 This message was deleted", System.currentTimeMillis(), "cloud")
                                    continue
                                }

                                if (senderId == currentUserId) continue
                                if (messageDao.getMessageById(messageId) != null) continue

                                val chatId = doc.getString("chatId") ?: continue
                                val content = doc.getString("content")
                                val dataType = doc.getString("dataType") ?: "text"
                                val mimeType = doc.getString("mimeType") ?: if (dataType == "image") "image/jpeg" else null
                                val mediaSizeBytes = doc.getLong("mediaSizeBytes") ?: 0L
                                val mediaCloudUrl = doc.getString("mediaCloudUrl")
                                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                val senderUsername = doc.getString("senderUsername") ?: senderId

                                var localSavedUri: String? = null
                                if (!mediaCloudUrl.isNullOrBlank() && mediaCloudUrl.startsWith("data:image")) {
                                    try {
                                        val b64 = mediaCloudUrl.substringAfter("base64,")
                                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                        val mediaDir = File(context.filesDir, "received_media").apply { mkdirs() }
                                        val ext = if (mimeType?.contains("png") == true) "png" else "jpg"
                                        val localFile = File(mediaDir, "${messageId}.$ext")
                                        localFile.writeBytes(bytes)
                                        localSavedUri = localFile.absolutePath
                                    } catch (e: Exception) {
                                        android.util.Log.e("ChatRepository", "Error decoding polling image: ${e.message}", e)
                                    }
                                } else if (!mediaCloudUrl.isNullOrBlank() && mediaCloudUrl.startsWith("data:audio")) {
                                    try {
                                        val b64 = mediaCloudUrl.substringAfter("base64,")
                                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                        val voiceDir = File(context.filesDir, "voice_notes").apply { mkdirs() }
                                        val localFile = File(voiceDir, "${messageId}.m4a")
                                        localFile.writeBytes(bytes)
                                        localSavedUri = localFile.absolutePath
                                    } catch (e: Exception) {
                                        android.util.Log.e("ChatRepository", "Error decoding polling voice note: ${e.message}", e)
                                    }
                                }

                                val isVoice = dataType == "voice" || (!mediaCloudUrl.isNullOrBlank() && mediaCloudUrl.startsWith("data:audio"))
                                val isMedia = dataType == "image" || (!mediaCloudUrl.isNullOrBlank() && mediaCloudUrl.startsWith("data:image"))
                                val previewText = if (isVoice) (content ?: "🎤 Voice message") else if (isMedia) "[Image Attachment]" else (content ?: "[File]")

                                ensureChatExists(chatId, senderUsername, previewText, timestamp, "cloud")

                                val msg = MessageEntity(
                                    messageId = messageId,
                                    chatId = chatId,
                                    senderId = senderId,
                                    targetId = currentUserId,
                                    content = content,
                                    dataType = if (isMedia) "image" else dataType,
                                    mediaLocalUri = localSavedUri,
                                    mediaCloudUrl = mediaCloudUrl,
                                    mediaSizeBytes = mediaSizeBytes,
                                    mimeType = mimeType,
                                    deliveryStatus = "delivered",
                                    hopCount = 0,
                                    transportUsed = "cloud",
                                    timestamp = timestamp
                                )
                                messageDao.insertMessage(msg)
                                chatDao.incrementUnread(chatId)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ChatRepository", "Polling error for chat ${chat.chatId}: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatRepository", "Polling fallback error: ${e.message}", e)
                }
                kotlinx.coroutines.delay(5000) // Poll every 5 seconds
            }
        }
    }

    fun stopCloudListener() {
        firestoreMessagesListener?.remove()
        firestoreMessagesListener = null
        cloudListenerStarted = false
    }

    private suspend fun ensureChatExists(
        chatId: String,
        targetUserId: String,
        preview: String,
        timestamp: Long,
        transport: String
    ) {
        val existing = chatDao.getChatById(chatId)
        if (existing == null) {
            val newChat = ChatEntity(
                chatId = chatId,
                participants = targetUserId,
                chatTitle = "Peer $targetUserId",
                lastMessagePreview = preview,
                lastMessageTimestamp = timestamp,
                unreadCount = 0,
                transportType = transport
            )
            chatDao.insertChat(newChat)
        } else {
            chatDao.updateLastMessage(chatId, preview, timestamp, transport)
        }
    }

    companion object {
        fun generateChatId(userA: String, userB: String): String {
            val cleanA = userA.trim().lowercase()
            val cleanB = userB.trim().lowercase()
            return if (cleanA < cleanB) "${cleanA}_${cleanB}" else "${cleanB}_${cleanA}"
        }
    }
}
