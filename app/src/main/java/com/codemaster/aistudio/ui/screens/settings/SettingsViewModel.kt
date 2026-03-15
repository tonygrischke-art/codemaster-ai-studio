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
    val model: String = "llama-3.3-70b-versatile",
    val isDarkTheme: Boolean = true,
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
            _uiState.value = _uiState.value.copy(
                apiKey = settingsRepository.getApiKey(),
                model = settingsRepository.getModel(),
                isDarkTheme = settingsRepository.getDarkTheme()
            )
        }
    }

    fun updateApiKey(key: String) { _uiState.value = _uiState.value.copy(apiKey = key, isSaved = false) }
    fun updateModel(model: String) { _uiState.value = _uiState.value.copy(model = model, isSaved = false) }
    fun toggleTheme() { _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme, isSaved = false) }

    fun saveSettings() {
        viewModelScope.launch {
            settingsRepository.saveApiKey(_uiState.value.apiKey)
            settingsRepository.saveModel(_uiState.value.model)
            settingsRepository.saveDarkTheme(_uiState.value.isDarkTheme)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
