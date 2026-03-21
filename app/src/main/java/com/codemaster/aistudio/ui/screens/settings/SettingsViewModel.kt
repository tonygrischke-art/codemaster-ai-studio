package com.codemaster.aistudio.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val claudeApiKey: String = "",
    val kimiApiKey: String = "",
    val model: String = "llama-3.3-70b-versatile",
    val isDarkTheme: Boolean = true,
    val githubToken: String = "",
    val githubOwner: String = "",
    val githubRepo: String = "",
    val githubBranch: String = "main",
    val projectPath: String = "/sdcard/codemaster-ai-studio",
    val onboardingComplete: Boolean = false,
    val totalTokensUsed: Long = 0L,
    val isSaved: Boolean = false
)

val GROQ_MODELS = listOf(
    "llama-3.3-70b-versatile",
    "llama-3.1-8b-instant",
    "mixtral-8x7b-32768",
    "gemma2-9b-it"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = SettingsUiState(
                apiKey             = settingsRepository.getApiKey(),
                claudeApiKey       = settingsRepository.getClaudeApiKey(),
                kimiApiKey         = settingsRepository.getKimiApiKey(),
                model              = settingsRepository.getModel(),
                isDarkTheme        = settingsRepository.getDarkTheme(),
                githubToken        = settingsRepository.getGitHubToken(),
                githubOwner        = settingsRepository.getGitHubOwner(),
                githubRepo         = settingsRepository.getGitHubRepo(),
                githubBranch       = settingsRepository.getGitHubBranch().ifBlank { "main" },
                projectPath        = settingsRepository.getProjectPath().ifBlank { "/sdcard/codemaster-ai-studio" },
                onboardingComplete = settingsRepository.getOnboardingComplete(),
                totalTokensUsed    = settingsRepository.getTotalTokensUsed()
            )
        }
    }

    fun updateApiKey(k: String)        { _uiState.value = _uiState.value.copy(apiKey = k, isSaved = false) }
    fun updateClaudeApiKey(k: String)  { _uiState.value = _uiState.value.copy(claudeApiKey = k, isSaved = false) }
    fun updateKimiApiKey(k: String)    { _uiState.value = _uiState.value.copy(kimiApiKey = k, isSaved = false) }
    fun updateModel(m: String)         { _uiState.value = _uiState.value.copy(model = m, isSaved = false) }
    fun toggleTheme()                  { _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme, isSaved = false) }
    fun updateGithubToken(t: String)   { _uiState.value = _uiState.value.copy(githubToken = t, isSaved = false) }
    fun updateGithubOwner(o: String)   { _uiState.value = _uiState.value.copy(githubOwner = o, isSaved = false) }
    fun updateGithubRepo(r: String)    { _uiState.value = _uiState.value.copy(githubRepo = r, isSaved = false) }
    fun updateGithubBranch(b: String)  { _uiState.value = _uiState.value.copy(githubBranch = b, isSaved = false) }
    fun updateProjectPath(p: String)   { _uiState.value = _uiState.value.copy(projectPath = p, isSaved = false) }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.saveOnboardingComplete(true)
            _uiState.value = _uiState.value.copy(onboardingComplete = true)
            saveSettings()
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            val s = _uiState.value
            settingsRepository.saveApiKey(s.apiKey)
            settingsRepository.saveClaudeApiKey(s.claudeApiKey)
            settingsRepository.saveKimiApiKey(s.kimiApiKey)
            settingsRepository.saveModel(s.model)
            settingsRepository.saveDarkTheme(s.isDarkTheme)
            settingsRepository.saveGitHubToken(s.githubToken)
            settingsRepository.saveGitHubOwner(s.githubOwner)
            settingsRepository.saveGitHubRepo(s.githubRepo)
            settingsRepository.saveGitHubBranch(s.githubBranch.ifBlank { "main" })
            settingsRepository.saveProjectPath(s.projectPath)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
