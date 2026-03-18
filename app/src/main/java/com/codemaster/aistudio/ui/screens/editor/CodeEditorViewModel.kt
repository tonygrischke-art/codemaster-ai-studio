package com.codemaster.aistudio.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.DiskFile
import com.codemaster.aistudio.data.repository.FileSystemRepository
import com.codemaster.aistudio.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val projectName: String = "",
    val rootPath: String = "",
    val files: List<DiskFile> = emptyList(),
    val expandedDirs: Set<String> = emptySet(),
    val currentFile: DiskFile? = null,
    val content: String = "",
    val language: String = "plaintext",
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isLoadingFiles: Boolean = false,
    val showNewFileDialog: Boolean = false,
    val newFileName: String = "",
    val newFileDir: String = "",
    val showFileTree: Boolean = true,
    val cursorLine: Int = 1,
    val cursorCol: Int = 1,
    val recentFiles: List<DiskFile> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null
)

@HiltViewModel
class CodeEditorViewModel @Inject constructor(
    private val fsRepository: FileSystemRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun init(projectId: Long, fileId: Long) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            val name = project?.name ?: "Editor"
            val desc = project?.description ?: ""

            // Extract path from description if it was an imported project
            val rootPath = when {
                desc.startsWith("Loaded from: ") -> desc.removePrefix("Loaded from: ")
                desc.startsWith("Imported from: ") -> desc.removePrefix("Imported from: ")
                else -> ""
            }

            _uiState.value = _uiState.value.copy(
                projectName = name,
                rootPath = rootPath,
                newFileDir = rootPath
            )

            if (rootPath.isNotBlank()) {
                scanFiles(rootPath)
            }
        }
    }

    fun scanFiles(path: String = _uiState.value.rootPath) {
        if (path.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFiles = true)
            fsRepository.scanDirectory(path).fold(
                onSuccess = { files ->
                    _uiState.value = _uiState.value.copy(
                        files = files,
                        rootPath = path,
                        newFileDir = path,
                        isLoadingFiles = false,
                        // Auto-expand root
                        expandedDirs = setOf(path)
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoadingFiles = false, error = e.message)
                }
            )
        }
    }

    fun toggleDir(dirPath: String) {
        val expanded = _uiState.value.expandedDirs.toMutableSet()
        if (dirPath in expanded) expanded.remove(dirPath) else expanded.add(dirPath)
        _uiState.value = _uiState.value.copy(expandedDirs = expanded)
    }

    fun openFile(file: DiskFile) {
        if (file.isDirectory) { toggleDir(file.path); return }
        // Save current file first
        if (_uiState.value.isDirty) saveCurrentFile()

        viewModelScope.launch {
            fsRepository.readFile(file.path).fold(
                onSuccess = { content ->
                    val lang = fsRepository.getLanguageFromExtension(file.extension)
                    val recent = (_uiState.value.recentFiles.filter { it.path != file.path } + file).takeLast(10)
                    _uiState.value = _uiState.value.copy(
                        currentFile = file,
                        content = content,
                        language = lang,
                        isDirty = false,
                        recentFiles = recent
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            )
        }
    }

    fun updateContent(content: String) {
        _uiState.value = _uiState.value.copy(content = content, isDirty = true)
    }

    fun updateCursor(line: Int, col: Int) {
        _uiState.value = _uiState.value.copy(cursorLine = line, cursorCol = col)
    }

    fun saveCurrentFile() {
        val state = _uiState.value
        val file = state.currentFile ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            fsRepository.writeFile(file.path, state.content).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(isSaving = false, isDirty = false) },
                onFailure = { e -> _uiState.value = _uiState.value.copy(isSaving = false, error = e.message) }
            )
        }
    }

    fun showNewFileDialog(dirPath: String = _uiState.value.rootPath) {
        _uiState.value = _uiState.value.copy(showNewFileDialog = true, newFileName = "", newFileDir = dirPath)
    }

    fun hideNewFileDialog() { _uiState.value = _uiState.value.copy(showNewFileDialog = false) }
    fun updateNewFileName(v: String) { _uiState.value = _uiState.value.copy(newFileName = v) }

    fun createFile() {
        val state = _uiState.value
        val name = state.newFileName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            fsRepository.createFile(state.newFileDir, name).fold(
                onSuccess = { path ->
                    hideNewFileDialog()
                    scanFiles()
                    // Open the new file
                    val newFile = DiskFile(
                        name = name, path = path,
                        relativePath = name,
                        size = 0, lastModified = System.currentTimeMillis(),
                        isDirectory = false,
                        extension = name.substringAfterLast('.', "")
                    )
                    openFile(newFile)
                },
                onFailure = { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            )
        }
    }

    fun deleteFile(file: DiskFile) {
        viewModelScope.launch {
            fsRepository.deleteFile(file.path)
            if (_uiState.value.currentFile?.path == file.path) {
                _uiState.value = _uiState.value.copy(currentFile = null, content = "")
            }
            scanFiles()
        }
    }

    fun toggleFileTree() { _uiState.value = _uiState.value.copy(showFileTree = !_uiState.value.showFileTree) }
    fun updateSearch(q: String) { _uiState.value = _uiState.value.copy(searchQuery = q) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    fun getVisibleFiles(): List<DiskFile> {
        val state = _uiState.value
        val query = state.searchQuery.trim()
        val root = state.rootPath

        return if (query.isNotBlank()) {
            // Search mode - show all matching files flat
            state.files.filter {
                !it.isDirectory && (
                    it.name.contains(query, ignoreCase = true) ||
                    it.relativePath.contains(query, ignoreCase = true)
                )
            }
        } else {
            // Tree mode - only show files whose parent dirs are expanded
            state.files.filter { file ->
                val parentPath = file.path.substringBeforeLast('/')
                parentPath == root || parentPath in state.expandedDirs ||
                    state.expandedDirs.any { file.path.startsWith(it + "/") && file.depth == 1 }
            }
        }
    }
}
