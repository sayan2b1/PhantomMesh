package com.phantom.mesh.util

import android.bluetooth.BluetoothManager
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
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.jpg")
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
}
