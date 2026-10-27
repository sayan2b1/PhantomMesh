package com.phantom.mesh.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.phantom.mesh.R
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val contactAliasManager: com.phantom.mesh.data.local.preferences.ContactAliasManager
) {
    companion object {
        const val CHANNEL_ID = "phantom_messages_channel"
        const val CHANNEL_NAME = "Messages"
        const val CALLS_CHANNEL_ID = "phantom_calls_channel"
        const val CALLS_CHANNEL_NAME = "Incoming Calls"
        const val CALL_NOTIFICATION_ID = 8888

        /**
         * The chatId that is currently active on screen, if any.
         * Suppresses heads-up notifications while the user is actively viewing this conversation.
         */
        @Volatile
        var currentActiveChatId: String? = null
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority encrypted message notifications"
                enableLights(true)
                lightColor = android.graphics.Color.CYAN
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            notificationManager.createNotificationChannel(channel)

            val callChannel = NotificationChannel(
                CALLS_CHANNEL_ID,
                CALLS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming voice and video call alerts"
                enableLights(true)
                lightColor = android.graphics.Color.GREEN
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(callChannel)
        }
    }

    /**
     * Posts a system notification for an incoming message with high priority (heads-up).
     * Automatically strips "Peer" prefix from the sender name for visual clarity.
     */
    fun showNewMessageNotification(
        chatId: String,
        senderName: String,
        content: String,
        dataType: String = "text"
    ) {
        // Do not notify if in duress mode (anti-surveillance stealth)
        if (sessionManager.isDuress()) {
            return
        }

        // Do not notify if the user is already actively reading this exact conversation
        if (currentActiveChatId != null && currentActiveChatId.equals(chatId, ignoreCase = true)) {
            return
        }

        // Runtime permission check on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                return
            }
        }

        val cleanSender = senderName
            .removePrefix("Peer ")
            .removePrefix("Peer-")
            .removePrefix("PEER: ")
            .trim()
            .ifBlank { "Encrypted Contact" }

        val displaySenderName = contactAliasManager.getFormattedDisplayName(cleanSender)

        val displayText = when {
            dataType == "image" || content.startsWith("data:image") -> "📷 Photo attachment"
            dataType == "video" || content.startsWith("data:video") -> "🎥 Video attachment"
            dataType == "voice" || content.startsWith("data:audio") -> "🎤 Voice message"
            dataType == "file" -> "📎 File attachment"
            content.startsWith("🚫") -> content
            else -> content
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_chat_id", chatId)
            putExtra("extra_target_id", cleanSender)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIconBitmap = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.app_logo)
        } catch (ignored: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mesh_network)
            .apply {
                if (largeIconBitmap != null) {
                    setLargeIcon(largeIconBitmap)
                }
            }
            .setContentTitle(displaySenderName)
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        try {
            notificationManager.notify(chatId.hashCode(), builder.build())
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to display notification: ${e.message}", e)
        }
    }

    /**
     * Displays a full-screen heads-up notification for an incoming call.
     */
    fun showIncomingCallNotification(
        callerUsername: String,
        callType: String
    ) {
        if (sessionManager.isDuress()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            CALL_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CALLS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mesh_network)
            .setContentTitle("Incoming $callType Call")
            .setContentText("@$callerUsername is calling...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)

        try {
            notificationManager.notify(CALL_NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to show call notification: ${e.message}")
        }
    }

    fun cancelIncomingCallNotification() {
        try {
            notificationManager.cancel(CALL_NOTIFICATION_ID)
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to cancel call notification: ${e.message}")
        }
    }
}
