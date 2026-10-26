package com.phantom.mesh.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory routing table for the 3-hop mesh network.
 *
 * Tracks all connected Nearby Connections endpoints and their known
 * reachable peers (learned via routing advertisements).
 *
 * ## Routing Logic
 *
 * When Node B receives a packet for Node C:
 * 1. Check if Node C is DIRECTLY connected → forward to C
 * 2. If not, check which connected peers claim to reach C → forward to best route
 * 3. If no route found → broadcast to ALL connected peers (flood)
 * 4. If hopCount >= 3 → DROP the packet (loop prevention)
 *
 * ## Topology Map
 *
 * The routing table is exposed as a [StateFlow] of [MeshNode] lists
 * for the Compose topology visualization screen.
 */
@Singleton
class MeshRoutingTable @Inject constructor() {

    /**
     * Represents a node in the mesh topology.
     *
     * @param endpointId Nearby Connections endpoint ID
     * @param userId PhantomMesh user ID (if authenticated / announced)
     * @param displayName Anonymous display name
     * @param reachablePeers Set of user IDs this node can reach (learned via advertisements)
     * @param connectionType "wifi_direct" or "bluetooth"
     * @param signalStrength Approximate signal quality (0.0–1.0)
     * @param connectedSince Epoch millis when the connection was established
     */
    data class MeshNode(
        val endpointId: String,
        val userId: String? = null,
        val displayName: String = "Anonymous",
        val reachablePeers: MutableSet<String> = mutableSetOf(),
        val connectionType: String = "bluetooth",
        val signalStrength: Float = 0.5f,
        val connectedSince: Long = System.currentTimeMillis()
    )

    /** Direct connections: endpointId → MeshNode */
    private val directConnections = ConcurrentHashMap<String, MeshNode>()

    /** Reverse lookup: userId → endpointId (for routing by user ID) */
    private val userToEndpoint = ConcurrentHashMap<String, String>()

    /** Reactive topology state for the UI */
    private val _topology = MutableStateFlow<List<MeshNode>>(emptyList())
    val topology: StateFlow<List<MeshNode>> = _topology.asStateFlow()

    /** Total connected peer count */
    private val _peerCount = MutableStateFlow(0)
    val peerCount: StateFlow<Int> = _peerCount.asStateFlow()

    // ── Connection Lifecycle ───────────────────────────────────

    /**
     * Register a new direct peer connection.
     * Called when Nearby Connections establishes a link.
     */
    fun addPeer(endpointId: String, displayName: String, connectionType: String) {
        val existing = directConnections[endpointId]
        val resolvedName = if (displayName.startsWith("Peer-") || displayName.equals("Anonymous", ignoreCase = true)) {
            if (existing?.displayName != null && !existing.displayName.startsWith("Peer-") && !existing.displayName.equals("Anonymous", ignoreCase = true)) {
                existing.displayName
            } else {
                displayName
            }
        } else {
            displayName
        }
        val node = MeshNode(
            endpointId = endpointId,
            userId = existing?.userId,
            displayName = resolvedName,
            connectionType = connectionType,
            reachablePeers = existing?.reachablePeers ?: mutableSetOf(),
            connectedSince = existing?.connectedSince ?: System.currentTimeMillis()
        )
        directConnections[endpointId] = node
        emitTopologyUpdate()
    }

    /**
     * Get a specific mesh node by endpoint ID.
     */
    fun getNode(endpointId: String): MeshNode? = directConnections[endpointId]

    /**
     * Check if a peer is connected.
     */
    fun isConnected(endpointId: String): Boolean = directConnections.containsKey(endpointId)

    /**
     * Associate a Nearby Connections endpoint with a PhantomMesh user ID or username.
     * Called after the peer sends its user identity announcement.
     */
    fun associateUserId(endpointId: String, userId: String) {
        val cleanUser = userId.trim().lowercase()
        directConnections[endpointId]?.let { node ->
            directConnections[endpointId] = node.copy(userId = cleanUser)
            userToEndpoint[cleanUser] = endpointId
            userToEndpoint[userId] = endpointId
            emitTopologyUpdate()
        }
    }

    /**
     * Remove a peer when the Nearby Connection is lost.
     */
    fun removePeer(endpointId: String) {
        val node = directConnections.remove(endpointId)
        node?.userId?.let { userToEndpoint.remove(it) }

        // Also remove this peer from other nodes' reachablePeers sets
        directConnections.values.forEach { peer ->
            node?.userId?.let { peer.reachablePeers.remove(it) }
        }

        emitTopologyUpdate()
    }

    /**
     * Update reachable peers for a node (learned from routing advertisements).
     * A connected peer broadcasts which other peers IT can reach,
     * allowing us to make 2nd and 3rd hop routing decisions.
     */
    fun updateReachablePeers(endpointId: String, reachableUserIds: Set<String>) {
        directConnections[endpointId]?.let { node ->
            node.reachablePeers.clear()
            node.reachablePeers.addAll(reachableUserIds)
            emitTopologyUpdate()
        }
    }

    // ── Routing Decisions ──────────────────────────────────────

    /**
     * Resolve the next-hop endpoint ID for a target user.
     *
     * @return The endpoint ID to forward to, or null if no route exists.
     *
     * Resolution order:
     * 1. Direct connection to target
     * 2. Indirect route via a peer that claims reachability
     * 3. null (caller should broadcast/flood)
     */
    fun resolveNextHop(targetUserId: String): String? {
        val cleanTarget = targetUserId.trim().lowercase()
        // 1. Direct connection by userId mapping
        userToEndpoint[cleanTarget]?.let { return it }
        userToEndpoint[targetUserId]?.let { return it }

        // 2. Direct connection matching node displayName or userId
        for ((endpointId, node) in directConnections) {
            if (node.displayName.equals(cleanTarget, ignoreCase = true) ||
                node.userId?.equals(cleanTarget, ignoreCase = true) == true) {
                return endpointId
            }
        }

        // 3. Indirect route — find a peer that can reach the target
        for ((endpointId, node) in directConnections) {
            if (cleanTarget in node.reachablePeers || targetUserId in node.reachablePeers) {
                return endpointId
            }
        }

        // 4. No known route
        return null
    }

    /**
     * Get all directly connected endpoint IDs for broadcast/flood.
     * Used when no specific route is known for a target.
     */
    fun getAllConnectedEndpoints(): List<String> =
        directConnections.keys().toList()

    /**
     * Check if a specific endpoint is directly connected.
     */
    fun isDirectlyConnected(endpointId: String): Boolean =
        directConnections.containsKey(endpointId)

    /**
     * Check if ANY peer is connected (for mesh availability indicator).
     */
    fun hasPeers(): Boolean = directConnections.isNotEmpty()

    /**
     * Get the connection type for a specific endpoint.
     * Used to determine if Wi-Fi Direct is available for heavy payloads.
     */
    fun getConnectionType(endpointId: String): String? =
        directConnections[endpointId]?.connectionType

    /**
     * Check if a stable Wi-Fi Direct link exists to the next hop.
     * Media payloads are only dequeued when this returns true.
     */
    fun hasStableWifiDirect(endpointId: String): Boolean =
        directConnections[endpointId]?.connectionType == "wifi_direct"

    /**
     * Generate a routing advertisement packet containing all user IDs
     * reachable from this node (direct connections + their known peers).
     */
    fun generateRoutingAdvertisement(): Set<String> {
        val reachable = mutableSetOf<String>()
        directConnections.values.forEach { node ->
            node.userId?.let { reachable.add(it) }
            reachable.addAll(node.reachablePeers)
        }
        return reachable
    }

    /**
     * Clear the entire routing table.
     * Called when mesh networking is stopped (e.g., internet restored).
     */
    fun clear() {
        directConnections.clear()
        userToEndpoint.clear()
        emitTopologyUpdate()
    }

    // ── Internal ───────────────────────────────────────────────

    private fun emitTopologyUpdate() {
        _topology.value = directConnections.values.toList()
        _peerCount.value = directConnections.size
    }
}
