package com.codemaster.aistudio.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.repository.AiRepository
import com.codemaster.aistudio.data.repository.DiskFile
import com.codemaster.aistudio.data.repository.FileSystemRepository
import com.codemaster.aistudio.data.repository.ProjectRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CodeError(val line: Int, val message: String, val severity: String = "error")

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
    val error: String? = null,
    val successMessage: String? = null,
    val isAiWriting: Boolean = false,
    val aiSuggestion: String? = null,
    val isLoadingAiSuggestion: Boolean = false,
    val liveErrors: List<CodeError> = emptyList(),
    val showAiPanel: Boolean = false,
    val aiPrompt: String = ""
)

@HiltViewModel
class CodeEditorViewModel @Inject constructor(
    private val fsRepository: FileSystemRepository,
    private val projectRepository: ProjectRepository,
    private val aiRepository: AiRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
    private var errorCheckJob: Job? = null
    private var suggestionJob: Job? = null
    private var autoSaveJob: Job? = null

    fun init(projectId: Long, fileId: Long) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            val name = project?.name ?: "Editor"
            // Use the proper path resolution
            val rootPath = projectRepository.getProjectPath(projectId)
            _uiState.value = _uiState.value.copy(
                projectName = name,
                rootPath = rootPath,
                newFileDir = rootPath
            )
            if (rootPath.isNotBlank()) scanFiles(rootPath)
        }
    }

    fun scanFiles(path: String = _uiState.value.rootPath) {
        if (path.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFiles = true)
            fsRepository.scanDirectory(path).fold(
                onSuccess = { files ->
                    _uiState.value = _uiState.value.copy(
                        files = files, rootPath = path, newFileDir = path,
                        isLoadingFiles = false, expandedDirs = setOf(path)
                    )
                },
                onFailure = { e -> _uiState.value = _uiState.value.copy(isLoadingFiles = false, error = e.message) }
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
        if (_uiState.value.isDirty) saveCurrentFile()
        viewModelScope.launch {
            fsRepository.readFile(file.path).fold(
                onSuccess = { content ->
                    val lang = fsRepository.getLanguageFromExtension(file.extension)
                    val recent = (_uiState.value.recentFiles.filter { it.path != file.path } + file).takeLast(10)
                    _uiState.value = _uiState.value.copy(
                        currentFile = file, content = content, language = lang,
                        isDirty = false, recentFiles = recent,
                        liveErrors = emptyList(), aiSuggestion = null
                    )
                },
                onFailure = { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            )
        }
    }

    fun updateContent(content: String) {
        _uiState.value = _uiState.value.copy(content = content, isDirty = true, aiSuggestion = null)
        scheduleErrorCheck(content)
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(2000)
            saveCurrentFile(silent = true)
        }
    }

    private fun scheduleErrorCheck(content: String) {
        errorCheckJob?.cancel()
        errorCheckJob = viewModelScope.launch {
            delay(800)
            val errors = detectErrors(content, _uiState.value.language)
            _uiState.value = _uiState.value.copy(liveErrors = errors)
        }
    }

    private fun detectErrors(content: String, language: String): List<CodeError> {
        val errors = mutableListOf<CodeError>()
        val lines = content.lines()
        lines.forEachIndexed { idx, line ->
            val lineNum = idx + 1
            if (line.contains("TODO(") || line.contains("FIXME")) {
                errors.add(CodeError(lineNum, "TODO/FIXME needs attention", "warning"))
            }
            if (line.length > 120) {
                errors.add(CodeError(lineNum, "Line too long (${line.length} chars)", "warning"))
            }
            when (language) {
                "python" -> {
                    if (line.trimStart().startsWith("except:")) {
                        errors.add(CodeError(lineNum, "Bare except — specify exception type", "warning"))
                    }
                }
                "json" -> {
                    val nextLine = lines.getOrNull(idx + 1)?.trim() ?: ""
                    if (line.trimEnd().endsWith(",") && (nextLine.startsWith("}") || nextLine.startsWith("]"))) {
                        errors.add(CodeError(lineNum, "Trailing comma in JSON", "error"))
                    }
                }
            }
        }
        if (language in listOf("kotlin", "java")) {
            val opens = content.count { it == '{' }
            val closes = content.count { it == '}' }
            if (opens != closes) {
                errors.add(CodeError(0, "Unbalanced braces: $opens { vs $closes }", "error"))
            }
        }
        return errors
    }

    fun requestInlineSuggestion() {
        val state = _uiState.value
        if (state.content.isBlank()) return
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingAiSuggestion = true, aiSuggestion = null)
            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = "Continue this ${state.language} code:\n\n${state.content.takeLast(2000)}",
                systemPrompt = "You are a code completion engine. Return only raw code to insert next, no explanation, no markdown."
            )
            result.fold(
                onSuccess = { suggestion ->
                    val clean = suggestion.removePrefix("```${state.language}").removePrefix("```").removeSuffix("```").trim()
                    _uiState.value = _uiState.value.copy(aiSuggestion = clean, isLoadingAiSuggestion = false)
                },
                onFailure = { _uiState.value = _uiState.value.copy(isLoadingAiSuggestion = false) }
            )
        }
    }

    fun acceptSuggestion() {
        val suggestion = _uiState.value.aiSuggestion ?: return
        val newContent = _uiState.value.content + "\n" + suggestion
        _uiState.value = _uiState.value.copy(content = newContent, isDirty = true, aiSuggestion = null)
    }

    fun dismissSuggestion() { _uiState.value = _uiState.value.copy(aiSuggestion = null) }

    fun aiWriteToFile(instruction: String) {
        val state = _uiState.value
        val file = state.currentFile ?: run {
            _uiState.value = _uiState.value.copy(error = "No file open"); return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAiWriting = true)
            val prompt = buildString {
                appendLine("File: ${file.name}")
                appendLine("Current content:")
                appendLine(state.content.take(4000))
                appendLine()
                appendLine("Instruction: $instruction")
                appendLine()
                append("Return the COMPLETE updated file content only. No markdown, no explanation.")
            }
            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = prompt,
                systemPrompt = "You are a code editing assistant. Return only the complete file content, nothing else."
            )
            result.fold(
                onSuccess = { newContent ->
                    val clean = newContent
                        .removePrefix("```${state.language}").removePrefix("```")
                        .removeSuffix("```").trim()
                    fsRepository.writeFile(file.path, clean)
                    _uiState.value = _uiState.value.copy(content = clean, isDirty = false, isAiWriting = false, successMessage = "AI wrote changes to ${file.name}")
                },
                onFailure = { e -> _uiState.value = _uiState.value.copy(isAiWriting = false, error = "AI write failed: ${e.message}") }
            )
        }
    }

    fun updateAiPrompt(p: String) { _uiState.value = _uiState.value.copy(aiPrompt = p) }
    fun toggleAiPanel() { _uiState.value = _uiState.value.copy(showAiPanel = !_uiState.value.showAiPanel) }
    fun updateCursor(line: Int, col: Int) { _uiState.value = _uiState.value.copy(cursorLine = line, cursorCol = col) }

    fun saveCurrentFile(silent: Boolean = false) {
        val state = _uiState.value
        val file = state.currentFile ?: return
        if (!state.isDirty) return
        viewModelScope.launch {
            if (!silent) _uiState.value = state.copy(isSaving = true)
            fsRepository.writeFile(file.path, state.content).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false, isDirty = false,
                        successMessage = if (silent) null else "Saved"
                    )
                },
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
                    hideNewFileDialog(); scanFiles()
                    openFile(DiskFile(name = name, path = path, relativePath = name, size = 0, lastModified = System.currentTimeMillis(), isDirectory = false, extension = name.substringAfterLast(".", "")))
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
    fun clearSuccess() { _uiState.value = _uiState.value.copy(successMessage = null) }

    fun getVisibleFiles(): List<DiskFile> {
        val state = _uiState.value
        val query = state.searchQuery.trim()
        val root = state.rootPath
        return if (query.isNotBlank()) {
            state.files.filter {
                !it.isDirectory && (it.name.contains(query, ignoreCase = true) || it.relativePath.contains(query, ignoreCase = true))
            }
        } else {
            state.files.filter { file ->
                val parentPath = file.path.substringBeforeLast("/")
                parentPath == root || parentPath in state.expandedDirs || state.expandedDirs.any { file.path.startsWith(it + "/") && file.depth == 1 }
            }
        }
    }
}
