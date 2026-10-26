package com.phantom.mesh.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.phantom.mesh.R
import com.phantom.mesh.network.NetworkStateManager
import com.phantom.mesh.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the mesh network alive when the app is backgrounded.
 *
 * Android requires a foreground notification for persistent Nearby Connections.
 * This service:
 * 1. Observes [NetworkStateManager.isInternetAvailable]
 * 2. Automatically starts mesh networking when internet drops
 * 3. Stops mesh and switches to cloud when internet returns
 * 4. Updates the notification to show current transport state
 */
@AndroidEntryPoint
class MeshNetworkService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "phantom_mesh_channel"
        const val NOTIFICATION_ID = 7891
    }

    @Inject lateinit var meshConnectionManager: MeshConnectionManager
    @Inject lateinit var networkStateManager: NetworkStateManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            val notification = buildNotification("Initializing...")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                androidx.core.app.ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("MeshNetworkService", "Failed to startForeground: ${e.message}", e)
            stopSelf()
            return
        }

        // Start and maintain continuous mesh networking so local peers are always discovered
        meshConnectionManager.startMesh()

        // Observe connectivity and update service notification
        lifecycleScope.launch {
            networkStateManager.connectivityFlow.collectLatest { hasInternet ->
                // Ensure mesh stays active across connectivity changes
                meshConnectionManager.startMesh()
                if (hasInternet) {
                    updateNotification("Phantom Mesh: Online (Cloud + Mesh Active)")
                } else {
                    updateNotification("Phantom Mesh: Offline (Mesh Networking Active)")
                }
            }
        }

        // Observe active transport for notification updates
        lifecycleScope.launch {
            networkStateManager.activeTransport.collectLatest { transport ->
                updateNotification("Active: ${transport.displayName}")
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        try {
            meshConnectionManager.stopMesh()
        } catch (e: Exception) {
            android.util.Log.e("MeshNetworkService", "Error stopping mesh on destroy: ${e.message}", e)
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PhantomMesh Network",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the current mesh network status"
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhantomMesh")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_mesh_network)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }
}
