package com.phantom.mesh.ui.calling

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.phantom.mesh.calling.webrtc.WebRtcClient
import com.phantom.mesh.calling.ActiveCall
import com.phantom.mesh.calling.CallStatus
import com.phantom.mesh.calling.CallTransportMode
import com.phantom.mesh.calling.CallType
import com.phantom.mesh.calling.CameraVideoEngine
import com.phantom.mesh.ui.theme.DarkBackground
import com.phantom.mesh.ui.theme.DarkCard
import com.phantom.mesh.ui.theme.DuressCrimson
import com.phantom.mesh.ui.theme.NeonCyan
import com.phantom.mesh.ui.theme.NeonGreen
import com.phantom.mesh.ui.theme.TextMuted
import com.phantom.mesh.ui.theme.TextPrimary
import com.phantom.mesh.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun CallOverlayScreen(
    call: ActiveCall,
    webRtcClient: WebRtcClient? = null,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onVideoToggle: () -> Unit,
    onFlipCamera: () -> Unit,
    onCameraPermissionGranted: (() -> Unit)? = null,
    onAccept: () -> Unit,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val camOk = perms[Manifest.permission.CAMERA] ?: true
        val micOk = perms[Manifest.permission.RECORD_AUDIO] ?: true
        if (camOk) {
            onCameraPermissionGranted?.invoke()
        }
    }

    LaunchedEffect(call.callId, call.callType) {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasCam = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val needsCam = call.callType == CallType.VIDEO

        val needed = mutableListOf<String>()
        if (!hasMic) needed.add(Manifest.permission.RECORD_AUDIO)
        if (needsCam && !hasCam) needed.add(Manifest.permission.CAMERA)
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    BackHandler(enabled = true) {
        // Prevent accidental dismissal during active or ringing call
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF070B12),
                            DarkBackground,
                            Color(0xFF03070C)
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
                // Background Cybernetic Grid / Video Feed Container
                if (call.callType == CallType.VIDEO && call.status == CallStatus.CONNECTED) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0A121D))
                            .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    ) {
                        // Remote Video Stream Container (Direct Frame Stream via LAN, Cloud & Mesh)
                        val remoteBmp = call.remoteVideoBitmap
                        var imageBitmap: androidx.compose.ui.graphics.ImageBitmap? = null
                        if (remoteBmp != null && !remoteBmp.isRecycled) {
                            try {
                                imageBitmap = remoteBmp.asImageBitmap()
                            } catch (_: Exception) {}
                        }

                        if (imageBitmap != null) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Remote Video Stream",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Cybernetic stream badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkBackground.copy(alpha = 0.75f))
                                    .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(NeonGreen)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when (call.transportMode) {
                                            CallTransportMode.SAME_WIFI_LAN -> "LOCAL WI-FI DIRECT (P2P LAN)"
                                            CallTransportMode.BLUETOOTH_MESH -> "BLE MESH DIRECT P2P"
                                            else -> "ENCRYPTED VIDEO STREAM"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonGreen,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = NeonCyan.copy(alpha = 0.5f),
                                    modifier = Modifier.size(96.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "ESTABLISHING ENCRYPTED VIDEO FEED...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonCyan.copy(alpha = 0.7f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Connecting Cloud & Mesh Video Engine",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        // Local Camera PiP (Picture in Picture)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .size(width = 110.dp, height = 150.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkCard)
                                .border(1.5.dp, NeonGreen.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (call.isVideoEnabled) {
                                AndroidView(
                                    factory = { ctx ->
                                        TextureView(ctx).apply {
                                            scaleX = if (call.isFrontCamera) -1f else 1f
                                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                                    try {
                                                        CameraVideoEngine.attachPreview(this@apply, surface)
                                                    } catch (_: Exception) {}
                                                }
                                                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                                                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                                    try {
                                                        CameraVideoEngine.detachPreview()
                                                    } catch (_: Exception) {}
                                                    return true
                                                }
                                                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                                            }
                                        }
                                    },
                                    update = { textureView ->
                                        try {
                                            textureView.scaleX = if (call.isFrontCamera) -1f else 1f
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (call.isFrontCamera) "FRONT" else "BACK",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonGreen,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                Text(
                                    text = "CAM OFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DuressCrimson,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }

                // Top Header Info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Security Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(DarkCard.copy(alpha = 0.8f))
                                .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (call.callType == CallType.VIDEO) "E2EE VIDEO" else "E2EE VOICE",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Real-Time Network Transport Badge (WebRTC vs Local Wi-Fi LAN vs Cloud Relay vs Mesh)
                        val (modeText, modeColor) = when (call.transportMode) {
                            CallTransportMode.WEBRTC_DIRECT -> Pair("⚡ WEBRTC (FREE P2P UDP)", NeonCyan)
                            CallTransportMode.SAME_WIFI_LAN -> Pair("🟢 LOCAL WI-FI (LAN <5ms)", NeonGreen)
                            CallTransportMode.CLOUD_RELAY -> Pair("☁️ CLOUD RELAY (FIREBASE)", NeonCyan)
                            CallTransportMode.BLUETOOTH_MESH -> Pair("📶 BLUETOOTH MESH", Color(0xFFFFB74D))
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(DarkCard.copy(alpha = 0.8f))
                                .border(1.dp, modeColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(modeColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = modeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = modeColor,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "@${call.peerUsername}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val statusText = when (call.status) {
                        CallStatus.OUTGOING_CALLING -> "Calling..."
                        CallStatus.INCOMING_RINGING -> "Incoming Call..."
                        CallStatus.CONNECTED -> {
                            val minutes = call.durationSeconds / 60
                            val seconds = call.durationSeconds % 60
                            String.format(Locale.US, "%02d:%02d", minutes, seconds)
                        }
                        CallStatus.ENDED -> "Call Ended"
                        CallStatus.IDLE -> ""
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (call.status == CallStatus.CONNECTED) NeonGreen else TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Center Avatar with pulsing rings for Voice Call
                if (call.callType == CallType.VOICE || call.status != CallStatus.CONNECTED) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Pulsing outer ripple when connected or calling
                        if (call.status == CallStatus.CONNECTED || call.status == CallStatus.OUTGOING_CALLING || call.status == CallStatus.INCOMING_RINGING) {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 1.0f,
                                targetValue = 1.35f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scale"
                            )

                            Box(
                                modifier = Modifier
                                    .size(170.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(
                                        if (call.status == CallStatus.INCOMING_RINGING)
                                            NeonGreen.copy(alpha = 0.15f)
                                        else
                                            NeonCyan.copy(alpha = 0.15f)
                                    )
                            )
                        }

                        // Inner Avatar
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .clip(CircleShape)
                                .background(DarkCard)
                                .border(
                                    width = 2.dp,
                                    color = if (call.status == CallStatus.CONNECTED) NeonGreen else NeonCyan,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (call.status == CallStatus.CONNECTED) NeonGreen else NeonCyan,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }

                // Bottom Call Control Actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Answer or Reject for incoming ringing
                    if (call.status == CallStatus.INCOMING_RINGING) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Decline (Red)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(DuressCrimson)
                                        .clickable { onEndCall() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Decline Call",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Decline", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                            }

                            // Accept (Green)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(NeonGreen)
                                        .clickable { onAccept() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = "Accept Call",
                                        tint = DarkBackground,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Accept", color = NeonGreen, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else {
                        // Controls for Active Call
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(32.dp))
                                .background(DarkCard.copy(alpha = 0.9f))
                                .border(1.dp, NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(32.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mic Mute Toggle
                            IconButton(onClick = onMuteToggle) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Mute",
                                    tint = if (call.isMuted) DuressCrimson else NeonCyan,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Speakerphone Toggle
                            IconButton(onClick = onSpeakerToggle) {
                                Icon(
                                    imageVector = if (call.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "Speaker",
                                    tint = if (call.isSpeakerOn) NeonGreen else TextSecondary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Video Controls (if video call)
                            if (call.callType == CallType.VIDEO) {
                                IconButton(onClick = onVideoToggle) {
                                    Icon(
                                        imageVector = Icons.Default.VideoLibrary,
                                        contentDescription = "Toggle Video",
                                        tint = if (call.isVideoEnabled) NeonGreen else TextMuted,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                IconButton(onClick = onFlipCamera) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Flip Camera",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // End Call (Hang Up)
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(DuressCrimson)
                                    .clickable { onEndCall() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "End Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
