package com.phantom.mesh.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phantom.mesh.data.local.entity.ChatEntity
import com.phantom.mesh.data.local.preferences.ContactAliasManager
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.data.repository.ChatRepository
import com.phantom.mesh.mesh.MeshConnectionManager
import com.phantom.mesh.network.NetworkStateManager
import com.phantom.mesh.network.NetworkTransport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val contactAliasManager: ContactAliasManager
) : ViewModel() {

    val activeTransport: StateFlow<NetworkTransport> = networkStateManager.activeTransport

    val connectedPeers: StateFlow<List<MeshRoutingTable.MeshNode>> = routingTable.topology

    val chats: StateFlow<List<ChatEntity>> = chatRepository.observeActiveChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isDuressSession: StateFlow<Boolean> = sessionManager.isDuressFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val currentUsername: StateFlow<String?> = sessionManager.usernameFlow
    val currentUserId: StateFlow<String?> = sessionManager.userIdFlow
    val isBluetoothOnlyMode: StateFlow<Boolean> = sessionManager.bluetoothOnlyModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val contactAliases: StateFlow<Map<String, String>> = contactAliasManager.aliasesFlow

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

    init {
        viewModelScope.launch {
            val userId = sessionManager.getUserId()
            val username = sessionManager.getUsername() ?: "Anonymous"
            if (userId != null) {
                chatRepository.syncUnreadCounts(userId)
                chatRepository.startCloudListener(userId, username)
                meshConnectionManager.setLocalIdentity(userId, username)
                meshConnectionManager.startMesh()
            }
        }
    }
}
