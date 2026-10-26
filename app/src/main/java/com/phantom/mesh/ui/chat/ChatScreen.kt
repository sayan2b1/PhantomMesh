package com.phantom.mesh.ui.chat

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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

    var inputMessage by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var selectedMessageForDelete by remember { mutableStateOf<MessageEntity?>(null) }

    val voicePlayerManager = remember { VoicePlayerManager() }
    DisposableEffect(Unit) {
        onDispose {
            voicePlayerManager.stop()
        }
    }

    // Media attachment picker for images & videos
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.sendUriAttachment(context, it) }
    }

    // Auto-scroll to latest message automatically when new messages arrive or are sent
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PEER: ${viewModel.targetId.take(12)}",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
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
                    // DYNAMIC NETWORK INDICATOR IN TOP APP BAR
                    NetworkIndicator(transport = transport, showLabel = true)
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
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                reverseLayout = true // Chat style: latest messages at the bottom
            ) {
                items(messages, key = { it.messageId }) { message ->
                    val isMe = message.senderId == currentUserId || 
                               (currentUsername != null && message.senderId.equals(currentUsername, ignoreCase = true))
                    Box(modifier = Modifier.animateItem()) {
                        MessageBubble(
                            message = message,
                            isMe = isMe,
                            voicePlayerManager = voicePlayerManager,
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

            ChatInputBar(
                text = inputMessage,
                onTextChange = { inputMessage = it },
                onAttachClick = {
                    mediaPickerLauncher.launch("*/*")
                },
                onSend = {
                    if (inputMessage.isNotBlank()) {
                        viewModel.sendMessage(inputMessage)
                        inputMessage = ""
                    }
                },
                onSendVoiceMessage = { file, durationMs ->
                    viewModel.sendVoiceMessage(file, durationMs)
                }
            )
        }
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    voicePlayerManager: VoicePlayerManager,
    onLongClick: () -> Unit = {}
) {
    val timeFormatted = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    val isDeleted = message.content == "🚫 This message was deleted" || message.dataType == "deleted"

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
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
                    .background(if (isMe) DarkCard else DarkSurfaceVariant)
                    .border(
                        1.dp,
                        if (isDeleted) Color.White.copy(alpha = 0.05f)
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
                        val isVoice = message.dataType == "voice" ||
                                message.mimeType?.startsWith("audio") == true ||
                                message.mediaCloudUrl?.startsWith("data:audio") == true

                        val isImage = !isVoice && (message.dataType == "image" ||
                                message.mimeType?.startsWith("image") == true ||
                                message.mediaCloudUrl?.startsWith("data:image") == true ||
                                message.mediaLocalUri?.endsWith(".jpg", ignoreCase = true) == true ||
                                message.mediaLocalUri?.endsWith(".png", ignoreCase = true) == true ||
                                message.content?.endsWith(".jpg", ignoreCase = true) == true ||
                                message.content?.endsWith(".png", ignoreCase = true) == true ||
                                message.content?.startsWith("attachment_", ignoreCase = true) == true)

                        val isVideo = !isVoice && !isImage && (message.dataType == "video" || message.mimeType?.startsWith("video") == true)

                        if (isVoice) {
                            VoiceMessageBubbleContent(
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
                        val isAttachmentPlaceholder = isVoice ||
                                message.content?.startsWith("attachment_", ignoreCase = true) == true ||
                                message.content?.startsWith("[Attachment:", ignoreCase = true) == true ||
                                message.content?.equals("[Image Attachment]", ignoreCase = true) == true ||
                                message.content?.endsWith(".jpg", ignoreCase = true) == true ||
                                message.content?.endsWith(".png", ignoreCase = true) == true

                        val showContent = !message.content.isNullOrBlank() && (!isImage && !isVideo || !isAttachmentPlaceholder) && !isVoice
                        if (showContent) {
                            Text(
                                text = message.content ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
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

                        // Timestamp
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )

                        if (isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            // HOP-STATE READ RECEIPTS (1 gray, 1 blue, 2 blue)
                            HopReceiptIcon(deliveryStatus = message.deliveryStatus)
                        }
                    }
                }
            }
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
