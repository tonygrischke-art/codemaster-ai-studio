package com.codemaster.aistudio.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateGeminiKey(key: String) {
        viewModelScope.launch { settingsRepository.updateGeminiKey(key) }
    }

    fun updateGroqKey(key: String) {
        viewModelScope.launch { settingsRepository.updateGroqKey(key) }
    }

    fun updateTheme(isDark: Boolean) {
        viewModelScope.launch { settingsRepository.updateDarkTheme(isDark) }
    }

    fun updateFontSize(size: Int) {
        viewModelScope.launch { settingsRepository.updateFontSize(size) }
    }
}
