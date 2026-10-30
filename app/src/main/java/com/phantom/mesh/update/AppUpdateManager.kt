package com.phantom.mesh.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import com.phantom.mesh.BuildConfig
import com.phantom.mesh.data.local.preferences.SessionManager
import com.phantom.mesh.network.NetworkStateManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.Serializable
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class ReleaseInfo(
    val tagName: String,
    val title: String,
    val body: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val publishedAt: String? = null
) : Serializable

sealed class UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
    object NoInternet : UpdateCheckResult()
    object Ignored : UpdateCheckResult()
    object Cooldown : UpdateCheckResult()
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class ReadyToInstall(val apkFile: File, val release: ReleaseInfo) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

data class PostInstallInfo(
    val currentVersion: String,
    val releaseTitle: String,
    val changelog: String
)

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val networkStateManager: NetworkStateManager
) {
    companion object {
        private const val TAG = "AppUpdateManager"
        private const val GITHUB_REPO_OWNER = "sayan2b1"
        private const val GITHUB_REPO_NAME = "PhantomMesh"
        private const val LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
        private const val REMIND_LATER_COOLDOWN_MS = 24 * 60 * 60 * 1000L // 24 Hours
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    /**
     * Checks GitHub for a newer published release.
     *
     * @param isManualCheck When true, ignores the 24-hour cooldown and ignored-version flags.
     */
    suspend fun checkForUpdates(isManualCheck: Boolean = false): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (!networkStateManager.isInternetAvailable.value) {
            return@withContext UpdateCheckResult.NoInternet
        }

        try {
            val url = URL(LATEST_RELEASE_API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "PhantomMesh-Android/${BuildConfig.VERSION_NAME}")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                // No releases published yet on this repository
                return@withContext UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME)
            }

            if (responseCode !in 200..299) {
                return@withContext UpdateCheckResult.Error("GitHub server returned HTTP $responseCode")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JsonParser.parseString(responseBody).asJsonObject

            val remoteTag = json.get("tag_name")?.asString?.trim() ?: ""
            val releaseTitle = json.get("name")?.asString?.takeIf { it.isNotBlank() } ?: "PhantomMesh $remoteTag"
            val releaseNotes = json.get("body")?.asString ?: "Bug fixes and performance enhancements."
            val publishedAt = json.get("published_at")?.asString

            val assetsArray = json.getAsJsonArray("assets")
            var apkUrl: String? = null
            var apkSize = 0L

            if (assetsArray != null && assetsArray.size() > 0) {
                for (element in assetsArray) {
                    val asset = element.asJsonObject
                    val name = asset.get("name")?.asString ?: ""
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.get("browser_download_url")?.asString
                        apkSize = asset.get("size")?.asLong ?: 0L
                        break
                    }
                }
                // Fallback to first asset if no file ending in .apk was found
                if (apkUrl == null && assetsArray.size() > 0) {
                    val first = assetsArray[0].asJsonObject
                    apkUrl = first.get("browser_download_url")?.asString
                    apkSize = first.get("size")?.asLong ?: 0L
                }
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val isNewer = isNewerVersion(remoteTag, currentVersion)

            if (!isNewer) {
                return@withContext UpdateCheckResult.UpToDate(currentVersion)
            }

            if (apkUrl.isNullOrBlank()) {
                if (isManualCheck) {
                    return@withContext UpdateCheckResult.Error("A newer release ($remoteTag) exists on GitHub, but no APK installer package was attached.")
                }
                return@withContext UpdateCheckResult.UpToDate(currentVersion)
            }

            // Check if user previously chose to ignore this specific version tag
            if (!isManualCheck && sessionManager.getIgnoredUpdateTag() == remoteTag) {
                return@withContext UpdateCheckResult.Ignored
            }

            // Check if user tapped "Remind me later" within the last 24 hours
            val lastRemind = sessionManager.getLastUpdateRemindMs()
            if (!isManualCheck && lastRemind > 0L && (System.currentTimeMillis() - lastRemind < REMIND_LATER_COOLDOWN_MS)) {
                return@withContext UpdateCheckResult.Cooldown
            }

            val releaseInfo = ReleaseInfo(
                tagName = remoteTag,
                title = releaseTitle,
                body = releaseNotes,
                apkDownloadUrl = apkUrl,
                apkSizeBytes = apkSize,
                publishedAt = publishedAt
            )

            return@withContext UpdateCheckResult.UpdateAvailable(releaseInfo)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Update check failed: ${e.message}", e)
            return@withContext UpdateCheckResult.Error(e.message ?: "Failed to check GitHub releases")
        }
    }

    /**
     * Downloads the APK file from GitHub with redirect handling and live progress tracking.
     */
    suspend fun downloadUpdate(release: ReleaseInfo): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val cleanTag = release.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destinationFile = File(updatesDir, "PhantomMesh_${cleanTag}.apk")

        _downloadState.value = DownloadState.Downloading(
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = release.apkSizeBytes
        )

        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            var currentUrl = release.apkDownloadUrl
            var redirects = 0
            val maxRedirects = 6

            // Follow HTTP redirects (GitHub releases 302-redirect to Amazon S3 buckets)
            while (redirects < maxRedirects) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 25000
                    setRequestProperty("User-Agent", "PhantomMesh-Android/${BuildConfig.VERSION_NAME}")
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    currentUrl = newUrl
                    redirects++
                } else if (status in 200..299) {
                    break
                } else {
                    throw IllegalStateException("GitHub download returned HTTP $status")
                }
            }

            val totalBytes = connection?.contentLength?.toLong()?.takeIf { it > 0 } ?: release.apkSizeBytes
            inputStream = connection?.inputStream ?: throw IllegalStateException("Null download input stream")
            outputStream = FileOutputStream(destinationFile)

            val buffer = ByteArray(16 * 1024)
            var bytesRead: Int
            var totalDownloaded = 0L
            var lastEmittedTime = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalDownloaded += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastEmittedTime > 100 || totalDownloaded == totalBytes) {
                    lastEmittedTime = now
                    val progress = if (totalBytes > 0) {
                        (totalDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    _downloadState.value = DownloadState.Downloading(
                        progress = progress,
                        downloadedBytes = totalDownloaded,
                        totalBytes = totalBytes
                    )
                }
            }

            outputStream.flush()

            if (destinationFile.length() <= 0) {
                throw IllegalStateException("Downloaded APK file is empty")
            }

            // Save changelog so post-install welcome pop-up can read it offline
            sessionManager.saveLatestReleaseInfo(release.tagName, release.title, release.body)

            _downloadState.value = DownloadState.ReadyToInstall(destinationFile, release)
            return@withContext destinationFile
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Download failed: ${e.message}", e)
            if (destinationFile.exists()) destinationFile.delete()
            _downloadState.value = DownloadState.Error(e.message ?: "Download failed")
            throw e
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Triggers the Android package installer for the downloaded APK file.
     * If Unknown Sources permission is missing on Android 8.0+, directs the user to system settings.
     */
    fun triggerInstall(apkFile: File) {
        if (!apkFile.exists() || apkFile.length() <= 0) {
            _downloadState.value = DownloadState.Error("Installer package not found on device")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            }
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed launching package installer: ${e.message}", e)
            _downloadState.value = DownloadState.Error("Could not launch package installer: ${e.message}")
        }
    }

    /**
     * Resets the active download state to Idle.
     */
    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }

    /**
     * Checks if the app was just updated from a previous version.
     * If so, returns the [PostInstallInfo] containing the new version and its release notes.
     */
    fun checkPostInstallWelcome(): PostInstallInfo? {
        val current = BuildConfig.VERSION_NAME
        val lastSeen = sessionManager.getLastSeenAppVersion()

        if (lastSeen.isNullOrBlank()) {
            // Initial cold-start: seed current version so next upgrade is detected
            sessionManager.setLastSeenAppVersion(current)
            return null
        }

        if (lastSeen != current) {
            // Version changed -> User successfully installed the new APK!
            sessionManager.setLastSeenAppVersion(current)
            val savedTitle = sessionManager.getLastSavedReleaseTitle() ?: "PhantomMesh v$current"
            val savedNotes = sessionManager.getLastSavedReleaseNotes()
                ?: "Welcome to the latest version of PhantomMesh with enhanced security, peer mesh resilience, and performance optimizations."
            return PostInstallInfo(
                currentVersion = current,
                releaseTitle = savedTitle,
                changelog = savedNotes
            )
        }

        return null
    }

    /**
     * Semantic version comparison supporting arbitrary dot-separated integers (e.g. 3.0.0 vs 3.1.0).
     */
    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

        val remoteParts = cleanRemote.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }

        if (remoteParts.isEmpty() || currentParts.isEmpty()) {
            return cleanRemote > cleanCurrent
        }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}

