package com.codemaster.aistudio.data.tools

import android.content.Context
import com.codemaster.aistudio.data.repository.FileSystemRepository
import com.codemaster.aistudio.data.repository.GitRepository
import com.codemaster.aistudio.data.util.PlatformHelper
import com.codemaster.aistudio.data.util.SafHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.annotation.Nullable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fsRepository: FileSystemRepository,
    private val gitRepository: GitRepository,
    private val platformHelper: PlatformHelper,
    @Nullable private val safHelper: SafHelper?
) {
    private var currentProjectPath: String = ""
    
    fun setProjectPath(path: String) {
        currentProjectPath = path
    }
    
    suspend fun executeTool(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        try {
            when (call.toolName) {
                "read_file" -> executeReadFile(call.arguments)
                "write_file" -> executeWriteFile(call.arguments)
                "create_directory" -> executeCreateDirectory(call.arguments)
                "delete_file" -> executeDeleteFile(call.arguments)
                "list_directory" -> executeListDirectory(call.arguments)
                "search_files" -> executeSearchFiles(call.arguments)
                "run_command" -> executeRunCommand(call.arguments)
                "git_status" -> executeGitStatus(call.arguments)
                "git_commit" -> executeGitCommit(call.arguments)
                "git_push" -> executeGitPush(call.arguments)
                "git_pull" -> executeGitPull(call.arguments)
                "search_web" -> executeSearchWeb(call.arguments)
                "get_project_info" -> executeGetProjectInfo()
                "build_project" -> executeBuildProject(call.arguments)
                "install_dependencies" -> executeInstallDependencies(call.arguments)
                else -> ToolResult(false, "", "Unknown tool: ${call.toolName}")
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Error executing ${call.toolName}: ${e.message}")
        }
    }
    
    private fun resolvePath(pathArg: String?): String {
        if (pathArg.isNullOrBlank()) return currentProjectPath
        return if (pathArg.startsWith("/") || pathArg.startsWith("saf://")) 
            pathArg 
        else 
            "$currentProjectPath/$pathArg"
    }
    
    private suspend fun executeReadFile(args: Map<String, String>): ToolResult {
        val path = resolvePath(args["path"])
        return fsRepository.readFile(path).fold(
            onSuccess = { ToolResult(true, it) },
            onFailure = { ToolResult(false, "", it.message ?: "Failed to read file") }
        )
    }
    
    private suspend fun executeWriteFile(args: Map<String, String>): ToolResult {
        val path = resolvePath(args["path"])
        val content = args["content"] ?: ""
        return fsRepository.writeFile(path, content).fold(
            onSuccess = { ToolResult(true, "File written successfully: $path") },
            onFailure = { ToolResult(false, "", it.message ?: "Failed to write file") }
        )
    }
    
    private suspend fun executeCreateDirectory(args: Map<String, String>): ToolResult {
        val path = resolvePath(args["path"])
        return if (path.startsWith("saf://")) {
            val parts = path.removePrefix("saf://").split("/")
            val dirName = parts.last()
            val parentDocId = if (parts.size > 1) parts.dropLast(1).joinToString("/") else ""
            safHelper?.createDirectory(parentDocId, dirName).fold(
                onSuccess = { ToolResult(true, "Directory created: $path") },
                onFailure = { ToolResult(false, "", it.message ?: "Failed to create directory") }
            )
        } else {
            val dir = java.io.File(path)
            if (dir.mkdirs()) ToolResult(true, "Directory created: $path")
            else ToolResult(false, "", "Failed to create directory")
        }
    }
    
    private suspend fun executeDeleteFile(args: Map<String, String>): ToolResult {
        val path = resolvePath(args["path"])
        return fsRepository.deleteFile(path).fold(
            onSuccess = { ToolResult(true, "Deleted: $path") },
            onFailure = { ToolResult(false, "", it.message ?: "Failed to delete") }
        )
    }
    
    private suspend fun executeListDirectory(args: Map<String, String>): ToolResult {
        val path = resolvePath(args["path"])
        return fsRepository.listFiles(path).fold(
            onSuccess = { files ->
                val output = files.joinToString("\n") { f ->
                    if (f.isDirectory) "📁 ${f.name}/" else "📄 ${f.name} (${fsRepository.formatFileSize(f.size)})"
                }
                ToolResult(true, output.ifBlank { "(empty)" })
            },
            onFailure = { ToolResult(false, "", it.message ?: "Failed to list directory") }
        )
    }
    
    private suspend fun executeSearchFiles(args: Map<String, String>): ToolResult {
        val pattern = args["pattern"] ?: "*"
        val searchPath = resolvePath(args["path"])
        
        return fsRepository.scanDirectory(searchPath, maxDepth = 4).fold(
            onSuccess = { files ->
                val matches = files.filter { file ->
                    matchGlob(file.name, pattern) || matchGlob(file.relativePath, pattern)
                }
                val output = matches.take(50).joinToString("\n") { f ->
                    if (f.isDirectory) "📁 ${f.relativePath}/" else "📄 ${f.relativePath}"
                }
                ToolResult(true, if (output.isBlank()) "No files match: $pattern" else output)
            },
            onFailure = { ToolResult(false, "", it.message ?: "Search failed") }
        )
    }
    
    private fun matchGlob(name: String, pattern: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("**/", "(.*/)?")
            .replace("**", ".*")
            .replace("*", "[^/]*")
            .replace("?", ".")
        return Regex(regex, RegexOption.IGNORE_CASE).matches(name)
    }
    
    private suspend fun executeRunCommand(args: Map<String, String>): ToolResult {
        val command = args["command"] ?: return ToolResult(false, "", "No command provided")
        val cwd = resolvePath(args["cwd"])
        
        if (cwd.startsWith("saf://")) {
            return ToolResult(false, "", "Cannot run commands on SAF storage. Use app storage or Termux.")
        }
        
        val workDir = java.io.File(cwd).takeIf { it.exists() } ?: context.filesDir
        
        val shellCmd = platformHelper.bashBinary ?: "/system/bin/sh"
        val shell = if (shellCmd.contains("bash")) shellCmd else "/system/bin/sh"
        
        return try {
            val pb = java.lang.ProcessBuilder(shell, "-c", command).apply {
                directory(workDir)
                environment().putAll(platformHelper.buildEnvironment())
                redirectErrorStream(true)
            }
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()
            
            val result = if (exitCode == 0) output else "Command exited with code $exitCode:\n$output"
            ToolResult(true, result)
        } catch (e: Exception) {
            ToolResult(false, "", "Command failed: ${e.message}")
        }
    }
    
    private suspend fun executeGitStatus(args: Map<String, String>): ToolResult {
        val path = resolvePath(args["path"])
        
        if (path.startsWith("saf://")) {
            return ToolResult(false, "", "Git not available on SAF storage. Use Termux or copy to app storage.")
        }
        
        return gitRepository.getStatus(path).fold(
            onSuccess = { status ->
                if (!status.isRepo) return ToolResult(true, "Not a git repository")
                buildString {
                    appendLine("Branch: ${status.branch}")
                    appendLine("Last commit: ${status.lastCommit}")
                    if (status.staged.isNotEmpty()) appendLine("Staged: ${status.staged.joinToString(", ")}")
                    if (status.unstaged.isNotEmpty()) appendLine("Modified: ${status.unstaged.joinToString(", ")}")
                    if (status.untracked.isNotEmpty()) appendLine("Untracked: ${status.untracked.joinToString(", ")}")
                }.let { ToolResult(true, it) }
            },
            onFailure = { ToolResult(false, "", it.message ?: "Git status failed") }
        )
    }
    
    private suspend fun executeGitCommit(args: Map<String, String>): ToolResult {
        val message = args["message"] ?: return ToolResult(false, "", "No commit message")
        val path = resolvePath(args["path"])
        
        if (path.startsWith("saf://")) {
            return ToolResult(false, "", "Git not available on SAF storage")
        }
        
        gitRepository.stageAll(path)
        return gitRepository.commit(path, message).fold(
            onSuccess = { ToolResult(true, it) },
            onFailure = { ToolResult(false, "", it.message ?: "Git commit failed") }
        )
    }
    
    private suspend fun executeGitPush(args: Map<String, String>): ToolResult {
        val path = resolvePath(args["path"])
        
        if (path.startsWith("saf://")) {
            return ToolResult(false, "", "Git not available on SAF storage")
        }
        
        return gitRepository.push(path).fold(
            onSuccess = { ToolResult(true, "Pushed successfully:\n$it") },
            onFailure = { ToolResult(false, "", it.message ?: "Git push failed") }
        )
    }
    
    private suspend fun executeGitPull(args: Map<String, String>): ToolResult {
        val path = resolvePath(args["path"])
        
        if (path.startsWith("saf://")) {
            return ToolResult(false, "", "Git not available on SAF storage")
        }
        
        return gitRepository.pull(path).fold(
            onSuccess = { ToolResult(true, "Pulled successfully:\n$it") },
            onFailure = { ToolResult(false, "", it.message ?: "Git pull failed") }
        )
    }
    
    private suspend fun executeSearchWeb(args: Map<String, String>): ToolResult {
        val query = args["query"] ?: return ToolResult(false, "", "No search query")
        return try {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url("https://duckduckgo.com/html/?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""
            
            val results = extractSearchResults(html).take(5)
            if (results.isEmpty()) {
                ToolResult(true, "No results found for: $query")
            } else {
                ToolResult(true, results.joinToString("\n\n") { "${it.first}\n${it.second}" })
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Search failed: ${e.message}")
        }
    }
    
    private fun extractSearchResults(html: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        val regex = Regex("""<a class="result__a"[^>]*href="([^"]*)"[^>]*>([^<]*)""")
        val snippetRegex = Regex("""result__snippet[^>]*>([^<]*)""")
        
        val matches = regex.findAll(html).take(5)
        val snippets = snippetRegex.findAll(html).take(5).toList()
        
        matches.forEachIndexed { index, match ->
            val title = match.groupValues[2].trim()
            val url = match.groupValues[1].take(100)
            val snippet = snippets.getOrNull(index)?.groupValues?.getOrNull(1)?.trim() ?: ""
            if (title.isNotBlank()) {
                results.add("$title" to (snippet.take(200)))
            }
        }
        return results
    }
    
    private suspend fun executeGetProjectInfo(): ToolResult {
        val path = currentProjectPath
        if (path.isBlank()) return ToolResult(false, "", "No project selected")
        
        return fsRepository.scanDirectory(path, maxDepth = 2).fold(
            onSuccess = { files ->
                val totalFiles = files.count { !it.isDirectory }
                val totalDirs = files.count { it.isDirectory }
                val byExtension = files.filter { !it.isDirectory }
                    .groupBy { it.extension }
                    .mapValues { it.value.size }
                    .toList()
                    .sortedByDescending { it.second }
                    .take(10)
                
                buildString {
                    appendLine("Project: ${path.substringAfterLast("/")}")
                    appendLine("Location: $path")
                    appendLine("Total: $totalFiles files, $totalDirs folders")
                    appendLine()
                    appendLine("File types:")
                    byExtension.forEach { (ext, count) ->
                        appendLine("  .$ext: $count")
                    }
                }.let { ToolResult(true, it) }
            },
            onFailure = { ToolResult(false, "", it.message ?: "Failed to get project info") }
        )
    }
    
    private suspend fun executeBuildProject(args: Map<String, String>): ToolResult {
        val path = resolvePath(args["path"])
        val task = args["task"] ?: "build"
        
        val command = when {
            java.io.File(path, "build.gradle.kts").exists() -> "./gradlew $task"
            java.io.File(path, "pom.xml").exists() -> "mvn $task"
            java.io.File(path, "package.json").exists() -> "npm run build"
            java.io.File(path, "Cargo.toml").exists() -> "cargo build"
            else -> return ToolResult(false, "", "Unknown project type")
        }
        
        return executeRunCommand(mapOf("command" to command, "cwd" to path))
    }
    
    private suspend fun executeInstallDependencies(args: Map<String, String>): ToolResult {
        val path = resolvePath(args["path"])
        val packageManager = args["package_manager"] ?: return ToolResult(false, "", "No package manager specified")
        val packages = args["packages"] ?: ""
        
        val command = when (packageManager.lowercase()) {
            "npm" -> "npm install $packages"
            "pip", "python" -> "pip install $packages"
            "yarn" -> "yarn add $packages"
            "cargo" -> "cargo add $packages"
            "gradle" -> "./gradlew dependencies"
            else -> return ToolResult(false, "", "Unknown package manager: $packageManager")
        }
        
        return executeRunCommand(mapOf("command" to command, "cwd" to path))
    }
}