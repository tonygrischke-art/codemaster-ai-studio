package com.codemaster.aistudio.data.repository

import android.content.Context
import android.net.Uri
import com.codemaster.aistudio.data.util.SafFile
import com.codemaster.aistudio.data.util.SafHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.annotation.Nullable
import javax.inject.Inject
import javax.inject.Singleton

data class DiskFile(
    val name: String,
    val path: String,
    val relativePath: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val extension: String,
    val depth: Int = 0,
    val isSafBased: Boolean = false,
    val safDocId: String? = null
)

@Singleton
class FileSystemRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Nullable private val safHelper: SafHelper?
) {
    private val ignoredDirs = setOf(
        ".git", "build", ".gradle", ".idea", "node_modules",
        "__pycache__", ".DS_Store", "out", "dist", ".dart_tool"
    )
    private val ignoredExtensions = setOf(
        "class", "jar", "aar", "apk", "so", "o", "a",
        "png", "jpg", "jpeg", "gif", "webp", "ico",
        "zip", "tar", "gz", "rar", "7z",
        "pdf", "doc", "docx", "xls", "xlsx"
    )
    private val codeExtensions = setOf(
        "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx",
        "dart", "cpp", "c", "h", "cs", "go", "rs", "swift",
        "rb", "php", "html", "css", "scss", "xml", "json",
        "yaml", "yml", "md", "txt", "sh", "bash", "gradle",
        "toml", "properties", "sql", "graphql", "proto"
    )

    fun isUsingSaf(): Boolean = safHelper?.hasPersistedAccess() == true

    suspend fun scanDirectory(rootPath: String, maxDepth: Int = 6): Result<List<DiskFile>> =
        withContext(Dispatchers.IO) {
            if (rootPath.startsWith("saf://")) {
                return@withContext scanSafDirectory(maxDepth)
            }
            
            try {
                val root = File(rootPath)
                if (!root.exists()) return@withContext Result.failure(Exception("Path does not exist: $rootPath"))
                if (!root.isDirectory) return@withContext Result.failure(Exception("Not a directory: $rootPath"))

                val files = mutableListOf<DiskFile>()
                scanRecursive(root, root.absolutePath, files, 0, maxDepth)
                Result.success(files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun scanSafDirectory(maxDepth: Int): Result<List<DiskFile>> {
        return try {
            val files = safHelper?.scanDirectory("", maxDepth) ?: emptyList()
            val diskFiles = files.map { safFileToDiskFile(it, "") }
            Result.success(diskFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun safFileToDiskFile(safFile: SafFile, rootPath: String): DiskFile {
        return DiskFile(
            name = safFile.name,
            path = "saf://${safFile.docId}",
            relativePath = safFile.path,
            size = safFile.size,
            lastModified = System.currentTimeMillis(),
            isDirectory = safFile.isDirectory,
            extension = if (safFile.isDirectory) "" else safFile.name.substringAfterLast(".", ""),
            depth = safFile.depth,
            isSafBased = true,
            safDocId = safFile.docId
        )
    }

    private fun scanRecursive(dir: File, rootPath: String, result: MutableList<DiskFile>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val files = try { dir.listFiles() ?: return } catch (_: Exception) { return }

        files.sortedWith(compareBy({ !it.isDirectory }, { it.name })).forEach { file ->
            if (file.name.startsWith(".") && file.name != ".env") return@forEach
            if (file.isDirectory && file.name in ignoredDirs) return@forEach
            val ext = file.extension.lowercase()
            if (!file.isDirectory && ext in ignoredExtensions) return@forEach

            val relative = file.absolutePath.removePrefix(rootPath).trimStart('/')
            result.add(DiskFile(
                name = file.name,
                path = file.absolutePath,
                relativePath = relative,
                size = if (file.isFile) file.length() else 0,
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                extension = ext,
                depth = depth
            ))

            if (file.isDirectory) {
                scanRecursive(file, rootPath, result, depth + 1, maxDepth)
            }
        }
    }

    suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        if (path.startsWith("saf://")) {
            val docId = path.removePrefix("saf://")
            return@withContext safHelper?.readFile(docId) ?: Result.failure(Exception("SAF not available"))
        }
        
        try {
            val file = File(path)
            if (!file.exists()) return@withContext Result.failure(Exception("File not found: $path"))
            if (file.length() > 500_000) return@withContext Result.failure(Exception("File too large to open (>500KB)"))
            Result.success(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeFile(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (path.startsWith("saf://")) {
            val docId = path.removePrefix("saf://")
            return@withContext safHelper?.writeFile(docId, content) ?: Result.failure(Exception("SAF not available"))
        }
        
        try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFile(dirPath: String, fileName: String): Result<String> = withContext(Dispatchers.IO) {
        if (dirPath.startsWith("saf://")) {
            val parentDocId = dirPath.removePrefix("saf://")
            val mimeType = getMimeType(fileName)
            return@withContext (safHelper?.createFile(parentDocId, fileName, mimeType) ?: Result.failure(Exception("SAF not available"))).map { "saf://${it.docId}" }
        }
        
        try {
            val file = File(dirPath, fileName)
            file.parentFile?.mkdirs()
            if (!file.exists()) file.createNewFile()
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDirectory(dirPath: String, dirName: String): Result<String> = withContext(Dispatchers.IO) {
        if (dirPath.startsWith("saf://")) {
            val parentDocId = dirPath.removePrefix("saf://")
            return@withContext (safHelper?.createDirectory(parentDocId, dirName) ?: Result.failure(Exception("SAF not available"))).map { "saf://${it.docId}" }
        }
        
        try {
            val dir = File(dirPath, dirName)
            dir.mkdirs()
            Result.success(dir.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (path.startsWith("saf://")) {
            val docId = path.removePrefix("saf://")
            return@withContext safHelper?.deleteFile(docId) ?: Result.failure(Exception("SAF not available"))
        }
        
        try { File(path).delete(); Result.success(Unit) }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun listFiles(dirPath: String): Result<List<DiskFile>> = withContext(Dispatchers.IO) {
        if (dirPath.startsWith("saf://")) {
            val docId = dirPath.removePrefix("saf://")
            val files = safHelper?.listFiles(docId) ?: emptyList()
            return@withContext Result.success(files.map { safFileToDiskFile(it, dirPath) })
        }
        
        try {
            val dir = File(dirPath)
            if (!dir.exists() || !dir.isDirectory) {
                return@withContext Result.failure(Exception("Not a directory: $dirPath"))
            }
            val files = dir.listFiles()?.map { file ->
                DiskFile(
                    name = file.name,
                    path = file.absolutePath,
                    relativePath = file.name,
                    size = if (file.isFile) file.length() else 0,
                    lastModified = file.lastModified(),
                    isDirectory = file.isDirectory,
                    extension = file.extension.lowercase(),
                    depth = 0
                )
            } ?: emptyList()
            Result.success(files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getLanguageFromExtension(ext: String): String = when (ext.lowercase()) {
        "kt", "kts"          -> "kotlin"
        "java"               -> "java"
        "py"                 -> "python"
        "js", "jsx"          -> "javascript"
        "ts", "tsx"          -> "typescript"
        "dart"               -> "dart"
        "cpp", "cc", "cxx"   -> "cpp"
        "c"                  -> "c"
        "cs"                 -> "csharp"
        "go"                 -> "go"
        "rs"                 -> "rust"
        "swift"              -> "swift"
        "rb"                 -> "ruby"
        "php"                -> "php"
        "html"               -> "html"
        "css"                -> "css"
        "scss"               -> "scss"
        "xml"                -> "xml"
        "json"               -> "json"
        "yaml", "yml"        -> "yaml"
        "md"                 -> "markdown"
        "sh", "bash"         -> "shell"
        "gradle", "groovy"   -> "groovy"
        "sql"                -> "sql"
        "toml"               -> "toml"
        else                 -> "plaintext"
    }

    fun formatFileSize(bytes: Long): String = when {
        bytes < 1024        -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else                -> "${bytes / (1024 * 1024)}MB"
    }

    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast(".", "").lowercase()) {
            "kt", "kts"    -> "text/x-kotlin"
            "java"         -> "text/x-java"
            "py"           -> "text/x-python"
            "js"           -> "text/javascript"
            "ts"           -> "text/typescript"
            "jsx"          -> "text/jsx"
            "tsx"          -> "text/tsx"
            "dart"         -> "text/x-dart"
            "cpp", "cc"    -> "text/x-c++src"
            "c", "h"       -> "text/x-csrc"
            "cs"           -> "text/x-csharp"
            "go"           -> "text/x-go"
            "rs"           -> "text/x-rustsrc"
            "swift"        -> "text/x-swift"
            "rb"           -> "text/x-ruby"
            "php"          -> "text/x-php"
            "html"         -> "text/html"
            "css"          -> "text/css"
            "scss"         -> "text/x-scss"
            "xml"          -> "text/xml"
            "json"         -> "application/json"
            "yaml", "yml"  -> "text/yaml"
            "md"           -> "text/markdown"
            "txt"          -> "text/plain"
            "sh", "bash"   -> "application/x-sh"
            "gradle"       -> "text/x-gradle"
            "toml"         -> "text/x-toml"
            "sql"          -> "text/x-sql"
            else           -> "application/octet-stream"
        }
    }
}