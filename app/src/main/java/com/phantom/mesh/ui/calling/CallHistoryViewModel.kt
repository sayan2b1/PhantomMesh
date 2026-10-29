package com.phantom.mesh.ui.calling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phantom.mesh.calling.CallInitiationResult
import com.phantom.mesh.calling.CallManager
import com.phantom.mesh.calling.CallType
import com.phantom.mesh.data.local.dao.CallLogDao
import com.phantom.mesh.data.local.entity.CallLogEntity
import com.phantom.mesh.data.local.preferences.ContactAliasManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallHistoryViewModel @Inject constructor(
    private val callLogDao: CallLogDao,
    private val callManager: CallManager,
    private val contactAliasManager: ContactAliasManager
) : ViewModel() {

    val callLogs: StateFlow<List<CallLogEntity>> = callLogDao.observeAllCallLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _callBlockedNotice = MutableStateFlow<String?>(null)
    val callBlockedNotice: StateFlow<String?> = _callBlockedNotice.asStateFlow()

    fun clearCallBlockedNotice() {
        _callBlockedNotice.value = null
    }

    fun deleteCallLog(callId: String) {
        viewModelScope.launch {
            callLogDao.deleteCallLog(callId)
        }
    }

    fun clearAllCallLogs() {
        viewModelScope.launch {
            callLogDao.clearAllCallLogs()
        }
    }

    fun getFormattedDisplayName(usernameOrId: String): String {
        return contactAliasManager.getFormattedDisplayName(usernameOrId)
    }

    fun startVoiceCall(peerUsername: String, peerAddress: String) {
        val result = callManager.startCall(peerUsername, peerAddress, CallType.VOICE)
        if (result is CallInitiationResult.BlockedByTransport) {
            _callBlockedNotice.value = result.reason
        }
    }

    fun startVideoCall(peerUsername: String, peerAddress: String) {
        val result = callManager.startCall(peerUsername, peerAddress, CallType.VIDEO)
        if (result is CallInitiationResult.BlockedByTransport) {
            _callBlockedNotice.value = result.reason
        }
    }
}

