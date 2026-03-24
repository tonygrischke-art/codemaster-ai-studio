package com.codemaster.aistudio.ui.screens.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.ProjectRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import com.codemaster.aistudio.data.util.PlatformHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject

data class GitUiState(
    val projectPath: String = "",
    val currentBranch: String = "",
    val status: String = "",
    val log: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GitViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
    private val platformHelper: PlatformHelper
) : ViewModel() {

    private val gitBinary: String
        get() = platformHelper.gitBinary ?: "/system/bin/git"

    private val _uiState = MutableStateFlow(GitUiState())
    val uiState: StateFlow<GitUiState> = _uiState.asStateFlow()

    fun init(projectId: Long) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            val path = project?.path ?: return@launch
            _uiState.value = _uiState.value.copy(projectPath = path)
            refresh()
        }
    }

    fun refresh() {
        val path = _uiState.value.projectPath
        if (path.isBlank()) return
        viewModelScope.launch {
            val branch = runGit(path, listOf(gitBinary, "branch", "--show-current")).trim()
            val status = runGit(path, listOf(gitBinary, "status", "--short"))
            _uiState.value = _uiState.value.copy(currentBranch = branch, status = status.ifBlank { "Working tree clean" })
        }
    }

    fun stageAll() {
        runGitCommand(listOf(gitBinary, "add", "-A"), "Staged all changes")
    }

    fun commitAndPush(message: String) {
        val path = _uiState.value.projectPath
        if (path.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            addLog("Staging all changes...")
            runGit(path, listOf(gitBinary, "add", "-A"))
            addLog("Committing: $message")
            val commitOut = runGit(path, listOf(gitBinary, "commit", "-m", message))
            addLog(commitOut.trim())
            addLog("Pushing to origin...")
            val pushOut = runGit(path, listOf(gitBinary, "push", "origin", "HEAD"))
            addLog(pushOut.trim())
            addLog("Done!")
            _uiState.value = _uiState.value.copy(isLoading = false)
            refresh()
        }
    }

    fun push() = runGitCommand(listOf(gitBinary, "push", "origin", "HEAD"), "Pushing...")
    fun pull() = runGitCommand(listOf(gitBinary, "pull"), "Pulling...")

    private fun runGitCommand(cmd: List<String>, label: String) {
        val path = _uiState.value.projectPath
        if (path.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            addLog(label)
            val out = runGit(path, cmd)
            addLog(out.trim().ifBlank { "Done" })
            _uiState.value = _uiState.value.copy(isLoading = false)
            refresh()
        }
    }

    private suspend fun runGit(path: String, cmd: List<String>): String = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(cmd).directory(File(path)).redirectErrorStream(true).start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun addLog(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logs = (_uiState.value.log + "[$ts] $msg").takeLast(30)
        _uiState.value = _uiState.value.copy(log = logs)
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
