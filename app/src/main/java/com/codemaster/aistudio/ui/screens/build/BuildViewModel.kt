package com.codemaster.aistudio.ui.screens.build

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.GitHubActionsRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BuildUiState(
    val isConfigured: Boolean = false,
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val isTriggering: Boolean = false,
    val triggerMessage: String? = null,
    val latestRunStatus: String? = null,
    val logs: List<String> = emptyList()
)

@HiltViewModel
class BuildViewModel @Inject constructor(
    private val gitHubActionsRepository: GitHubActionsRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
        refreshStatus()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val token  = settingsRepository.getGitHubToken()
            val owner  = settingsRepository.getGitHubOwner()
            val repo   = settingsRepository.getGitHubRepo()
            val branch = settingsRepository.getGitHubBranch()
            _uiState.value = _uiState.value.copy(
                isConfigured = token.isNotBlank() && owner.isNotBlank() && repo.isNotBlank(),
                owner = owner,
                repo = repo,
                branch = branch.ifBlank { "main" }
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
                    _uiState.value = _uiState.value.copy(
                        isTriggering = false,
                        triggerMessage = msg
                    )
                    addLog(msg)
                    // Auto-refresh status after trigger
                    kotlinx.coroutines.delay(3000)
                    refreshStatus()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isTriggering = false,
                        triggerMessage = "❌ ${e.message}"
                    )
                    addLog("ERROR: ${e.message}")
                }
            )
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            loadConfig()
            val state = _uiState.value
            if (!state.isConfigured) return@launch

            val result = gitHubActionsRepository.getLatestRun()
            result.fold(
                onSuccess = { status ->
                    _uiState.value = _uiState.value.copy(latestRunStatus = status)
                },
                onFailure = { }
            )
        }
    }

    private fun addLog(message: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logs = (_uiState.value.logs + "[$ts] $message").takeLast(20)
        _uiState.value = _uiState.value.copy(logs = logs)
    }
}
