package com.phantom.mesh.mesh

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.phantom.mesh.data.local.dao.MeshQueueDao
import com.phantom.mesh.data.local.dao.MessageDao
import com.phantom.mesh.data.local.entity.MeshQueueEntity
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.network.NetworkStateManager
import com.phantom.mesh.network.NetworkTransport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central Nearby Connections manager — the heart of the mesh networking engine.
 *
 * ## Responsibilities
 * 1. Automatic advertising + discovery when internet drops (triggered by [NetworkStateManager])
 * 2. Accepting/managing peer connections using [Strategy.P2P_CLUSTER]
 * 3. Receiving packets and routing them through the [MeshRoutingTable]
 * 4. Forwarding packets that aren't addressed to this node (multi-hop relay)
 * 5. Dropping packets that exceed 3 hops (loop prevention)
 * 6. Driving the priority queue — text instant, media waits for Wi-Fi Direct
 * 7. Sending ACK packets back through the mesh for read receipts
 *
 * ## Connection Strategy
 * P2P_CLUSTER allows multiple simultaneous connections, which is essential
 * for forming the mesh topology. Bluetooth handles the initial handshake,
 * then the connection upgrades to Wi-Fi Direct for high-bandwidth transfers.
 */
@Singleton
class MeshConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routingTable: MeshRoutingTable,
    private val networkStateManager: NetworkStateManager,
    private val messageDao: MessageDao,
    private val meshQueueDao: MeshQueueDao,
    private val sessionManager: SessionManager
) {
    companion object {
        /** Service ID must match across all PhantomMesh clients for discovery */
        private const val SERVICE_ID = "com.phantom.mesh.p2p"

        /** Max packet relay retries before marking as failed */
        private const val MAX_RETRIES = 3

        private const val TAG = "MeshConnectionManager"
    }

    private val exceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e(TAG, "Unhandled coroutine error: ${throwable.message}", throwable)
    }
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    /** The local user's identifier — set after authentication */
    private var localUserId: String? = null
    private var localDisplayName: String = "Anonymous"

    private var isAdvertising = false
    private var isDiscovering = false

    /** Emitted when a new message arrives via mesh for the local user */
    private val _incomingPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<MeshPacket> = _incomingPackets.asSharedFlow()

    /** Emitted when an ACK is received (for updating read receipts) */
    private val _incomingAcks = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)
    val incomingAcks: SharedFlow<MeshPacket> = _incomingAcks.asSharedFlow()

    private val pendingConnections = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val incomingFilePayloads = java.util.concurrent.ConcurrentHashMap<Long, Payload>()
    private var livenessJob: kotlinx.coroutines.Job? = null

    // ── Public API ─────────────────────────────────────────────

    /**
     * Set the authenticated user's identity.
     * Must be called after login before starting mesh operations.
     */
    fun setLocalIdentity(userId: String, displayName: String) {
        val changed = localUserId != userId || localDisplayName != displayName
        localUserId = userId
        localDisplayName = displayName
        if (changed && isAdvertising) {
            android.util.Log.i(TAG, "Identity updated to $displayName ($userId), restarting advertising")
            stopAdvertising()
            startAdvertising()
        }
    }

    /**
     * Start mesh networking — begin advertising and discovering.
     * Also starts a background liveness loop to maintain persistent connectivity.
     */
    fun startMesh() {
        if (localUserId == null) {
            localUserId = sessionManager.getUserId() ?: "anon_${UUID.randomUUID().toString().take(6)}"
            localDisplayName = sessionManager.getUsername() ?: "Anonymous"
        }
        startAdvertising()
        startDiscovering()

        if (livenessJob?.isActive != true) {
            livenessJob = scope.launch {
                while (true) {
                    kotlinx.coroutines.delay(8000)
                    try {
                        if (!isAdvertising) {
                            android.util.Log.d(TAG, "Liveness heartbeat: restarting advertising")
                            startAdvertising()
                        }
                        if (!isDiscovering) {
                            android.util.Log.d(TAG, "Liveness heartbeat: restarting discovery")
                            startDiscovering()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Liveness heartbeat error: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Forcefully restart advertising and discovery (useful for manual refresh from UI).
     */
    fun restartMesh() {
        android.util.Log.i(TAG, "Restarting mesh discovery and advertising")
        stopAdvertising()
        stopDiscovering()
        startMesh()
    }

    /** Returns true if at least one mesh peer is connected */
    fun hasActivePeers(): Boolean = routingTable.hasPeers()

    /**
     * Stop all mesh networking operations.
     * Called when internet is restored (handoff back to cloud).
     */
    fun stopMesh() {
        try {
            livenessJob?.cancel()
            livenessJob = null
            stopAdvertising()
            stopDiscovering()
            connectionsClient.stopAllEndpoints()
            routingTable.clear()
            pendingConnections.clear()
            incomingFilePayloads.clear()
            isAdvertising = false
            isDiscovering = false
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error stopping mesh: ${e.message}", e)
        }
    }

    /**
     * Send a [MeshPacket] into the mesh network.
     *
     * For text packets (priority 0): sent immediately.
     * For media packets (priority 1+): enqueued and sent when link is active.
     */
    fun sendPacket(packet: MeshPacket) {
        scope.launch {
            try {
                if (packet.priority() == 0) {
                    // Text / ACK / routing — instant send
                    routeAndSend(packet)
                } else {
                    // Media — enqueue for priority transfer
                    enqueueMediaPacket(packet)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed in sendPacket: ${e.message}", e)
            }
        }
    }

    /**
     * Process the next pending media packet in the priority queue.
     * Called periodically and when a link is confirmed active.
     */
    fun drainQueue() {
        scope.launch {
            try {
                while (true) {
                    val nextPacket = meshQueueDao.dequeueNext() ?: break

                    val packet = MeshPacket.fromJson(nextPacket.payloadJson)
                    val nextHop = routingTable.resolveNextHop(packet.targetId)

                    // Send to next hop if known, or flood to all active peers
                    val targetEndpoints = if (nextHop != null && routingTable.isConnected(nextHop)) {
                        listOf(nextHop)
                    } else {
                        routingTable.getAllConnectedEndpoints()
                    }

                    if (targetEndpoints.isNotEmpty()) {
                        meshQueueDao.updateStatus(nextPacket.packetId, "in_progress")
                        for (endpoint in targetEndpoints) {
                            sendToEndpoint(endpoint, packet)
                        }
                        meshQueueDao.updateStatus(nextPacket.packetId, "sent")
                    } else if (nextPacket.retryCount < MAX_RETRIES) {
                        // Re-queue for later retry and break out of loop since no peers are available right now
                        meshQueueDao.incrementRetry(nextPacket.packetId)
                        break
                    } else {
                        meshQueueDao.updateStatus(nextPacket.packetId, "failed")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed in drainQueue: ${e.message}", e)
            }
        }
    }

    // ── Advertising ────────────────────────────────────────────

    private fun startAdvertising() {
        if (isAdvertising) return

        try {
            val options = AdvertisingOptions.Builder()
                .setStrategy(Strategy.P2P_CLUSTER)
                .build()

            connectionsClient.startAdvertising(
                localDisplayName,
                SERVICE_ID,
                connectionLifecycleCallback,
                options
            ).addOnSuccessListener {
                isAdvertising = true
                android.util.Log.i(TAG, "Advertising active as '$localDisplayName' ($SERVICE_ID)")
            }.addOnFailureListener { e ->
                val statusCode = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
                if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                    isAdvertising = true
                    android.util.Log.i(TAG, "Already advertising (STATUS_ALREADY_ADVERTISING)")
                } else {
                    isAdvertising = false
                    android.util.Log.w(TAG, "startAdvertising failed (code=$statusCode): ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error starting advertising: ${e.message}", e)
            isAdvertising = false
        }
    }

    private fun stopAdvertising() {
        try {
            connectionsClient.stopAdvertising()
        } catch (ignored: Exception) {}
        isAdvertising = false
    }

    // ── Discovery ──────────────────────────────────────────────

    private fun startDiscovering() {
        if (isDiscovering) return

        try {
            val options = DiscoveryOptions.Builder()
                .setStrategy(Strategy.P2P_CLUSTER)
                .build()

            connectionsClient.startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                options
            ).addOnSuccessListener {
                isDiscovering = true
                android.util.Log.i(TAG, "Discovery active ($SERVICE_ID)")
            }.addOnFailureListener { e ->
                val statusCode = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
                if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                    isDiscovering = true
                    android.util.Log.i(TAG, "Already discovering (STATUS_ALREADY_DISCOVERING)")
                } else {
                    isDiscovering = false
                    android.util.Log.w(TAG, "startDiscovery failed (code=$statusCode): ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error starting discovery: ${e.message}", e)
            isDiscovering = false
        }
    }

    private fun stopDiscovering() {
        try {
            connectionsClient.stopDiscovery()
        } catch (ignored: Exception) {}
        isDiscovering = false
    }

    // ── Endpoint Discovery Callback ────────────────────────────

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val peerName = if (info.endpointName.isNotBlank()) info.endpointName else endpointId
            android.util.Log.i(TAG, "Nearby peer discovered: $endpointId ($peerName)")

            if (routingTable.isConnected(endpointId)) {
                android.util.Log.i(TAG, "Peer $endpointId already connected, skipping request")
                return
            }

            // Symmetry breaking: The device with lexicographically smaller identifier initiates the connection
            // This prevents mutual request collision in P2P_CLUSTER mode
            val myKey = (localUserId ?: localDisplayName).lowercase().trim()
            val peerKey = if (info.endpointName.isNotBlank()) info.endpointName.lowercase().trim() else endpointId
            val shouldInitiate = if (myKey != peerKey) myKey < peerKey else localDisplayName < endpointId

            if (shouldInitiate) {
                if (pendingConnections.add(endpointId)) {
                    android.util.Log.i(TAG, "Initiating connection to peer: $endpointId ($peerName) [myKey < peerKey]")
                    connectionsClient.requestConnection(
                        localDisplayName,
                        endpointId,
                        connectionLifecycleCallback
                    ).addOnSuccessListener {
                        android.util.Log.i(TAG, "Requested connection to peer: $endpointId")
                    }.addOnFailureListener { e ->
                        pendingConnections.remove(endpointId)
                        android.util.Log.w(TAG, "Failed to request connection to $endpointId: ${e.message}")
                    }
                }
            } else {
                android.util.Log.i(TAG, "Discovered $endpointId ($peerName), waiting for peer to initiate [myKey >= peerKey]")
                // Fallback timeout: If peer doesn't initiate within 1.5 seconds, initiate anyway to avoid deadlock
                scope.launch {
                    kotlinx.coroutines.delay(1500)
                    if (!routingTable.isConnected(endpointId) && pendingConnections.add(endpointId)) {
                        android.util.Log.i(TAG, "Fallback timeout: Requesting connection to peer $endpointId ($peerName)")
                        connectionsClient.requestConnection(
                            localDisplayName,
                            endpointId,
                            connectionLifecycleCallback
                        ).addOnFailureListener { e ->
                            pendingConnections.remove(endpointId)
                            android.util.Log.w(TAG, "Fallback request failed: ${e.message}")
                        }
                    }
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            android.util.Log.i(TAG, "Nearby peer lost: $endpointId")
            pendingConnections.remove(endpointId)
            routingTable.removePeer(endpointId)
        }
    }

    // ── Connection Lifecycle Callback ──────────────────────────

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            pendingConnections.remove(endpointId)
            val peerName = if (info.endpointName.isNotBlank()) info.endpointName else "Peer-${endpointId.take(4)}"
            android.util.Log.i(TAG, "Incoming connection initiated from $endpointId ($peerName)")
            routingTable.addPeer(endpointId, peerName, "bluetooth")
            routingTable.associateUserId(endpointId, peerName.lowercase())

            // Auto-accept all connections for zero-config mesh networking
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnSuccessListener {
                    android.util.Log.i(TAG, "Accepted connection from $endpointId")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e(TAG, "Failed to accept connection from $endpointId: ${e.message}", e)
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pendingConnections.remove(endpointId)
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK,
                ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT -> {
                    android.util.Log.i(TAG, "Connection ESTABLISHED with $endpointId (statusCode=${result.status.statusCode})")
                    val existing = routingTable.getNode(endpointId)
                    val resolvedName = existing?.displayName ?: "Peer-${endpointId.take(4)}"
                    routingTable.addPeer(endpointId, resolvedName, "wifi_direct")
                    networkStateManager.updateMeshTransport(NetworkTransport.WifiDirect)

                    // Send our identity and routing advertisement immediately
                    scope.launch {
                        sendIdentityAnnouncement(endpointId)
                        sendRoutingAdvertisement(endpointId)
                    }

                    drainQueue()
                }

                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    android.util.Log.w(TAG, "Connection rejected by peer: $endpointId")
                }

                ConnectionsStatusCodes.STATUS_ERROR -> {
                    android.util.Log.w(TAG, "Connection error with peer: $endpointId (${result.status.statusMessage})")
                    routingTable.removePeer(endpointId)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            pendingConnections.remove(endpointId)
            routingTable.removePeer(endpointId)

            // If no peers left, update transport to Offline
            if (!routingTable.hasPeers()) {
                networkStateManager.updateMeshTransport(NetworkTransport.Offline)
            }
        }
    }

    // ── Payload Callback (Receiving Packets) ───────────────────

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val json = String(bytes, Charsets.UTF_8)
                    scope.launch {
                        handleIncomingPacket(json, endpointId)
                    }
                }
                Payload.Type.FILE -> {
                    incomingFilePayloads[payload.id] = payload
                }
                else -> {}
            }
        }

        override fun onPayloadTransferUpdate(
            endpointId: String,
            update: PayloadTransferUpdate
        ) {
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    val payload = incomingFilePayloads.remove(update.payloadId)
                    if (payload != null && payload.type == Payload.Type.FILE) {
                        val receivedFile = payload.asFile()?.asJavaFile()
                        if (receivedFile != null && receivedFile.exists()) {
                            scope.launch {
                                try {
                                    val json = receivedFile.readText(Charsets.UTF_8)
                                    handleIncomingPacket(json, endpointId)
                                } catch (e: Exception) {
                                    android.util.Log.e(TAG, "Failed reading incoming file payload: ${e.message}", e)
                                } finally {
                                    try { receivedFile.delete() } catch (ignored: Exception) {}
                                }
                            }
                        }
                    }
                }
                PayloadTransferUpdate.Status.FAILURE,
                PayloadTransferUpdate.Status.CANCELED -> {
                    incomingFilePayloads.remove(update.payloadId)
                }
                else -> {}
            }
        }
    }

    // ── Core Routing Logic ─────────────────────────────────────

    /**
     * Handle an incoming mesh packet — the core of the 3-hop routing.
     *
     * Decision tree:
     * 1. Is this packet for ME?        → Emit to [incomingPackets], send ACK back
     * 2. Is this packet expired (3+)?   → DROP (loop prevention)
     * 3. Otherwise                      → Forward to next hop (relay)
     */
    private suspend fun handleIncomingPacket(json: String, fromEndpointId: String) {
        val packet = try {
            MeshPacket.fromJson(json)
        } catch (e: Exception) {
            return // Malformed packet — silently drop
        }

        val myUsername = sessionManager.getUsername()
        val myUserId = sessionManager.getUserId() ?: localUserId
        val isForMe = packet.targetId.equals(myUserId, ignoreCase = true) ||
                      packet.targetId.equals(localUserId, ignoreCase = true) ||
                      (myUsername != null && packet.targetId.equals(myUsername, ignoreCase = true)) ||
                      packet.targetId.equals(localDisplayName, ignoreCase = true) ||
                      packet.targetId.equals("broadcast", ignoreCase = true)

        when {
            packet.dataType == "identity" -> {
                val parts = packet.data.split(":")
                val peerUserId = parts.getOrNull(0) ?: packet.senderId
                val peerUsername = parts.getOrNull(1) ?: peerUserId
                routingTable.associateUserId(fromEndpointId, peerUserId)
                routingTable.associateUserId(fromEndpointId, peerUsername.lowercase())
            }

            // CASE 1: Packet is addressed to us
            isForMe -> {
                when (packet.dataType) {
                    "ack" -> {
                        // ACK received — update delivery status to "delivered"
                        _incomingAcks.emit(packet)
                        packet.refMessageId?.let { msgId ->
                            messageDao.updateDeliveryStatus(msgId, "delivered")
                        }
                    }
                    "routing" -> {
                        // Routing advertisement — update peer's reachable table
                        val reachable = packet.data.split(",").toSet()
                        routingTable.updateReachablePeers(fromEndpointId, reachable)
                    }
                    "delete_msg" -> {
                        // Remote message delete request received via mesh
                        _incomingPackets.emit(packet)
                        val ack = packet.createAck()
                        routeAndSend(ack)
                    }
                    else -> {
                        // Regular message — emit for local processing
                        _incomingPackets.emit(packet)

                        // Send ACK back to sender (bounces through mesh)
                        val ack = packet.createAck()
                        routeAndSend(ack)
                    }
                }
            }

            // CASE 2: Packet has exceeded 3 hops — DROP
            packet.isExpired() -> {
                // Silently dropped — no further action
                return
            }

            // CASE 3: Not for us and not expired — FORWARD (relay)
            else -> {
                val forwardPacket = packet.incrementHop()
                routeAndSend(forwardPacket)
            }
        }
    }

    /**
     * Route a packet to its target — either direct send or flood broadcast.
     */
    private fun routeAndSend(packet: MeshPacket) {
        val nextHop = routingTable.resolveNextHop(packet.targetId)

        if (nextHop != null) {
            // Known route — send to specific endpoint
            sendToEndpoint(nextHop, packet)
        } else {
            // No known route — flood broadcast to all connected peers
            val allEndpoints = routingTable.getAllConnectedEndpoints()
            if (allEndpoints.isNotEmpty()) {
                for (endpointId in allEndpoints) {
                    sendToEndpoint(endpointId, packet)
                }
            } else {
                // No peers connected right now: Enqueue so packet will be sent when connection links up!
                scope.launch {
                    enqueueMediaPacket(packet)
                }
            }
        }
    }

    /**
     * Send a serialized packet to a specific Nearby Connections endpoint.
     * Small control packets (<= 32KB) are sent via Payload.fromBytes.
     * Larger payloads (e.g. image attachments) are sent via Payload.fromFile to prevent
     * Nearby Connections 32KB buffer overflow.
     */
    private fun sendToEndpoint(endpointId: String, packet: MeshPacket) {
        try {
            val jsonBytes = packet.toJson().toByteArray(Charsets.UTF_8)
            if (jsonBytes.size <= 32 * 1024) {
                val payload = Payload.fromBytes(jsonBytes)
                connectionsClient.sendPayload(endpointId, payload)
            } else {
                val cacheFile = java.io.File(context.cacheDir, "mesh_send_${packet.packetId}.json")
                cacheFile.writeBytes(jsonBytes)
                val filePayload = Payload.fromFile(cacheFile)
                connectionsClient.sendPayload(endpointId, filePayload)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to send payload to endpoint $endpointId: ${e.message}", e)
        }
    }

    // ── Identity & Routing Advertisements ──────────────────────

    /**
     * Announce our user ID to a newly connected peer so they can
     * populate their routing table with our identity.
     */
    private fun sendIdentityAnnouncement(endpointId: String) {
        val userId = localUserId ?: return
        val packet = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            senderId = userId,
            targetId = endpointId, // Directed to this specific peer
            data = "$userId:$localDisplayName",
            dataType = "identity"
        )
        sendToEndpoint(endpointId, packet)
    }

    /**
     * Send our routing table knowledge to a peer — enables multi-hop routing.
     * Each peer learns which other peers are reachable through us.
     */
    private fun sendRoutingAdvertisement(endpointId: String) {
        val userId = localUserId ?: return
        val reachable = routingTable.generateRoutingAdvertisement()
        if (reachable.isEmpty()) return

        val packet = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            senderId = userId,
            targetId = endpointId,
            data = reachable.joinToString(","),
            dataType = "routing"
        )
        sendToEndpoint(endpointId, packet)
    }

    // ── Priority Queue Management ──────────────────────────────

    /**
     * Enqueue a media packet for deferred transfer.
     */
    private suspend fun enqueueMediaPacket(packet: MeshPacket) {
        val nextHop = routingTable.resolveNextHop(packet.targetId)
        val queueEntry = MeshQueueEntity(
            packetId = packet.packetId,
            payloadJson = packet.toJson(),
            priority = packet.priority(),
            nextHopEndpointId = nextHop,
            status = "pending"
        )
        meshQueueDao.enqueue(queueEntry)

        // Immediately try to drain if an active link exists
        if (nextHop != null && routingTable.isConnected(nextHop)) {
            drainQueue()
        }
    }
}
