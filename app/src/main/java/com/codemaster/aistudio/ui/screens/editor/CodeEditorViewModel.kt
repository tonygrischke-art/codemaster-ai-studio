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

data class CodeError(
    val line: Int,
    val message: String,
    val severity: String = "error" // "error" or "warning"
)

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
    // AI features
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

    fun init(projectId: Long, fileId: Long) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            val name = project?.name ?: "Editor"
            val rootPath = project?.path ?: run {
                val desc = project?.description ?: ""
                when {
                    desc.startsWith("Loaded from: ") -> desc.removePrefix("Loaded from: ")
                    desc.startsWith("Imported from: ") -> desc.removePrefix("Imported from: ")
                    else -> ""
                }
            }
            _uiState.value = _uiState.value.copy(projectName = name, rootPath = rootPath, newFileDir = rootPath)
            if (rootPath.isNotBlank()) scanFiles(rootPath)
        }
    }

    fun scanFiles(path: String = _uiState.value.rootPath) {
        if (path.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFiles = true)
            fsRepository.scanDirectory(path).fold(
                onSuccess = { files ->
                    _uiState.value = _uiState.value.copy(files = files, rootPath = path, newFileDir = path, isLoadingFiles = false, expandedDirs = setOf(path))
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
                    _uiState.value = _uiState.value.copy(currentFile = file, content = content, language = lang, isDirty = false, recentFiles = recent, liveErrors = emptyList(), aiSuggestion = null)
                },
                onFailure = { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            )
        }
    }

    fun updateContent(content: String) {
        _uiState.value = _uiState.value.copy(content = content, isDirty = true, aiSuggestion = null)
        scheduleErrorCheck(content)
    }

    // ── Live error detection ─────────────────────────────────
    private fun scheduleErrorCheck(content: String) {
        errorCheckJob?.cancel()
        errorCheckJob = viewModelScope.launch {
            delay(800) // debounce
            val errors = detectBasicErrors(content, _uiState.value.language)
            _uiState.value = _uiState.value.copy(liveErrors = errors)
        }
    }

    private fun detectBasicErrors(content: String, language: String): List<CodeError> {
        val errors = mutableListOf<CodeError>()
        val lines = content.lines()
        lines.forEachIndexed { idx, line ->
            val lineNum = idx + 1
            when (language) {
                "kotlin", "java" -> {
                    if (line.trimEnd().endsWith("{") && !line.contains("//")) {
                        // Check for unclosed braces (very basic)
                    }
                    if (line.contains("TODO(") || line.contains("FIXME")) {
                        errors.add(CodeError(lineNum, "TODO/FIXME marker", "warning"))
                    }
                    if (line.length > 120) {
                        errors.add(CodeError(lineNum, "Line too long (${line.length} chars)", "warning"))
                    }
                }
                "python" -> {
                    if (line.contains("\t") && content.contains("    ")) {
                        errors.add(CodeError(lineNum, "Mixed tabs and spaces", "error"))
                    }
                    if (line.trimStart().startsWith("except:")) {
                        errors.add(CodeError(lineNum, "Bare except clause — specify exception type", "warning"))
                    }
                }
                "json" -> {
                    if (line.trimEnd().endsWith(",") && lines.getOrNull(idx + 1)?.trim()?.let { it.startsWith("}") || it.startsWith("]") } == true) {
                        errors.add(CodeError(lineNum, "Trailing comma in JSON", "error"))
                    }
                }
            }
        }
        // Check bracket balance
        val opens = content.count { it == '{' }
        val closes = content.count { it == '}' }
        if (opens != closes && (language == "kotlin" || language == "java")) {
            errors.add(CodeError(0, "Unbalanced braces: $opens { vs $closes }", "error"))
        }
        return errors
    }

    // ── Inline AI suggestion ─────────────────────────────────
    fun requestInlineSuggestion() {
        val state = _uiState.value
        val file = state.currentFile ?: return
        val content = state.content
        if (content.isBlank()) return

        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingAiSuggestion = true, aiSuggestion = null)
            val prompt = "Continue or complete this ${state.language} code. Return ONLY the code to insert next, no explanation:\n\n${content.takeLast(2000)}"
            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = prompt,
                systemPrompt = "You are a code completion engine. Return only raw code, no markdown, no explanation."
            )
            result.fold(
                onSuccess = { suggestion ->
                    _uiState.value = _uiState.value.copy(aiSuggestion = suggestion.trim(), isLoadingAiSuggestion = false)
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

    // ── AI writes directly to file ───────────────────────────
    fun aiWriteToFile(instruction: String) {
        val state = _uiState.value
        val file = state.currentFile ?: run {
            _uiState.value = _uiState.value.copy(error = "No file open")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAiWriting = true)
            val prompt = """You are editing the file: ${file.name}
Current content:
${state.content.take(4000)}

Instruction: $instruction

Return the COMPLETE updated file content only. No markdown, no explanation, just the raw file content."""

            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = prompt,
                systemPrompt = "You are a code editing assistant. Return only the complete file content, nothing else."
            )
            result.fold(
                onSuccess = { newContent ->
                    // Strip markdown fences if AI added them
                    val clean = newContent
                        .removePrefix("```${state.language}").removePrefix("```")
                        .removeSuffix("```").trim()
                    _uiState.value = _uiState.value.copy(content = clean, isDirty = true, isAiWriting = false)
                    // Auto-save
                    fsRepository.writeFile(file.path, clean)
                    _uiState.value = _uiState.value.copy(isDirty = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isAiWriting = false, error = "AI write failed: ${e.message}")
                }
            )
        }
    }

    fun updateAiPrompt(p: String) { _uiState.value = _uiState.value.copy(aiPrompt = p) }
    fun toggleAiPanel() { _uiState.value = _uiState.value.copy(showAiPanel = !_uiState.value.showAiPanel) }

    fun updateCursor(line: Int, col: Int) { _uiState.value = _uiState.value.copy(cursorLine = line, cursorCol = col) }

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
            if (_uiState.value.currentFile?.path == file.path) _uiState.value = _uiState.value.copy(currentFile = null, content = "")
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
            state.files.filter { !it.isDirectory && (it.name.contains(query, ignoreCase = true) || it.relativePath.contains(query, ignoreCase = true)) }
        } else {
            state.files.filter { file ->
                val parentPath = file.path.substringBeforeLast("/")
                parentPath == root || parentPath in state.expandedDirs || state.expandedDirs.any { file.path.startsWith(it + "/") && file.depth == 1 }
            }
        }
    }
}
