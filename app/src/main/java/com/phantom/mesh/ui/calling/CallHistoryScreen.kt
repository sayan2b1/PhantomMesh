package com.phantom.mesh.ui.calling

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.Schedule
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

    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL" or "MISSED"

    val missedCount = remember(callLogs) {
        callLogs.count { log ->
            val raw = log.status.uppercase()
            log.direction.equals("MISSED", ignoreCase = true) ||
            raw == "MISSED" ||
            raw == "DECLINED" ||
            raw == "REJECTED" ||
            raw == "NOT_ANSWERED" ||
            raw == "NOT_CONNECTED"
        }
    }

    val filteredLogs = remember(callLogs, selectedFilter) {
        if (selectedFilter == "MISSED") {
            callLogs.filter { log ->
                val raw = log.status.uppercase()
                log.direction.equals("MISSED", ignoreCase = true) ||
                raw == "MISSED" ||
                raw == "DECLINED" ||
                raw == "REJECTED" ||
                raw == "NOT_ANSWERED" ||
                raw == "NOT_CONNECTED"
            }
        } else {
            callLogs
        }
    }

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
                Column(modifier = Modifier.fillMaxSize()) {
                    // Filter Tab Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedFilter == "ALL") NeonCyan.copy(alpha = 0.15f) else DarkCard)
                                .border(
                                    1.dp,
                                    if (selectedFilter == "ALL") NeonCyan else TextMuted.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedFilter = "ALL" }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "ALL (${callLogs.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (selectedFilter == "ALL") NeonCyan else TextMuted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedFilter == "MISSED") DuressCrimson.copy(alpha = 0.15f) else DarkCard)
                                .border(
                                    1.dp,
                                    if (selectedFilter == "MISSED") DuressCrimson else TextMuted.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedFilter = "MISSED" }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "MISSED / UNANSWERED ($missedCount)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (selectedFilter == "MISSED") DuressCrimson else TextMuted
                            )
                        }
                    }

                    if (filteredLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                text = if (selectedFilter == "MISSED") "No missed or declined calls" else "No calls found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredLogs, key = { it.callId }) { log ->
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
    val isVideo = log.callType.equals("VIDEO", ignoreCase = true)
    val isOutgoing = log.direction.equals("OUTGOING", ignoreCase = true)

    val rawStatus = log.status.uppercase()
    val isMissed = log.direction.equals("MISSED", ignoreCase = true) || rawStatus == "MISSED"
    val isDeclined = rawStatus == "DECLINED" || rawStatus == "REJECTED" || log.status.contains("Declined", ignoreCase = true)
    val isNotAnswered = rawStatus == "NOT_ANSWERED" || rawStatus == "CANCELLED" || log.status.contains("Cancelled", ignoreCase = true)
    val isNotConnected = rawStatus == "NOT_CONNECTED" || rawStatus == "FAILED" || rawStatus == "BLOCKED"
    val isCompleted = log.durationSeconds > 0 || rawStatus == "COMPLETED"

    val dirIcon = when {
        isMissed -> Icons.AutoMirrored.Filled.CallMissed
        isDeclined && !isOutgoing -> Icons.AutoMirrored.Filled.CallMissed
        isOutgoing -> Icons.AutoMirrored.Filled.CallMade
        else -> Icons.AutoMirrored.Filled.CallReceived
    }
    val dirColor = when {
        isMissed -> DuressCrimson
        isDeclined -> Color(0xFFFF9100) // Amber / Orange
        isNotConnected -> DuressCrimson
        isNotAnswered -> Color(0xFFF59E0B) // Amber
        isOutgoing -> NeonCyan
        else -> NeonGreen
    }

    val directionLabel = if (isOutgoing) "Outgoing" else "Incoming"

    val (statusLabel, statusColor) = when {
        isCompleted -> Pair("Completed", NeonGreen)
        isMissed -> Pair("Missed Call", DuressCrimson)
        isDeclined -> Pair("Call Declined", Color(0xFFFF9100))
        isNotAnswered -> Pair("Not Answered", Color(0xFFF59E0B))
        isNotConnected -> Pair("Not Connected", DuressCrimson)
        else -> Pair(log.status.ifBlank { "Completed" }, NeonCyan)
    }

    val durationText = remember(log.durationSeconds) {
        if (log.durationSeconds > 0) {
            val hours = log.durationSeconds / 3600
            val minutes = (log.durationSeconds % 3600) / 60
            val seconds = log.durationSeconds % 60
            when {
                hours > 0 -> String.format(Locale.US, "%dh %dm %ds", hours, minutes, seconds)
                minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, seconds)
                else -> String.format(Locale.US, "%ds", seconds)
            }
        } else {
            null
        }
    }

    val timeFormatted = remember(log.timestamp) {
        val date = Date(log.timestamp)
        val now = System.currentTimeMillis()
        val diffDays = (now - log.timestamp) / (1000 * 60 * 60 * 24)
        val timePart = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
        when {
            diffDays == 0L -> "Today, $timePart"
            diffDays == 1L -> "Yesterday, $timePart"
            else -> SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(date)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onVoiceClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Avatar circle with call type icon
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(DarkCard)
                .border(
                    width = 1.dp,
                    color = when {
                        isMissed -> DuressCrimson.copy(alpha = 0.6f)
                        isDeclined -> Color(0xFFFF9100).copy(alpha = 0.6f)
                        isNotAnswered || isNotConnected -> TextMuted.copy(alpha = 0.5f)
                        isVideo -> NeonCyan.copy(alpha = 0.4f)
                        else -> NeonGreen.copy(alpha = 0.4f)
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Phone,
                contentDescription = if (isVideo) "Video Call" else "Audio Call",
                tint = when {
                    isMissed -> DuressCrimson
                    isDeclined -> Color(0xFFFF9100)
                    isNotAnswered || isNotConnected -> TextMuted
                    isVideo -> NeonCyan
                    else -> NeonGreen
                },
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // 2. Info column
        Column(modifier = Modifier.weight(1f)) {
            // Row 1: Contact Display Name + Call Type Chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isMissed) DuressCrimson else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Call Type Pill (AUDIO / VIDEO)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isVideo) NeonCyan.copy(alpha = 0.12f) else NeonGreen.copy(alpha = 0.12f))
                        .border(
                            0.5.dp,
                            if (isVideo) NeonCyan.copy(alpha = 0.35f) else NeonGreen.copy(alpha = 0.35f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Phone,
                            contentDescription = null,
                            tint = if (isVideo) NeonCyan else NeonGreen,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isVideo) "VIDEO" else "AUDIO",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isVideo) NeonCyan else NeonGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Row 2: Direction Icon + Direction Label + Status Badge + Duration
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = dirIcon,
                    contentDescription = null,
                    tint = dirColor,
                    modifier = Modifier.size(13.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = directionLabel,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "•",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = statusLabel,
                    fontSize = 11.sp,
                    color = statusColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )

                if (durationText != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = durationText,
                        fontSize = 11.sp,
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Row 3: Time of calling with clock icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(11.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = timeFormatted,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 3. Quick Call Back buttons
        IconButton(
            onClick = onVoiceClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = "Voice Call",
                tint = NeonCyan,
                modifier = Modifier.size(19.dp)
            )
        }

        IconButton(
            onClick = onVideoClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = "Video Call",
                tint = NeonCyan,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

