package com.phantom.mesh.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.filled.Phone
import com.phantom.mesh.ui.calling.CallOverlayScreen
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.phantom.mesh.data.local.entity.MessageEntity
import com.phantom.mesh.ui.components.HopReceiptIcon
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
import com.phantom.mesh.util.AudioRecorderHelper
import com.phantom.mesh.util.HardwareStateUtil
import com.phantom.mesh.util.VoicePlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit
) {
    val transport by viewModel.activeTransport.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val currentUsername by viewModel.currentUsername.collectAsState()
    val chatWallpaperPath by viewModel.chatWallpaperPath.collectAsState()
    val contactAliases by viewModel.contactAliases.collectAsState()
    val currentChat by viewModel.currentChat.collectAsState()
    val blockedUsers by viewModel.blockedUsers.collectAsState()

    var inputMessage by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedMessageForDelete by remember { mutableStateOf<MessageEntity?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showAudioPickerSheet by remember { mutableStateOf(false) }
    var showCustomNameDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showBlockContactDialog by remember { mutableStateOf(false) }
    var aliasInput by remember { mutableStateOf("") }

    val cleanTargetName = remember(viewModel.targetId) {
        viewModel.targetId
            .removePrefix("Peer ")
            .removePrefix("Peer-")
            .removePrefix("PEER: ")
            .trim()
            .ifBlank { "Encrypted Contact" }
    }

    val isContactBlocked = remember(blockedUsers, viewModel.targetId) {
        viewModel.isContactBlocked()
    }

    val formattedTargetName = remember(viewModel.targetId, contactAliases) {
        viewModel.getFormattedTargetName()
    }

    val readReceiptsEnabled by viewModel.readReceiptsEnabled.collectAsState()
    val unreadMessageIds by viewModel.unreadMessageIds.collectAsState()
    val senderBubbleColorHex by viewModel.senderBubbleColor.collectAsState()
    val receiverBubbleColorHex by viewModel.receiverBubbleColor.collectAsState()
    val chatFontStyle by viewModel.chatFontStyle.collectAsState()
    val chatFontSizeScale by viewModel.chatFontSizeScale.collectAsState()

    val senderBubbleColor = remember(senderBubbleColorHex) {
        try {
            Color(android.graphics.Color.parseColor(senderBubbleColorHex))
        } catch (_: Exception) {
            DarkCard
        }
    }

    val receiverBubbleColor = remember(receiverBubbleColorHex) {
        try {
            Color(android.graphics.Color.parseColor(receiverBubbleColorHex))
        } catch (_: Exception) {
            DarkSurfaceVariant
        }
    }

    val chatFontFamily = remember(chatFontStyle) {
        when (chatFontStyle.uppercase()) {
            "MONOSPACE" -> androidx.compose.ui.text.font.FontFamily.Monospace
            "SANS_SERIF" -> androidx.compose.ui.text.font.FontFamily.SansSerif
            "SERIF" -> androidx.compose.ui.text.font.FontFamily.Serif
            else -> androidx.compose.ui.text.font.FontFamily.Default // "DEVICE" - adaptive to device font
        }
    }

    val chatFontSize = remember(chatFontSizeScale) {
        when (chatFontSizeScale.uppercase()) {
            "SMALL" -> 13.sp
            "LARGE" -> 17.sp
            "EXTRA_LARGE" -> 19.sp
            else -> 15.sp // "MEDIUM"
        }
    }

    val voicePlayerManager = remember { VoicePlayerManager() }
    DisposableEffect(Unit) {
        onDispose {
            voicePlayerManager.stop()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.markChatAsRead()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Suppress notifications for this chat while active on screen
    DisposableEffect(viewModel.chatId) {
        com.phantom.mesh.util.NotificationHelper.currentActiveChatId = viewModel.chatId
        onDispose {
            if (com.phantom.mesh.util.NotificationHelper.currentActiveChatId == viewModel.chatId) {
                com.phantom.mesh.util.NotificationHelper.currentActiveChatId = null
            }
        }
    }

    // Photo Picker with device albums
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.sendUriAttachment(context, it) }
    }

    // Video Picker with device albums
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.sendUriAttachment(context, it) }
    }

    // Documents picker (Android system files)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.sendUriAttachment(context, it) }
    }

    // Audio permission check for WhatsApp-like audio picker
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showAudioPickerSheet = true
        } else {
            Toast.makeText(context, "Storage permission is required to view audio tracks", Toast.LENGTH_SHORT).show()
        }
    }

    // Contacts picker
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.sendContact(context, it) }
    }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contactPickerLauncher.launch(null)
        } else {
            Toast.makeText(context, "Contact permission is required to share contacts", Toast.LENGTH_SHORT).show()
        }
    }

    // Wallpaper theme picker launcher
    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.setWallpaperFromUri(context, it) }
    }

    // Voice call permission launcher (Microphone on-demand)
    val voiceCallPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceCall()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice calls", Toast.LENGTH_SHORT).show()
        }
    }

    // Video call permission launcher (Microphone & Camera on-demand)
    val videoCallPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val micOk = perms[Manifest.permission.RECORD_AUDIO] == true
        val camOk = perms[Manifest.permission.CAMERA] == true
        if (micOk && camOk) {
            viewModel.startVideoCall()
        } else {
            Toast.makeText(context, "Camera & Microphone permissions required for video calls", Toast.LENGTH_SHORT).show()
        }
    }

    val oldestUnreadIndex = remember(messages, unreadMessageIds) {
        if (unreadMessageIds.isEmpty()) -1
        else messages.indexOfLast { unreadMessageIds.contains(it.messageId) }
    }

    // Anchor chat view to last read message / unread divider on entry.
    // Unread messages appear below it, requiring user to scroll down to view them.
    var hasScrolledToInitialPosition by remember { mutableStateOf(false) }
    LaunchedEffect(messages.isNotEmpty(), oldestUnreadIndex) {
        if (messages.isNotEmpty() && !hasScrolledToInitialPosition) {
            if (oldestUnreadIndex > 0) {
                listState.scrollToItem(oldestUnreadIndex)
                hasScrolledToInitialPosition = true
            } else if (oldestUnreadIndex == 0) {
                listState.scrollToItem(0)
                hasScrolledToInitialPosition = true
            } else if (unreadMessageIds.isEmpty()) {
                listState.scrollToItem(0)
                hasScrolledToInitialPosition = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                aliasInput = viewModel.getTargetAlias() ?: ""
                                showCustomNameDialog = true
                            }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formattedTargetName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Contact Name",
                                tint = TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = "E2E Hybrid Encrypted Channel",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
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
                    // Voice Call Button
                    IconButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                viewModel.startVoiceCall()
                            } else {
                                voiceCallPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Voice Call",
                            tint = NeonCyan
                        )
                    }

                    // Video Call Button
                    IconButton(
                        onClick = {
                            val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasAudio && hasCam) {
                                viewModel.startVideoCall()
                            } else {
                                videoCallPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = "Video Call",
                            tint = NeonCyan
                        )
                    }

                    // Dynamic Network Indicator in Top App Bar
                    NetworkIndicator(transport = transport, showLabel = true)

                    // Menu for Theme / Wallpaper / Contact options
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = NeonCyan
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(DarkCard)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Contact Name", color = TextPrimary) },
                                onClick = {
                                    showMenu = false
                                    aliasInput = viewModel.getTargetAlias() ?: ""
                                    showCustomNameDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Badge,
                                        contentDescription = null,
                                        tint = NeonCyan
                                    )
                                }
                            )

                            // Mute / Unmute Notifications
                            val isMuted = currentChat?.isMuted == true
                            DropdownMenuItem(
                                text = { Text(if (isMuted) "Unmute Notifications" else "Mute Notifications", color = TextPrimary) },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleMuteChat()
                                    Toast.makeText(context, if (isMuted) "Notifications unmuted" else "Notifications muted", Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                        contentDescription = null,
                                        tint = NeonCyan
                                    )
                                }
                            )

                            // Archive / Unarchive Chat
                            val isArchived = currentChat?.isArchived == true
                            DropdownMenuItem(
                                text = { Text(if (isArchived) "Unarchive Chat" else "Archive Chat", color = TextPrimary) },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleArchiveChat()
                                    Toast.makeText(context, if (isArchived) "Chat unarchived" else "Chat archived", Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                        contentDescription = null,
                                        tint = NeonCyan
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Set Chat Wallpaper", color = TextPrimary) },
                                onClick = {
                                    showMenu = false
                                    wallpaperPickerLauncher.launch("image/*")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Wallpaper,
                                        contentDescription = null,
                                        tint = NeonCyan
                                    )
                                }
                            )

                            if (!chatWallpaperPath.isNullOrBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Reset Wallpaper", color = DuressCrimson) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.removeWallpaper(context)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = DuressCrimson
                                        )
                                    }
                                )
                            }

                            // Clear Chat Option
                            DropdownMenuItem(
                                text = { Text("Clear Chat", color = DuressCrimson) },
                                onClick = {
                                    showMenu = false
                                    showClearChatDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        tint = DuressCrimson
                                    )
                                }
                            )

                            // Block / Unblock Contact
                            DropdownMenuItem(
                                text = { Text(if (isContactBlocked) "Unblock Contact" else "Block Contact", color = if (isContactBlocked) NeonCyan else DuressCrimson) },
                                onClick = {
                                    showMenu = false
                                    if (isContactBlocked) {
                                        viewModel.unblockContact()
                                        Toast.makeText(context, "@$cleanTargetName unblocked", Toast.LENGTH_SHORT).show()
                                    } else {
                                        showBlockContactDialog = true
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = null,
                                        tint = if (isContactBlocked) NeonCyan else DuressCrimson
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            // Render custom chat wallpaper if set by the user
            if (!chatWallpaperPath.isNullOrBlank()) {
                val wallpaperFile = remember(chatWallpaperPath) { File(chatWallpaperPath!!) }
                if (wallpaperFile.exists()) {
                    AsyncImage(
                        model = wallpaperFile,
                        contentDescription = "Chat Wallpaper",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // 55% dark scrim overlay to guarantee high-contrast legibility of neon chat bubbles
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                if (isContactBlocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DuressCrimson.copy(alpha = 0.15f))
                            .border(1.dp, DuressCrimson.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.unblockContact()
                                Toast.makeText(context, "@$cleanTargetName unblocked", Toast.LENGTH_SHORT).show()
                            }
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = DuressCrimson,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "You have blocked this contact. Tap here to unblock.",
                                color = DuressCrimson,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "UNBLOCK",
                                color = NeonCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    reverseLayout = true // Chat style: latest messages at the bottom
                ) {
                    itemsIndexed(messages, key = { _, message -> message.messageId }) { index, message ->
                        val isMe = message.senderId == currentUserId || 
                                   (currentUsername != null && message.senderId.equals(currentUsername, ignoreCase = true))
                        val isUnread = unreadMessageIds.contains(message.messageId)

                        // In reverseLayout = true, render UnreadMessagesDivider at the top of the oldest unread item
                        // placing it directly between the last read message (above) and the first unread message (below)
                        if (index == oldestUnreadIndex && unreadMessageIds.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            UnreadMessagesDivider(count = unreadMessageIds.size)
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Box(modifier = Modifier.animateItem()) {
                            MessageBubble(
                                message = message,
                                isMe = isMe,
                                isUnread = isUnread,
                                voicePlayerManager = voicePlayerManager,
                                senderBubbleColor = senderBubbleColor,
                                receiverBubbleColor = receiverBubbleColor,
                                fontFamily = chatFontFamily,
                                fontSize = chatFontSize,
                                readReceiptsEnabled = readReceiptsEnabled,
                                onReply = { msg -> viewModel.setReplyingTo(msg) },
                                onLongClick = {
                                    if (message.content != "🚫 This message was deleted" && message.dataType != "deleted") {
                                        selectedMessageForDelete = message
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // Swipe to Reply Active Preview Banner
                val activeReply by viewModel.activeReply.collectAsState()
                AnimatedVisibility(
                    visible = activeReply != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    activeReply?.let { reply ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkCard)
                                .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.5.dp)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(NeonCyan)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Replying to ${reply.senderName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = reply.contentSnippet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(
                                onClick = { viewModel.cancelReply() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Reply",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                ChatInputBar(
                    text = inputMessage,
                    onTextChange = { inputMessage = it },
                    onAttachClick = {
                        showAttachmentSheet = true
                    },
                    onSend = {
                        if (inputMessage.isNotBlank()) {
                            viewModel.sendMessage(inputMessage)
                            inputMessage = ""
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        }
                    },
                    onSendVoiceMessage = { file, durationMs ->
                        viewModel.sendVoiceMessage(file, durationMs)
                    }
                )
            }
        }
    }

    // Attachment Options Bottom Sheet (Photos, Videos, Documents, Contacts)
    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = DarkCard,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = "Share Attachment",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp
                )
                Text(
                    text = "Select media category or contact to send",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Photos (Visual Media Picker with Albums)
                    AttachmentOptionCard(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Photos",
                        subLabel = "Albums",
                        iconTint = Color(0xFF4E95FF),
                        backgroundTint = Color(0xFF4E95FF).copy(alpha = 0.15f),
                        borderColor = Color(0xFF4E95FF).copy(alpha = 0.4f),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAttachmentSheet = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // 2. Videos (Visual Media Picker with Albums)
                    AttachmentOptionCard(
                        icon = Icons.Default.VideoLibrary,
                        label = "Videos",
                        subLabel = "Albums",
                        iconTint = Color(0xFFFF5252),
                        backgroundTint = Color(0xFFFF5252).copy(alpha = 0.15f),
                        borderColor = Color(0xFFFF5252).copy(alpha = 0.4f),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAttachmentSheet = false
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 3. Music (Audio Tracks & Songs)
                    AttachmentOptionCard(
                        icon = Icons.Default.MusicNote,
                        label = "Music",
                        subLabel = "Audio Tracks",
                        iconTint = Color(0xFFFF9800),
                        backgroundTint = Color(0xFFFF9800).copy(alpha = 0.15f),
                        borderColor = Color(0xFFFF9800).copy(alpha = 0.4f),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAttachmentSheet = false
                            if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
                                showAudioPickerSheet = true
                            } else {
                                audioPermissionLauncher.launch(audioPermission)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // 4. Documents (Android Files)
                    AttachmentOptionCard(
                        icon = Icons.Default.Description,
                        label = "Documents",
                        subLabel = "All Files",
                        iconTint = Color(0xFF9C27B0),
                        backgroundTint = Color(0xFF9C27B0).copy(alpha = 0.15f),
                        borderColor = Color(0xFF9C27B0).copy(alpha = 0.4f),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAttachmentSheet = false
                            documentPickerLauncher.launch("*/*")
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Contacts (Phone Contacts as Text Card)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, Color(0xFF00E676).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .clickable {
                            showAttachmentSheet = false
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                contactPickerLauncher.launch(null)
                            } else {
                                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF00E676).copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContactPhone,
                            contentDescription = "Contacts",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Contacts",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Phone Book • Shared as contact card",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }

    // WhatsApp-like Audio Picker Screen
    if (showAudioPickerSheet) {
        AudioPickerSheet(
            onAudioSelected = { uri ->
                viewModel.sendUriAttachment(context, uri)
            },
            onDismiss = {
                showAudioPickerSheet = false
            }
        )
    }

    // Custom Contact Naming Dialog
    if (showCustomNameDialog) {
        val currentAlias = viewModel.getTargetAlias()
        AlertDialog(
            onDismissRequest = { showCustomNameDialog = false },
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
                        text = "Set a customized name for @$cleanTargetName. Both names will appear together, e.g. Sayan Dolai ($cleanTargetName).",
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

                    // Live Preview Box
                    val previewText = remember(aliasInput, cleanTargetName) {
                        val trimmed = aliasInput.trim()
                        if (trimmed.isNotBlank() && !trimmed.equals(cleanTargetName, ignoreCase = true)) {
                            "$trimmed ($cleanTargetName)"
                        } else {
                            cleanTargetName
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
                                viewModel.clearTargetAlias()
                                showCustomNameDialog = false
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
                                viewModel.setTargetAlias(trimmed)
                                Toast.makeText(context, "Contact name updated", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.clearTargetAlias()
                            }
                            showCustomNameDialog = false
                        }
                    ) {
                        Text("SAVE", color = NeonCyan, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomNameDialog = false }) {
                    Text("CANCEL", color = TextMuted, fontSize = 12.sp)
                }
            },
            containerColor = DarkCard
        )
    }

    // Delete message dialog (WhatsApp style)
    if (selectedMessageForDelete != null) {
        val targetMsg = selectedMessageForDelete!!
        val isSender = targetMsg.senderId == currentUserId ||
                (currentUsername != null && targetMsg.senderId.equals(currentUsername, ignoreCase = true))

        AlertDialog(
            onDismissRequest = { selectedMessageForDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Message?", color = TextPrimary)
                }
            },
            text = {
                Text(
                    text = if (isSender)
                        "You can delete this message for everyone or delete it only from this device."
                    else
                        "Delete this message from your device?",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (isSender) {
                        TextButton(
                            onClick = {
                                viewModel.deleteMessageForEveryone(targetMsg)
                                selectedMessageForDelete = null
                            }
                        ) {
                            Text(
                                text = "DELETE FOR EVERYONE",
                                color = DuressCrimson,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            viewModel.deleteMessageForMe(targetMsg)
                            selectedMessageForDelete = null
                        }
                    ) {
                        Text(
                            text = "DELETE FOR ME",
                            color = NeonCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedMessageForDelete = null }) {
                    Text("CANCEL", color = TextMuted)
                }
            },
            containerColor = DarkCard
        )
    }

    // Clear Chat Confirmation Dialog
    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = DuressCrimson,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Chat?", color = TextPrimary)
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all messages in this conversation with @$cleanTargetName? This cannot be undone.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChat()
                        showClearChatDialog = false
                        Toast.makeText(context, "Chat cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("CLEAR CHAT", color = DuressCrimson, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text("CANCEL", color = TextMuted, fontSize = 12.sp)
                }
            },
            containerColor = DarkCard
        )
    }

    // Block Contact Confirmation Dialog
    if (showBlockContactDialog) {
        AlertDialog(
            onDismissRequest = { showBlockContactDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = DuressCrimson,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Block Contact?", color = TextPrimary)
                }
            },
            text = {
                Text(
                    text = "Block @$cleanTargetName? They will not be able to send you messages or initiate calls with you.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.blockContact()
                        showBlockContactDialog = false
                        Toast.makeText(context, "@$cleanTargetName blocked", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("BLOCK", color = DuressCrimson, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockContactDialog = false }) {
                    Text("CANCEL", color = TextMuted, fontSize = 12.sp)
                }
            },
            containerColor = DarkCard
        )
    }

    // Calling Restricted Alert Dialog (when on Bluetooth Mesh due to ~2 KB/s data send rate)
    val callBlockedNotice by viewModel.callBlockedNotice.collectAsState()
    if (callBlockedNotice != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearCallBlockedNotice() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = DuressCrimson,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Video Calling Restricted on Bluetooth",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp
                )
            },
            text = {
                Text(
                    text = callBlockedNotice ?: "",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCallBlockedNotice() }) {
                    Text("UNDERSTOOD", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkCard
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    isUnread: Boolean = false,
    voicePlayerManager: VoicePlayerManager,
    senderBubbleColor: Color = DarkCard,
    receiverBubbleColor: Color = DarkSurfaceVariant,
    fontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    readReceiptsEnabled: Boolean = true,
    onReply: (MessageEntity) -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val timeFormatted = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    val isDeleted = message.content == "🚫 This message was deleted" || message.dataType == "deleted"

    // Horizontal swipe-to-reply state with spring resistance and haptic feedback
    var dragOffsetX by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "swipeToReplyOffset"
    )
    val haptic = LocalHapticFeedback.current
    var hapticTriggered by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.messageId) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragOffsetX >= 60f) {
                            onReply(message)
                        }
                        dragOffsetX = 0f
                        hapticTriggered = false
                    },
                    onDragCancel = {
                        dragOffsetX = 0f
                        hapticTriggered = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        // Only swipe right
                        val next = (dragOffsetX + dragAmount).coerceIn(0f, 110f)
                        dragOffsetX = next
                        if (next >= 60f && !hapticTriggered) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            hapticTriggered = true
                        }
                    }
                )
            },
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        // Behind-the-bubble animated reply icon
        if (animatedOffset > 8f) {
            val progress = (animatedOffset / 60f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(30.dp)
                    .scale(progress)
                    .clip(CircleShape)
                    .background(if (progress >= 1f) NeonCyan else DarkCard)
                    .border(1.dp, NeonCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Reply",
                    tint = if (progress >= 1f) DarkBackground else NeonCyan,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier
                .offset { IntOffset(animatedOffset.toInt(), 0) }
                .widthIn(max = 280.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMe) 16.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 16.dp
                        )
                    )
                    .background(if (isMe) senderBubbleColor else receiverBubbleColor)
                    .border(
                        if (isUnread && !isMe) 1.5.dp else 1.dp,
                        if (isDeleted) Color.White.copy(alpha = 0.05f)
                        else if (isUnread && !isMe) NeonCyan.copy(alpha = 0.8f)
                        else if (isMe) NeonCyan.copy(alpha = 0.3f)
                        else Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMe) 16.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 16.dp
                        )
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongClick
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    // Quoted message preview if this message is a reply
                    if (!isDeleted && !message.replyToContent.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkBackground.copy(alpha = 0.45f))
                                .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(NeonCyan)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = message.replyToSender ?: "Replied message",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = message.replyToContent,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                    if (isDeleted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "This message was deleted",
                                fontStyle = FontStyle.Italic,
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        val isVoice = message.dataType == "voice"

                        val isAudio = !isVoice && (message.dataType == "audio" ||
                                message.mimeType?.startsWith("audio") == true ||
                                message.mediaCloudUrl?.startsWith("data:audio") == true ||
                                message.content?.endsWith(".mp3", ignoreCase = true) == true ||
                                message.content?.endsWith(".m4a", ignoreCase = true) == true ||
                                message.content?.endsWith(".wav", ignoreCase = true) == true ||
                                message.content?.endsWith(".aac", ignoreCase = true) == true ||
                                message.content?.endsWith(".ogg", ignoreCase = true) == true ||
                                message.content?.endsWith(".flac", ignoreCase = true) == true ||
                                message.mediaLocalUri?.endsWith(".mp3", ignoreCase = true) == true ||
                                message.mediaLocalUri?.endsWith(".m4a", ignoreCase = true) == true ||
                                message.mediaLocalUri?.endsWith(".wav", ignoreCase = true) == true)

                        val isImage = !isVoice && !isAudio && (message.dataType == "image" ||
                                message.mimeType?.startsWith("image") == true ||
                                message.mediaCloudUrl?.startsWith("data:image") == true ||
                                message.mediaLocalUri?.endsWith(".jpg", ignoreCase = true) == true ||
                                message.mediaLocalUri?.endsWith(".png", ignoreCase = true) == true ||
                                message.content?.endsWith(".jpg", ignoreCase = true) == true ||
                                message.content?.endsWith(".png", ignoreCase = true) == true ||
                                message.content?.startsWith("attachment_", ignoreCase = true) == true)

                        val isVideo = !isVoice && !isAudio && !isImage && (message.dataType == "video" || message.mimeType?.startsWith("video") == true)

                        if (isVoice) {
                            VoiceMessageBubbleContent(
                                message = message,
                                voicePlayerManager = voicePlayerManager,
                                isMe = isMe,
                                onLongClick = onLongClick
                            )
                        } else if (isAudio) {
                            AudioTrackBubbleContent(
                                message = message,
                                voicePlayerManager = voicePlayerManager,
                                isMe = isMe,
                                onLongClick = onLongClick
                            )
                        } else if (isImage) {
                            ImageMessageContent(message = message, onLongClick = onLongClick)
                        } else if (isVideo) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = onLongClick
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = "Video",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = message.content ?: "Video Attachment",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Text caption (hide placeholder tags, voice captions, and auto-generated filenames)
                        val isAttachmentPlaceholder = isVoice || isAudio ||
                                message.content?.startsWith("attachment_", ignoreCase = true) == true ||
                                message.content?.startsWith("[Attachment:", ignoreCase = true) == true ||
                                message.content?.equals("[Image Attachment]", ignoreCase = true) == true ||
                                message.content?.endsWith(".jpg", ignoreCase = true) == true ||
                                message.content?.endsWith(".png", ignoreCase = true) == true

                        val isContactCard = message.content?.startsWith("👤 Contact Card:") == true
                        val showContent = !message.content.isNullOrBlank() && (!isImage && !isVideo || !isAttachmentPlaceholder) && !isVoice && !isAudio
                        if (showContent) {
                            if (isContactCard) {
                                ContactCardContent(content = message.content!!, isMe = isMe)
                            } else {
                                Text(
                                    text = message.content ?: "",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = fontFamily,
                                        fontSize = fontSize
                                    ),
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    } // End of !isDeleted check

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        // Transport tag (cloud vs mesh hop indicator)
                        Text(
                            text = if (message.transportUsed == "mesh") "MESH [HOP ${message.hopCount}]" else "CLOUD",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (message.transportUsed == "mesh") NeonCyan else TextMuted
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        if (isUnread && !isMe) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeonCyan.copy(alpha = 0.2f))
                                    .border(0.5.dp, NeonCyan, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "NEW",
                                    color = NeonCyan,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        // Timestamp
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )

                        if (isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            // HOP-STATE READ RECEIPTS (1 gray, 1 blue, 2 blue)
                            HopReceiptIcon(
                                deliveryStatus = message.deliveryStatus,
                                readReceiptsEnabled = readReceiptsEnabled
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnreadMessagesDivider(count: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant.copy(alpha = 0.95f))
                .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(NeonCyan)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$count UNREAD MESSAGE${if (count > 1) "S" else ""}",
                color = NeonCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VoiceMessageBubbleContent(
    message: MessageEntity,
    voicePlayerManager: VoicePlayerManager,
    isMe: Boolean,
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val activePlayingId by voicePlayerManager.activePlayingMessageId.collectAsState()
    val isPlaying by voicePlayerManager.isPlaying.collectAsState()
    val progress by voicePlayerManager.playbackProgress.collectAsState()
    val currentPositionMs by voicePlayerManager.currentPositionMs.collectAsState()
    val totalDurationMs by voicePlayerManager.totalDurationMs.collectAsState()

    val isThisMessagePlaying = activePlayingId == message.messageId && isPlaying

    val displayProgress = if (activePlayingId == message.messageId) progress else 0f
    val displayDuration = if (activePlayingId == message.messageId && totalDurationMs > 0) {
        val currSec = currentPositionMs / 1000
        val totSec = totalDurationMs / 1000
        String.format("%d:%02d / %d:%02d", currSec / 60, currSec % 60, totSec / 60, totSec % 60)
    } else {
        message.content?.substringAfter("Voice message (")?.substringBefore(")") ?: "Voice note"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    voicePlayerManager.togglePlay(context, message.messageId, message.mediaLocalUri, message.mediaCloudUrl)
                },
                onLongClick = onLongClick
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play / Pause glowing button with ripple
        IconButton(
            onClick = {
                voicePlayerManager.togglePlay(context, message.messageId, message.mediaLocalUri, message.mediaCloudUrl)
            },
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isThisMessagePlaying) NeonCyan else Color.White.copy(alpha = 0.12f))
        ) {
            Icon(
                imageVector = if (isThisMessagePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isThisMessagePlaying) "Pause" else "Play",
                tint = if (isThisMessagePlaying) DarkBackground else NeonCyan,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Dancing animated audio equalizer waveform
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val barCount = 20
                for (i in 0 until barCount) {
                    val baseHeight = remember(i) { ((i * 7 + 13) % 18 + 6).dp }
                    val isPastProgress = (i.toFloat() / barCount.toFloat()) <= displayProgress

                    // Animate equalizer height when playing
                    val infiniteTransition = rememberInfiniteTransition(label = "voice_wave_$i")
                    val animatedFactor by if (isThisMessagePlaying) {
                        infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 200 + (i * 37) % 300, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "wave_factor_$i"
                        )
                    } else {
                        remember { mutableStateOf(1f) }
                    }

                    val finalHeight = if (isThisMessagePlaying) baseHeight * animatedFactor else baseHeight

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(finalHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isPastProgress) NeonCyan
                                else Color.White.copy(alpha = 0.25f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayDuration,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isThisMessagePlaying) NeonCyan else TextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioTrackBubbleContent(
    message: MessageEntity,
    voicePlayerManager: VoicePlayerManager,
    isMe: Boolean,
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val activePlayingId by voicePlayerManager.activePlayingMessageId.collectAsState()
    val isPlaying by voicePlayerManager.isPlaying.collectAsState()
    val progress by voicePlayerManager.playbackProgress.collectAsState()
    val currentPositionMs by voicePlayerManager.currentPositionMs.collectAsState()
    val totalDurationMs by voicePlayerManager.totalDurationMs.collectAsState()

    val isThisTrackPlaying = activePlayingId == message.messageId && isPlaying
    val displayProgress = if (activePlayingId == message.messageId) progress else 0f
    val displayDuration = if (activePlayingId == message.messageId && totalDurationMs > 0) {
        val currSec = currentPositionMs / 1000
        val totSec = totalDurationMs / 1000
        String.format("%d:%02d / %d:%02d", currSec / 60, currSec % 60, totSec / 60, totSec % 60)
    } else {
        "Audio Track"
    }

    val trackTitle = remember(message.content) {
        val raw = message.content?.trim()
        if (raw.isNullOrBlank() || raw.startsWith("attachment_") || raw.startsWith("[Attachment:")) {
            "Audio Track"
        } else {
            raw
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {
                    voicePlayerManager.togglePlay(context, message.messageId, message.mediaLocalUri, message.mediaCloudUrl)
                },
                onLongClick = onLongClick
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play / Pause circular action button
            IconButton(
                onClick = {
                    voicePlayerManager.togglePlay(context, message.messageId, message.mediaLocalUri, message.mediaCloudUrl)
                },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isThisTrackPlaying) Color(0xFFFF9800) else Color(0xFFFF9800).copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = if (isThisTrackPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isThisTrackPlaying) "Pause" else "Play",
                    tint = if (isThisTrackPlaying) DarkBackground else Color(0xFFFF9800),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = trackTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(displayProgress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFFF9800))
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "MUSIC",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF9800),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onAttachClick: () -> Unit,
    onSend: () -> Unit,
    onSendVoiceMessage: (File, Long) -> Unit
) {
    val context = LocalContext.current
    val recorderHelper = remember { AudioRecorderHelper() }
    val isRecording by recorderHelper.isRecording.collectAsState()
    val recordingDurationMs by recorderHelper.recordingDurationMs.collectAsState()
    val currentAmplitude by recorderHelper.currentAmplitude.collectAsState()

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val started = recorderHelper.startRecording(context)
            if (!started) {
                Toast.makeText(context, "Could not start audio recording", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission required for voice messages", Toast.LENGTH_SHORT).show()
        }
    }

    val infinitePulse = rememberInfiniteTransition(label = "mic_pulse")
    val pulseAlpha by infinitePulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_pulse_alpha"
    )

    AnimatedContent(
        targetState = isRecording,
        transitionSpec = {
            fadeIn(tween(250)) + scaleIn(tween(250)) togetherWith fadeOut(tween(200)) + scaleOut(tween(200))
        },
        label = "input_mode_transition"
    ) { recording ->
        if (recording) {
            // Live Voice Recording Bar with Waveform & Pulsing Timer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsing red recording dot
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(DuressCrimson.copy(alpha = pulseAlpha))
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Timer
                val seconds = (recordingDurationMs / 1000)
                val timeString = String.format("%d:%02d", seconds / 60, seconds % 60)
                Text(
                    text = timeString,
                    color = DuressCrimson,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Real-time bouncing amplitude equalizer
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val bars = 16
                    for (i in 0 until bars) {
                        val pseudoSeed = ((i * 7) % 11) / 10f
                        val heightFraction = ((currentAmplitude * (1f + pseudoSeed) * 0.7f) + 0.15f).coerceIn(0.12f, 1f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height((heightFraction * 26).dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(NeonCyan.copy(alpha = 0.6f + (heightFraction * 0.4f)))
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Cancel button (Trash)
                IconButton(
                    onClick = { recorderHelper.cancelRecording() },
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Recording",
                        tint = DuressCrimson
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Send voice button
                IconButton(
                    onClick = {
                        val result = recorderHelper.stopRecording()
                        if (result != null) {
                            onSendVoiceMessage(result.first, result.second)
                        } else {
                            Toast.makeText(context, "Voice message was too short", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(NeonCyan, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Voice Message",
                        tint = DarkBackground
                    )
                }
            }
        } else {
            // Standard text + media + mic input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAttachClick) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach File",
                        tint = NeonCyan
                    )
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("Encrypted message...", color = TextMuted) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Smooth morph between Microphone and Send icon
                AnimatedContent(
                    targetState = text.isNotBlank(),
                    transitionSpec = {
                        fadeIn(tween(180)) + scaleIn(tween(180)) togetherWith fadeOut(tween(150)) + scaleOut(tween(150))
                    },
                    label = "send_mic_morph"
                ) { hasText ->
                    if (hasText) {
                        IconButton(
                            onClick = onSend,
                            modifier = Modifier
                                .size(44.dp)
                                .background(NeonCyan, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = DarkBackground
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val hasAudioPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasAudioPermission) {
                                    val started = recorderHelper.startRecording(context)
                                    if (!started) {
                                        Toast.makeText(context, "Could not start audio recording", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(NeonCyan.copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, NeonCyan.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Record Voice Note",
                                tint = NeonCyan
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageMessageContent(
    message: MessageEntity,
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var bitmap by remember(message.messageId, message.mediaLocalUri, message.mediaCloudUrl) {
        mutableStateOf<Bitmap?>(null)
    }
    var isResolving by remember { mutableStateOf(true) }
    var showFullscreenDialog by remember { mutableStateOf(false) }

    LaunchedEffect(message.messageId, message.mediaLocalUri, message.mediaCloudUrl) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Try local file path first
                if (!message.mediaLocalUri.isNullOrBlank()) {
                    val file = File(message.mediaLocalUri)
                    if (file.exists() && file.length() > 0) {
                        bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    }
                }

                // 2. Try inline base64 data URI
                if (bitmap == null && !message.mediaCloudUrl.isNullOrBlank() && message.mediaCloudUrl.contains("base64,")) {
                    val b64 = message.mediaCloudUrl.substringAfter("base64,")
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    if (bytes.isNotEmpty()) {
                        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        // Proactively cache to local storage if missing
                        if (message.mediaLocalUri.isNullOrBlank()) {
                            try {
                                val mediaDir = File(context.filesDir, "received_media").apply { mkdirs() }
                                val cachedFile = File(mediaDir, "${message.messageId}.jpg")
                                if (!cachedFile.exists()) {
                                    cachedFile.writeBytes(bytes)
                                }
                            } catch (ignored: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatScreen", "Error resolving image bitmap: ${e.message}", e)
            } finally {
                isResolving = false
            }
        }
    }

    if (bitmap != null) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = { showFullscreenDialog = true },
                        onLongClick = onLongClick
                    )
            ) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Image attachment",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action row: Tap hint + Save to Gallery button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap to enlarge • Hold to delete",
                    fontSize = 10.sp,
                    color = TextMuted,
                    modifier = Modifier.combinedClickable(
                        onClick = { showFullscreenDialog = true },
                        onLongClick = onLongClick
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            val success = HardwareStateUtil.saveBitmapToGallery(
                                context,
                                bitmap!!,
                                "Phantom_${message.messageId}"
                            )
                            if (success) {
                                Toast.makeText(
                                    context,
                                    "Saved to Gallery (Pictures/PhantomMesh)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Save to Gallery",
                        tint = NeonCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Save",
                        fontSize = 11.sp,
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        // Fullscreen Image Dialog
        if (showFullscreenDialog) {
            Dialog(
                onDismissRequest = { showFullscreenDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.95f))
                ) {
                    // Fullscreen Image
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Full Image View",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 64.dp),
                        contentScale = ContentScale.Fit
                    )

                    // Top Bar with Close and Save Actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showFullscreenDialog = false },
                            modifier = Modifier
                                .size(40.dp)
                                .background(DarkSurfaceVariant.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(NeonCyan)
                                .clickable {
                                    val success = HardwareStateUtil.saveBitmapToGallery(
                                        context,
                                        bitmap!!,
                                        "Phantom_${message.messageId}"
                                    )
                                    if (success) {
                                        Toast.makeText(
                                            context,
                                            "Saved to Gallery (Pictures/PhantomMesh)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = DarkBackground,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Save to Gallery",
                                color = DarkBackground,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    } else if (isResolving) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = NeonCyan,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Loading attachment...", fontSize = 11.sp, color = TextMuted)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    } else if (!message.mediaCloudUrl.isNullOrBlank() && message.mediaCloudUrl.startsWith("http")) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
        ) {
            AsyncImage(
                model = message.mediaCloudUrl,
                contentDescription = "Image attachment",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
fun AttachmentOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subLabel: String,
    iconTint: Color,
    backgroundTint: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, borderColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(backgroundTint)
                .border(1.dp, borderColor.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = subLabel,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
fun ContactCardContent(
    content: String,
    isMe: Boolean
) {
    val lines = content.lines()
    val nameLine = lines.firstOrNull { it.contains("Contact Card:") }?.substringAfter("Contact Card:")?.trim() ?: "Contact"
    val numbers = lines.filter { it.contains("Mobile:") }.map { it.substringAfter("Mobile:").trim() }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isMe) DarkCard.copy(alpha = 0.7f) else DarkSurfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nameLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontSize = 15.sp
                )
                Text(
                    text = "Shared Contact",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }

        if (numbers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            numbers.forEach { number ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                    data = android.net.Uri.parse("tel:$number")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Number: $number", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📞 $number",
                        color = NeonCyan,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "DIAL",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

