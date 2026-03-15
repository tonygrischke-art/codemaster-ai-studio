package com.codemaster.aistudio.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.CodeFile
import com.codemaster.aistudio.data.repository.ProjectRepository
import com.codemaster.aistudio.data.dao.CodeFileDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val files: List<CodeFile> = emptyList(),
    val currentFile: CodeFile? = null,
    val content: String = "",
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val showFileTree: Boolean = true,
    val showNewFileDialog: Boolean = false,
    val newFileName: String = "",
    val error: String? = null,
    val projectName: String = ""
)

@HiltViewModel
class CodeEditorViewModel @Inject constructor(
    private val codeFileDao: CodeFileDao,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var currentProjectId: Long = -1L

    fun init(projectId: Long, fileId: Long) {
        currentProjectId = projectId
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            _uiState.value = _uiState.value.copy(projectName = project?.name ?: "Editor")

            codeFileDao.getFilesForProject(projectId)
                .catch { }
                .collect { files ->
                    _uiState.value = _uiState.value.copy(files = files)
                    if (_uiState.value.currentFile == null && files.isNotEmpty()) {
                        val target = if (fileId != -1L) files.find { it.id == fileId } else files.first()
                        target?.let { openFile(it) }
                    }
                }
        }
    }

    fun openFile(file: CodeFile) {
        _uiState.value = _uiState.value.copy(currentFile = file, content = file.content, isDirty = false)
    }

    fun updateContent(content: String) {
        _uiState.value = _uiState.value.copy(content = content, isDirty = true)
    }

    fun saveCurrentFile() {
        val state = _uiState.value
        val file = state.currentFile ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            codeFileDao.updateFile(file.copy(content = state.content, lastModified = System.currentTimeMillis()))
            _uiState.value = _uiState.value.copy(isSaving = false, isDirty = false)
        }
    }

    fun toggleFileTree() { _uiState.value = _uiState.value.copy(showFileTree = !_uiState.value.showFileTree) }
    fun showNewFileDialog() { _uiState.value = _uiState.value.copy(showNewFileDialog = true) }
    fun hideNewFileDialog() { _uiState.value = _uiState.value.copy(showNewFileDialog = false, newFileName = "") }
    fun updateNewFileName(name: String) { _uiState.value = _uiState.value.copy(newFileName = name) }

    fun createFile() {
        val name = _uiState.value.newFileName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            val language = when {
                name.endsWith(".kt") -> "kotlin"
                name.endsWith(".java") -> "java"
                name.endsWith(".py") -> "python"
                name.endsWith(".js") || name.endsWith(".ts") -> "javascript"
                name.endsWith(".xml") -> "xml"
                else -> "text"
            }
            val id = codeFileDao.insertFile(
                CodeFile(projectId = currentProjectId, name = name, path = name, language = language)
            )
            hideNewFileDialog()
            val file = codeFileDao.getFileById(id)
            file?.let { openFile(it) }
        }
    }

    fun deleteFile(file: CodeFile) {
        viewModelScope.launch {
            codeFileDao.deleteFile(file)
            if (_uiState.value.currentFile?.id == file.id) {
                _uiState.value = _uiState.value.copy(currentFile = null, content = "")
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
