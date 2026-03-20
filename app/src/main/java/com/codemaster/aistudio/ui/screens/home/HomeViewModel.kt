package com.codemaster.aistudio.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.Project
import com.codemaster.aistudio.data.repository.AiRepository
import com.codemaster.aistudio.data.repository.FileSystemRepository
import com.codemaster.aistudio.data.repository.ProjectRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
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
    val showFileBrowserDialog: Boolean = false,
    val importPath: String = "",
    // AI App Builder
    val showAiBuilderDialog: Boolean = false,
    val aiBuilderPrompt: String = "",
    val aiBuilderLanguage: String = "Kotlin",
    val isAiBuilding: Boolean = false,
    val aiBuilderResult: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val fsRepository: FileSystemRepository,
    private val aiRepository: AiRepository,
    private val settingsRepository: SettingsRepository
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
    fun showFileBrowserDialog() { _uiState.value = _uiState.value.copy(showFileBrowserDialog = true) }
    fun hideFileBrowserDialog() { _uiState.value = _uiState.value.copy(showFileBrowserDialog = false) }
    fun updateImportPath(p: String) { _uiState.value = _uiState.value.copy(importPath = p) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    // AI App Builder
    fun showAiBuilder() { _uiState.value = _uiState.value.copy(showAiBuilderDialog = true, aiBuilderPrompt = "", aiBuilderResult = null) }
    fun hideAiBuilder() { _uiState.value = _uiState.value.copy(showAiBuilderDialog = false) }
    fun updateAiBuilderPrompt(p: String) { _uiState.value = _uiState.value.copy(aiBuilderPrompt = p) }
    fun updateAiBuilderLanguage(l: String) { _uiState.value = _uiState.value.copy(aiBuilderLanguage = l) }

    fun buildAppWithAi(onCreated: (Long) -> Unit) {
        val state = _uiState.value
        if (state.aiBuilderPrompt.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAiBuilding = true)
            val prompt = """You are an expert ${state.aiBuilderLanguage} developer.
Create a complete, working project for: ${state.aiBuilderPrompt}

Respond with a JSON object like this:
{
  "projectName": "MyApp",
  "description": "Brief description",
  "files": {
    "MainActivity.kt": "full file content here",
    "README.md": "content here"
  }
}

Return ONLY valid JSON, no markdown, no explanation."""

            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = prompt,
                systemPrompt = "You are a code generation engine. Return only valid JSON."
            )
            result.fold(
                onSuccess = { response ->
                    try {
                        // Parse the JSON response
                        val clean = response.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        val nameMatch = Regex(""projectName"\s*:\s*"([^"]+)"").find(clean)
                        val descMatch = Regex(""description"\s*:\s*"([^"]+)"").find(clean)
                        val projectName = nameMatch?.groupValues?.get(1) ?: "AI Generated App"
                        val description = descMatch?.groupValues?.get(1) ?: state.aiBuilderPrompt

                        // Create project
                        val id = projectRepository.createProject(projectName, state.aiBuilderLanguage, description)

                        // Create files on sdcard
                        val projectPath = "/sdcard/$projectName"
                        File(projectPath).mkdirs()

                        // Extract files from JSON
                        val filesMatch = Regex(""files"\s*:\s*\{(.+)\}", RegexOption.DOT_MATCHES_ALL).find(clean)
                        filesMatch?.let { fm ->
                            val filesContent = fm.groupValues[1]
                            val filePattern = Regex(""([^"]+)"\s*:\s*"((?:[^"\\]|\\.)*)"")
                            filePattern.findAll(filesContent).forEach { fileMatch ->
                                val fileName = fileMatch.groupValues[1]
                                val fileContent = fileMatch.groupValues[2]
                                    .replace("\n", "
").replace("\t", "	").replace("\"", """)
                                File(projectPath, fileName).apply {
                                    parentFile?.mkdirs()
                                    writeText(fileContent)
                                }
                            }
                        }

                        // Update project path
                        projectRepository.getProjectById(id)?.let {
                            projectRepository.updateProject(it.copy(path = projectPath, description = "Loaded from: $projectPath"))
                        }

                        _uiState.value = _uiState.value.copy(isAiBuilding = false, aiBuilderResult = "Created $projectName at $projectPath")
                        hideAiBuilder()
                        onCreated(id)
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(isAiBuilding = false, error = "AI Builder failed: ${e.message}")
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isAiBuilding = false, error = "AI error: ${e.message}")
                }
            )
        }
    }

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
            val id = projectRepository.createProject(name, "Kotlin", "Loaded from: $path")
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
