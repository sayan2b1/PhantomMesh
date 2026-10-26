package com.phantom.mesh.util

import android.bluetooth.BluetoothManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.location.LocationManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.location.LocationManagerCompat
import java.io.File
import java.io.FileOutputStream

object HardwareStateUtil {

    /**
     * Checks if device Bluetooth adapter is currently powered ON.
     */
    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothManager?.adapter?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if device Location services (GPS/network) are currently enabled in system settings.
     * Android Nearby Connections BLE discovery strictly requires Location to be ON.
     */
    fun isLocationEnabled(context: Context): Boolean {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager.isLocationEnabled
            } else {
                LocationManagerCompat.isLocationEnabled(locationManager)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launch system Bluetooth settings so user can toggle it on.
     */
    fun openBluetoothSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * Launch system Location settings so user can toggle GPS on.
     */
    fun openLocationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * Checks if device Wi-Fi radio is currently powered ON.
     * Note: Does not require active internet or connection to a router.
     * Google Nearby Connections P2P_CLUSTER uses Wi-Fi Direct which requires Wi-Fi radio to be ON.
     */
    fun isWifiEnabled(context: Context): Boolean {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            wifiManager?.isWifiEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launch system Wi-Fi settings so user can toggle it on.
     */
    fun openWifiSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * Saves an image [Bitmap] directly to the user's public device Gallery under Pictures/PhantomMesh.
     * Works across Android 10+ (scoped storage) and legacy Android.
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String = "PhantomMesh_${System.currentTimeMillis()}"): Boolean {
        val resolver = context.contentResolver
        val cleanName = if (fileName.endsWith(".jpg", ignoreCase = true)) fileName else "$fileName.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhantomMesh")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return false

        return try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            } ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("HardwareStateUtil", "Error saving bitmap to gallery: ${e.message}", e)
            try {
                resolver.delete(uri, null, null)
            } catch (ignored: Exception) {}
            false
        }
    }

    /**
     * Purges saved media files (Images, Video, Audio) from device public MediaStore
     * and external public directories matching the message ID when a message is deleted for everyone.
     */
    fun deleteFromMediaStore(context: Context, messageId: String) {
        val resolver = context.contentResolver
        val cleanUuid = messageId.removePrefix("media_").removePrefix("msg_").removePrefix("voice_").removePrefix("video_").trim()
        val shortId = if (messageId.length >= 8) messageId.take(8) else messageId
        val shortUuid = if (cleanUuid.length >= 8) cleanUuid.take(8) else cleanUuid

        val tokens = listOfNotNull(
            messageId,
            shortId,
            cleanUuid.ifBlank { null },
            shortUuid.ifBlank { null }
        ).filter { it.length >= 4 }.distinct()

        // Helper to query and delete individual MediaStore items by specific ContentUri
        fun purgeCollection(collectionUri: android.net.Uri, mediaType: String) {
            try {
                val selectionBuilder = StringBuilder()
                val argsList = mutableListOf<String>()
                tokens.forEachIndexed { index, token ->
                    if (index > 0) selectionBuilder.append(" OR ")
                    selectionBuilder.append("${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?")
                    argsList.add("%$token%")
                }
                val selection = selectionBuilder.toString()
                val args = argsList.toTypedArray()

                val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
                resolver.query(collectionUri, projection, selection, args, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        if (idCol != -1) {
                            val id = cursor.getLong(idCol)
                            val name = if (nameCol != -1) cursor.getString(nameCol) else id.toString()
                            val itemUri = ContentUris.withAppendedId(collectionUri, id)
                            try {
                                val deleted = resolver.delete(itemUri, null, null)
                                android.util.Log.i("HardwareStateUtil", "Deleted MediaStore $mediaType item: $name (rows=$deleted)")
                            } catch (e: Exception) {
                                android.util.Log.w("HardwareStateUtil", "Error deleting MediaStore item $name: ${e.message}")
                            }
                        }
                    }
                }

                // Also attempt batch delete as backup
                resolver.delete(collectionUri, selection, args)
            } catch (e: Exception) {
                android.util.Log.w("HardwareStateUtil", "Error in purgeCollection ($mediaType): ${e.message}")
            }
        }

        // 1. Purge Images, Videos, and Audio from MediaStore
        purgeCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image")
        purgeCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video")
        purgeCollection(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "audio")

        // 2. Direct filesystem sweep in public PhantomMesh folders
        val publicFolders = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PhantomMesh"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "PhantomMesh"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "PhantomMesh"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "PhantomMesh"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PhantomMesh")
        )

        for (folder in publicFolders) {
            try {
                if (folder.exists() && folder.isDirectory) {
                    folder.listFiles()?.forEach { file ->
                        val matches = tokens.any { token -> file.name.contains(token, ignoreCase = true) }
                        if (matches) {
                            val deleted = file.delete()
                            android.util.Log.i("HardwareStateUtil", "Physically deleted public file: ${file.name} (success=$deleted)")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("HardwareStateUtil", "Error sweeping public folder ${folder.name}: ${e.message}")
            }
        }
    }
}
