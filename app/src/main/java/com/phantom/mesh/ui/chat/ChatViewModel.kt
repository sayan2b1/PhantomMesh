package com.phantom.mesh.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phantom.mesh.data.local.entity.MessageEntity
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.data.repository.ChatRepository
import com.phantom.mesh.network.NetworkStateManager
import com.phantom.mesh.network.NetworkTransport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val networkStateManager: NetworkStateManager,
    private val sessionManager: SessionManager,
    private val contactAliasManager: com.phantom.mesh.data.local.preferences.ContactAliasManager,
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

    init {
        com.phantom.mesh.util.NotificationHelper.currentActiveChatId = chatId
        viewModelScope.launch {
            _currentUserId.value = sessionManager.getUserId()
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
        viewModelScope.launch {
            try {
                chatRepository.sendTextMessage(chatId, targetId, text.trim())
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
