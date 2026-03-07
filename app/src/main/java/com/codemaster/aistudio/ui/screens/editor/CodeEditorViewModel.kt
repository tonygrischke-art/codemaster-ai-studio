package com.codemaster.aistudio.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.CodeMasterDatabase
import com.codemaster.aistudio.data.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val project: Project? = null,
    val files: List<String> = emptyList(),           // file names
    val fileContents: Map<String, String> = emptyMap(), // fileName -> content
    val activeFileName: String? = null,
    val activeFileContent: String? = null,
    val hasUnsavedChanges: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class CodeEditorViewModel @Inject constructor(
    private val db: CodeMasterDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun init(projectId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val project = db.projectDao().getProjectById(projectId)
            if (project != null) {
                // For now, files are stored as a simple map in the project path field
                // Phase 3 will move this to real filesystem
                val files = listOf("main.kt") // placeholder
                val contents = mapOf("main.kt" to "// Start coding here\n\nfun main() {\n    println(\"Hello from CodeMaster!\")\n}")
                _uiState.update {
                    it.copy(
                        project = project,
                        files = files,
                        fileContents = contents,
                        activeFileName = files.firstOrNull(),
                        activeFileContent = contents[files.firstOrNull() ?: ""],
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Project not found") }
            }
        }
    }

    // Called when Monaco fires onChange
    fun onContentChange(newContent: String) {
        _uiState.update { state ->
            val updatedContents = state.fileContents.toMutableMap().apply {
                state.activeFileName?.let { put(it, newContent) }
            }
            state.copy(
                fileContents = updatedContents,
                activeFileContent = newContent,
                hasUnsavedChanges = true
            )
        }
    }

    // Save — gets live value from Monaco via suspend lambda
    fun saveFile(getLiveContent: suspend () -> String) {
        viewModelScope.launch {
            val liveContent = getLiveContent()
            val state = _uiState.value
            val updatedContents = state.fileContents.toMutableMap().apply {
                state.activeFileName?.let { put(it, liveContent) }
            }
            _uiState.update {
                it.copy(
                    fileContents = updatedContents,
                    activeFileContent = liveContent,
                    hasUnsavedChanges = false
                )
            }
            // TODO: Phase 3 - persist to filesystem here
        }
    }

    // Switch files — saves current file first via suspend lambda
    fun switchFile(fileName: String, getLiveContent: suspend () -> String) {
        viewModelScope.launch {
            val state = _uiState.value
            // Save current file content before switching
            if (state.hasUnsavedChanges) {
                val liveContent = getLiveContent()
                val updatedContents = state.fileContents.toMutableMap().apply {
                    state.activeFileName?.let { put(it, liveContent) }
                }
                _uiState.update {
                    it.copy(
                        fileContents = updatedContents,
                        activeFileName = fileName,
                        activeFileContent = updatedContents[fileName] ?: "",
                        hasUnsavedChanges = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        activeFileName = fileName,
                        activeFileContent = it.fileContents[fileName] ?: ""
                    )
                }
            }
        }
    }
}
