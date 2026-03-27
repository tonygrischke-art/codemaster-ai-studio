package com.codemaster.aistudio.ui.screens.dualai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.DualAiProgress
import com.codemaster.aistudio.data.repository.DualAiRepository
import com.codemaster.aistudio.data.repository.ProjectRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class DualAiBuilderUiState(
    val userDescription: String = "",
    val language: String = "Kotlin",
    val hasClaudeKey: Boolean = false,
    val hasKimiKey: Boolean = false,
    val hasGroqKey: Boolean = false,
    val isBuilding: Boolean = false,
    val isComplete: Boolean = false,
    val progress: DualAiProgress? = null,
    val projectId: Long = -1L,
    val totalTokensUsed: Int = 0,
    val estimatedCost: String = "$0.00",
    val error: String? = null
)

@HiltViewModel
class DualAiBuilderViewModel @Inject constructor(
    private val dualAiRepository: DualAiRepository,
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DualAiBuilderUiState())
    val uiState: StateFlow<DualAiBuilderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                hasClaudeKey = settingsRepository.getClaudeApiKey().isNotBlank(),
                hasKimiKey = settingsRepository.getKimiApiKey().isNotBlank(),
                hasGroqKey = settingsRepository.getApiKey().isNotBlank()
            )
        }
    }

    fun updateDescription(d: String) { _uiState.value = _uiState.value.copy(userDescription = d) }
    fun updateLanguage(l: String) { _uiState.value = _uiState.value.copy(language = l) }

    fun startBuild(onProjectCreated: (Long) -> Unit) {
        val state = _uiState.value
        if (state.userDescription.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBuilding = true, isComplete = false, error = null)

            // Determine output path - use app's files directory as base
            val projectName = state.userDescription.take(30)
                .replace(Regex("[^a-zA-Z0-9 ]"), "")
                .trim().replace(" ", "-")
                .ifBlank { "AI-App" }
            val baseDir = context.getExternalFilesDir(null)?.absolutePath
                ?: context.filesDir.absolutePath
            val outputDir = File(baseDir, projectName)
            outputDir.mkdirs()
            val outputPath = outputDir.absolutePath

            dualAiRepository.buildApp(
                userDescription = state.userDescription,
                language = state.language,
                outputPath = outputPath
            ).collect { progress ->
                _uiState.value = _uiState.value.copy(
                    progress = progress,
                    totalTokensUsed = progress.totalTokens,
                    estimatedCost = progress.estimatedCost
                )

                when (progress.stage) {
                    DualAiProgress.Stage.COMPLETE -> {
                        // Register project
                        val id = projectRepository.createProject(
                            projectName, state.language, "Loaded from: $outputPath"
                        )
                        projectRepository.getProjectById(id)?.let {
                            projectRepository.updateProject(it.copy(path = outputPath))
                        }
                        _uiState.value = _uiState.value.copy(
                            isBuilding = false, isComplete = true, projectId = id
                        )
                        onProjectCreated(id)
                    }
                    DualAiProgress.Stage.ERROR -> {
                        _uiState.value = _uiState.value.copy(
                            isBuilding = false, error = progress.error
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}
