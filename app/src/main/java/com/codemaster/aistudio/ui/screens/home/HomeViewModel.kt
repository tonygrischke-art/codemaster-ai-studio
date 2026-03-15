package com.codemaster.aistudio.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.Project
import com.codemaster.aistudio.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = false,
    val showNewProjectDialog: Boolean = false,
    val newProjectName: String = "",
    val newProjectLanguage: String = "Kotlin",
    val newProjectDescription: String = "",
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            projectRepository.getAllProjects()
                .catch { e -> _uiState.value = _uiState.value.copy(error = e.message, isLoading = false) }
                .collect { projects ->
                    _uiState.value = _uiState.value.copy(projects = projects, isLoading = false)
                }
        }
    }

    fun showNewProjectDialog() { _uiState.value = _uiState.value.copy(showNewProjectDialog = true) }
    fun hideNewProjectDialog() { _uiState.value = _uiState.value.copy(showNewProjectDialog = false, newProjectName = "", newProjectLanguage = "Kotlin", newProjectDescription = "") }
    fun updateProjectName(name: String) { _uiState.value = _uiState.value.copy(newProjectName = name) }
    fun updateProjectLanguage(lang: String) { _uiState.value = _uiState.value.copy(newProjectLanguage = lang) }
    fun updateProjectDescription(desc: String) { _uiState.value = _uiState.value.copy(newProjectDescription = desc) }

    fun createProject(onCreated: (Long) -> Unit) {
        val state = _uiState.value
        if (state.newProjectName.isBlank()) {
            _uiState.value = state.copy(error = "Project name cannot be empty")
            return
        }
        viewModelScope.launch {
            val id = projectRepository.createProject(
                name = state.newProjectName.trim(),
                language = state.newProjectLanguage,
                description = state.newProjectDescription.trim()
            )
            hideNewProjectDialog()
            onCreated(id)
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch { projectRepository.deleteProject(project) }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
