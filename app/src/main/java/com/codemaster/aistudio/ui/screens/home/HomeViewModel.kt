package com.codemaster.aistudio.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.Project
import com.codemaster.aistudio.data.repository.FileSystemRepository
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
    val isLoading: Boolean = true,
    val error: String? = null,
    val showNewProjectDialog: Boolean = false,
    val newProjectName: String = "",
    val newProjectLanguage: String = "Kotlin",
    val newProjectDescription: String = "",
    val showImportDialog: Boolean = false,
    val importPath: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val fsRepository: FileSystemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepository.getAllProjects()
                .catch { e -> _uiState.value = _uiState.value.copy(error = e.message, isLoading = false) }
                .collect { projects -> _uiState.value = _uiState.value.copy(projects = projects, isLoading = false) }
        }
    }

    fun showNewProjectDialog() { _uiState.value = _uiState.value.copy(showNewProjectDialog = true, newProjectName = "", newProjectLanguage = "Kotlin", newProjectDescription = "") }
    fun hideNewProjectDialog() { _uiState.value = _uiState.value.copy(showNewProjectDialog = false) }
    fun updateProjectName(n: String) { _uiState.value = _uiState.value.copy(newProjectName = n) }
    fun updateProjectLanguage(l: String) { _uiState.value = _uiState.value.copy(newProjectLanguage = l) }
    fun updateProjectDescription(d: String) { _uiState.value = _uiState.value.copy(newProjectDescription = d) }
    fun showImportDialog() { _uiState.value = _uiState.value.copy(showImportDialog = true) }
    fun hideImportDialog() { _uiState.value = _uiState.value.copy(showImportDialog = false) }
    fun updateImportPath(p: String) { _uiState.value = _uiState.value.copy(importPath = p) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    fun createProject(onCreated: (Long) -> Unit) {
        val state = _uiState.value
        if (state.newProjectName.isBlank()) return
        viewModelScope.launch {
            val id = projectRepository.createProject(state.newProjectName, state.newProjectLanguage, state.newProjectDescription)
            hideNewProjectDialog()
            onCreated(id)
        }
    }

    fun importProject(onImported: (Long) -> Unit) {
        val path = _uiState.value.importPath.trim()
        if (path.isBlank()) return
        viewModelScope.launch {
            val name = path.substringAfterLast("/").ifBlank { "Imported Project" }
            val project = Project(name = name, language = "Kotlin", description = "Loaded from: $path", path = path)
            val id = projectRepository.createProject(project.name, project.language, project.description)
            // Update path
            projectRepository.getProjectById(id)?.let {
                projectRepository.updateProject(it.copy(path = path))
            }
            hideImportDialog()
            onImported(id)
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch { projectRepository.deleteProject(project) }
    }
}
