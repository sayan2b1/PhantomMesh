package com.phantom.mesh.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages custom user aliases / nicknames set by the local user.
 * Example: Custom alias "Sayan Dolai" for username "sayan2b1" displays as "Sayan Dolai (sayan2b1)".
 */
@Singleton
class ContactAliasManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "phantom_contact_aliases"
        private const val KEY_PREFIX_ALIAS = "alias_"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _aliasesFlow = MutableStateFlow<Map<String, String>>(loadAllAliases())
    val aliasesFlow: StateFlow<Map<String, String>> = _aliasesFlow.asStateFlow()

    private fun loadAllAliases(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(KEY_PREFIX_ALIAS) && value is String && value.isNotBlank()) {
                val userKey = key.removePrefix(KEY_PREFIX_ALIAS).lowercase()
                map[userKey] = value
            }
        }
        return map
    }

    fun getAlias(usernameOrId: String): String? {
        val cleanKey = cleanKey(usernameOrId)
        return _aliasesFlow.value[cleanKey] ?: prefs.getString(KEY_PREFIX_ALIAS + cleanKey, null)
    }

    fun setAlias(usernameOrId: String, alias: String) {
        val cleanKey = cleanKey(usernameOrId)
        val cleanAlias = alias.trim()
        if (cleanAlias.isBlank()) {
            clearAlias(usernameOrId)
            return
        }

        prefs.edit().putString(KEY_PREFIX_ALIAS + cleanKey, cleanAlias).commit()
        val updated = _aliasesFlow.value.toMutableMap()
        updated[cleanKey] = cleanAlias
        _aliasesFlow.value = updated
    }

    fun clearAlias(usernameOrId: String) {
        val cleanKey = cleanKey(usernameOrId)
        prefs.edit().remove(KEY_PREFIX_ALIAS + cleanKey).commit()
        val updated = _aliasesFlow.value.toMutableMap()
        updated.remove(cleanKey)
        _aliasesFlow.value = updated
    }

    /**
     * Resolves the display name for a contact.
     * If an alias exists: returns "Custom Name (username)"
     * If no alias exists: returns "username"
     */
    fun getFormattedDisplayName(usernameOrId: String): String {
        val cleanUser = usernameOrId
            .removePrefix("Peer ")
            .removePrefix("Peer-")
            .removePrefix("PEER: ")
            .trim()

        if (cleanUser.isBlank()) return "Encrypted Contact"

        val alias = getAlias(cleanUser)
        return if (!alias.isNullOrBlank() && !alias.equals(cleanUser, ignoreCase = true)) {
            "$alias ($cleanUser)"
        } else {
            cleanUser
        }
    }

    private fun cleanKey(usernameOrId: String): String {
        return usernameOrId
            .removePrefix("Peer ")
            .removePrefix("Peer-")
            .removePrefix("PEER: ")
            .trim()
            .lowercase()
    }
}
