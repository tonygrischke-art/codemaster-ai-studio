package com.codemaster.aistudio.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.FileResult
import com.codemaster.aistudio.data.repository.FileSystemRepository
import com.codemaster.aistudio.data.repository.ProjectDirectory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val projects: List<ProjectDirectory> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showNewProjectDialog: Boolean = false,
    val newProjectName: String = "",
    val newProjectLanguage: String = "python",
    val storageBasePath: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val fileSystemRepository: FileSystemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
        _uiState.update { it.copy(storageBasePath = fileSystemRepository.getBasePath()) }
    }

    fun loadProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = fileSystemRepository.listProjects()) {
                is FileResult.Success -> {
                    _uiState.update { it.copy(projects = result.data, isLoading = false) }
                }
                is FileResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun showNewProjectDialog() {
        _uiState.update { it.copy(showNewProjectDialog = true) }
    }

    fun hideNewProjectDialog() {
        _uiState.update { it.copy(showNewProjectDialog = false, newProjectName = "", newProjectLanguage = "python") }
    }

    fun updateNewProjectName(name: String) {
        _uiState.update { it.copy(newProjectName = name) }
    }

    fun updateNewProjectLanguage(language: String) {
        _uiState.update { it.copy(newProjectLanguage = language) }
    }

    fun createProject(onSuccess: (ProjectDirectory) -> Unit) {
        val state = _uiState.value
        if (state.newProjectName.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val templates = fileSystemRepository.getTemplateFiles(
                state.newProjectLanguage,
                state.newProjectName
            )
            when (val result = fileSystemRepository.createProject(
                name = state.newProjectName,
                language = state.newProjectLanguage,
                templateFiles = templates
            )) {
                is FileResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, showNewProjectDialog = false) }
                    loadProjects()
                    onSuccess(result.data)
                }
                is FileResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun deleteProject(projectPath: String) {
        viewModelScope.launch {
            when (fileSystemRepository.deleteProject(projectPath)) {
                is FileResult.Success -> loadProjects()
                is FileResult.Error -> { /* handle */ }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
