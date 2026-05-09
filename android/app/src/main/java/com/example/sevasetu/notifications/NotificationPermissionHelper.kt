package com.example.sevasetu.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

object NotificationPermissionHelper {
    private const val TAG = "NotificationPermHelper"

    fun isPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-Android 13 doesn't require explicit permission
            true
        }
    }

    fun needsPermissionRequest(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !isPermissionGranted(context)
    }

    fun logPermissionStatus(context: Context) {
        val granted = isPermissionGranted(context)
        val apiLevel = Build.VERSION.SDK_INT
        Log.d(TAG, "API Level: $apiLevel, Permission Granted: $granted")

        if (!granted && needsPermissionRequest(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted - user will not receive notifications")
        }
    }
}

