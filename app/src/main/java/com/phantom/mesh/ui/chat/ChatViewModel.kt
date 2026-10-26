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

    init {
        viewModelScope.launch {
            _currentUserId.value = sessionManager.getUserId()
        }
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
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val isImage = mimeType.startsWith("image")
                val isVideo = mimeType.startsWith("video")
                val dataType = if (isImage) "image" else if (isVideo) "video" else "file"

                val extension = if (isImage) "jpg" else if (isVideo) "mp4" else "bin"
                val tempFile = File(context.cacheDir, "attachment_${System.currentTimeMillis()}.$extension")

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

                chatRepository.sendMediaMessage(chatId, targetId, tempFile, mimeType, dataType)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to process attachment: ${e.message}", e)
            }
        }
    }
}
