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
    val showImportDialog: Boolean = false,
    val showTemplateDialog: Boolean = false,
    val newProjectName: String = "",
    val newProjectLanguage: String = "Kotlin",
    val newProjectDescription: String = "",
    val importPath: String = "",
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { loadProjects() }

    private fun loadProjects() {
        viewModelScope.launch {
            projectRepository.getAllProjects()
                .catch { e -> _uiState.value = _uiState.value.copy(error = e.message, isLoading = false) }
                .collect { projects -> _uiState.value = _uiState.value.copy(projects = projects, isLoading = false) }
        }
    }

    fun showNewProjectDialog()  { _uiState.value = _uiState.value.copy(showNewProjectDialog = true) }
    fun hideNewProjectDialog()  { _uiState.value = _uiState.value.copy(showNewProjectDialog = false, newProjectName = "", newProjectLanguage = "Kotlin", newProjectDescription = "") }
    fun showImportDialog()      { _uiState.value = _uiState.value.copy(showImportDialog = true, importPath = "/data/data/com.termux/files/home/") }
    fun hideImportDialog()      { _uiState.value = _uiState.value.copy(showImportDialog = false, importPath = "") }
    fun updateProjectName(v: String)        { _uiState.value = _uiState.value.copy(newProjectName = v) }
    fun updateProjectLanguage(v: String)    { _uiState.value = _uiState.value.copy(newProjectLanguage = v) }
    fun updateProjectDescription(v: String) { _uiState.value = _uiState.value.copy(newProjectDescription = v) }
    fun updateImportPath(v: String)         { _uiState.value = _uiState.value.copy(importPath = v) }

    fun createProject(onCreated: (Long) -> Unit) {
        val state = _uiState.value
        if (state.newProjectName.isBlank()) { _uiState.value = state.copy(error = "Project name cannot be empty"); return }
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

    fun importProject(onImported: (Long) -> Unit) {
        val state = _uiState.value
        val path = state.importPath.trim()
        if (path.isBlank()) { _uiState.value = state.copy(error = "Enter a folder path"); return }

        val folderName = path.trimEnd('/').substringAfterLast('/')
        val displayName = if (folderName.isNotBlank()) folderName else "Imported Project"

        val language = try {
            val dir = java.io.File(path)
            when {
                dir.listFiles()?.any { it.name.endsWith(".kt") } == true   -> "Kotlin"
                dir.listFiles()?.any { it.name.endsWith(".java") } == true -> "Java"
                dir.listFiles()?.any { it.name.endsWith(".py") } == true   -> "Python"
                dir.listFiles()?.any { it.name.endsWith(".js") } == true   -> "JavaScript"
                else -> "Kotlin"
            }
        } catch (_: Exception) { "Kotlin" }

        viewModelScope.launch {
            val id = projectRepository.createProject(
                name = displayName,
                language = language,
                description = "Loaded from: $path"
            )
            hideImportDialog()
            onImported(id)
        }
    }

    fun deleteProject(project: Project) { viewModelScope.launch { projectRepository.deleteProject(project) } }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    fun showTemplateDialog() { _uiState.value = _uiState.value.copy(showTemplateDialog = true) }
    fun hideTemplateDialog() { _uiState.value = _uiState.value.copy(showTemplateDialog = false) }

    fun createFromTemplate(template: com.codemaster.aistudio.data.templates.ProjectTemplate, basePath: String) {
        viewModelScope.launch {
            val projectPath = "$basePath/${template.name.replace(" ", "-").lowercase()}"
            java.io.File(projectPath).mkdirs()
            template.files.forEach { (relPath, content) ->
                val file = java.io.File(projectPath, relPath)
                file.parentFile?.mkdirs()
                file.writeText(content)
            }
            val id = projectRepository.createProject(template.name, template.language, "Loaded from: $projectPath")
            hideTemplateDialog()
        }
    }
}
