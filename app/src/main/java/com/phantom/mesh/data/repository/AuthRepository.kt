package com.phantom.mesh.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.phantom.mesh.data.local.DuressWipeManager
import com.phantom.mesh.data.local.dao.UserDao
import com.phantom.mesh.data.local.entity.UserEntity
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.mesh.MeshConnectionManager
import com.phantom.mesh.network.NetworkStateManager
import com.phantom.mesh.util.CryptoUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data class Success(val userId: String, val username: String, val isDuress: Boolean = false) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

data class AuthCloudRequest(
    val username: String,
    val password: String,
    val action: String, // "register", "login", "change_password"
    val newPassword: String? = null
)

data class AuthCloudResponse(
    val success: Boolean,
    val token: String?,
    @SerializedName("userId") val userId: String?,
    val error: String?
)

@Singleton
class AuthRepository @Inject constructor(
    private val userDao: UserDao,
    private val duressWipeManager: DuressWipeManager,
    private val sessionManager: SessionManager,
    private val networkStateManager: NetworkStateManager,
    private val meshConnectionManager: MeshConnectionManager
) {
    // Replace with your project's Firebase Cloud Function endpoint or emulator
    private val cloudFunctionUrl = "https://us-central1-phantom-msg-mesh.cloudfunctions.net/auth"
    private val gson = Gson()
    private val firebaseAuth = FirebaseAuth.getInstance()

    /**
     * Authenticates the user with username and password.
     *
     * 1. Checks for DURESS login first: If password matches stored decoy hash,
     *    nukes the entire local database and logs into an empty dummy state.
     * 2. If online: Authenticates via Cloud Function -> receives Custom Token -> calls signInWithCustomToken().
     * 3. If offline: Falls back to locally stored password hash in Room DB.
     */
    suspend fun login(username: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim().lowercase()

        // ── 1. DURESS / DECOY PASSWORD INTERCEPTION ────────────────────────
        val localUser = userDao.getUserByUsername(cleanUsername)
        if (localUser != null && localUser.decoyPasswordHash != null) {
            val isDecoyMatch = CryptoUtil.verifyPassword(password, localUser.decoyPasswordHash)
            if (isDecoyMatch) {
                // EMERGENCY TRIGGER: Destroy all local messages, chats, media, and cached credentials
                duressWipeManager.executeWipe()

                // Spawn dummy user session
                val dummyUserId = "dummy_${UUID.randomUUID().toString().take(8)}"
                sessionManager.saveSession(dummyUserId, "Anonymous", null, isDuress = true)
                meshConnectionManager.setLocalIdentity(dummyUserId, "Decoy Node")

                return@withContext AuthResult.Success(
                    userId = dummyUserId,
                    username = "Anonymous",
                    isDuress = true
                )
            }
        }

        // ── 2. ONLINE LOGIN (CLOUD FUNCTION + FIREBASE CUSTOM TOKEN) ────────
        if (networkStateManager.isInternetAvailable.value) {
            try {
                val cloudResponse = callCloudAuth(cleanUsername, password, "login")
                if (cloudResponse.success && cloudResponse.token != null && cloudResponse.userId != null) {
                    // Sign in to Firebase Auth with the custom token for Firestore Security Rules
                    firebaseAuth.signInWithCustomToken(cloudResponse.token).await()

                    // Hash password locally for offline capability
                    val localHash = CryptoUtil.hashPassword(password)
                    val updatedUser = UserEntity(
                        userId = cloudResponse.userId,
                        username = cleanUsername,
                        passwordHash = localHash,
                        decoyPasswordHash = localUser?.decoyPasswordHash,
                        displayName = cleanUsername,
                        firebaseToken = cloudResponse.token,
                        lastActive = System.currentTimeMillis()
                    )
                    userDao.insertUser(updatedUser)

                    sessionManager.saveSession(cloudResponse.userId, cleanUsername, cloudResponse.token, isDuress = false)
                    meshConnectionManager.setLocalIdentity(cloudResponse.userId, cleanUsername)
                    meshConnectionManager.startMesh()

                    return@withContext AuthResult.Success(cloudResponse.userId, cleanUsername, isDuress = false)
                } else {
                    return@withContext AuthResult.Error(cloudResponse.error ?: "Authentication failed")
                }
            } catch (e: Exception) {
                // If cloud request failed (e.g. timeout), fall through to offline local check
            }
        }

        // ── 3. OFFLINE LOGIN FALLBACK ─────────────────────────────────────
        if (localUser != null) {
            val passwordValid = CryptoUtil.verifyPassword(password, localUser.passwordHash)
            if (passwordValid) {
                userDao.updateLastActive(localUser.userId)
                sessionManager.saveSession(localUser.userId, localUser.username, localUser.firebaseToken, isDuress = false)
                meshConnectionManager.setLocalIdentity(localUser.userId, localUser.username)
                meshConnectionManager.startMesh()
                return@withContext AuthResult.Success(localUser.userId, localUser.username, isDuress = false)
            }
        }

        return@withContext AuthResult.Error("Invalid credentials or user not cached locally while offline")
    }

    /**
     * Registers a new anonymous user identity.
     */
    suspend fun register(username: String, password: String, decoyPassword: String? = null): AuthResult = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim().lowercase()
        val isOnline = networkStateManager.isInternetAvailable.value

        val decoyHash = if (!decoyPassword.isNullOrBlank()) {
            CryptoUtil.hashPassword(decoyPassword)
        } else null

        if (isOnline) {
            try {
                val cloudResponse = callCloudAuth(cleanUsername, password, "register")
                if (cloudResponse.success && cloudResponse.token != null && cloudResponse.userId != null) {
                    firebaseAuth.signInWithCustomToken(cloudResponse.token).await()

                    val localHash = CryptoUtil.hashPassword(password)
                    val newUser = UserEntity(
                        userId = cloudResponse.userId,
                        username = cleanUsername,
                        passwordHash = localHash,
                        decoyPasswordHash = decoyHash,
                        displayName = cleanUsername,
                        firebaseToken = cloudResponse.token,
                        lastActive = System.currentTimeMillis()
                    )
                    userDao.insertUser(newUser)
                    sessionManager.saveSession(cloudResponse.userId, cleanUsername, cloudResponse.token, isDuress = false)
                    meshConnectionManager.setLocalIdentity(cloudResponse.userId, cleanUsername)
                    meshConnectionManager.startMesh()

                    return@withContext AuthResult.Success(cloudResponse.userId, cleanUsername, isDuress = false)
                } else {
                    return@withContext AuthResult.Error(cloudResponse.error ?: "Registration failed")
                }
            } catch (e: Exception) {
                return@withContext AuthResult.Error("Cloud connection error: ${e.localizedMessage}")
            }
        } else {
            // Local offline identity generation if no network is available
            val offlineUserId = "user_${UUID.randomUUID().toString().take(12)}"
            val localHash = CryptoUtil.hashPassword(password)
            val newUser = UserEntity(
                userId = offlineUserId,
                username = cleanUsername,
                passwordHash = localHash,
                decoyPasswordHash = decoyHash,
                displayName = cleanUsername,
                firebaseToken = null,
                lastActive = System.currentTimeMillis()
            )
            userDao.insertUser(newUser)
            sessionManager.saveSession(offlineUserId, cleanUsername, null, isDuress = false)
            meshConnectionManager.setLocalIdentity(offlineUserId, cleanUsername)
            meshConnectionManager.startMesh()

            return@withContext AuthResult.Success(offlineUserId, cleanUsername, isDuress = false)
        }
    }

    /**
     * Configures or updates the decoy duress passphrase for the current active user.
     */
    suspend fun setDecoyPassword(decoyPassword: String): Boolean = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext false
        val decoyHash = CryptoUtil.hashPassword(decoyPassword)
        userDao.setDecoyPassword(userId, decoyHash)
        true
    }

    /**
     * Changes the current authenticated user's password.
     * Updates locally and pushes to cloud if online.
     */
    suspend fun changePassword(oldPassword: String, newPassword: String): Result<String> = withContext(Dispatchers.IO) {
        val currentUsername = sessionManager.getUsername()
            ?: return@withContext Result.failure(Exception("No active user session found"))
        val currentUserId = sessionManager.getUserId()
            ?: return@withContext Result.failure(Exception("No active user ID found"))

        if (newPassword.length < 8) {
            return@withContext Result.failure(Exception("New password must be at least 8 characters"))
        }

        val cleanUsername = currentUsername.trim().lowercase()
        val localUser = userDao.getUserByUsername(cleanUsername)

        // Verify old password locally
        if (localUser != null) {
            val matches = CryptoUtil.verifyPassword(oldPassword, localUser.passwordHash)
            if (!matches) {
                return@withContext Result.failure(Exception("Current password does not match"))
            }
        }

        // If online, update Cloud Function
        if (networkStateManager.isInternetAvailable.value) {
            try {
                val cloudResponse = callCloudAuth(cleanUsername, oldPassword, "change_password", newPassword)
                if (!cloudResponse.success) {
                    return@withContext Result.failure(Exception(cloudResponse.error ?: "Cloud password update rejected"))
                }
            } catch (e: Exception) {
                android.util.Log.w("AuthRepository", "Cloud change_password call failed: ${e.message}")
            }
        }

        // Update local database
        val newHash = CryptoUtil.hashPassword(newPassword)
        userDao.updatePasswordHash(currentUserId, newHash)

        return@withContext Result.success("Password changed successfully")
    }

    /**
     * Changes the current authenticated user's username.
     * Updates locally, re-syncs mesh identity, and pushes to cloud if online.
     */
    suspend fun changeUsername(newUsername: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanNewUsername = newUsername.trim().lowercase()
        if (cleanNewUsername.length < 3) {
            return@withContext Result.failure(Exception("Username must be at least 3 characters"))
        }
        if (!cleanNewUsername.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            return@withContext Result.failure(Exception("Username can only contain letters, numbers, and underscores"))
        }

        val currentUserId = sessionManager.getUserId()
            ?: return@withContext Result.failure(Exception("No active user session found"))
        val currentUsername = sessionManager.getUsername() ?: ""

        if (cleanNewUsername == currentUsername.lowercase()) {
            return@withContext Result.failure(Exception("New username must be different from current username"))
        }

        // Check if another local user profile already has this username
        val existing = userDao.getUserByUsername(cleanNewUsername)
        if (existing != null && existing.userId != currentUserId) {
            return@withContext Result.failure(Exception("Username '$cleanNewUsername' is already taken"))
        }

        // If online, update Cloud / Firebase Auth
        if (networkStateManager.isInternetAvailable.value) {
            try {
                callCloudAuth(currentUsername, "", "change_username", cleanNewUsername)
            } catch (e: Exception) {
                android.util.Log.w("AuthRepository", "Cloud username call note: ${e.message}")
            }

            try {
                firebaseAuth.currentUser?.let { user ->
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(cleanNewUsername)
                        .build()
                    user.updateProfile(profileUpdates).await()
                }
            } catch (e: Exception) {
                android.util.Log.w("AuthRepository", "Firebase display name update note: ${e.message}")
            }
        }

        // Update local database & active session
        userDao.updateUsername(currentUserId, cleanNewUsername)
        sessionManager.updateUsername(cleanNewUsername)
        meshConnectionManager.setLocalIdentity(currentUserId, cleanNewUsername)

        return@withContext Result.success("Username updated to $cleanNewUsername")
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        firebaseAuth.signOut()
        sessionManager.clearSession()
        meshConnectionManager.stopMesh()
    }

    private fun callCloudAuth(username: String, password: String, action: String, newPassword: String? = null): AuthCloudResponse {
        val url = URL(cloudFunctionUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.doOutput = true

        val requestBody = gson.toJson(AuthCloudRequest(username, password, action, newPassword))
        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(requestBody)
            writer.flush()
        }

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream?.bufferedReader()?.use { it.readText() } ?: "{}"

        return try {
            gson.fromJson(responseText, AuthCloudResponse::class.java)
        } catch (e: Exception) {
            AuthCloudResponse(false, null, null, "Server responded with status $responseCode")
        }
    }
}
