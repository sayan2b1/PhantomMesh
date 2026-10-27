package com.phantom.mesh.ui.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.border
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import com.phantom.mesh.util.HardwareStateUtil
import com.phantom.mesh.ui.theme.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.mesh.data.local.entity.ChatEntity
import com.phantom.mesh.ui.components.NetworkIndicator
import com.phantom.mesh.ui.theme.DarkBackground
import com.phantom.mesh.ui.theme.DarkCard
import com.phantom.mesh.ui.theme.DarkSurface
import com.phantom.mesh.ui.theme.DarkSurfaceVariant
import com.phantom.mesh.ui.theme.DuressCrimson
import com.phantom.mesh.ui.theme.NeonCyan
import com.phantom.mesh.ui.theme.TextMuted
import com.phantom.mesh.ui.theme.TextPrimary
import com.phantom.mesh.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onChatClick: (chatId: String, targetId: String) -> Unit,
    onNavigateTopology: () -> Unit,
    onNavigateDropZone: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val transport by viewModel.activeTransport.collectAsState()
    val chats by viewModel.chats.collectAsState()
    val isDuress by viewModel.isDuressSession.collectAsState()
    val currentUsername by viewModel.currentUsername.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val isBtOnly by viewModel.isBluetoothOnlyMode.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsState()
    val contactAliases by viewModel.contactAliases.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val radarRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAngle"
    )

    var showNewChatDialog by remember { mutableStateOf(false) }
    var newChatPeerId by remember { mutableStateOf("") }
    var chatForAliasEdit by remember { mutableStateOf<ChatEntity?>(null) }
    var aliasInput by remember { mutableStateOf("") }

    val context = LocalContext.current
    var isBtEnabled by remember { mutableStateOf(HardwareStateUtil.isBluetoothEnabled(context)) }
    var isLocEnabled by remember { mutableStateOf(HardwareStateUtil.isLocationEnabled(context)) }
    var isWifiEnabled by remember { mutableStateOf(HardwareStateUtil.isWifiEnabled(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            isBtEnabled = HardwareStateUtil.isBluetoothEnabled(context)
            isLocEnabled = HardwareStateUtil.isLocationEnabled(context)
            isWifiEnabled = HardwareStateUtil.isWifiEnabled(context)
            kotlinx.coroutines.delay(2500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "PHANTOM",
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        if (isDuress) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DuressCrimson.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "DECOY STATE",
                                    color = DuressCrimson,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Dynamic Transport Indicator
                    NetworkIndicator(transport = transport)

                    Spacer(modifier = Modifier.width(4.dp))

                    // Topology Radar Button with live sweep animation
                    IconButton(onClick = onNavigateTopology) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "Mesh Topology",
                            tint = NeonCyan,
                            modifier = Modifier.rotate(radarRotation)
                        )
                    }

                    // Proximity Drop Zone Button
                    IconButton(onClick = onNavigateDropZone) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Drop Zone", tint = NeonCyan)
                    }

                    // Settings Button
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatDialog = true },
                containerColor = NeonCyan,
                contentColor = DarkBackground
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Anonymous Conversation")
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Hardware state warning banners for Wi-Fi, Bluetooth, and Location
            if (isBtOnly) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonCyan.copy(alpha = 0.08f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Bluetooth-Only Mode Active (Wi-Fi kept OFF for testing)",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else if (!isWifiEnabled && !isBtEnabled) {
                MeshHardwareWarningBanner(
                    icon = Icons.Default.Wifi,
                    title = "All Mesh Radios are OFF",
                    message = "Turn on Bluetooth or Wi-Fi to allow your phone to discover and link with local mesh peers.",
                    actionText = "TURN ON",
                    onAction = { HardwareStateUtil.openWifiSettings(context) }
                )
            }
            if (!isBtEnabled) {
                MeshHardwareWarningBanner(
                    icon = Icons.Default.Bluetooth,
                    title = "Bluetooth is Turned OFF",
                    message = "Nearby Mesh network requires Bluetooth to discover and connect with local peers.",
                    actionText = "TURN ON",
                    onAction = { HardwareStateUtil.openBluetoothSettings(context) }
                )
            }
            if (!isLocEnabled) {
                MeshHardwareWarningBanner(
                    icon = Icons.Default.LocationOn,
                    title = "Location (GPS) is Turned OFF",
                    message = "Android requires Location turned ON to discover local BLE mesh devices.",
                    actionText = "TURN ON",
                    onAction = { HardwareStateUtil.openLocationSettings(context) }
                )
            }

            if (connectedPeers.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkCard)
                        .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "NEARBY MESH PEERS (${connectedPeers.size})",
                                color = NeonCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "Tap to chat",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(connectedPeers, key = { it.endpointId }) { peer ->
                            val rawTarget = peer.displayName.ifBlank { peer.userId ?: peer.endpointId }
                            val peerTarget = rawTarget.removePrefix("Peer ").removePrefix("Peer-").removePrefix("PEER: ").trim()
                            val formattedPeerName = viewModel.getFormattedDisplayName(peerTarget)
                            val myIdentity = currentUsername ?: currentUserId ?: "user"
                            val chatId = com.phantom.mesh.data.repository.ChatRepository.generateChatId(myIdentity, peerTarget)

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(NeonCyan.copy(alpha = 0.12f))
                                    .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                    .clickable {
                                        onChatClick(chatId, peerTarget)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(com.phantom.mesh.ui.theme.NeonGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formattedPeerName,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CHAT",
                                    color = NeonCyan,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            if (chats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isDuress) "Decoy Session Active. Database clean." else "No active conversations",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Connect with nearby mesh peers or start a cloud chat",
                            color = TextMuted.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(chats, key = { it.chatId }) { chat ->
                        val rawTitle = chat.chatTitle ?: chat.participants
                        val formattedTitle = viewModel.getFormattedDisplayName(rawTitle)
                        ChatItem(
                            chat = chat,
                            displayName = formattedTitle,
                            modifier = Modifier.animateItem(),
                            onClick = { onChatClick(chat.chatId, chat.participants) },
                            onLongClick = {
                                val cleanTarget = rawTitle.removePrefix("Peer ").removePrefix("Peer-").removePrefix("PEER: ").trim()
                                aliasInput = viewModel.getAlias(cleanTarget) ?: ""
                                chatForAliasEdit = chat
                            }
                        )
                    }
                }
            }
        }
    }

    if (showNewChatDialog) {
        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            title = { Text("New Anonymous Chat", color = NeonCyan) },
            text = {
                Column {
                    Text("Enter username or user ID to start conversation:", color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newChatPeerId,
                        onValueChange = { newChatPeerId = it },
                        label = { Text("Username / User ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (connectedPeers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Connected nearby users:", fontSize = 12.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(6.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(connectedPeers, key = { it.endpointId }) { peer ->
                                val target = peer.displayName.ifBlank { peer.userId ?: peer.endpointId }
                                    .removePrefix("Peer ")
                                    .removePrefix("Peer-")
                                    .removePrefix("PEER: ")
                                    .trim()
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(NeonCyan.copy(alpha = 0.15f))
                                        .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                        .clickable {
                                            newChatPeerId = target
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(target, color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newChatPeerId.isNotBlank()) {
                            val cleanPeer = newChatPeerId.trim()
                                .removePrefix("Peer ")
                                .removePrefix("Peer-")
                                .removePrefix("PEER: ")
                                .trim()
                                .lowercase()
                            val myIdentity = currentUsername ?: currentUserId ?: "user"
                            val generatedId = com.phantom.mesh.data.repository.ChatRepository.generateChatId(myIdentity, cleanPeer)
                            showNewChatDialog = false
                            onChatClick(generatedId, cleanPeer)
                        }
                    }
                ) {
                    Text("OPEN CHANNEL", color = NeonCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewChatDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = DarkCard
        )
    }

    // Custom Contact Naming Dialog from Chat List
    if (chatForAliasEdit != null) {
        val targetChat = chatForAliasEdit!!
        val rawUser = (targetChat.chatTitle ?: targetChat.participants)
            .removePrefix("Peer ")
            .removePrefix("Peer-")
            .removePrefix("PEER: ")
            .trim()
            .ifBlank { "User" }
        val currentAlias = viewModel.getAlias(rawUser)

        AlertDialog(
            onDismissRequest = { chatForAliasEdit = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Custom Contact Name", color = TextPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Set a customized name for @$rawUser. Format: Sayan Dolai ($rawUser).",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        label = { Text("Custom Name") },
                        placeholder = { Text("e.g. Sayan Dolai") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val previewText = remember(aliasInput, rawUser) {
                        val trimmed = aliasInput.trim()
                        if (trimmed.isNotBlank() && !trimmed.equals(rawUser, ignoreCase = true)) {
                            "$trimmed ($rawUser)"
                        } else {
                            rawUser
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant.copy(alpha = 0.6f))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text("LIVE DISPLAY PREVIEW", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = previewText,
                                fontSize = 14.sp,
                                color = NeonCyan,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!currentAlias.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                viewModel.clearAlias(rawUser)
                                chatForAliasEdit = null
                                Toast.makeText(context, "Custom name removed", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("RESET", color = DuressCrimson, fontSize = 12.sp)
                        }
                    }

                    TextButton(
                        onClick = {
                            val trimmed = aliasInput.trim()
                            if (trimmed.isNotBlank()) {
                                viewModel.setAlias(rawUser, trimmed)
                                Toast.makeText(context, "Contact name updated", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.clearAlias(rawUser)
                            }
                            chatForAliasEdit = null
                        }
                    ) {
                        Text("SAVE", color = NeonCyan, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { chatForAliasEdit = null }) {
                    Text("CANCEL", color = TextMuted, fontSize = 12.sp)
                }
            },
            containerColor = DarkCard
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatItem(
    chat: ChatEntity,
    displayName: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val timeFormatted = remember(chat.lastMessageTimestamp) {
        if (chat.lastMessageTimestamp == 0L) ""
        else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(chat.lastMessageTimestamp))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(DarkCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.lastMessagePreview ?: "No messages yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Transport indicator tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (chat.transportType == "mesh") NeonCyan.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = chat.transportType.uppercase(),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (chat.transportType == "mesh") NeonCyan else TextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun MeshHardwareWarningBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .border(1.dp, WarningAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(WarningAmber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = WarningAmber,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = WarningAmber
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            TextButton(
                onClick = onAction,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = NeonCyan
                )
            ) {
                Text(
                    text = actionText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
