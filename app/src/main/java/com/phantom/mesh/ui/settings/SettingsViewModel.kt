package com.phantom.mesh.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phantom.mesh.data.local.DuressWipeManager
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.phantom.mesh.data.local.backup.BackupFrequency
import com.phantom.mesh.data.local.backup.BackupMetadata
import com.phantom.mesh.data.local.backup.LocalChatBackupManager
import com.phantom.mesh.model.ConversationMode
import com.phantom.mesh.update.AppUpdateManager
import com.phantom.mesh.update.DownloadState
import com.phantom.mesh.update.ReleaseInfo
import com.phantom.mesh.update.UpdateCheckResult
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val duressWipeManager: DuressWipeManager,
    private val sessionManager: SessionManager,
    private val localChatBackupManager: LocalChatBackupManager,
    private val contactAliasManager: com.phantom.mesh.data.local.preferences.ContactAliasManager,
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    val username: StateFlow<String?> = sessionManager.usernameFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isDuress: StateFlow<Boolean> = sessionManager.isDuressFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val isDuressSession: StateFlow<Boolean> get() = isDuress

    val isBluetoothOnlyMode: StateFlow<Boolean> = sessionManager.bluetoothOnlyModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val conversationMode: StateFlow<ConversationMode> = sessionManager.conversationModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionManager.getConversationMode())

    val chatWallpaperPath: StateFlow<String?> = sessionManager.chatWallpaperFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Local Chat Backup StateFlows
    val autoBackupFrequency: StateFlow<String> = sessionManager.autoBackupFrequencyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionManager.getAutoBackupFrequency())

    val lastBackupTimestamp: StateFlow<Long> = sessionManager.lastBackupTimestampFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionManager.getLastBackupTimestamp())

    val lastBackupSize: StateFlow<Long> = sessionManager.lastBackupSizeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionManager.getLastBackupSize())

    val lastBackupMessageCount: StateFlow<Int> = sessionManager.lastBackupMessageCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionManager.getLastBackupMessageCount())

    private val _isBackupRunning = MutableStateFlow(false)
    val isBackupRunning: StateFlow<Boolean> = _isBackupRunning.asStateFlow()

    private val _isRestoreRunning = MutableStateFlow(false)
    val isRestoreRunning: StateFlow<Boolean> = _isRestoreRunning.asStateFlow()

    private val _availableBackups = MutableStateFlow<List<BackupMetadata>>(emptyList())
    val availableBackups: StateFlow<List<BackupMetadata>> = _availableBackups.asStateFlow()

    private val _securityQuestion = MutableStateFlow<String?>(null)
    val securityQuestion: StateFlow<String?> = _securityQuestion.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isChangingUsername = MutableStateFlow(false)
    val isChangingUsername: StateFlow<Boolean> = _isChangingUsername.asStateFlow()

    // ── Appearance & Personalization Flows ─────────────────────────────
    val senderBubbleColor: StateFlow<String> = sessionManager.senderBubbleColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionManager.getSenderBubbleColor())

    val receiverBubbleColor: StateFlow<String> = sessionManager.receiverBubbleColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionManager.getReceiverBubbleColor())

    val chatFontStyle: StateFlow<String> = sessionManager.chatFontStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionManager.getChatFontStyle())

    val chatFontSizeScale: StateFlow<String> = sessionManager.chatFontSizeScaleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionManager.getChatFontSizeScale())

    // ── Privacy & Performance Flows ────────────────────────────────────
    val readReceiptsEnabled: StateFlow<Boolean> = sessionManager.readReceiptsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionManager.isReadReceiptsEnabled())

    val preferredRefreshRate: StateFlow<Int> = sessionManager.preferredRefreshRateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionManager.getPreferredRefreshRate())

    val blockedUsers: StateFlow<Set<String>> = sessionManager.blockedUsersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionManager.getBlockedUsers())

    val isDndEnabled: StateFlow<Boolean> = sessionManager.dndEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionManager.isDndEnabled())

    fun setDndEnabled(enabled: Boolean) {
        sessionManager.setDndEnabled(enabled)
    }

    fun unblockUser(usernameOrId: String) {
        sessionManager.unblockUser(usernameOrId)
    }

    fun getFormattedDisplayName(usernameOrId: String): String {
        return contactAliasManager.getFormattedDisplayName(usernameOrId)
    }

    init {
        refreshAvailableBackups()
        loadSecurityQuestion()
    }

    fun loadSecurityQuestion() {
        viewModelScope.launch {
            try {
                val u = sessionManager.getUsername()
                if (!u.isNullOrBlank()) {
                    _securityQuestion.value = authRepository.getSecurityQuestionForUsername(u)
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to load security question", e)
            }
        }
    }

    fun refreshAvailableBackups() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _availableBackups.value = localChatBackupManager.listAvailableBackups()
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to list available backups", e)
            }
        }
    }

    fun setAutoBackupFrequency(frequency: BackupFrequency) {
        viewModelScope.launch {
            localChatBackupManager.scheduleAutoBackup(frequency)
            _statusMessage.value = "Auto-backup frequency set to: ${frequency.title}"
        }
    }

    fun performBackupNow() {
        if (_isBackupRunning.value) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isBackupRunning.value = true
            val result = localChatBackupManager.performBackup()
            _statusMessage.value = result.message
            _isBackupRunning.value = false
            refreshAvailableBackups()
        }
    }

    fun restoreFromFile(file: java.io.File) {
        if (_isRestoreRunning.value) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isRestoreRunning.value = true
            val result = localChatBackupManager.restoreFromFile(file)
            _statusMessage.value = result.message
            _isRestoreRunning.value = false
        }
    }

    fun restoreFromUri(uri: android.net.Uri) {
        if (_isRestoreRunning.value) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isRestoreRunning.value = true
            val result = localChatBackupManager.restoreFromUri(uri)
            _statusMessage.value = result.message
            _isRestoreRunning.value = false
        }
    }

    fun setWallpaperFromUri(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val wallpaperFile = java.io.File(context.filesDir, "chat_wallpaper.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(wallpaperFile).use { output ->
                        input.copyTo(output)
                    }
                }
                sessionManager.setChatWallpaperPath(wallpaperFile.absolutePath)
                _statusMessage.value = "Chat wallpaper updated successfully!"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to update wallpaper: ${e.message}"
            }
        }
    }

    fun removeWallpaper(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val wallpaperFile = java.io.File(context.filesDir, "chat_wallpaper.jpg")
                if (wallpaperFile.exists()) {
                    wallpaperFile.delete()
                }
                sessionManager.setChatWallpaperPath(null)
                _statusMessage.value = "Chat theme reset to default."
            } catch (e: Exception) {
                _statusMessage.value = "Failed to reset wallpaper: ${e.message}"
            }
        }
    }

    fun setConversationMode(mode: ConversationMode) {
        viewModelScope.launch {
            sessionManager.setConversationMode(mode)
            _statusMessage.value = when (mode) {
                ConversationMode.HYBRID -> "Conversation mode updated: Hybrid (Adaptive Cloud & Mesh multi-transport active)."
                ConversationMode.ONLINE -> "Conversation mode updated: Online (Cloud infrastructure active; mesh radios optional)."
                ConversationMode.OFFLINE -> "Conversation mode updated: Offline (Decentralized mesh active; cloud services bypassed)."
            }
        }
    }

    fun setBluetoothOnlyMode(enabled: Boolean) {
        viewModelScope.launch {
            sessionManager.setBluetoothOnlyMode(enabled)
            _statusMessage.value = if (enabled)
                "Bluetooth-only mode active: Wi-Fi disruptive upgrades blocked."
            else
                "Adaptive mesh active: Wi-Fi direct & Bluetooth both enabled."
        }
    }

    fun setReadReceiptsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionManager.setReadReceiptsEnabled(enabled)
            _statusMessage.value = if (enabled) "Read receipts enabled (blue ticks active)" else "Read receipts disabled (private mode)"
        }
    }

    fun setSenderBubbleColor(colorHex: String) {
        viewModelScope.launch {
            sessionManager.setSenderBubbleColor(colorHex)
        }
    }

    fun setReceiverBubbleColor(colorHex: String) {
        viewModelScope.launch {
            sessionManager.setReceiverBubbleColor(colorHex)
        }
    }

    fun setChatFontStyle(style: String) {
        viewModelScope.launch {
            sessionManager.setChatFontStyle(style)
        }
    }

    fun setChatFontSizeScale(scale: String) {
        viewModelScope.launch {
            sessionManager.setChatFontSizeScale(scale)
        }
    }

    fun setPreferredRefreshRate(rate: Int) {
        viewModelScope.launch {
            sessionManager.setPreferredRefreshRate(rate)
            _statusMessage.value = if (rate > 0) "Refresh rate set to $rate Hz" else "Adaptive refresh rate enabled"
        }
    }


    fun updateDecoyPassword(passphrase: String) {
        if (passphrase.length < 8) {
            _statusMessage.value = "Decoy passphrase must be at least 8 characters"
            return
        }
        viewModelScope.launch {
            val success = authRepository.setDecoyPassword(passphrase)
            _statusMessage.value = if (success) "Decoy duress passphrase successfully configured" else "Failed to set decoy"
        }
    }

    fun changePassword(oldPassword: String, newPassword: String, confirmPassword: String) {
        if (oldPassword.isBlank()) {
            _statusMessage.value = "Please enter your current password"
            return
        }
        if (newPassword.length < 8) {
            _statusMessage.value = "New password must be at least 8 characters"
            return
        }
        if (newPassword != confirmPassword) {
            _statusMessage.value = "New passwords do not match"
            return
        }

        viewModelScope.launch {
            val result = authRepository.changePassword(oldPassword, newPassword)
            result.onSuccess {
                _statusMessage.value = "Password updated successfully!"
            }.onFailure { e ->
                _statusMessage.value = e.message ?: "Failed to change password"
            }
        }
    }

    fun updateSecurityQuestion(question: String, answer: String) {
        if (question.isBlank() || answer.isBlank()) {
            _statusMessage.value = "Security question and answer cannot be blank"
            return
        }
        viewModelScope.launch {
            val userId = sessionManager.getUserId()
            if (userId != null) {
                val result = authRepository.updateSecurityQuestion(userId, question, answer)
                result.onSuccess {
                    _securityQuestion.value = question
                    _statusMessage.value = "Security recovery question updated successfully!"
                }.onFailure { e ->
                    _statusMessage.value = e.message ?: "Failed to update security question"
                }
            } else {
                _statusMessage.value = "User session not found"
            }
        }
    }

    fun resetPasswordWithSecurityAnswer(
        answer: String,
        newPassword: String,
        confirmPassword: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val u = sessionManager.getUsername()
        if (u.isNullOrBlank()) {
            _statusMessage.value = "No active user session found"
            onComplete(false)
            return
        }
        if (answer.isBlank()) {
            _statusMessage.value = "Please enter your secret answer"
            onComplete(false)
            return
        }
        if (newPassword.length < 8) {
            _statusMessage.value = "New password must be at least 8 characters"
            onComplete(false)
            return
        }
        if (newPassword != confirmPassword) {
            _statusMessage.value = "New passwords do not match"
            onComplete(false)
            return
        }

        viewModelScope.launch {
            val result = authRepository.resetPasswordWithSecurityAnswer(u, answer, newPassword)
            result.onSuccess {
                _statusMessage.value = "Password successfully reset via Security Question!"
                onComplete(true)
            }.onFailure { e ->
                _statusMessage.value = e.message ?: "Failed to reset password"
                onComplete(false)
            }
        }
    }

    fun changeUsername(newUsername: String) {
        val clean = newUsername.trim()
        if (clean.length < 3) {
            _statusMessage.value = "Username must be at least 3 characters"
            return
        }
        if (_isChangingUsername.value) return

        viewModelScope.launch {
            _isChangingUsername.value = true
            val result = authRepository.changeUsername(clean)
            result.onSuccess { msg ->
                _statusMessage.value = msg
            }.onFailure { e ->
                _statusMessage.value = e.message ?: "Failed to update username"
            }
            _isChangingUsername.value = false
        }
    }

    fun triggerEmergencyWipe(onWiped: () -> Unit) {
        viewModelScope.launch {
            duressWipeManager.executeWipe()
            sessionManager.clearSession()
            onWiped()
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // ── IN-APP OVER-THE-AIR (OTA) UPDATES ──────────────────────────────
    val downloadState: StateFlow<DownloadState> = appUpdateManager.downloadState

    private val _updateCheckResult = MutableStateFlow<UpdateCheckResult?>(null)
    val updateCheckResult: StateFlow<UpdateCheckResult?> = _updateCheckResult.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    fun checkForUpdatesManual() {
        if (_isCheckingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateCheckResult.value = null
            val result = appUpdateManager.checkForUpdates(isManualCheck = true)
            _updateCheckResult.value = result
            _isCheckingUpdate.value = false
        }
    }

    fun downloadAndInstall(release: ReleaseInfo) {
        viewModelScope.launch {
            try {
                val apkFile = appUpdateManager.downloadUpdate(release)
                appUpdateManager.triggerInstall(apkFile)
            } catch (e: Exception) {
                // Error state is caught and exposed by appUpdateManager.downloadState
            }
        }
    }

    fun triggerInstall(apkFile: File) {
        appUpdateManager.triggerInstall(apkFile)
    }

    fun remindUpdateLater() {
        sessionManager.setLastUpdateRemindMs(System.currentTimeMillis())
        _updateCheckResult.value = null
        appUpdateManager.resetDownloadState()
    }

    fun ignoreUpdate(tag: String) {
        sessionManager.setIgnoredUpdateTag(tag)
        _updateCheckResult.value = null
        appUpdateManager.resetDownloadState()
    }

    fun dismissUpdateDialog() {
        _updateCheckResult.value = null
        appUpdateManager.resetDownloadState()
    }

    fun getCurrentSavedReleaseNotes(): String {
        return sessionManager.getLastSavedReleaseNotes()
            ?: "PhantomMesh v${com.phantom.mesh.BuildConfig.VERSION_NAME} (Build ${com.phantom.mesh.BuildConfig.VERSION_CODE})\n\n" +
               "• In-App OTA Updates: Auto-checks GitHub Releases with changelogs and one-tap in-app installation.\n" +
               "• 3-Mode Conversation Control: Hybrid (Mesh + Cloud), Online-Only (Zero Hardware Enforcement), and Offline-Only (Strict Air-Gapped Mesh).\n" +
               "• Decentralized P2P Mesh: End-to-end forward secrecy over BLE & Wi-Fi Direct.\n" +
               "• Cloud Messaging & Encrypted Backups: Multi-device sync with zero tracking."
    }
}
