package com.codemaster.aistudio.data.util

import android.content.Context
import android.os.Build
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.annotation.Nullable
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    @Nullable private val safHelper: SafHelper?
) {
    private val ignoredDirs = setOf(
        ".git", "build", ".gradle", ".idea", "node_modules",
        "__pycache__", ".DS_Store", "out", "dist", ".dart_tool"
    )

    data class StorageLocation(
        val name: String,
        val path: String,
        val isWritable: Boolean,
        val isAppPrivate: Boolean,
        val isSafBased: Boolean = false,
        val safDocId: String? = null
    )

    fun getAvailableLocations(): List<StorageLocation> {
        val locations = mutableListOf<StorageLocation>()
        
        context.getExternalFilesDir(null)?.let { dir ->
            dir.mkdirs()
            locations.add(StorageLocation(
                name = "App Files",
                path = dir.absolutePath,
                isWritable = true,
                isAppPrivate = true
            ))
        }
        
        context.filesDir.let { dir ->
            locations.add(StorageLocation(
                name = "Internal Storage",
                path = dir.absolutePath,
                isWritable = true,
                isAppPrivate = true
            ))
        }
        
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { dir ->
            dir.mkdirs()
            locations.add(StorageLocation(
                name = "Documents",
                path = dir.absolutePath,
                isWritable = true,
                isAppPrivate = true
            ))
        }
        
        if (safHelper?.hasPersistedAccess() == true) {
            val rootName = safHelper.getRootDisplayName() ?: "External Storage"
            locations.add(StorageLocation(
                name = rootName,
                path = "saf://root",
                isWritable = true,
                isAppPrivate = false,
                isSafBased = true,
                safDocId = ""
            ))
        }
        
        return locations
    }
    
    fun getDefaultLocation(): StorageLocation {
        return getAvailableLocations().firstOrNull { it.isWritable } ?: StorageLocation(
            name = "Internal",
            path = context.filesDir.absolutePath,
            isWritable = true,
            isAppPrivate = true
        )
    }
    
    fun getSafLocation(): StorageLocation? {
        if (safHelper?.hasPersistedAccess() != true) return null
        return StorageLocation(
            name = safHelper.getRootDisplayName() ?: "External",
            path = "saf://root",
            isWritable = true,
            isAppPrivate = false,
            isSafBased = true,
            safDocId = ""
        )
    }
    
    fun isUsingSaf(): Boolean = safHelper?.hasPersistedAccess() == true
    
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
    
    fun isIgnoredDirectory(name: String): Boolean = name in ignoredDirs
}