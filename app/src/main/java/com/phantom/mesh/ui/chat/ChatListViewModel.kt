package com.phantom.mesh.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phantom.mesh.data.local.entity.ChatEntity
import com.phantom.mesh.data.local.preferences.ContactAliasManager
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.data.repository.ChatRepository
import com.phantom.mesh.mesh.MeshConnectionManager
import com.phantom.mesh.model.ConversationMode
import com.phantom.mesh.network.NetworkStateManager
import com.phantom.mesh.network.NetworkTransport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.phantom.mesh.mesh.MeshRoutingTable
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val networkStateManager: NetworkStateManager,
    private val sessionManager: SessionManager,
    private val meshConnectionManager: MeshConnectionManager,
    private val routingTable: MeshRoutingTable,
    private val contactAliasManager: ContactAliasManager,
    private val callManager: com.phantom.mesh.calling.CallManager,
    private val typingStatusManager: com.phantom.mesh.util.TypingStatusManager
) : ViewModel() {

    val activeTransport: StateFlow<NetworkTransport> = kotlinx.coroutines.flow.combine(
        networkStateManager.activeTransport,
        sessionManager.conversationModeFlow
    ) { transport, mode ->
        when (mode) {
            ConversationMode.OFFLINE -> {
                if (transport == NetworkTransport.Cloud) {
                    if (meshConnectionManager.hasActivePeers()) NetworkTransport.Bluetooth else NetworkTransport.Offline
                } else {
                    transport
                }
            }
            ConversationMode.ONLINE -> {
                if (transport is NetworkTransport.Bluetooth || transport is NetworkTransport.WifiDirect) {
                    if (networkStateManager.isInternetAvailable.value) NetworkTransport.Cloud else NetworkTransport.Offline
                } else {
                    transport
                }
            }
            ConversationMode.HYBRID -> transport
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), networkStateManager.activeTransport.value)

    val peerTypingStates: StateFlow<Map<String, com.phantom.mesh.util.TypingStatusManager.TypingState>> =
        typingStatusManager.peerTypingStates

    val connectedPeers: StateFlow<List<MeshRoutingTable.MeshNode>> = routingTable.topology

    // Feature 6: Ensure chats are strictly sequenced with the most recent at the top
    val chats: StateFlow<List<ChatEntity>> = chatRepository.observeActiveChats()
        .map { list -> list.sortedByDescending { maxOf(it.lastMessageTimestamp, it.createdAt) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedChats: StateFlow<List<ChatEntity>> = chatRepository.observeArchivedChats()
        .map { list -> list.sortedByDescending { maxOf(it.lastMessageTimestamp, it.createdAt) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isDndEnabled: StateFlow<Boolean> = sessionManager.dndEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isDuressSession: StateFlow<Boolean> = sessionManager.isDuressFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val currentUsername: StateFlow<String?> = sessionManager.usernameFlow
    val currentUserId: StateFlow<String?> = sessionManager.userIdFlow
    val isBluetoothOnlyMode: StateFlow<Boolean> = sessionManager.bluetoothOnlyModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val conversationMode: StateFlow<ConversationMode> = sessionManager.conversationModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionManager.getConversationMode())

    fun setConversationMode(mode: ConversationMode) {
        sessionManager.setConversationMode(mode)
    }

    val contactAliases: StateFlow<Map<String, String>> = contactAliasManager.aliasesFlow

    fun setDndEnabled(enabled: Boolean) {
        sessionManager.setDndEnabled(enabled)
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.deleteChatAndMessages(chatId)
        }
    }

    fun toggleMuteChat(chat: ChatEntity) {
        viewModelScope.launch {
            chatRepository.setChatMuted(chat.chatId, !chat.isMuted)
        }
    }

    fun toggleArchiveChat(chat: ChatEntity) {
        viewModelScope.launch {
            chatRepository.setChatArchived(chat.chatId, !chat.isArchived)
        }
    }

    fun getFormattedDisplayName(usernameOrId: String): String {
        return contactAliasManager.getFormattedDisplayName(usernameOrId)
    }

    fun getAlias(usernameOrId: String): String? {
        return contactAliasManager.getAlias(usernameOrId)
    }

    fun setAlias(usernameOrId: String, alias: String) {
        contactAliasManager.setAlias(usernameOrId, alias)
    }

    fun clearAlias(usernameOrId: String) {
        contactAliasManager.clearAlias(usernameOrId)
    }

    fun getPeerTypingState(chatId: String) = typingStatusManager.getPeerTypingState(chatId)

    init {
        viewModelScope.launch {
            val userId = sessionManager.getUserId()
            val username = sessionManager.getUsername() ?: "Anonymous"
            if (userId != null) {
                chatRepository.syncUnreadCounts(userId)
                chatRepository.startCloudListener(userId, username)
                callManager.startCloudCallListener(username)
                meshConnectionManager.setLocalIdentity(userId, username)
                meshConnectionManager.startMesh()
            }
        }
    }
}
