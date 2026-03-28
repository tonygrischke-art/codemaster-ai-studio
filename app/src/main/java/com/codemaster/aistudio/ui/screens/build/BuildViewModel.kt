package com.codemaster.aistudio.ui.screens.build

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.GitHubActionsRepository
import com.codemaster.aistudio.data.repository.ProjectRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

data class BuildUiState(
    val isConfigured: Boolean = false,
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val isTriggering: Boolean = false,
    val isLocalBuilding: Boolean = false,
    val triggerMessage: String? = null,
    val latestRunStatus: String? = null,
    val logs: List<String> = emptyList(),
    val apkPath: String? = null,
    val buildErrors: List<String> = emptyList(),
    val projectPath: String = "",
    val errorForAi: String? = null
)

@HiltViewModel
class BuildViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitHubActionsRepository: GitHubActionsRepository,
    private val settingsRepository: SettingsRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

    fun init(projectId: Long) {
        loadConfig()
        refreshStatus()
        viewModelScope.launch {
            // Use proper path resolution
            val path = projectRepository.getProjectPath(projectId)
            _uiState.value = _uiState.value.copy(projectPath = path)
        }
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val token  = settingsRepository.getGitHubToken()
            val owner  = settingsRepository.getGitHubOwner()
            val repo   = settingsRepository.getGitHubRepo()
            val branch = settingsRepository.getGitHubBranch()
            _uiState.value = _uiState.value.copy(
                isConfigured = token.isNotBlank() && owner.isNotBlank() && repo.isNotBlank(),
                owner = owner, repo = repo, branch = branch.ifBlank { "main" }
            )
        }
    }

    fun triggerBuild() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTriggering = true, triggerMessage = null)
            addLog("Triggering GitHub Actions build...")
            val result = gitHubActionsRepository.triggerBuild()
            result.fold(
                onSuccess = { msg ->
                    _uiState.value = _uiState.value.copy(isTriggering = false, triggerMessage = msg)
                    addLog(msg)
                    kotlinx.coroutines.delay(3000)
                    refreshStatus()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isTriggering = false, triggerMessage = "FAIL ${e.message}")
                    addLog("ERROR: ${e.message}")
                }
            )
        }
    }

    fun triggerLocalBuild() {
        val projectPath = _uiState.value.projectPath
        if (projectPath.isBlank()) {
            addLog("No project path set. Load a project first.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLocalBuilding = true, buildErrors = emptyList(), apkPath = null, errorForAi = null)
            addLog("Starting local build...")
            addLog("Project: $projectPath")
            withContext(Dispatchers.IO) {
                try {
                    val gradlew = File(projectPath, "gradlew")
                    val termuxGit = "/data/data/com.termux/files/usr/bin/git"
                    if (!gradlew.exists()) {
                        addLog("gradlew not found in $projectPath")
                        _uiState.value = _uiState.value.copy(isLocalBuilding = false)
                        return@withContext
                    }
                    gradlew.setExecutable(true)
                    val process = ProcessBuilder(listOf("$projectPath/gradlew", "assembleDebug", "--stacktrace"))
                        .directory(File(projectPath)).redirectErrorStream(true).start()
                    val errors = mutableListOf<String>()
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            line?.let { l ->
                                addLog(l)
                                if (l.contains("error:", ignoreCase = true) || l.contains("FAILED") || l.contains("Exception")) {
                                    errors.add(l)
                                }
                            }
                        }
                    }
                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        addLog("BUILD SUCCESSFUL")
                        val apk = File(projectPath).walk().filter { it.extension == "apk" && it.name.contains("debug") }.maxByOrNull { it.lastModified() }
                        if (apk != null) {
                            addLog("APK ready: ${apk.name}")
                            _uiState.value = _uiState.value.copy(apkPath = apk.absolutePath)
                        }
                    } else {
                        addLog("BUILD FAILED (exit $exitCode)")
                        val errorSummary = errors.takeLast(20).joinToString("\n")
                        _uiState.value = _uiState.value.copy(buildErrors = errors, errorForAi = errorSummary)
                    }
                } catch (e: Exception) {
                    addLog("Exception: ${e.message}")
                } finally {
                    _uiState.value = _uiState.value.copy(isLocalBuilding = false)
                }
            }
        }
    }

    fun installApk(apkPath: String) {
        try {
            val apkFile = File(apkPath)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            addLog("Install failed: ${e.message}")
        }
    }

    fun clearErrorForAi() { _uiState.value = _uiState.value.copy(errorForAi = null) }

    fun refreshStatus() {
        viewModelScope.launch {
            loadConfig()
            if (!_uiState.value.isConfigured) return@launch
            gitHubActionsRepository.getLatestRun().fold(
                onSuccess = { status -> _uiState.value = _uiState.value.copy(latestRunStatus = status) },
                onFailure = { }
            )
        }
    }

    private fun addLog(message: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logs = (_uiState.value.logs + "[$ts] $message").takeLast(100)
        _uiState.value = _uiState.value.copy(logs = logs)
    }
}
