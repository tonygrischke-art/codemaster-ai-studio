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

data class SetupUiState(
    val label:    String  = "Preparing...",
    val progress: Int     = 0,
    val isDone:   Boolean = false,
    val isFailed: Boolean = false,
    val error:    String? = null
)

@HiltViewModel
class EmbeddedSetupViewModel @Inject constructor(
    private val env: EmbeddedEnvironment
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        if (env.isReady) {
            _uiState.value = SetupUiState(label = "Terminal ready!", progress = 100, isDone = true)
        } else {
            startSetup()
        }
    }

    private fun startSetup() {
        viewModelScope.launch {
            env.setup { label, progress ->
                if (progress == -1) {
                    _uiState.value = SetupUiState(
                        label    = label,
                        progress = 0,
                        isFailed = true,
                        error    = label
                    )
                } else {
                    _uiState.value = SetupUiState(
                        label    = label,
                        progress = progress,
                        isDone   = progress == 100
                    )
                }
            }
        }
    }

    fun retry() {
        _uiState.value = SetupUiState(label = "Retrying...", progress = 0)
        startSetup()
    }
}
