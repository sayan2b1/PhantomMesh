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
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val duressWipeManager: DuressWipeManager,
    private val sessionManager: SessionManager
) : ViewModel() {

    val username: StateFlow<String?> = sessionManager.usernameFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isDuress: StateFlow<Boolean> = sessionManager.isDuressFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val isDuressSession: StateFlow<Boolean> get() = isDuress

    val isBluetoothOnlyMode: StateFlow<Boolean> = sessionManager.bluetoothOnlyModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBluetoothOnlyMode(enabled: Boolean) {
        viewModelScope.launch {
            sessionManager.setBluetoothOnlyMode(enabled)
            _statusMessage.value = if (enabled)
                "Bluetooth-only mode active: Wi-Fi disruptive upgrades blocked."
            else
                "Adaptive mesh active: Wi-Fi direct & Bluetooth both enabled."
        }
    }

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

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
}
