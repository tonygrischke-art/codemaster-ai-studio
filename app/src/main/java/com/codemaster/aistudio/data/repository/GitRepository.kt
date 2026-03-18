package com.codemaster.aistudio.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class GitStatus(
    val branch: String = "",
    val staged: List<String> = emptyList(),
    val unstaged: List<String> = emptyList(),
    val untracked: List<String> = emptyList(),
    val isRepo: Boolean = false,
    val lastCommit: String = ""
)

@Singleton
class GitRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private fun findGitBinary(): String? {
        return listOf(
            "/data/data/com.termux/files/usr/bin/git",
            "/system/bin/git",
            "/system/xbin/git"
        ).firstOrNull { File(it).exists() }
    }

    private fun buildEnv(): Map<String, String> {
        val env = mutableMapOf<String, String>()
        if (File("/data/data/com.termux/files/usr/bin/git").exists()) {
            env["HOME"] = "/data/data/com.termux/files/home"
            env["PATH"] = "/data/data/com.termux/files/usr/bin:/system/bin"
            env["LD_LIBRARY_PATH"] = "/data/data/com.termux/files/usr/lib"
            env["GIT_EXEC_PATH"] = "/data/data/com.termux/files/usr/libexec/git-core"
            env["GIT_TEMPLATE_DIR"] = "/data/data/com.termux/files/usr/share/git-core/templates"
        } else {
            env["HOME"] = context.filesDir.absolutePath
            env["PATH"] = "/system/bin:/system/xbin"
        }
        return env
    }

    private suspend fun runGit(vararg args: String, workDir: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val git = findGitBinary()
                    ?: return@withContext Result.failure(Exception("git not found. Install Termux and run: pkg install git"))

                val cmd = mutableListOf(git) + args.toList()
                val pb = ProcessBuilder(cmd).apply {
                    workDir?.let { directory(File(it)) }
                    val env = environment()
                    buildEnv().forEach { (k, v) -> env[k] = v }
                    redirectErrorStream(false)
                }
                val proc = pb.start()
                val out = proc.inputStream.bufferedReader().readText().trim()
                val err = proc.errorStream.bufferedReader().readText().trim()
                proc.waitFor()
                if (proc.exitValue() != 0 && err.isNotBlank()) Result.failure(Exception(err))
                else Result.success(if (out.isNotBlank()) out else err)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getStatus(repoPath: String): Result<GitStatus> = withContext(Dispatchers.IO) {
        try {
            val isRepo = runGit("rev-parse", "--git-dir", workDir = repoPath).isSuccess
            if (!isRepo) return@withContext Result.success(GitStatus(isRepo = false))

            val branch = runGit("branch", "--show-current", workDir = repoPath).getOrElse { "unknown" }
            val statusOut = runGit("status", "--porcelain", workDir = repoPath).getOrElse { "" }
            val lastCommit = runGit("log", "--oneline", "-1", workDir = repoPath).getOrElse { "No commits" }

            val staged = mutableListOf<String>()
            val unstaged = mutableListOf<String>()
            val untracked = mutableListOf<String>()

            statusOut.lines().filter { it.isNotBlank() }.forEach { line ->
                val xy = line.take(2)
                val file = line.drop(3)
                when {
                    xy.startsWith("?") -> untracked.add(file)
                    xy[0] != ' '       -> staged.add(file)
                    xy[1] != ' '       -> unstaged.add(file)
                }
            }
            Result.success(GitStatus(
                branch = branch, staged = staged, unstaged = unstaged,
                untracked = untracked, isRepo = true, lastCommit = lastCommit
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stageAll(repoPath: String): Result<String> = runGit("add", "-A", workDir = repoPath)

    suspend fun commit(repoPath: String, message: String): Result<String> {
        val owner = settingsRepository.getGitHubOwner()
        val email = if (owner.isNotBlank()) "$owner@users.noreply.github.com" else "codemaster@app.local"
        runGit("config", "user.email", email, workDir = repoPath)
        runGit("config", "user.name", if (owner.isNotBlank()) owner else "CodeMaster", workDir = repoPath)
        return runGit("commit", "-m", message, workDir = repoPath)
    }

    suspend fun push(repoPath: String): Result<String> {
        val token = settingsRepository.getGitHubToken()
        val owner = settingsRepository.getGitHubOwner()
        val repo  = settingsRepository.getGitHubRepo()
        if (token.isBlank() || owner.isBlank() || repo.isBlank())
            return Result.failure(Exception("Configure GitHub token, username, and repo in Settings first."))
        val url = "https://$owner:$token@github.com/$owner/$repo.git"
        return runGit("push", url, "HEAD", workDir = repoPath)
    }

    suspend fun pull(repoPath: String): Result<String> = runGit("pull", workDir = repoPath)

    suspend fun log(repoPath: String, count: Int = 10): Result<String> =
        runGit("log", "--oneline", "-$count", workDir = repoPath)
}
