package com.codemaster.aistudio.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// ─── Result wrapper ────────────────────────────────────────────
sealed class FileResult<out T> {
    data class Success<T>(val data: T) : FileResult<T>()
    data class Error(val message: String, val cause: Exception? = null) : FileResult<Nothing>()
}

// ─── Project file model ────────────────────────────────────────
data class ProjectFile(
    val name: String,
    val path: String,
    val content: String,
    val lastModified: Long = System.currentTimeMillis(),
    val size: Long = 0L
)

// ─── Project directory model ───────────────────────────────────
data class ProjectDirectory(
    val name: String,
    val path: String,
    val files: List<ProjectFile> = emptyList(),
    val lastModified: Long = System.currentTimeMillis()
)

@Singleton
class FileSystemRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Base directory: /sdcard/CodeMasterProjects/
    // Using external storage so user can access files from file manager
    private val baseDir: File by lazy {
        val dir = File(
            android.os.Environment.getExternalStorageDirectory(),
            "CodeMasterProjects"
        )
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    // Internal fallback if external storage not available
    private val internalBaseDir: File by lazy {
        val dir = File(context.filesDir, "projects")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private fun resolveBaseDir(): File {
        return if (android.os.Environment.getExternalStorageState() ==
            android.os.Environment.MEDIA_MOUNTED) {
            baseDir
        } else {
            internalBaseDir
        }
    }

    // ─── Project operations ────────────────────────────────────

    suspend fun createProject(
        name: String,
        language: String,
        templateFiles: Map<String, String>
    ): FileResult<ProjectDirectory> = withContext(Dispatchers.IO) {
        try {
            val projectDir = File(resolveBaseDir(), sanitizeName(name))
            if (projectDir.exists()) {
                return@withContext FileResult.Error("Project '$name' already exists")
            }
            projectDir.mkdirs()

            // Write template files
            val createdFiles = mutableListOf<ProjectFile>()
            templateFiles.forEach { (fileName, content) ->
                val file = File(projectDir, fileName)
                file.writeText(content)
                createdFiles.add(
                    ProjectFile(
                        name = fileName,
                        path = file.absolutePath,
                        content = content,
                        lastModified = file.lastModified(),
                        size = file.length()
                    )
                )
            }

            // Write project metadata
            val meta = File(projectDir, ".codemaster")
            meta.writeText("name=$name\nlanguage=$language\ncreated=${System.currentTimeMillis()}")

            FileResult.Success(
                ProjectDirectory(
                    name = name,
                    path = projectDir.absolutePath,
                    files = createdFiles,
                    lastModified = projectDir.lastModified()
                )
            )
        } catch (e: IOException) {
            FileResult.Error("Failed to create project: ${e.message}", e)
        }
    }

    suspend fun listProjects(): FileResult<List<ProjectDirectory>> = withContext(Dispatchers.IO) {
        try {
            val dirs = resolveBaseDir().listFiles { file ->
                file.isDirectory && !file.name.startsWith(".")
            } ?: emptyArray()

            val projects = dirs.map { dir ->
                ProjectDirectory(
                    name = readProjectName(dir) ?: dir.name,
                    path = dir.absolutePath,
                    files = listFilesInDir(dir),
                    lastModified = dir.lastModified()
                )
            }.sortedByDescending { it.lastModified }

            FileResult.Success(projects)
        } catch (e: Exception) {
            FileResult.Error("Failed to list projects: ${e.message}", e)
        }
    }

    suspend fun loadProject(projectPath: String): FileResult<ProjectDirectory> =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(projectPath)
                if (!dir.exists() || !dir.isDirectory) {
                    return@withContext FileResult.Error("Project directory not found: $projectPath")
                }
                FileResult.Success(
                    ProjectDirectory(
                        name = readProjectName(dir) ?: dir.name,
                        path = dir.absolutePath,
                        files = listFilesInDir(dir),
                        lastModified = dir.lastModified()
                    )
                )
            } catch (e: Exception) {
                FileResult.Error("Failed to load project: ${e.message}", e)
            }
        }

    suspend fun deleteProject(projectPath: String): FileResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(projectPath)
                if (dir.deleteRecursively()) {
                    FileResult.Success(Unit)
                } else {
                    FileResult.Error("Failed to delete project directory")
                }
            } catch (e: Exception) {
                FileResult.Error("Failed to delete project: ${e.message}", e)
            }
        }

    // ─── File operations ───────────────────────────────────────

    suspend fun readFile(filePath: String): FileResult<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext FileResult.Error("File not found: $filePath")
            if (file.length() > 1024 * 1024) { // 1MB limit
                return@withContext FileResult.Error("File too large to edit (>1MB)")
            }
            FileResult.Success(file.readText())
        } catch (e: IOException) {
            FileResult.Error("Failed to read file: ${e.message}", e)
        }
    }

    suspend fun writeFile(filePath: String, content: String): FileResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                file.writeText(content)
                FileResult.Success(Unit)
            } catch (e: IOException) {
                FileResult.Error("Failed to write file: ${e.message}", e)
            }
        }

    suspend fun createFile(
        projectPath: String,
        fileName: String,
        content: String = ""
    ): FileResult<ProjectFile> = withContext(Dispatchers.IO) {
        try {
            val file = File(projectPath, fileName)
            if (file.exists()) return@withContext FileResult.Error("File already exists: $fileName")
            file.parentFile?.mkdirs()
            file.writeText(content)
            FileResult.Success(
                ProjectFile(
                    name = fileName,
                    path = file.absolutePath,
                    content = content,
                    lastModified = file.lastModified(),
                    size = file.length()
                )
            )
        } catch (e: IOException) {
            FileResult.Error("Failed to create file: ${e.message}", e)
        }
    }

    suspend fun renameFile(filePath: String, newName: String): FileResult<ProjectFile> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                val newFile = File(file.parent, newName)
                if (newFile.exists()) return@withContext FileResult.Error("File '$newName' already exists")
                if (file.renameTo(newFile)) {
                    FileResult.Success(
                        ProjectFile(
                            name = newName,
                            path = newFile.absolutePath,
                            content = newFile.readText(),
                            lastModified = newFile.lastModified(),
                            size = newFile.length()
                        )
                    )
                } else {
                    FileResult.Error("Failed to rename file")
                }
            } catch (e: Exception) {
                FileResult.Error("Failed to rename file: ${e.message}", e)
            }
        }

    suspend fun deleteFile(filePath: String): FileResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.delete()) {
                FileResult.Success(Unit)
            } else {
                FileResult.Error("Failed to delete file")
            }
        } catch (e: Exception) {
            FileResult.Error("Failed to delete file: ${e.message}", e)
        }
    }

    // ─── Storage info ──────────────────────────────────────────

    fun getBasePath(): String = resolveBaseDir().absolutePath

    suspend fun getStorageInfo(): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val dir = resolveBaseDir()
        val used = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val free = dir.freeSpace
        Pair(used, free)
    }

    // ─── Helpers ───────────────────────────────────────────────

    private fun sanitizeName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_\\-. ]"), "_")
            .trim()
            .replace(" ", "_")
    }

    private fun readProjectName(dir: File): String? {
        return try {
            val meta = File(dir, ".codemaster")
            if (meta.exists()) {
                meta.readLines().find { it.startsWith("name=") }?.removePrefix("name=")
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun listFilesInDir(dir: File): List<ProjectFile> {
        return dir.listFiles { file ->
            file.isFile && !file.name.startsWith(".")
        }?.map { file ->
            ProjectFile(
                name = file.name,
                path = file.absolutePath,
                content = "", // Don't read content until needed - lazy load
                lastModified = file.lastModified(),
                size = file.length()
            )
        }?.sortedBy { it.name } ?: emptyList()
    }

    // ─── Language templates ────────────────────────────────────
    fun getTemplateFiles(language: String, projectName: String): Map<String, String> {
        val pkg = projectName.lowercase().replace(Regex("[^a-z0-9]"), "")
        return when (language.lowercase()) {
            "kotlin" -> mapOf(
                "MainActivity.kt" to """package com.example.$pkg

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("Hello from $projectName!")
    }
}""",
                "build.gradle.kts" to """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.$pkg"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.$pkg"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
}"""
            )
            "python" -> mapOf(
                "main.py" to """#!/usr/bin/env python3
# $projectName

def main():
    print("Hello from $projectName!")

if __name__ == "__main__":
    main()
""",
                "requirements.txt" to "# Add your dependencies here\n"
            )
            "javascript" -> mapOf(
                "index.js" to """// $projectName

function main() {
    console.log("Hello from $projectName!");
}

main();
""",
                "package.json" to """{
  "name": "${pkg}",
  "version": "1.0.0",
  "description": "$projectName",
  "main": "index.js",
  "scripts": {
    "start": "node index.js"
  }
}"""
            )
            "java" -> mapOf(
                "Main.java" to """public class Main {
    public static void main(String[] args) {
        System.out.println("Hello from $projectName!");
    }
}"""
            )
            "cpp" -> mapOf(
                "main.cpp" to """#include <iostream>

int main() {
    std::cout << "Hello from $projectName!" << std::endl;
    return 0;
}"""
            )
            "dart" -> mapOf(
                "main.dart" to """import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '$projectName',
      home: Scaffold(
        appBar: AppBar(title: const Text('$projectName')),
        body: const Center(child: Text('Hello from $projectName!')),
      ),
    );
  }
}"""
            )
            else -> mapOf(
                "README.md" to "# $projectName\n\nCreated with CodeMaster AI Studio\n"
            )
        }
    }
}
