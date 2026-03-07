package com.codemaster.aistudio.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.AppSettings
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateGeminiKey(key: String) {
        viewModelScope.launch { settingsRepository.updateGeminiKey(key) }
    }

    fun updateKimiKey(key: String) {
        viewModelScope.launch { settingsRepository.updateKimiKey(key) }
    }

    fun updateSandboxMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateSandboxMode(enabled) }
    }

    fun updateAutoSave(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoSave(enabled) }
    }
}
