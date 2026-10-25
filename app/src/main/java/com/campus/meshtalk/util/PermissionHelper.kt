package com.campus.meshtalk.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

object PermissionHelper {

    fun getRequiredPermissions(): Array<String> = getRequiredStartupPermissions()

    fun getNearbyPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            else -> arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    fun getNotificationPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else null
    }

    fun getCameraPermission(): String = Manifest.permission.CAMERA

    fun getRequiredStartupPermissions(): Array<String> {
        val nearby = getNearbyPermissions()
        val notif = getNotificationPermission()
        return if (notif != null) nearby + notif else nearby
    }

    fun hasNearbyPermissions(context: Context): Boolean {
        return getNearbyPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        val notif = getNotificationPermission() ?: return true
        return ContextCompat.checkSelfPermission(context, notif) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, getCameraPermission()) == PackageManager.PERMISSION_GRANTED
    }

    fun getAudioRecordingPermission(): String = Manifest.permission.RECORD_AUDIO

    fun hasAudioRecordingPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, getAudioRecordingPermission()) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAllStartupPermissions(context: Context): Boolean {
        return hasNearbyPermissions(context)
    }

    fun hasAllPermissions(context: Context): Boolean = hasNearbyPermissions(context)

    fun isBluetoothEnabled(context: Context): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bm?.adapter?.isEnabled ?: false
    }

    fun isLocationEnabled(context: Context): Boolean {
        // Location service only strictly required for BLE scanning on API <= 30
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) return true
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openBluetoothSettings(context: Context) {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }

    fun openLocationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
