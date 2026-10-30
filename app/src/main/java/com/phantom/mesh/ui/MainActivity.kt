package com.phantom.mesh.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.model.ConversationMode
import com.phantom.mesh.data.worker.DropZoneTtlWorker
import com.phantom.mesh.mesh.MeshConnectionManager
import com.phantom.mesh.mesh.MeshNetworkService
import com.phantom.mesh.ui.navigation.Destinations
import com.phantom.mesh.ui.navigation.PhantomNavGraph
import com.phantom.mesh.ui.theme.PhantomMeshTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

import android.content.pm.PackageManager
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
import java.io.File
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.phantom.mesh.calling.CallManager
import com.phantom.mesh.calling.CallType
import com.phantom.mesh.ui.calling.CallOverlayScreen
import com.phantom.mesh.data.repository.ChatRepository

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var callManager: CallManager

    @Inject
    lateinit var meshConnectionManager: MeshConnectionManager

    @Inject
    lateinit var chatRepository: ChatRepository

    @Inject
    lateinit var localChatBackupManager: com.phantom.mesh.data.local.backup.LocalChatBackupManager

    @Inject
    lateinit var appUpdateManager: com.phantom.mesh.update.AppUpdateManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (canStartForegroundMeshService()) {
            if (sessionManager.getConversationMode() != ConversationMode.ONLINE) {
                startMeshService()
            }
            if (sessionManager.isSessionValid()) {
                val userId = sessionManager.getUserId()
                val username = sessionManager.getUsername() ?: "Anonymous"
                if (userId != null) {
                    meshConnectionManager.setLocalIdentity(userId, username)
                    if (sessionManager.getConversationMode() != ConversationMode.ONLINE) {
                        meshConnectionManager.startMesh()
                    }
                }
            }
        } else {
            Log.w("MainActivity", "Required permissions not fully granted; mesh foreground service held back")
        }
    }

    private val incomingCallPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val micOk = perms[Manifest.permission.RECORD_AUDIO] == true
        val camOk = perms[Manifest.permission.CAMERA] ?: true
        if (micOk && camOk) {
            callManager.acceptCall()
        } else {
            Toast.makeText(this, "Camera & Microphone permissions required to answer call", Toast.LENGTH_SHORT).show()
        }
    }

    private val pendingRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    private fun handleNotificationIntent(intent: Intent?) {
        val chatId = intent?.getStringExtra("extra_chat_id")
        val targetId = intent?.getStringExtra("extra_target_id")
        if (!chatId.isNullOrBlank() && !targetId.isNullOrBlank()) {
            pendingRoute.value = Destinations.chatRoute(chatId, targetId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)

        // Enable edge-to-edge for smooth keyboard / IME handling
        enableEdgeToEdge()

        // Request runtime permissions required for Nearby Connections (API 31+)
        requestRequiredPermissions()

        // Schedule periodic TTL drop zone cleanup
        DropZoneTtlWorker.schedule(applicationContext)

        // Ensure auto-backup is scheduled if configured
        localChatBackupManager.ensureScheduledFromPreferences()

        // Apply dynamic adaptive display refresh rate (60Hz, 90Hz, 120Hz, Adaptive)
        CoroutineScope(Dispatchers.Main).launch {
            sessionManager.preferredRefreshRateFlow.collect { rate ->
                applyRefreshRate(rate)
            }
        }

        // ── 24-HOUR SESSION PERSISTENCE RESTORATION ─────────────────────────
        val isSessionValid = sessionManager.isSessionValid()
        val initialDestination: String

        if (isSessionValid) {
            val userId = sessionManager.getUserId()
            val username = sessionManager.getUsername() ?: "Anonymous"
            if (userId != null) {
                meshConnectionManager.setLocalIdentity(userId, username)
                if (sessionManager.getConversationMode() != ConversationMode.OFFLINE) {
                    chatRepository.startCloudListener(userId, username)
                    callManager.startCloudCallListener(username)
                }
                if (sessionManager.getConversationMode() != ConversationMode.ONLINE) {
                    meshConnectionManager.startMesh()
                }
            }

            // Silently refresh / ensure Firebase Auth has an active session in background
            val token = sessionManager.getCustomToken()
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null && !token.isNullOrBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        auth.signInWithCustomToken(token).await()
                        Log.i("MainActivity", "Firebase Auth session automatically restored")
                    } catch (e: Exception) {
                        Log.w("MainActivity", "Could not exchange token: ${e.message}")
                    }
                }
            }

            initialDestination = Destinations.CHAT_LIST
        } else {
            initialDestination = Destinations.AUTH
        }

        setContent {
            PhantomMeshTheme {
                val navController = rememberNavController()
                val targetRoute by pendingRoute.collectAsState()

                androidx.compose.runtime.LaunchedEffect(targetRoute) {
                    targetRoute?.let { route ->
                        if (sessionManager.isSessionValid()) {
                            navController.navigate(route)
                        }
                        pendingRoute.value = null
                    }
                }

                PhantomNavGraph(navController = navController, startDestination = initialDestination)

                // ── PREVIOUS CRASH LOG DETECTOR ─────────────────────────────
                var crashLogText by remember { mutableStateOf<String?>(null) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    try {
                        val crashFile = File(filesDir, "crash_log.txt")
                        if (crashFile.exists()) {
                            val text = crashFile.readText().trim()
                            if (text.isNotBlank()) {
                                crashLogText = text
                            }
                        }
                    } catch (_: Exception) {}
                }

                crashLogText?.let { log ->
                    AlertDialog(
                        onDismissRequest = {
                            try { File(filesDir, "crash_log.txt").delete() } catch (_: Exception) {}
                            crashLogText = null
                        },
                        title = { Text("Crash Report Detected") },
                        text = {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                Text(
                                    text = "A crash was recorded from the previous session. You can copy the exact stack trace to help diagnose the issue:",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = log.take(1500),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = ClipData.newPlainText("PhantomMesh Crash Log", log)
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(this@MainActivity, "Crash log copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    try { File(filesDir, "crash_log.txt").delete() } catch (_: Exception) {}
                                    crashLogText = null
                                }
                            ) {
                                Text("Copy Log")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    try { File(filesDir, "crash_log.txt").delete() } catch (_: Exception) {}
                                    crashLogText = null
                                }
                            ) {
                                Text("Dismiss")
                            }
                        }
                    )
                }

                // ── POST-INSTALLATION WELCOME POP-UP ────────────────────────
                var postInstallInfo by remember { mutableStateOf<com.phantom.mesh.update.PostInstallInfo?>(null) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    postInstallInfo = appUpdateManager.checkPostInstallWelcome()
                }

                postInstallInfo?.let { info ->
                    com.phantom.mesh.ui.update.PostInstallWelcomeDialog(
                        info = info,
                        onDismiss = { postInstallInfo = null }
                    )
                }

                // ── STARTUP OVER-THE-AIR (OTA) UPDATE CHECK ─────────────────
                var startupUpdateRelease by remember { mutableStateOf<com.phantom.mesh.update.ReleaseInfo?>(null) }
                val updateDownloadState by appUpdateManager.downloadState.collectAsState()

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    val result = appUpdateManager.checkForUpdates(isManualCheck = false)
                    if (result is com.phantom.mesh.update.UpdateCheckResult.UpdateAvailable) {
                        startupUpdateRelease = result.release
                    }
                }

                startupUpdateRelease?.let { release ->
                    com.phantom.mesh.ui.update.UpdateAvailableDialog(
                        release = release,
                        downloadState = updateDownloadState,
                        onUpdateClick = {
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    val apk = appUpdateManager.downloadUpdate(release)
                                    appUpdateManager.triggerInstall(apk)
                                } catch (_: Exception) {}
                            }
                        },
                        onRemindLaterClick = {
                            sessionManager.setLastUpdateRemindMs(System.currentTimeMillis())
                            startupUpdateRelease = null
                            appUpdateManager.resetDownloadState()
                        },
                        onIgnoreClick = {
                            sessionManager.setIgnoredUpdateTag(release.tagName)
                            startupUpdateRelease = null
                            appUpdateManager.resetDownloadState()
                        },
                        onDismissRequest = {
                            startupUpdateRelease = null
                            appUpdateManager.resetDownloadState()
                        }
                    )
                }

                // ── GLOBAL ACTIVE & INCOMING CALL OVERLAY ───────────────────
                // Renders across the entire app whenever a call is ringing or active
                val activeCall by callManager.currentCall.collectAsState()
                val activeWebRtcClient by callManager.webRtcClientFlow.collectAsState()
                activeCall?.let { call ->
                    CallOverlayScreen(
                        call = call,
                        webRtcClient = activeWebRtcClient,
                        onMuteToggle = { callManager.toggleMute() },
                        onSpeakerToggle = { callManager.toggleSpeaker() },
                        onVideoToggle = { callManager.toggleVideo() },
                        onFlipCamera = { callManager.flipCamera() },
                        onCameraPermissionGranted = { callManager.startVideoStreamIfPermitted() },
                        onAccept = {
                            val hasAudio = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            val hasCam = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            val needsCam = call.callType == CallType.VIDEO

                            if (hasAudio && (!needsCam || hasCam)) {
                                callManager.acceptCall()
                            } else {
                                val needed = mutableListOf<String>()
                                if (!hasAudio) needed.add(Manifest.permission.RECORD_AUDIO)
                                if (needsCam && !hasCam) needed.add(Manifest.permission.CAMERA)
                                incomingCallPermissionLauncher.launch(needed.toTypedArray())
                            }
                        },
                        onEndCall = { callManager.endCall() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (sessionManager.isSessionValid()) {
            val userId = sessionManager.getUserId()
            val username = sessionManager.getUsername() ?: "Anonymous"
            if (userId != null) {
                meshConnectionManager.setLocalIdentity(userId, username)
                if (sessionManager.getConversationMode() != ConversationMode.ONLINE) {
                    meshConnectionManager.startMesh()
                }
            }
        }
    }

    private fun canStartForegroundMeshService(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasBtConnect = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasBtConnect) return false
        }
        return true
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Location permissions required by Android OS for BLE / Nearby Connections mesh discovery
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Calling & Voice notes permissions (Record audio & Camera)
        permissions.add(Manifest.permission.RECORD_AUDIO)
        permissions.add(Manifest.permission.CAMERA)

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            permissionLauncher.launch(ungranted.toTypedArray())
        } else {
            startMeshService()
        }
    }

    private fun startMeshService() {
        if (!canStartForegroundMeshService()) {
            Log.w("MainActivity", "Cannot start MeshNetworkService: BLUETOOTH_CONNECT not granted")
            return
        }
        try {
            val serviceIntent = Intent(this, MeshNetworkService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start MeshNetworkService: ${e.message}", e)
        }
    }

    private fun applyRefreshRate(rate: Int) {
        try {
            val params = window.attributes
            if (rate > 0) {
                params.preferredRefreshRate = rate.toFloat()
            } else {
                // 0f = system default / max adaptive smoothness
                params.preferredRefreshRate = 0f
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (rate > 0) {
                    val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        display
                    } else {
                        @Suppress("DEPRECATION")
                        windowManager.defaultDisplay
                    }
                    val matchingMode = currentDisplay?.supportedModes?.firstOrNull {
                        kotlin.math.abs(it.refreshRate - rate) < 2f
                    }
                    if (matchingMode != null) {
                        params.preferredDisplayModeId = matchingMode.modeId
                    }
                } else {
                    params.preferredDisplayModeId = 0
                }
            }
            window.attributes = params
            Log.i("MainActivity", "Applied display refresh rate: ${if (rate > 0) "$rate Hz" else "Adaptive / Device Max"}")
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to set refresh rate: ${e.message}")
        }
    }
}
