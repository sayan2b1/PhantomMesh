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
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.phantom.mesh.data.local.preferences.SessionManager
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

import com.phantom.mesh.data.repository.ChatRepository

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var meshConnectionManager: MeshConnectionManager

    @Inject
    lateinit var chatRepository: ChatRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (canStartForegroundMeshService()) {
            startMeshService()
            if (sessionManager.isSessionValid()) {
                val userId = sessionManager.getUserId()
                val username = sessionManager.getUsername() ?: "Anonymous"
                if (userId != null) {
                    meshConnectionManager.setLocalIdentity(userId, username)
                    meshConnectionManager.startMesh()
                }
            }
        } else {
            Log.w("MainActivity", "Required permissions not fully granted; mesh foreground service held back")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge for smooth keyboard / IME handling
        enableEdgeToEdge()

        // Request runtime permissions required for Nearby Connections (API 31+)
        requestRequiredPermissions()

        // Schedule periodic TTL drop zone cleanup
        DropZoneTtlWorker.schedule(applicationContext)

        // ── 24-HOUR SESSION PERSISTENCE RESTORATION ─────────────────────────
        val isSessionValid = sessionManager.isSessionValid()
        val initialDestination: String

        if (isSessionValid) {
            val userId = sessionManager.getUserId()
            val username = sessionManager.getUsername() ?: "Anonymous"
            if (userId != null) {
                meshConnectionManager.setLocalIdentity(userId, username)
                chatRepository.startCloudListener(userId, username)
                meshConnectionManager.startMesh()
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
                PhantomNavGraph(navController = navController, startDestination = initialDestination)
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
                meshConnectionManager.startMesh()
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

        // Audio Recording permission for voice messaging
        permissions.add(Manifest.permission.RECORD_AUDIO)

        // OxygenOS / Android Location permissions for Nearby Connections discovery
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

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
}
