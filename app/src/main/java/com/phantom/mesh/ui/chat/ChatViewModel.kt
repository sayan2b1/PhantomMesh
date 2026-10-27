package com.phantom.mesh.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phantom.mesh.data.local.entity.MessageEntity
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.data.repository.ChatRepository
import com.phantom.mesh.network.NetworkStateManager
import com.phantom.mesh.network.NetworkTransport
import com.phantom.mesh.calling.ActiveCall
import com.phantom.mesh.calling.CallInitiationResult
import com.phantom.mesh.calling.CallManager
import com.phantom.mesh.calling.CallType
import com.phantom.mesh.util.SoundEffectManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ReplyContext(
    val messageId: String,
    val senderName: String,
    val contentSnippet: String
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val networkStateManager: NetworkStateManager,
    private val sessionManager: SessionManager,
    private val contactAliasManager: com.phantom.mesh.data.local.preferences.ContactAliasManager,
    private val callManager: CallManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val chatId: String = checkNotNull(savedStateHandle["chatId"])
    val targetId: String = checkNotNull(savedStateHandle["targetId"])

    val activeTransport: StateFlow<NetworkTransport> = networkStateManager.activeTransport

    val messages: StateFlow<List<MessageEntity>> = chatRepository.observeMessages(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()
    val currentUsername: StateFlow<String?> = sessionManager.usernameFlow

    val chatWallpaperPath: StateFlow<String?> = sessionManager.chatWallpaperFlow
    val contactAliases: StateFlow<Map<String, String>> = contactAliasManager.aliasesFlow

    val readReceiptsEnabled: StateFlow<Boolean> = sessionManager.readReceiptsEnabledFlow
    val senderBubbleColor: StateFlow<String> = sessionManager.senderBubbleColorFlow
    val receiverBubbleColor: StateFlow<String> = sessionManager.receiverBubbleColorFlow
    val chatFontStyle: StateFlow<String> = sessionManager.chatFontStyleFlow
    val chatFontSizeScale: StateFlow<String> = sessionManager.chatFontSizeScaleFlow

    // ── SWIPE TO REPLY ───────────────────────────────────────────────────────
    private val _activeReply = MutableStateFlow<ReplyContext?>(null)
    val activeReply: StateFlow<ReplyContext?> = _activeReply.asStateFlow()

    fun setReplyingTo(message: MessageEntity) {
        val myId = _currentUserId.value
        val sender = if (message.senderId == myId) "You" else getFormattedTargetName().ifBlank { message.senderId }
        val snippet = when (message.dataType) {
            "image" -> "📷 Photo"
            "video" -> "🎥 Video"
            "voice" -> "🎤 Voice message"
            "audio" -> "🎵 Audio"
            else -> message.content ?: ""
        }
        _activeReply.value = ReplyContext(
            messageId = message.messageId,
            senderName = sender,
            contentSnippet = snippet.take(80)
        )
    }

    fun cancelReply() {
        _activeReply.value = null
    }

    // ── CALLING ENGINE ───────────────────────────────────────────────────────
    val currentCall: StateFlow<ActiveCall?> = callManager.currentCall

    private val _callBlockedNotice = MutableStateFlow<String?>(null)
    val callBlockedNotice: StateFlow<String?> = _callBlockedNotice.asStateFlow()

    fun clearCallBlockedNotice() {
        _callBlockedNotice.value = null
    }

    fun startVoiceCall() {
        val targetName = getFormattedTargetName().ifBlank { targetId }
        val result = callManager.startCall(targetName, targetId, CallType.VOICE)
        if (result is CallInitiationResult.BlockedByTransport) {
            _callBlockedNotice.value = result.reason
        }
    }

    fun startVideoCall() {
        val targetName = getFormattedTargetName().ifBlank { targetId }
        val result = callManager.startCall(targetName, targetId, CallType.VIDEO)
        if (result is CallInitiationResult.BlockedByTransport) {
            _callBlockedNotice.value = result.reason
        }
    }

    fun toggleCallMute() = callManager.toggleMute()
    fun toggleCallSpeaker() = callManager.toggleSpeaker()
    fun toggleCallVideo() = callManager.toggleVideo()
    fun flipCallCamera() = callManager.flipCamera()
    fun acceptIncomingCall() = callManager.acceptCall()
    fun endCall() = callManager.endCall()

    fun setTargetAlias(alias: String) {
        contactAliasManager.setAlias(targetId, alias)
    }

    fun clearTargetAlias() {
        contactAliasManager.clearAlias(targetId)
    }

    fun getTargetAlias(): String? {
        return contactAliasManager.getAlias(targetId)
    }

    fun getFormattedTargetName(): String {
        return contactAliasManager.getFormattedDisplayName(targetId)
    }

    private val _unreadMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val unreadMessageIds: StateFlow<Set<String>> = _unreadMessageIds.asStateFlow()

    private var isInitialLoadDone = false
    private var lastObservedCount = 0

    init {
        com.phantom.mesh.util.NotificationHelper.currentActiveChatId = chatId
        viewModelScope.launch {
            val myId = sessionManager.getUserId() ?: ""
            _currentUserId.value = myId

            // 1. Capture which messages are unread before marking as read
            val unreadIds = chatRepository.getUnreadMessageIds(chatId, myId)
            if (unreadIds.isNotEmpty()) {
                _unreadMessageIds.value = unreadIds.toSet()
            }

            // 2. Mark chat as read and dispatch read receipt if enabled
            val readReceiptsOn = sessionManager.isReadReceiptsEnabled()
            chatRepository.markChatAsRead(chatId, myId, sendReadReceipt = readReceiptsOn)

            // 3. Listen for incoming messages to trigger pop-up sound
            messages.collect { list ->
                if (!isInitialLoadDone) {
                    isInitialLoadDone = true
                    lastObservedCount = list.size
                } else if (list.size > lastObservedCount) {
                    val newCount = list.size - lastObservedCount
                    val newMessages = list.takeLast(newCount)
                    if (newMessages.any { it.senderId != myId }) {
                        SoundEffectManager.playMessageReceivedSound()
                    }
                    lastObservedCount = list.size
                } else {
                    lastObservedCount = list.size
                }
            }
        }
    }

    override fun onCleared() {
        if (com.phantom.mesh.util.NotificationHelper.currentActiveChatId == chatId) {
            com.phantom.mesh.util.NotificationHelper.currentActiveChatId = null
        }
        super.onCleared()
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val reply = _activeReply.value
        _activeReply.value = null
        viewModelScope.launch {
            try {
                chatRepository.sendTextMessage(
                    chatId = chatId,
                    targetId = targetId,
                    content = text.trim(),
                    replyToMessageId = reply?.messageId,
                    replyToContent = reply?.contentSnippet,
                    replyToSender = reply?.senderName
                )
                SoundEffectManager.playMessageSentSound()
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error in sendMessage: ${e.message}", e)
            }
        }
    }

    fun sendMedia(file: File, mimeType: String, dataType: String = "image") {
        viewModelScope.launch {
            try {
                chatRepository.sendMediaMessage(chatId, targetId, file, mimeType, dataType)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error in sendMedia: ${e.message}", e)
            }
        }
    }

    fun sendVoiceMessage(file: File, durationMs: Long) {
        viewModelScope.launch {
            try {
                chatRepository.sendVoiceMessage(chatId, targetId, file, durationMs)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error in sendVoiceMessage: ${e.message}", e)
            }
        }
    }

    fun deleteMessageForEveryone(message: MessageEntity) {
        viewModelScope.launch {
            try {
                chatRepository.deleteMessageForEveryone(chatId, message.messageId, targetId)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error in deleteMessageForEveryone: ${e.message}", e)
            }
        }
    }

    fun deleteMessageForMe(message: MessageEntity) {
        viewModelScope.launch {
            try {
                chatRepository.deleteMessageForMe(chatId, message.messageId)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error in deleteMessageForMe: ${e.message}", e)
            }
        }
    }

    fun sendUriAttachment(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val isImage = mimeType.startsWith("image")
                val isVideo = mimeType.startsWith("video")
                val isAudio = mimeType.startsWith("audio")
                val dataType = if (isImage) "image" else if (isVideo) "video" else if (isAudio) "audio" else "file"

                // Query real file display name if available
                var realDisplayName: String? = null
                try {
                    contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) realDisplayName = cursor.getString(idx)
                        }
                    }
                } catch (ignored: Exception) {}

                val fileExtFromName = realDisplayName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
                val extension = if (isImage) "jpg"
                    else if (isVideo) "mp4"
                    else if (isAudio) (fileExtFromName ?: "mp3")
                    else (fileExtFromName ?: "bin")

                val prefix = if (isAudio) "audio_" else "attachment_"
                val tempFile = File(context.cacheDir, "${prefix}${System.currentTimeMillis()}.$extension")

                if (isImage) {
                    // Downsample to max 1280px to optimize transfer and avoid OOM
                    val boundsStream = contentResolver.openInputStream(uri)
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeStream(boundsStream, null, options)
                    boundsStream?.close()

                    var inSampleSize = 1
                    val maxDim = 1280
                    while (options.outWidth / inSampleSize > maxDim || options.outHeight / inSampleSize > maxDim) {
                        inSampleSize *= 2
                    }

                    val scaleOptions = android.graphics.BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                    }
                    val scaledStream = contentResolver.openInputStream(uri)
                    val bitmap = android.graphics.BitmapFactory.decodeStream(scaledStream, null, scaleOptions)
                    scaledStream?.close()

                    if (bitmap != null) {
                        java.io.FileOutputStream(tempFile).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                        }
                        bitmap.recycle()
                    } else {
                        contentResolver.openInputStream(uri)?.use { input ->
                            java.io.FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                } else {
                    contentResolver.openInputStream(uri)?.use { input ->
                        java.io.FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                val finalDisplayName = realDisplayName ?: tempFile.name
                chatRepository.sendMediaMessage(chatId, targetId, tempFile, mimeType, dataType, finalDisplayName)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to process attachment: ${e.message}", e)
            }
        }
    }

    fun setWallpaperFromUri(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val wallpaperFile = File(context.filesDir, "chat_wallpaper.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(wallpaperFile).use { output ->
                        input.copyTo(output)
                    }
                }
                sessionManager.setChatWallpaperPath(wallpaperFile.absolutePath)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to save wallpaper: ${e.message}", e)
            }
        }
    }

    fun removeWallpaper(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val wallpaperFile = File(context.filesDir, "chat_wallpaper.jpg")
                if (wallpaperFile.exists()) {
                    wallpaperFile.delete()
                }
                sessionManager.setChatWallpaperPath(null)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to remove wallpaper: ${e.message}", e)
            }
        }
    }

    fun sendContact(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val card = com.phantom.mesh.util.ContactHelper.extractContactCard(context, uri)
                if (!card.isNullOrBlank()) {
                    chatRepository.sendTextMessage(chatId, targetId, card)
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to send contact: ${e.message}", e)
            }
        }
    }
}
