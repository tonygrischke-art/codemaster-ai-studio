package com.codemaster.aistudio.data.repository

import android.content.Context
import com.codemaster.aistudio.data.util.PlatformHelper
import com.codemaster.aistudio.data.util.SafHelper
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
    private val settingsRepository: SettingsRepository,
    private val platformHelper: PlatformHelper
) {
    private fun findGitBinary(): String? = platformHelper.gitBinary

    private fun buildEnv(): Map<String, String> = platformHelper.buildEnvironment()

    private fun isPathSafBased(path: String?): Boolean = path?.startsWith("saf://") == true

    private suspend fun runGit(vararg args: String, workDir: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            val git = findGitBinary()
                ?: return@withContext Result.failure(Exception(
                    "Git not available. " + platformHelper.getGitInstallInstructions()
                ))

            if (isPathSafBased(workDir)) {
                return@withContext Result.failure(Exception(
                    "Cannot run Git on SAF paths directly. Copy project to app storage or use Termux environment."
                ))
            }

            try {
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
        if (isPathSafBased(repoPath)) {
            return@withContext Result.success(GitStatus(
                isRepo = false,
                lastCommit = "Git unavailable on SAF storage. Use Termux or copy to app storage."
            ))
        }

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
        
        val cacheTimeout = 300
        runGit("config", "--global", "credential.helper", "cache --timeout=$cacheTimeout", workDir = repoPath)
        
        val url = "https://github.com/$owner/$repo.git"
        runGit("remote", "set-url", "origin", url, workDir = repoPath)
        return runGit("push", "origin", "HEAD", workDir = repoPath)
    }

    suspend fun pull(repoPath: String): Result<String> = runGit("pull", workDir = repoPath)

    suspend fun log(repoPath: String, count: Int = 10): Result<String> =
        runGit("log", "--oneline", "-$count", workDir = repoPath)

    fun getGitUnavailableMessage(): String = 
        "Git requires Termux. Install Termux, then run: pkg install git"
}