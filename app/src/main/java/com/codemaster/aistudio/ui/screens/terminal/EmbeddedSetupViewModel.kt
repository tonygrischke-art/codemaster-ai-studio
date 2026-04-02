package com.codemaster.aistudio.ui.screens.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.terminal.EmbeddedEnvironment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmbeddedSetupUiState(
    val isInstalling: Boolean = false,
    val isReady: Boolean = false,
    val statusMessage: String = "",
    val error: String? = null
)

@HiltViewModel
class EmbeddedSetupViewModel @Inject constructor(
    private val embeddedEnv: EmbeddedEnvironment
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(EmbeddedSetupUiState())
    val uiState: StateFlow<EmbeddedSetupUiState> = _uiState.asStateFlow()
    
    fun init() {
        if (embeddedEnv.isReady) {
            _uiState.value = EmbeddedSetupUiState(isReady = true)
        }
    }
    
    fun startInstallation() {
        viewModelScope.launch {
            _uiState.value = EmbeddedSetupUiState(isInstalling = true, statusMessage = "Starting...")
            try {
                embeddedEnv.install { status ->
                    _uiState.value = _uiState.value.copy(statusMessage = status)
                }
                _uiState.value = EmbeddedSetupUiState(isReady = true)
            } catch (e: Exception) {
                _uiState.value = EmbeddedSetupUiState(
                    isInstalling = false,
                    error = e.message ?: "Installation failed"
                )
            }
        }
    }
}
