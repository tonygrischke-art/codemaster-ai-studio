package com.codemaster.aistudio.data.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SafHelper"
        private const val PREF_NAME = "saf_prefs"
        private const val KEY_ROOT_TREE_URI = "root_tree_uri"
    }

    private val prefs by lazy { context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    var persistedUri: Uri?
        get() {
            val uriString = prefs.getString(KEY_ROOT_TREE_URI, null)
            return uriString?.let { Uri.parse(it) }
        }
        private set(value) {
            prefs.edit().putString(KEY_ROOT_TREE_URI, value?.toString()).apply()
        }

    fun hasPersistedAccess(): Boolean {
        val uri = persistedUri ?: return false
        try {
            val granted = context.contentResolver.persistedUriPermissions.any { permission ->
                permission.uri == uri &&
                permission.isReadPermission &&
                permission.isWritePermission
            }
            if (!granted) {
                Log.w(TAG, "No persisted permission found for URI: $uri — clearing saved URI")
                persistedUri = null
            }
            return granted
        } catch (e: Exception) {
            Log.e(TAG, "Error checking persisted access", e)
            persistedUri = null
            return false
        }
    }

    fun validateAndRepair(): Boolean {
        if (!hasPersistedAccess()) {
            Log.w(TAG, "SAF permission expired, clearing stored URI")
            persistedUri = null
            return false
        }
        return true
    }

    fun getDocumentFile(): DocumentFile? {
        return persistedUri?.let { uri ->
            try {
                DocumentFile.fromTreeUri(context, uri)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get DocumentFile from $uri", e)
                null
            }
        }
    }

    fun createDocumentPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }

    fun isValidUri(uri: Uri): Boolean {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
                val displayName = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                displayName >= 0
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun takePersistablePermission(uri: Uri): Boolean {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            // Verify the grant actually landed
            val confirmed = context.contentResolver.persistedUriPermissions.any { perm ->
                perm.uri == uri && perm.isReadPermission && perm.isWritePermission
            }
            if (!confirmed) {
                Log.e(TAG, "takePersistableUriPermission did not result in a persisted grant for $uri")
            }
            confirmed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistable permission for $uri", e)
            false
        }
    }

    fun releasePersistablePermission() {
        persistedUri?.let { uri ->
            try {
                val releaseFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(uri, releaseFlags)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release permission for $uri", e)
            }
        }
        persistedUri = null
    }

    fun onDirectoryPicked(uri: Uri): Boolean {
        // Take the permission FIRST — only store URI if the grant was confirmed
        persistedUri = uri  // temporarily store so hasPersistedAccess() can validate
        val granted = takePersistablePermission(uri)
        if (!granted) {
            Log.e(TAG, "Permission not granted for $uri — not persisting URI")
            persistedUri = null
        }
        return granted
    }

    suspend fun listFiles(subPath: String = ""): List<SafFile> = withContext(Dispatchers.IO) {
        val rootUri = persistedUri ?: return@withContext emptyList()
        
        try {
            val targetUri = if (subPath.isEmpty()) {
                rootUri
            } else {
                DocumentsContract.buildDocumentUriUsingTree(rootUri, subPath)
            }
            
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, subPath)
            
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    Document.COLUMN_DOCUMENT_ID,
                    Document.COLUMN_DISPLAY_NAME,
                    Document.COLUMN_MIME_TYPE,
                    Document.COLUMN_SIZE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val files = mutableListOf<SafFile>()
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mimeType = cursor.getString(2) ?: ""
                    val size = cursor.getLong(3)
                    
                    val isDir = mimeType == Document.MIME_TYPE_DIR
                    files.add(SafFile(
                        name = name,
                        docId = docId,
                        path = subPath,
                        isDirectory = isDir,
                        size = size,
                        mimeType = mimeType
                    ))
                }
                files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list files at $subPath", e)
            emptyList()
        }
    }

    suspend fun readFile(docId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rootUri = persistedUri ?: return@withContext Result.failure(Exception("No directory selected"))
            val uri = DocumentsContract.buildDocumentUriUsingTree(rootUri, docId)
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    Result.success(reader.readText())
                }
            } ?: Result.failure(Exception("Cannot open file"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeFile(docId: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val rootUri = persistedUri ?: return@withContext Result.failure(Exception("No directory selected"))
            val uri = DocumentsContract.buildDocumentUriUsingTree(rootUri, docId)
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write(content)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFile(parentDocId: String, name: String, mimeType: String): Result<SafFile> = withContext(Dispatchers.IO) {
        val rootUri = persistedUri ?: return@withContext Result.failure(Exception("No directory selected"))
        try {
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, parentDocId)
            val newDocUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                mimeType,
                name
            )
            
            if (newDocUri != null) {
                val docId = DocumentsContract.getDocumentId(newDocUri)
                Result.success(SafFile(
                    name = name,
                    docId = docId,
                    path = parentDocId,
                    isDirectory = false,
                    size = 0,
                    mimeType = mimeType
                ))
            } else {
                Result.failure(Exception("Failed to create file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDirectory(parentDocId: String, name: String): Result<SafFile> = withContext(Dispatchers.IO) {
        val rootUri = persistedUri ?: return@withContext Result.failure(Exception("No directory selected"))
        try {
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, parentDocId)
            val newDirUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                Document.MIME_TYPE_DIR,
                name
            )
            
            if (newDirUri != null) {
                val docId = DocumentsContract.getDocumentId(newDirUri)
                Result.success(SafFile(
                    name = name,
                    docId = docId,
                    path = parentDocId,
                    isDirectory = true,
                    size = 0,
                    mimeType = Document.MIME_TYPE_DIR
                ))
            } else {
                Result.failure(Exception("Failed to create directory"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(docId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val rootUri = persistedUri ?: return@withContext Result.failure(Exception("No directory selected"))
            val uri = DocumentsContract.buildDocumentUriUsingTree(rootUri, docId)
            val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
            if (deleted) Result.success(Unit)
            else Result.failure(Exception("Failed to delete"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFilePath(docId: String): String? = withContext(Dispatchers.IO) {
        val rootUri = persistedUri ?: return@withContext null
        try {
            val uri = DocumentsContract.buildDocumentUriUsingTree(rootUri, docId)
            val treeUri = DocumentsContract.buildTreeDocumentUri(rootUri.authority, rootUri.lastPathSegment ?: "")
            if (docId.startsWith(treeUri.lastPathSegment ?: "")) {
                docId.substringAfter(treeUri.lastPathSegment ?: "")
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun getRootDisplayName(): String? {
        return persistedUri?.let { uri ->
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    } else null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun scanDirectory(docId: String = "", maxDepth: Int = 6): List<SafFile> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SafFile>()
        scanRecursive(docId, result, 0, maxDepth)
        result.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    private suspend fun scanRecursive(docId: String, result: MutableList<SafFile>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        
        val files = listFiles(docId)
        for (file in files) {
            if (file.name.startsWith(".")) continue
            result.add(file.copy(depth = depth))
            if (file.isDirectory) {
                scanRecursive(file.docId, result, depth + 1, maxDepth)
            }
        }
    }
}

data class SafFile(
    val name: String,
    val docId: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val mimeType: String,
    val depth: Int = 0
)