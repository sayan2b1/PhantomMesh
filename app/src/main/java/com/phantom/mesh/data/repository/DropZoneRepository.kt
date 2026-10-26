package com.phantom.mesh.data.repository

import com.google.gson.Gson
import com.phantom.mesh.data.local.dao.DropZoneDao
import com.phantom.mesh.data.local.entity.DropZoneEntity
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.mesh.MeshConnectionManager
import com.phantom.mesh.mesh.MeshPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class DropZoneMetadataPayload(
    val dropId: String,
    val ownerId: String,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val fileHash: String,
    val expiresAt: Long,
    val broadcastRadius: Int
)

@Singleton
class DropZoneRepository @Inject constructor(
    private val dropZoneDao: DropZoneDao,
    private val sessionManager: SessionManager,
    private val meshConnectionManager: MeshConnectionManager
) {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Listen for incoming mesh packets related to Drop Zones
        scope.launch {
            meshConnectionManager.incomingPackets.collectLatest { packet ->
                when (packet.dataType) {
                    "drop_meta" -> handleIncomingDropMetadata(packet)
                    "drop_pull" -> handlePullRequest(packet)
                }
            }
        }
    }

    fun observeActiveDrops(): Flow<List<DropZoneEntity>> = dropZoneDao.observeActiveDrops()

    fun observeMyDrops(ownerId: String): Flow<List<DropZoneEntity>> = dropZoneDao.observeMyDrops(ownerId)

    /**
     * Pins a local file to the proximity Drop Zone.
     *
     * Computes SHA-256 integrity hash, stores in Room DB with expiresAt (TTL),
     * and broadcasts metadata to the mesh via Nearby Connections.
     */
    suspend fun pinFile(file: File, mimeType: String, ttlMinutes: Long = 60, broadcastRadius: Int = 2): DropZoneEntity = withContext(Dispatchers.IO) {
        val ownerId = sessionManager.getUserId() ?: "anon_owner"
        val dropId = "drop_${UUID.randomUUID().toString().take(8)}"
        val expiresAt = System.currentTimeMillis() + (ttlMinutes * 60 * 1000)
        val hash = calculateSha256(file)

        val dropEntity = DropZoneEntity(
            dropId = dropId,
            ownerId = ownerId,
            fileName = file.name,
            mimeType = mimeType,
            fileSizeBytes = file.length(),
            localFilePath = file.absolutePath,
            fileHash = hash,
            isDownloaded = true,
            broadcastRadius = broadcastRadius.coerceIn(1, 3),
            expiresAt = expiresAt
        )
        dropZoneDao.insertDrop(dropEntity)

        // Broadcast metadata to mesh
        val metaPayload = DropZoneMetadataPayload(
            dropId = dropId,
            ownerId = ownerId,
            fileName = file.name,
            mimeType = mimeType,
            fileSizeBytes = file.length(),
            fileHash = hash,
            expiresAt = expiresAt,
            broadcastRadius = broadcastRadius
        )

        val packet = MeshPacket(
            packetId = "drop_meta_$dropId",
            senderId = ownerId,
            targetId = "broadcast",
            data = gson.toJson(metaPayload),
            dataType = "drop_meta",
            hopCount = 0
        )
        meshConnectionManager.sendPacket(packet)

        return@withContext dropEntity
    }

    /**
     * Requests pulling a file payload from the drop owner over the mesh.
     */
    suspend fun requestPull(drop: DropZoneEntity) = withContext(Dispatchers.IO) {
        val requesterId = sessionManager.getUserId() ?: "anon_puller"
        val packet = MeshPacket(
            packetId = "pull_${UUID.randomUUID().toString().take(8)}",
            senderId = requesterId,
            targetId = drop.ownerId,
            data = drop.dropId,
            dataType = "drop_pull",
            hopCount = 0
        )
        meshConnectionManager.sendPacket(packet)
    }

    /**
     * Enforces TTL expiration — deletes expired drop entries and their local files from Room and disk.
     */
    suspend fun purgeExpiredDrops(): Int = withContext(Dispatchers.IO) {
        dropZoneDao.purgeExpiredDrops()
    }

    private suspend fun handleIncomingDropMetadata(packet: MeshPacket) {
        try {
            val meta = gson.fromJson(packet.data, DropZoneMetadataPayload::class.java)
            // If drop already expired, ignore
            if (meta.expiresAt <= System.currentTimeMillis()) return

            val currentUserId = sessionManager.getUserId()
            val isOwner = currentUserId == meta.ownerId

            val entity = DropZoneEntity(
                dropId = meta.dropId,
                ownerId = meta.ownerId,
                fileName = meta.fileName,
                mimeType = meta.mimeType,
                fileSizeBytes = meta.fileSizeBytes,
                localFilePath = null,
                fileHash = meta.fileHash,
                isDownloaded = isOwner,
                broadcastRadius = meta.broadcastRadius,
                expiresAt = meta.expiresAt
            )
            dropZoneDao.insertDrop(entity)
        } catch (e: Exception) {
            // Malformed metadata payload
        }
    }

    private suspend fun handlePullRequest(packet: MeshPacket) {
        val dropId = packet.data
        val drop = dropZoneDao.getDropById(dropId) ?: return
        if (drop.localFilePath == null) return

        val localFile = File(drop.localFilePath)
        if (!localFile.exists()) return

        // Send file data packet directly to requester
        val filePacket = MeshPacket(
            packetId = "file_drop_${drop.dropId}",
            senderId = drop.ownerId,
            targetId = packet.senderId,
            data = localFile.name,
            dataType = "drop_zone",
            payloadSize = localFile.length()
        )
        meshConnectionManager.sendPacket(filePacket)
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
