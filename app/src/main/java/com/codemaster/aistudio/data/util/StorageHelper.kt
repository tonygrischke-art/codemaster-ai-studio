package com.codemaster.aistudio.data.util

import android.content.Context
import android.os.Build
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val isExternalStorageManager: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

    data class StorageLocation(
        val name: String,
        val path: String,
        val isWritable: Boolean,
        val isAppPrivate: Boolean
    )

    fun getAvailableLocations(): List<StorageLocation> {
        val locations = mutableListOf<StorageLocation>()
        
        // 1. App's private external files directory (always accessible)
        context.getExternalFilesDir(null)?.let { dir ->
            locations.add(StorageLocation(
                name = "App Files",
                path = dir.absolutePath,
                isWritable = true,
                isAppPrivate = true
            ))
            // Ensure directory exists
            dir.mkdirs()
        }
        
        // 2. App's private files directory
        context.filesDir.let { dir ->
            locations.add(StorageLocation(
                name = "Internal Storage",
                path = dir.absolutePath,
                isWritable = true,
                isAppPrivate = true
            ))
        }
        
        // 3. App's external documents directory
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { dir ->
            dir.mkdirs()
            locations.add(StorageLocation(
                name = "Documents",
                path = dir.absolutePath,
                isWritable = true,
                isAppPrivate = true
            ))
        }
        
        // 4. Shared external storage (requires MANAGE_EXTERNAL_STORAGE on Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (isExternalStorageManager) {
                addExternalStorageLocations(locations)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            addExternalStorageLocations(locations)
        }
        
        return locations
    }
    
    private fun addExternalStorageLocations(locations: MutableList<StorageLocation>) {
        val externalDirs = mutableListOf<File>()
        
        // Primary external storage
        Environment.getExternalStorageDirectory()?.let { externalDirs.add(it) }
        
        // Additional external storage volumes (USB, SD cards)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val externalDirsArray = context.getExternalFilesDirs(null)
            externalDirsArray.forEach { dir ->
                dir?.parentFile?.parentFile?.parentFile?.let { volumeRoot ->
                    if (!externalDirs.contains(volumeRoot) && volumeRoot.canRead()) {
                        externalDirs.add(volumeRoot)
                    }
                }
            }
        }
        
        externalDirs.forEach { dir ->
            locations.add(StorageLocation(
                name = if (dir.path.contains("sdcard", ignoreCase = true)) "SDCard" else "Storage",
                path = dir.absolutePath,
                isWritable = dir.canWrite(),
                isAppPrivate = false
            ))
        }
    }
    
    fun getDefaultLocation(): StorageLocation {
        return getAvailableLocations().firstOrNull { it.isWritable } ?: StorageLocation(
            name = "Internal",
            path = context.filesDir.absolutePath,
            isWritable = true,
            isAppPrivate = true
        )
    }
    
    fun ensureDirectoryExists(path: String): Boolean {
        return try {
            File(path).mkdirs()
        } catch (_: Exception) {
            false
        }
    }
    
    fun isPathAccessible(path: String): Boolean {
        return try {
            val file = File(path)
            file.exists() && file.canRead()
        } catch (_: Exception) {
            false
        }
    }
}
