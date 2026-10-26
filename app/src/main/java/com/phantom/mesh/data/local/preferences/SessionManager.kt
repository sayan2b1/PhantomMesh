package com.phantom.mesh.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages active user session, token storage, and persistent 24-hour validity.
 * Backed by synchronous SharedPreferences for 100% reliable instant cold-start restoration.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "phantom_session_prefs"
        private const val KEY_USER_ID = "session_user_id"
        private const val KEY_USERNAME = "session_username"
        private const val KEY_CUSTOM_TOKEN = "session_custom_token"
        private const val KEY_IS_DURESS = "session_is_duress"
        private const val KEY_IS_LOGGED_IN = "session_is_logged_in"
        private const val KEY_LOGIN_TIMESTAMP = "session_login_timestamp"
        private const val KEY_BLUETOOTH_ONLY_MODE = "session_prefer_bluetooth_only"

        /** 24 Hours in milliseconds */
        const val SESSION_VALIDITY_DURATION_MS = 24 * 60 * 60 * 1000L
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _userIdFlow = MutableStateFlow(prefs.getString(KEY_USER_ID, null))
    val userIdFlow: StateFlow<String?> = _userIdFlow.asStateFlow()

    private val _usernameFlow = MutableStateFlow(prefs.getString(KEY_USERNAME, null))
    val usernameFlow: StateFlow<String?> = _usernameFlow.asStateFlow()

    private val _isDuressFlow = MutableStateFlow(prefs.getBoolean(KEY_IS_DURESS, false))
    val isDuressFlow: StateFlow<Boolean> = _isDuressFlow.asStateFlow()

    private val _isLoggedInFlow = MutableStateFlow(prefs.getBoolean(KEY_IS_LOGGED_IN, false))
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

    private val _bluetoothOnlyModeFlow = MutableStateFlow(prefs.getBoolean(KEY_BLUETOOTH_ONLY_MODE, false))
    val bluetoothOnlyModeFlow: StateFlow<Boolean> = _bluetoothOnlyModeFlow.asStateFlow()

    /**
     * Persists session credentials immediately and stamps the current time.
     */
    fun saveSession(userId: String, username: String, token: String?, isDuress: Boolean = false) {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .putString(KEY_CUSTOM_TOKEN, token ?: "")
            .putBoolean(KEY_IS_DURESS, isDuress)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putLong(KEY_LOGIN_TIMESTAMP, now)
            .commit() // Synchronous flush to disk

        _userIdFlow.value = userId
        _usernameFlow.value = username
        _isDuressFlow.value = isDuress
        _isLoggedInFlow.value = true
    }

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun getCustomToken(): String? {
        val token = prefs.getString(KEY_CUSTOM_TOKEN, null)
        return if (token.isNullOrBlank()) null else token
    }
    fun isDuress(): Boolean = prefs.getBoolean(KEY_IS_DURESS, false)
    fun isBluetoothOnlyMode(): Boolean = prefs.getBoolean(KEY_BLUETOOTH_ONLY_MODE, false)

    fun setBluetoothOnlyMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLUETOOTH_ONLY_MODE, enabled).commit()
        _bluetoothOnlyModeFlow.value = enabled
    }

    /**
     * Checks if the stored session is valid and has not expired (within 24 hours).
     * Returns true if valid; clears credentials and returns false if expired or in duress mode.
     */
    fun isSessionValid(): Boolean {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val isDuress = prefs.getBoolean(KEY_IS_DURESS, false)
        val loginTime = prefs.getLong(KEY_LOGIN_TIMESTAMP, 0L)

        if (!isLoggedIn || isDuress || loginTime <= 0L) {
            return false
        }

        val elapsed = System.currentTimeMillis() - loginTime
        val isValid = elapsed in 0 until SESSION_VALIDITY_DURATION_MS
        if (!isValid) {
            clearSession()
            return false
        }
        return true
    }

    /**
     * Completely wipes session credentials and signs out of Firebase Auth.
     */
    fun clearSession() {
        prefs.edit().clear().commit()

        _userIdFlow.value = null
        _usernameFlow.value = null
        _isDuressFlow.value = false
        _isLoggedInFlow.value = false

        try {
            FirebaseAuth.getInstance().signOut()
        } catch (ignored: Exception) {}
    }
}
