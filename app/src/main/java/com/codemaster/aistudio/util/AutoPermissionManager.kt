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
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        val hasOverlay = Settings.canDrawOverlays(context)

        return hasStorage && hasOverlay
    }

    fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                missing.add("All Files Access")
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

        return missing
    }
}
