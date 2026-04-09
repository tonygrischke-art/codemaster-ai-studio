package com.codemaster.aistudio.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

class AutoPermissionManager(private val context: Context) {

    fun hasAllPermissions(): Boolean {
        val hasStorage = hasStoragePermission()
        val hasOverlay = Settings.canDrawOverlays(context)
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return hasStorage && hasOverlay && hasNotification
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                missing.add("All Files Access (SAF)")
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                missing.add("Storage")
            }
        }

        if (!Settings.canDrawOverlays(context)) {
            missing.add("Display over other apps")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                missing.add("Notifications")
            }
        }

        return missing
    }

    fun isStorageFullAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun shouldShowRationale(activity: Activity, permissions: Array<String>): Boolean {
        return permissions.any { permission ->
            activity.shouldShowRequestPermissionRationale(permission)
        }
    }
}