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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import com.phantom.mesh.util.HardwareStateUtil
import com.phantom.mesh.ui.theme.WarningAmber
import com.phantom.mesh.ui.theme.NeonGreen
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
    onNavigateCallHistory: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val transport by viewModel.activeTransport.collectAsState()
    val chats by viewModel.chats.collectAsState()
    val archivedChats by viewModel.archivedChats.collectAsState()
    val isDndEnabled by viewModel.isDndEnabled.collectAsState()
    val isDuress by viewModel.isDuressSession.collectAsState()
    val currentUsername by viewModel.currentUsername.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val isBtOnly by viewModel.isBluetoothOnlyMode.collectAsState()
    val connectedPeers by viewModel.connectedPeers.collectAsState()
    val contactAliases by viewModel.contactAliases.collectAsState()
    val peerTypingStates by viewModel.peerTypingStates.collectAsState()

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
    var selectedChatForActions by remember { mutableStateOf<ChatEntity?>(null) }
    var chatToDelete by remember { mutableStateOf<ChatEntity?>(null) }
    var showArchivedChats by remember { mutableStateOf(false) }
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

                    // Call History Button (Feature 2)
                    IconButton(onClick = onNavigateCallHistory) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Call History",
                            tint = NeonCyan
                        )
                    }

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
            // Feature 4: Do Not Disturb quick status banner
            if (isDndEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DuressCrimson.copy(alpha = 0.12f))
                        .border(1.dp, DuressCrimson.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable {
                            viewModel.setDndEnabled(false)
                            Toast.makeText(context, "Do Not Disturb turned OFF", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = DuressCrimson,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Do Not Disturb Active",
                                color = DuressCrimson,
                                fontSize = 12.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "All message & call alerts silenced. Tap to turn OFF.",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                viewModel.setDndEnabled(false)
                                Toast.makeText(context, "Do Not Disturb turned OFF", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("TURN OFF", color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
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

            // Feature 5: Archived Chats Access Header
            if (archivedChats.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkCard)
                        .border(1.dp, NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clickable { showArchivedChats = !showArchivedChats }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Archived Conversations (${archivedChats.size})",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = if (showArchivedChats) "HIDE" else "VIEW",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            if (chats.isEmpty() && (!showArchivedChats || archivedChats.isEmpty())) {
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
                    // Show archived chats if toggled open
                    if (showArchivedChats && archivedChats.isNotEmpty()) {
                        item(key = "header_archived") {
                            Text(
                                text = "ARCHIVED CONVERSATIONS",
                                color = NeonCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(archivedChats, key = { "archived_" + it.chatId }) { chat ->
                            val resolvedTarget = chat.participants.split(",").map { it.trim() }.firstOrNull {
                                it.isNotBlank() && !it.equals(currentUsername, ignoreCase = true) && !it.equals(currentUserId, ignoreCase = true)
                            } ?: (chat.chatTitle ?: chat.participants)
                            val formattedTitle = viewModel.getFormattedDisplayName(resolvedTarget)
                            ChatItem(
                                chat = chat,
                                displayName = formattedTitle,
                                modifier = Modifier.animateItem(),
                                typingState = peerTypingStates[chat.chatId] ?: com.phantom.mesh.util.TypingStatusManager.TypingState.IDLE,
                                onClick = { onChatClick(chat.chatId, resolvedTarget) },
                                onLongClick = {
                                    selectedChatForActions = chat
                                }
                            )
                        }
                        item(key = "divider_archived") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.1f))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Feature 6: Active chats properly sequenced by most recent message
                    items(chats, key = { it.chatId }) { chat ->
                        val resolvedTarget = chat.participants.split(",").map { it.trim() }.firstOrNull {
                            it.isNotBlank() && !it.equals(currentUsername, ignoreCase = true) && !it.equals(currentUserId, ignoreCase = true)
                        } ?: (chat.chatTitle ?: chat.participants)
                        val formattedTitle = viewModel.getFormattedDisplayName(resolvedTarget)
                        ChatItem(
                            chat = chat,
                            displayName = formattedTitle,
                            modifier = Modifier.animateItem(),
                            typingState = peerTypingStates[chat.chatId] ?: com.phantom.mesh.util.TypingStatusManager.TypingState.IDLE,
                            onClick = { onChatClick(chat.chatId, resolvedTarget) },
                            onLongClick = {
                                selectedChatForActions = chat
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
        val resolvedTarget = targetChat.participants.split(",").map { it.trim() }.firstOrNull {
            it.isNotBlank() && !it.equals(currentUsername, ignoreCase = true) && !it.equals(currentUserId, ignoreCase = true)
        } ?: (targetChat.chatTitle ?: targetChat.participants)
        val rawUser = resolvedTarget
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

    // Chat Actions BottomSheet (Edit Alias, Mute/Unmute, Archive/Unarchive, Delete Chat)
    if (selectedChatForActions != null) {
        val targetChat = selectedChatForActions!!
        val resolvedTarget = targetChat.participants.split(",").map { it.trim() }.firstOrNull {
            it.isNotBlank() && !it.equals(currentUsername, ignoreCase = true) && !it.equals(currentUserId, ignoreCase = true)
        } ?: (targetChat.chatTitle ?: targetChat.participants)
        val rawUser = resolvedTarget
            .removePrefix("Peer ")
            .removePrefix("Peer-")
            .removePrefix("PEER: ")
            .trim()
            .ifBlank { "User" }
        val formattedTitle = viewModel.getFormattedDisplayName(rawUser)
        val isMuted = targetChat.isMuted
        val isArchived = targetChat.isArchived

        ModalBottomSheet(
            onDismissRequest = { selectedChatForActions = null },
            containerColor = DarkCard,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = formattedTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@$rawUser",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action 1: Edit Contact Name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val cleanTarget = rawUser
                            aliasInput = viewModel.getAlias(cleanTarget) ?: ""
                            chatForAliasEdit = targetChat
                            selectedChatForActions = null
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Badge, contentDescription = null, tint = NeonCyan)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(text = "Edit Contact Name", color = TextPrimary, fontSize = 14.sp)
                }

                // Action 2: Mute / Unmute
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            viewModel.toggleMuteChat(targetChat)
                            selectedChatForActions = null
                            Toast.makeText(context, if (isMuted) "Notifications unmuted" else "Notifications muted", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = null,
                        tint = NeonCyan
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(text = if (isMuted) "Unmute Notifications" else "Mute Notifications", color = TextPrimary, fontSize = 14.sp)
                }

                // Action 3: Archive / Unarchive
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            viewModel.toggleArchiveChat(targetChat)
                            selectedChatForActions = null
                            Toast.makeText(context, if (isArchived) "Chat unarchived" else "Chat moved to Archive", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = null,
                        tint = NeonCyan
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(text = if (isArchived) "Unarchive Conversation" else "Archive Conversation", color = TextPrimary, fontSize = 14.sp)
                }

                // Action 4: Delete Chat
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            chatToDelete = targetChat
                            selectedChatForActions = null
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = DuressCrimson)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(text = "Delete Chat", color = DuressCrimson, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Delete Chat Confirmation Dialog
    if (chatToDelete != null) {
        val target = chatToDelete!!
        val resolvedTarget = target.participants.split(",").map { it.trim() }.firstOrNull {
            it.isNotBlank() && !it.equals(currentUsername, ignoreCase = true) && !it.equals(currentUserId, ignoreCase = true)
        } ?: (target.chatTitle ?: target.participants)
        val formattedTitle = viewModel.getFormattedDisplayName(resolvedTarget)

        AlertDialog(
            onDismissRequest = { chatToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = DuressCrimson)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Conversation?", color = TextPrimary)
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete this chat with $formattedTitle and all its messages?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChat(target.chatId)
                        chatToDelete = null
                        Toast.makeText(context, "Conversation deleted", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("DELETE", color = DuressCrimson, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { chatToDelete = null }) {
                    Text("CANCEL", color = TextMuted)
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
    typingState: com.phantom.mesh.util.TypingStatusManager.TypingState = com.phantom.mesh.util.TypingStatusManager.TypingState.IDLE,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (chat.isMuted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Muted",
                            tint = TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chat.unreadCount > 0) com.phantom.mesh.ui.theme.NeonGreen else TextMuted,
                    fontWeight = if (chat.unreadCount > 0) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (typingState == com.phantom.mesh.util.TypingStatusManager.TypingState.TYPING) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "typing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonCyan,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        com.phantom.mesh.ui.chat.AnimatedTypingDots(color = NeonCyan)
                    }
                } else if (typingState == com.phantom.mesh.util.TypingStatusManager.TypingState.RECORDING) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "recording audio...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFF9800),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                } else if (!chat.draftMessage.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Draft: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF00E676),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = chat.draftMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = chat.lastMessagePreview ?: "No messages yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (chat.unreadCount > 0) TextPrimary else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

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

                // Unread messages count badge
                if (chat.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            .clip(CircleShape)
                            .background(com.phantom.mesh.ui.theme.NeonGreen)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                            color = com.phantom.mesh.ui.theme.DarkBackground,
                            fontSize = 11.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
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
