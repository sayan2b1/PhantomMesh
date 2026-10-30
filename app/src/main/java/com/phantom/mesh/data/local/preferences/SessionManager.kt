package com.phantom.mesh.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.phantom.mesh.model.ConversationMode
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
        private const val KEY_CONVERSATION_MODE = "session_conversation_mode"
        private const val KEY_CHAT_WALLPAPER_PATH = "session_chat_wallpaper_path"
        private const val KEY_AUTO_BACKUP_FREQUENCY = "session_auto_backup_frequency"
        private const val KEY_LAST_BACKUP_TIMESTAMP = "session_last_backup_timestamp"
        private const val KEY_LAST_BACKUP_SIZE = "session_last_backup_size"
        private const val KEY_LAST_BACKUP_MESSAGE_COUNT = "session_last_backup_message_count"
        private const val KEY_LAST_BACKUP_PATH = "session_last_backup_path"
        private const val KEY_READ_RECEIPTS_ENABLED = "session_read_receipts_enabled"
        private const val KEY_SENDER_BUBBLE_COLOR = "session_sender_bubble_color"
        private const val KEY_RECEIVER_BUBBLE_COLOR = "session_receiver_bubble_color"
        private const val KEY_CHAT_FONT_STYLE = "session_chat_font_style"
        private const val KEY_CHAT_FONT_SIZE_SCALE = "session_chat_font_size_scale"
        private const val KEY_PREFERRED_REFRESH_RATE = "session_preferred_refresh_rate"
        private const val KEY_BLOCKED_USERS = "session_blocked_users"
        private const val KEY_DND_ENABLED = "session_dnd_enabled"

        // ── OTA Software Updates ───────────────────────────────────
        private const val KEY_IGNORED_UPDATE_TAG = "session_ignored_update_tag"
        private const val KEY_LAST_UPDATE_REMIND_MS = "session_last_update_remind_ms"
        private const val KEY_LAST_SEEN_APP_VERSION = "session_last_seen_app_version"
        private const val KEY_LAST_SAVED_RELEASE_NOTES = "session_last_saved_release_notes"
        private const val KEY_LAST_SAVED_RELEASE_TITLE = "session_last_saved_release_title"
        private const val KEY_LAST_SAVED_RELEASE_TAG = "session_last_saved_release_tag"

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

    private val _conversationModeFlow = MutableStateFlow(
        ConversationMode.fromString(prefs.getString(KEY_CONVERSATION_MODE, ConversationMode.HYBRID.name))
    )
    val conversationModeFlow: StateFlow<ConversationMode> = _conversationModeFlow.asStateFlow()

    private val _chatWallpaperFlow = MutableStateFlow(prefs.getString(KEY_CHAT_WALLPAPER_PATH, null))
    val chatWallpaperFlow: StateFlow<String?> = _chatWallpaperFlow.asStateFlow()

    private val _autoBackupFrequencyFlow = MutableStateFlow(prefs.getString(KEY_AUTO_BACKUP_FREQUENCY, "MANUAL") ?: "MANUAL")
    val autoBackupFrequencyFlow: StateFlow<String> = _autoBackupFrequencyFlow.asStateFlow()

    private val _lastBackupTimestampFlow = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L))
    val lastBackupTimestampFlow: StateFlow<Long> = _lastBackupTimestampFlow.asStateFlow()

    private val _lastBackupSizeFlow = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP_SIZE, 0L))
    val lastBackupSizeFlow: StateFlow<Long> = _lastBackupSizeFlow.asStateFlow()

    private val _lastBackupMessageCountFlow = MutableStateFlow(prefs.getInt(KEY_LAST_BACKUP_MESSAGE_COUNT, 0))
    val lastBackupMessageCountFlow: StateFlow<Int> = _lastBackupMessageCountFlow.asStateFlow()

    private val _readReceiptsEnabledFlow = MutableStateFlow(prefs.getBoolean(KEY_READ_RECEIPTS_ENABLED, true))
    val readReceiptsEnabledFlow: StateFlow<Boolean> = _readReceiptsEnabledFlow.asStateFlow()

    private val _senderBubbleColorFlow = MutableStateFlow(prefs.getString(KEY_SENDER_BUBBLE_COLOR, "#0D3B3F") ?: "#0D3B3F")
    val senderBubbleColorFlow: StateFlow<String> = _senderBubbleColorFlow.asStateFlow()

    private val _receiverBubbleColorFlow = MutableStateFlow(prefs.getString(KEY_RECEIVER_BUBBLE_COLOR, "#161B22") ?: "#161B22")
    val receiverBubbleColorFlow: StateFlow<String> = _receiverBubbleColorFlow.asStateFlow()

    private val _chatFontStyleFlow = MutableStateFlow(prefs.getString(KEY_CHAT_FONT_STYLE, "DEVICE") ?: "DEVICE")
    val chatFontStyleFlow: StateFlow<String> = _chatFontStyleFlow.asStateFlow()

    private val _chatFontSizeScaleFlow = MutableStateFlow(prefs.getString(KEY_CHAT_FONT_SIZE_SCALE, "MEDIUM") ?: "MEDIUM")
    val chatFontSizeScaleFlow: StateFlow<String> = _chatFontSizeScaleFlow.asStateFlow()

    private val _preferredRefreshRateFlow = MutableStateFlow(prefs.getInt(KEY_PREFERRED_REFRESH_RATE, 0))
    val preferredRefreshRateFlow: StateFlow<Int> = _preferredRefreshRateFlow.asStateFlow()

    private val _blockedUsersFlow = MutableStateFlow<Set<String>>(
        prefs.getStringSet(KEY_BLOCKED_USERS, emptySet())?.toSet() ?: emptySet()
    )
    val blockedUsersFlow: StateFlow<Set<String>> = _blockedUsersFlow.asStateFlow()

    private val _dndEnabledFlow = MutableStateFlow(prefs.getBoolean(KEY_DND_ENABLED, false))
    val dndEnabledFlow: StateFlow<Boolean> = _dndEnabledFlow.asStateFlow()

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

    fun updateUsername(newUsername: String) {
        prefs.edit().putString(KEY_USERNAME, newUsername).commit()
        _usernameFlow.value = newUsername
    }
    fun getCustomToken(): String? {
        val token = prefs.getString(KEY_CUSTOM_TOKEN, null)
        return if (token.isNullOrBlank()) null else token
    }
    fun isDuress(): Boolean = prefs.getBoolean(KEY_IS_DURESS, false)

    fun getConversationMode(): ConversationMode {
        return ConversationMode.fromString(prefs.getString(KEY_CONVERSATION_MODE, ConversationMode.HYBRID.name))
    }

    fun setConversationMode(mode: ConversationMode) {
        prefs.edit()
            .putString(KEY_CONVERSATION_MODE, mode.name)
            .putBoolean(KEY_BLUETOOTH_ONLY_MODE, mode == ConversationMode.OFFLINE)
            .commit()
        _conversationModeFlow.value = mode
        _bluetoothOnlyModeFlow.value = (mode == ConversationMode.OFFLINE)
    }

    fun isBluetoothOnlyMode(): Boolean = getConversationMode() == ConversationMode.OFFLINE

    fun setBluetoothOnlyMode(enabled: Boolean) {
        setConversationMode(if (enabled) ConversationMode.OFFLINE else ConversationMode.HYBRID)
    }

    fun getChatWallpaperPath(): String? = prefs.getString(KEY_CHAT_WALLPAPER_PATH, null)

    fun setChatWallpaperPath(path: String?) {
        if (path == null) {
            prefs.edit().remove(KEY_CHAT_WALLPAPER_PATH).commit()
        } else {
            prefs.edit().putString(KEY_CHAT_WALLPAPER_PATH, path).commit()
        }
        _chatWallpaperFlow.value = path
    }

    fun getAutoBackupFrequency(): String = prefs.getString(KEY_AUTO_BACKUP_FREQUENCY, "MANUAL") ?: "MANUAL"

    fun setAutoBackupFrequency(frequency: String) {
        prefs.edit().putString(KEY_AUTO_BACKUP_FREQUENCY, frequency).commit()
        _autoBackupFrequencyFlow.value = frequency
    }

    fun recordBackupSuccess(timestamp: Long, sizeBytes: Long, messageCount: Int, path: String) {
        prefs.edit()
            .putLong(KEY_LAST_BACKUP_TIMESTAMP, timestamp)
            .putLong(KEY_LAST_BACKUP_SIZE, sizeBytes)
            .putInt(KEY_LAST_BACKUP_MESSAGE_COUNT, messageCount)
            .putString(KEY_LAST_BACKUP_PATH, path)
            .commit()

        _lastBackupTimestampFlow.value = timestamp
        _lastBackupSizeFlow.value = sizeBytes
        _lastBackupMessageCountFlow.value = messageCount
    }

    fun getLastBackupTimestamp(): Long = prefs.getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L)
    fun getLastBackupSize(): Long = prefs.getLong(KEY_LAST_BACKUP_SIZE, 0L)
    fun getLastBackupMessageCount(): Int = prefs.getInt(KEY_LAST_BACKUP_MESSAGE_COUNT, 0)
    fun getLastBackupPath(): String? = prefs.getString(KEY_LAST_BACKUP_PATH, null)

    fun isReadReceiptsEnabled(): Boolean = prefs.getBoolean(KEY_READ_RECEIPTS_ENABLED, true)
    fun setReadReceiptsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_READ_RECEIPTS_ENABLED, enabled).commit()
        _readReceiptsEnabledFlow.value = enabled
    }

    fun getSenderBubbleColor(): String = prefs.getString(KEY_SENDER_BUBBLE_COLOR, "#0D3B3F") ?: "#0D3B3F"
    fun setSenderBubbleColor(colorHex: String) {
        prefs.edit().putString(KEY_SENDER_BUBBLE_COLOR, colorHex).commit()
        _senderBubbleColorFlow.value = colorHex
    }

    fun getReceiverBubbleColor(): String = prefs.getString(KEY_RECEIVER_BUBBLE_COLOR, "#161B22") ?: "#161B22"
    fun setReceiverBubbleColor(colorHex: String) {
        prefs.edit().putString(KEY_RECEIVER_BUBBLE_COLOR, colorHex).commit()
        _receiverBubbleColorFlow.value = colorHex
    }

    fun getChatFontStyle(): String = prefs.getString(KEY_CHAT_FONT_STYLE, "DEVICE") ?: "DEVICE"
    fun setChatFontStyle(style: String) {
        prefs.edit().putString(KEY_CHAT_FONT_STYLE, style).commit()
        _chatFontStyleFlow.value = style
    }

    fun getChatFontSizeScale(): String = prefs.getString(KEY_CHAT_FONT_SIZE_SCALE, "MEDIUM") ?: "MEDIUM"
    fun setChatFontSizeScale(scale: String) {
        prefs.edit().putString(KEY_CHAT_FONT_SIZE_SCALE, scale).commit()
        _chatFontSizeScaleFlow.value = scale
    }

    fun getPreferredRefreshRate(): Int = prefs.getInt(KEY_PREFERRED_REFRESH_RATE, 0)
    fun setPreferredRefreshRate(rate: Int) {
        prefs.edit().putInt(KEY_PREFERRED_REFRESH_RATE, rate).commit()
        _preferredRefreshRateFlow.value = rate
    }

    // ── BLOCKED USERS MANAGEMENT ──────────────────────────────
    private fun cleanBlockTarget(target: String): String {
        return target.lowercase().trim()
            .removePrefix("peer ")
            .removePrefix("peer-")
            .removePrefix("peer: ")
            .trim()
    }

    fun getBlockedUsers(): Set<String> {
        return prefs.getStringSet(KEY_BLOCKED_USERS, emptySet())?.toSet() ?: emptySet()
    }

    fun isUserBlocked(usernameOrId: String?): Boolean {
        if (usernameOrId.isNullOrBlank()) return false
        val clean = cleanBlockTarget(usernameOrId)
        val currentSet = getBlockedUsers()
        return currentSet.any { cleanBlockTarget(it) == clean }
    }

    fun blockUser(usernameOrId: String) {
        if (usernameOrId.isBlank()) return
        val clean = cleanBlockTarget(usernameOrId)
        val current = getBlockedUsers().toMutableSet()
        current.add(clean)
        prefs.edit().putStringSet(KEY_BLOCKED_USERS, current).commit()
        _blockedUsersFlow.value = current
    }

    fun unblockUser(usernameOrId: String) {
        if (usernameOrId.isBlank()) return
        val clean = cleanBlockTarget(usernameOrId)
        val current = getBlockedUsers().toMutableSet()
        current.removeAll { cleanBlockTarget(it) == clean }
        prefs.edit().putStringSet(KEY_BLOCKED_USERS, current).commit()
        _blockedUsersFlow.value = current
    }

    // ── DO NOT DISTURB (DND) MODE ─────────────────────────────
    fun isDndEnabled(): Boolean = prefs.getBoolean(KEY_DND_ENABLED, false)

    fun setDndEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DND_ENABLED, enabled).commit()
        _dndEnabledFlow.value = enabled
    }

    // ── OTA SOFTWARE UPDATES ──────────────────────────────────
    fun getIgnoredUpdateTag(): String? = prefs.getString(KEY_IGNORED_UPDATE_TAG, null)
    fun setIgnoredUpdateTag(tag: String) {
        prefs.edit().putString(KEY_IGNORED_UPDATE_TAG, tag).commit()
    }

    fun getLastUpdateRemindMs(): Long = prefs.getLong(KEY_LAST_UPDATE_REMIND_MS, 0L)
    fun setLastUpdateRemindMs(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_UPDATE_REMIND_MS, timestamp).commit()
    }

    fun getLastSeenAppVersion(): String? = prefs.getString(KEY_LAST_SEEN_APP_VERSION, null)
    fun setLastSeenAppVersion(version: String) {
        prefs.edit().putString(KEY_LAST_SEEN_APP_VERSION, version).commit()
    }

    fun saveLatestReleaseInfo(tag: String, title: String, notes: String) {
        prefs.edit()
            .putString(KEY_LAST_SAVED_RELEASE_TAG, tag)
            .putString(KEY_LAST_SAVED_RELEASE_TITLE, title)
            .putString(KEY_LAST_SAVED_RELEASE_NOTES, notes)
            .commit()
    }

    fun getLastSavedReleaseNotes(): String? = prefs.getString(KEY_LAST_SAVED_RELEASE_NOTES, null)
    fun getLastSavedReleaseTitle(): String? = prefs.getString(KEY_LAST_SAVED_RELEASE_TITLE, null)
    fun getLastSavedReleaseTag(): String? = prefs.getString(KEY_LAST_SAVED_RELEASE_TAG, null)

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
