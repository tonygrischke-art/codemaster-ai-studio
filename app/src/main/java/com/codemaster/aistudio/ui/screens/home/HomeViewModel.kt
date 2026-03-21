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
    fun showAiBuilder() { _uiState.value = _uiState.value.copy(showAiBuilderDialog = true, aiBuilderPrompt = "", aiBuilderResult = null) }
    fun hideAiBuilder() { _uiState.value = _uiState.value.copy(showAiBuilderDialog = false) }
    fun updateAiBuilderPrompt(p: String) { _uiState.value = _uiState.value.copy(aiBuilderPrompt = p) }
    fun updateAiBuilderLanguage(l: String) { _uiState.value = _uiState.value.copy(aiBuilderLanguage = l) }

    fun buildAppWithAi(onCreated: (Long) -> Unit) {
        val state = _uiState.value
        if (state.aiBuilderPrompt.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAiBuilding = true)

            // We use a simple delimiter format that is easy to parse without regex
            // AI returns: NAME: AppName\nDESC: description\nFILE: filename\n<<<\ncontent\n>>>
            val systemPrompt = buildString {
                append("You are an expert ${state.aiBuilderLanguage} developer and code generator. ")
                append("When asked to build an app, you generate complete, production-quality code. ")
                append("You MUST respond using EXACTLY this format and no other text:\n")
                append("NAME: YourAppName\n")
                append("DESC: Brief one-line description\n")
                append("FILE: path/to/filename.ext\n")
                append("<<<\n")
                append("complete file content here\n")
                append(">>>\n")
                append("FILE: path/to/nextfile.ext\n")
                append("<<<\n")
                append("complete file content here\n")
                append(">>>\n")
                append("Generate at least 3-5 complete files. No explanations, no markdown, just the format above.")
            }

            val userPrompt = buildString {
                append("Build a complete ${state.aiBuilderLanguage} project: ${state.aiBuilderPrompt}. ")
                append("Generate all necessary files with full working code. ")
                append("Use the exact format specified.")
            }

            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = userPrompt,
                systemPrompt = systemPrompt
            )

            result.fold(
                onSuccess = { response ->
                    try {
                        val lines = response.lines()

                        // Extract project name and description
                        val projectName = lines
                            .firstOrNull { it.startsWith("NAME:") }
                            ?.removePrefix("NAME:")?.trim()
                            ?.replace(" ", "-")
                            ?.ifBlank { "AI-Project" } ?: "AI-Project"

                        val description = lines
                            .firstOrNull { it.startsWith("DESC:") }
                            ?.removePrefix("DESC:")?.trim()
                            ?.ifBlank { state.aiBuilderPrompt } ?: state.aiBuilderPrompt

                        // Create project directory on sdcard
                        val projectPath = "/data/data/com.termux/files/home/$projectName"
                        File(projectPath).mkdirs()

                        // Parse and write each file using simple state machine
                        var currentFileName = ""
                        var isCollecting = false
                        val currentContent = StringBuilder()
                        var filesCreated = 0

                        for (line in lines) {
                            when {
                                line.startsWith("FILE:") -> {
                                    // Save previous file if any
                                    if (currentFileName.isNotBlank() && currentContent.isNotBlank()) {
                                        val file = File(projectPath, currentFileName)
                                        file.parentFile?.mkdirs()
                                        file.writeText(currentContent.toString().trimEnd())
                                        filesCreated++
                                    }
                                    currentFileName = line.removePrefix("FILE:").trim()
                                    currentContent.clear()
                                    isCollecting = false
                                }
                                line.trim() == "<<<" -> {
                                    isCollecting = true
                                }
                                line.trim() == ">>>" -> {
                                    isCollecting = false
                                }
                                isCollecting -> {
                                    currentContent.appendLine(line)
                                }
                            }
                        }

                        // Save the last file
                        if (currentFileName.isNotBlank() && currentContent.isNotBlank()) {
                            val file = File(projectPath, currentFileName)
                            file.parentFile?.mkdirs()
                            file.writeText(currentContent.toString().trimEnd())
                            filesCreated++
                        }

                        // Always ensure README exists
                        val readme = File(projectPath, "README.md")
                        if (!readme.exists()) {
                            readme.writeText(
                                "# $projectName\n\n$description\n\nBuilt with CodeMaster AI Studio\n"
                            )
                            filesCreated++
                        }

                        // Register project in database
                        val id = projectRepository.createProject(
                            projectName,
                            state.aiBuilderLanguage,
                            "Loaded from: $projectPath"
                        )
                        projectRepository.getProjectById(id)?.let {
                            projectRepository.updateProject(it.copy(path = projectPath))
                        }

                        _uiState.value = _uiState.value.copy(
                            isAiBuilding = false,
                            aiBuilderResult = "Created $projectName with $filesCreated files at $projectPath"
                        )
                        hideAiBuilder()
                        onCreated(id)

                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isAiBuilding = false,
                            error = "AI Builder error: ${e.message}"
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isAiBuilding = false,
                        error = "AI error: ${e.message}"
                    )
                }
            )
        }
    }

    fun createProject(onCreated: (Long) -> Unit) {
        val state = _uiState.value
        if (state.newProjectName.isBlank()) return
        viewModelScope.launch {
            val id = projectRepository.createProject(
                state.newProjectName,
                state.newProjectLanguage,
                state.newProjectDescription
            )
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
