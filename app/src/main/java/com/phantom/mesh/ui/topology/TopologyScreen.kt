package com.phantom.mesh.ui.topology

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.mesh.mesh.MeshRoutingTable
import com.phantom.mesh.ui.theme.DarkBackground
import com.phantom.mesh.ui.theme.DarkCard
import com.phantom.mesh.ui.theme.DarkSurface
import com.phantom.mesh.ui.theme.NeonCyan
import com.phantom.mesh.ui.theme.NeonGreen
import com.phantom.mesh.ui.theme.TextMuted
import com.phantom.mesh.ui.theme.TextPrimary
import com.phantom.mesh.ui.theme.TextSecondary
import com.phantom.mesh.ui.theme.TransportBluetooth
import com.phantom.mesh.ui.theme.TransportWifiDirect
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopologyScreen(
    viewModel: TopologyViewModel,
    onBackClick: () -> Unit,
    onNodeClick: (MeshRoutingTable.MeshNode) -> Unit = {}
) {
    val nodes by viewModel.nodes.collectAsState()
    val peerCount by viewModel.peerCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MESH TOPOLOGY RADAR",
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Connected Peers: $peerCount | Max Hops: 3",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NeonCyan
                        )
                    }
                },
                actions = {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(
                        onClick = {
                            viewModel.restartMesh()
                            android.widget.Toast.makeText(context, "Scanning for mesh peers...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Restart Mesh Scan",
                            tint = NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Radar Graph Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(DarkSurface),
                contentAlignment = Alignment.Center
            ) {
                RadarCanvas(nodes = nodes)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Routing Table List Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE ROUTING TABLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${nodes.size} DIRECT NODES",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Routing Table List
            if (nodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No mesh peers connected",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ensure Bluetooth, Location & Wi-Fi are ON (no internet required)",
                            color = TextMuted.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        val context = androidx.compose.ui.platform.LocalContext.current
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                viewModel.restartMesh()
                                android.widget.Toast.makeText(context, "Restarting mesh discovery...", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SCAN & LINK PEERS",
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    items(nodes, key = { it.endpointId }) { node ->
                        MeshNodeCard(node = node, onNodeClick = onNodeClick)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RadarCanvas(nodes: List<MeshRoutingTable.MeshNode>) {
    val infiniteTransition = rememberInfiniteTransition(label = "radarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    Canvas(modifier = Modifier.size(240.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2

        // Radar grid circles (representing 1-hop, 2-hop, 3-hop distance)
        drawCircle(
            color = NeonCyan.copy(alpha = 0.15f),
            radius = radius * 0.33f,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(
            color = NeonCyan.copy(alpha = 0.2f),
            radius = radius * 0.66f,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(
            color = NeonCyan.copy(alpha = 0.25f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Crosshairs
        drawLine(
            color = NeonCyan.copy(alpha = 0.15f),
            start = Offset(center.x, 0f),
            end = Offset(center.x, size.height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = NeonCyan.copy(alpha = 0.15f),
            start = Offset(0f, center.y),
            end = Offset(size.width, center.y),
            strokeWidth = 1.dp.toPx()
        )

        // Local Node in Center
        drawCircle(color = NeonGreen, radius = 6.dp.toPx(), center = center)

        // Draw connected mesh nodes arranged along radius
        nodes.forEachIndexed { index, node ->
            val angleDeg = (index.toFloat() / nodes.size.coerceAtLeast(1)) * 360f + 30f
            val rad = Math.toRadians(angleDeg.toDouble())
            val nodeDistance = radius * (0.45f + (index % 3) * 0.2f)
            val nodeX = (center.x + nodeDistance * cos(rad)).toFloat()
            val nodeY = (center.y + nodeDistance * sin(rad)).toFloat()

            // Connection line from center to node
            drawLine(
                color = if (node.connectionType == "wifi_direct") NeonCyan.copy(alpha = 0.4f) else TransportBluetooth.copy(alpha = 0.3f),
                start = center,
                end = Offset(nodeX, nodeY),
                strokeWidth = 1.dp.toPx()
            )

            // Peer Node Dot
            drawCircle(
                color = if (node.connectionType == "wifi_direct") TransportWifiDirect else TransportBluetooth,
                radius = 5.dp.toPx(),
                center = Offset(nodeX, nodeY)
            )
        }
    }
}

@Composable
fun MeshNodeCard(
    node: MeshRoutingTable.MeshNode,
    onNodeClick: (MeshRoutingTable.MeshNode) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable { onNodeClick(node) }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (node.connectionType == "wifi_direct") TransportWifiDirect.copy(alpha = 0.15f) else TransportBluetooth.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (node.connectionType == "wifi_direct") Icons.Default.Wifi else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (node.connectionType == "wifi_direct") TransportWifiDirect else TransportBluetooth,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    val cleanNodeName = remember(node.displayName) {
                        node.displayName
                            .removePrefix("Peer ")
                            .removePrefix("Peer-")
                            .removePrefix("PEER: ")
                            .trim()
                            .ifBlank { "User-${node.endpointId.take(4)}" }
                    }
                    Text(
                        text = cleanNodeName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "ID: ${node.userId?.take(8) ?: node.endpointId.take(8)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (node.connectionType == "wifi_direct") TransportWifiDirect.copy(alpha = 0.2f) else TransportBluetooth.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (node.connectionType == "wifi_direct") "WI-FI DIRECT" else "BLUETOOTH",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (node.connectionType == "wifi_direct") TransportWifiDirect else TransportBluetooth
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${node.reachablePeers.size} Hops Known",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonCyan)
                        .clickable { onNodeClick(node) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CHAT",
                        color = DarkBackground,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
