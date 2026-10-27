package com.phantom.mesh.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phantom.mesh.data.repository.AuthRepository
import com.phantom.mesh.data.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isRegisterMode: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val isDuressTriggered: Boolean = false,
    val isForgotPwDialogVisible: Boolean = false,
    val recoveryUsername: String = "",
    val recoveryQuestion: String? = null,
    val recoveryLoading: Boolean = false,
    val recoveryError: String? = null,
    val recoverySuccessMessage: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            isRegisterMode = !_uiState.value.isRegisterMode,
            errorMessage = null
        )
    }

    fun submit(
        username: String,
        password: String,
        decoyPassword: String? = null,
        securityQuestion: String? = null,
        securityAnswer: String? = null
    ) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Username and password required")
            return
        }

        val alphanumericRegex = "^[a-zA-Z0-9]+$".toRegex()
        if (!username.matches(alphanumericRegex)) {
            _uiState.value = _uiState.value.copy(errorMessage = "Username must be alphanumeric only")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = if (_uiState.value.isRegisterMode) {
                authRepository.register(
                    username = username,
                    password = password,
                    decoyPassword = decoyPassword,
                    securityQuestion = securityQuestion,
                    securityAnswer = securityAnswer
                )
            } else {
                authRepository.login(username, password)
            }

            when (result) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = true,
                        isDuressTriggered = result.isDuress
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    // ── FORGOT PASSWORD RECOVERY ─────────────────────────────────────────────

    fun openForgotPassword() {
        _uiState.value = _uiState.value.copy(
            isForgotPwDialogVisible = true,
            recoveryUsername = "",
            recoveryQuestion = null,
            recoveryError = null,
            recoverySuccessMessage = null
        )
    }

    fun closeForgotPassword() {
        _uiState.value = _uiState.value.copy(isForgotPwDialogVisible = false)
    }

    fun fetchRecoveryQuestion(username: String) {
        if (username.isBlank()) {
            _uiState.value = _uiState.value.copy(recoveryError = "Please enter your username")
            return
        }
        _uiState.value = _uiState.value.copy(recoveryLoading = true, recoveryError = null)
        viewModelScope.launch {
            val question = authRepository.getSecurityQuestionForUsername(username)
            if (question.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    recoveryLoading = false,
                    recoveryError = "No security question configured for @$username"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    recoveryLoading = false,
                    recoveryUsername = username,
                    recoveryQuestion = question,
                    recoveryError = null
                )
            }
        }
    }

    fun resetPassword(username: String, answer: String, newPassword: String) {
        if (answer.isBlank()) {
            _uiState.value = _uiState.value.copy(recoveryError = "Please answer the security question")
            return
        }
        if (newPassword.length < 6) {
            _uiState.value = _uiState.value.copy(recoveryError = "New password must be at least 6 characters")
            return
        }
        _uiState.value = _uiState.value.copy(recoveryLoading = true, recoveryError = null)
        viewModelScope.launch {
            val result = authRepository.resetPasswordWithSecurityAnswer(username, answer, newPassword)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        recoveryLoading = false,
                        recoverySuccessMessage = "Password successfully reset! Logging in...",
                        isSuccess = true
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        recoveryLoading = false,
                        recoveryError = error.message ?: "Failed to reset password"
                    )
                }
            )
        }
    }
}
