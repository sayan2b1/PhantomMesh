package com.phantom.mesh.ui.chat

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phantom.mesh.ui.theme.DarkBackground
import com.phantom.mesh.ui.theme.DarkCard
import com.phantom.mesh.ui.theme.DarkSurface
import com.phantom.mesh.ui.theme.NeonCyan
import com.phantom.mesh.ui.theme.TextMuted
import com.phantom.mesh.ui.theme.TextPrimary
import com.phantom.mesh.ui.theme.TextSecondary
import com.phantom.mesh.util.AudioMediaHelper
import com.phantom.mesh.util.AudioTrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPickerSheet(
    onAudioSelected: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var audioTracks by remember { mutableStateOf<List<AudioTrackItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<AudioTrackItem?>(null) }

    // Media Player preview state
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingTrackId by remember { mutableStateOf<Long?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    fun stopPreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {}
        previewPlayer = null
        playingTrackId = null
        isPlaying = false
    }

    DisposableEffect(Unit) {
        onDispose {
            stopPreview()
        }
    }

    fun togglePreview(track: AudioTrackItem) {
        if (playingTrackId == track.id && isPlaying) {
            try {
                previewPlayer?.pause()
            } catch (_: Exception) {}
            isPlaying = false
        } else if (playingTrackId == track.id && previewPlayer != null) {
            try {
                previewPlayer?.start()
                isPlaying = true
            } catch (_: Exception) {
                stopPreview()
            }
        } else {
            stopPreview()
            try {
                val player = MediaPlayer().apply {
                    setDataSource(context, track.uri)
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        mp.start()
                        isPlaying = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        playingTrackId = null
                    }
                    setOnErrorListener { _, _, _ ->
                        isPlaying = false
                        playingTrackId = null
                        true
                    }
                }
                previewPlayer = player
                playingTrackId = track.id
            } catch (e: Exception) {
                stopPreview()
            }
        }
    }

    // Load audio files from MediaStore asynchronously
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = AudioMediaHelper.queryDeviceAudioFiles(context)
            withContext(Dispatchers.Main) {
                audioTracks = list
                isLoading = false
            }
        }
    }

    val filteredTracks = remember(audioTracks, searchQuery) {
        if (searchQuery.isBlank()) {
            audioTracks
        } else {
            audioTracks.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = {
            stopPreview()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            stopPreview()
                            onDismiss()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    },
                    title = {
                        if (isSearchActive) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search songs or audio...", color = TextMuted, fontSize = 14.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Column {
                                Text(
                                    text = "Send audio",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = if (selectedTrack != null) "1 selected" else "Tap to select",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selectedTrack != null) NeonCyan else TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (isSearchActive) {
                                searchQuery = ""
                                isSearchActive = false
                            } else {
                                isSearchActive = true
                            }
                        }) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchActive) "Close Search" else "Search",
                                tint = TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = selectedTrack != null,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    FloatingActionButton(
                        onClick = {
                            val trackToSend = selectedTrack
                            if (trackToSend != null) {
                                stopPreview()
                                onAudioSelected(trackToSend.uri)
                                onDismiss()
                            }
                        },
                        containerColor = Color(0xFF00E676), // WhatsApp Green send button
                        contentColor = Color.Black,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Audio",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            containerColor = DarkBackground
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFF9800))
                    }
                } else if (filteredTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No audio tracks matching \"$searchQuery\"" else "No audio tracks found on this device",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Audio files, songs, and recordings will appear here automatically",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredTracks, key = { it.id }) { track ->
                            val isThisSelected = selectedTrack?.id == track.id
                            val isThisPlaying = playingTrackId == track.id && isPlaying

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isThisSelected) NeonCyan.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable {
                                        selectedTrack = if (isThisSelected) null else track
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Orange rounded square with headphones icon (WhatsApp style)
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFF9800)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Headphones,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Track title & metadata
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isThisSelected) NeonCyan else TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = if (isThisSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${AudioMediaHelper.formatDuration(track.durationMs)} • ${AudioMediaHelper.formatFileSize(track.sizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Preview Play / Pause circular button
                                IconButton(
                                    onClick = {
                                        togglePreview(track)
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isThisPlaying) Color(0xFFFF9800)
                                            else Color.White.copy(alpha = 0.12f)
                                        )
                                ) {
                                    Icon(
                                        imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isThisPlaying) "Pause Preview" else "Play Preview",
                                        tint = if (isThisPlaying) DarkBackground else TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Selection checkmark indicator
                                if (isThisSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(NeonCyan),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = DarkBackground,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
