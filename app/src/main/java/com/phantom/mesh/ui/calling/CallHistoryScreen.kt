package com.phantom.mesh.ui.calling

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.phantom.mesh.data.local.entity.CallLogEntity
import com.phantom.mesh.ui.theme.DarkBackground
import com.phantom.mesh.ui.theme.DarkCard
import com.phantom.mesh.ui.theme.DarkSurface
import com.phantom.mesh.ui.theme.DuressCrimson
import com.phantom.mesh.ui.theme.NeonCyan
import com.phantom.mesh.ui.theme.NeonGreen
import com.phantom.mesh.ui.theme.TextMuted
import com.phantom.mesh.ui.theme.TextPrimary
import com.phantom.mesh.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    viewModel: CallHistoryViewModel,
    onBackClick: () -> Unit
) {
    val callLogs by viewModel.callLogs.collectAsState()
    val callBlockedNotice by viewModel.callBlockedNotice.collectAsState()
    val context = LocalContext.current

    var showClearAllDialog by remember { mutableStateOf(false) }
    var selectedLogToDelete by remember { mutableStateOf<CallLogEntity?>(null) }
    var pendingCallTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // (peer, isVideo)

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCallTarget?.let { (peer, _) ->
                viewModel.startVoiceCall(peer, peer)
            }
        } else {
            Toast.makeText(context, "Microphone permission required for calls", Toast.LENGTH_SHORT).show()
        }
        pendingCallTarget = null
    }

    val videoPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val mic = perms[Manifest.permission.RECORD_AUDIO] == true
        val cam = perms[Manifest.permission.CAMERA] == true
        if (mic && cam) {
            pendingCallTarget?.let { (peer, _) ->
                viewModel.startVideoCall(peer, peer)
            }
        } else {
            Toast.makeText(context, "Camera & Microphone permissions required for video calls", Toast.LENGTH_SHORT).show()
        }
        pendingCallTarget = null
    }

    LaunchedEffect(callBlockedNotice) {
        if (!callBlockedNotice.isNullOrBlank()) {
            Toast.makeText(context, callBlockedNotice, Toast.LENGTH_LONG).show()
            viewModel.clearCallBlockedNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CALL HISTORY",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
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
                    if (callLogs.isNotEmpty()) {
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Call History",
                                tint = DuressCrimson
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
        ) {
            if (callLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(DarkCard)
                                .border(1.dp, NeonCyan.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneMissed,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No call history yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Outgoing and incoming encrypted calls will appear here",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(callLogs, key = { it.callId }) { log ->
                        val formattedName = viewModel.getFormattedDisplayName(log.peerUsername)
                        CallLogRow(
                            log = log,
                            displayName = formattedName,
                            onVoiceClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    viewModel.startVoiceCall(log.peerUsername, log.peerAddress.ifBlank { log.peerUsername })
                                } else {
                                    pendingCallTarget = Pair(log.peerUsername, false)
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onVideoClick = {
                                val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                if (hasAudio && hasCam) {
                                    viewModel.startVideoCall(log.peerUsername, log.peerAddress.ifBlank { log.peerUsername })
                                } else {
                                    pendingCallTarget = Pair(log.peerUsername, true)
                                    videoPermissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
                                }
                            },
                            onLongClick = {
                                selectedLogToDelete = log
                            }
                        )
                    }
                }
            }
        }
    }

    // Clear All Calls Confirmation Dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear Call History?", color = TextPrimary) },
            text = { Text("Are you sure you want to permanently clear all call records?", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllCallLogs()
                        showClearAllDialog = false
                        Toast.makeText(context, "Call history cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("CLEAR ALL", color = DuressCrimson, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("CANCEL", color = TextMuted)
                }
            },
            containerColor = DarkCard
        )
    }

    // Delete Single Call Confirmation Dialog
    if (selectedLogToDelete != null) {
        val target = selectedLogToDelete!!
        AlertDialog(
            onDismissRequest = { selectedLogToDelete = null },
            title = { Text("Delete Call Record?", color = TextPrimary) },
            text = { Text("Remove this call entry with @${target.peerUsername}?", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCallLog(target.callId)
                        selectedLogToDelete = null
                        Toast.makeText(context, "Call record removed", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("DELETE", color = DuressCrimson, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedLogToDelete = null }) {
                    Text("CANCEL", color = TextMuted)
                }
            },
            containerColor = DarkCard
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CallLogRow(
    log: CallLogEntity,
    displayName: String,
    onVoiceClick: () -> Unit,
    onVideoClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isMissed = log.direction == "MISSED" || log.status == "MISSED" || log.status == "REJECTED"
    val isIncoming = log.direction == "INCOMING"
    val isOutgoing = log.direction == "OUTGOING"
    val isVideo = log.callType == "VIDEO"

    val timeFormatted = remember(log.timestamp) {
        val date = Date(log.timestamp)
        val now = System.currentTimeMillis()
        val diffDays = (now - log.timestamp) / (1000 * 60 * 60 * 24)
        when {
            diffDays == 0L -> "Today, " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            diffDays == 1L -> "Yesterday, " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(date)
        }
    }

    val durationText = remember(log.durationSeconds, isMissed, log.status) {
        when {
            isMissed -> if (log.status == "REJECTED") "Declined" else "Missed"
            log.durationSeconds > 0 -> {
                val mins = log.durationSeconds / 60
                val secs = log.durationSeconds % 60
                String.format(Locale.US, "%02d:%02d", mins, secs)
            }
            log.status == "CANCELLED" -> "Cancelled"
            else -> "Completed"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onVoiceClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(DarkCard)
                .border(1.dp, if (isMissed) DuressCrimson.copy(alpha = 0.5f) else NeonCyan.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Phone,
                contentDescription = null,
                tint = if (isMissed) DuressCrimson else NeonCyan,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                color = if (isMissed) DuressCrimson else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Direction icon
                val dirIcon = when {
                    isMissed -> Icons.AutoMirrored.Filled.CallMissed
                    isIncoming -> Icons.AutoMirrored.Filled.CallReceived
                    else -> Icons.AutoMirrored.Filled.CallMade
                }
                val dirColor = when {
                    isMissed -> DuressCrimson
                    isIncoming -> NeonGreen
                    else -> NeonCyan
                }

                Icon(
                    imageVector = dirIcon,
                    contentDescription = null,
                    tint = dirColor,
                    modifier = Modifier.size(14.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = durationText,
                    fontSize = 11.sp,
                    color = dirColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "•",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = timeFormatted,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        // Quick Call back icon buttons
        IconButton(
            onClick = onVoiceClick,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = "Voice Call",
                tint = NeonCyan,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onVideoClick,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = "Video Call",
                tint = NeonCyan,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

