package com.codemaster.aistudio.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileSystemRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val baseDir: File = context.filesDir

    fun getProjectsDir(): File = File(baseDir, "projects").also { it.mkdirs() }

    fun getProjectDir(projectId: String): File = File(getProjectsDir(), projectId).also { it.mkdirs() }

    fun listProjects(): List<File> = getProjectsDir().listFiles()?.filter { it.isDirectory } ?: emptyList()

    fun readFile(path: String): String? = runCatching { File(path).readText() }.getOrNull()

    fun writeFile(path: String, content: String): Boolean = runCatching { File(path).writeText(content); true }.getOrElse { false }

    fun deleteFile(path: String): Boolean = File(path).deleteRecursively()

    fun listFiles(dirPath: String): List<File> = File(dirPath).listFiles()?.toList() ?: emptyList()
}
