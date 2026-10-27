package com.phantom.mesh.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.util.Locale

data class AudioTrackItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long
)

object AudioMediaHelper {
    private const val TAG = "AudioMediaHelper"

    /**
     * Queries device storage via MediaStore for all available audio tracks, songs, and voice recordings.
     */
    fun queryDeviceAudioFiles(context: Context): List<AudioTrackItem> {
        val audioList = mutableListOf<AudioTrackItem>()
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )

        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val displayCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = if (titleCol >= 0) cursor.getString(titleCol) ?: "" else ""
                    val displayName = if (displayCol >= 0) cursor.getString(displayCol) ?: "" else ""
                    val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val cleanTitle = when {
                        displayName.isNotBlank() -> displayName
                        title.isNotBlank() -> title
                        else -> "Audio Track"
                    }

                    // Exclude zero-byte corrupted files
                    if (size > 0 || duration > 0) {
                        audioList.add(
                            AudioTrackItem(
                                id = id,
                                uri = contentUri,
                                title = cleanTitle,
                                durationMs = duration,
                                sizeBytes = size
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query audio media from device", e)
        }

        return audioList
    }

    fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000).coerceAtLeast(0)
        val mins = seconds / 60
        val remSecs = seconds % 60
        return String.format(Locale.US, "%02d:%02d", mins, remSecs)
    }

    fun formatFileSize(sizeBytes: Long): String {
        return when {
            sizeBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", sizeBytes.toFloat() / (1024 * 1024))
            sizeBytes >= 1024 -> String.format(Locale.US, "%d kB", sizeBytes / 1024)
            sizeBytes > 0 -> "$sizeBytes B"
            else -> "0 B"
        }
    }
}
