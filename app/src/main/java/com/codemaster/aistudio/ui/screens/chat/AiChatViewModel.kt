package com.codemaster.aistudio.ui.screens.chat

import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.AiPersona
import com.codemaster.aistudio.data.model.AI_PERSONAS
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.repository.AiRepository
import com.codemaster.aistudio.data.repository.ChatRepository
import com.codemaster.aistudio.data.repository.FileSystemRepository
import com.codemaster.aistudio.data.repository.ProjectRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import com.codemaster.aistudio.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class OpenTab(
    val fileName: String,
    val filePath: String,
    val content: String,
    val isDirty: Boolean = false
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val attachedFileName: String? = null,
    val attachedFileContent: String? = null,
    val totalTokens: Int = 0,
    val error: String? = null,
    val successMessage: String? = null,
    val currentPersona: AiPersona = AI_PERSONAS.first(),
    val showPersonaPicker: Boolean = false,
    val projectContext: String? = null,
    val openTabs: List<OpenTab> = emptyList(),
    val activeTabIndex: Int = 0,
    val terminalOutput: String? = null
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val fsRepository: FileSystemRepository,
    private val projectRepository: ProjectRepository
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var currentProjectId: Long = -1L
    private var projectPath: String = ""

    override fun handleException(throwable: Throwable) {
        _uiState.value = _uiState.value.copy(error = throwable.message, isLoading = false)
    }
    private var autoSaveJobs = mutableMapOf<Int, Job>()

    fun init(projectId: Long) {
        currentProjectId = projectId
        viewModelScope.launch {
            val personaId = settingsRepository.getPersonaId()
            val persona = AI_PERSONAS.find { it.id == personaId } ?: AI_PERSONAS.first()
            _uiState.value = _uiState.value.copy(currentPersona = persona)

            // Use proper path resolution
            projectPath = projectRepository.getProjectPath(projectId)
            if (projectPath.isNotBlank()) loadProjectContext(projectPath)

            chatRepository.getMessagesForProject(projectId)
                .catch { }
                .collect { messages ->
                    _uiState.value = _uiState.value.copy(
                        messages = messages,
                        totalTokens = messages.sumOf { it.tokenCount }
                    )
                }
        }
    }

    private fun loadProjectContext(path: String) {
        viewModelScope.launch {
            try {
                val root = File(path)
                if (!root.exists()) return@launch
                val tree = buildFileTree(root, root, 0)
                _uiState.value = _uiState.value.copy(projectContext = tree)
            } catch (_: Exception) {}
        }
    }

    private fun buildFileTree(root: File, current: File, depth: Int): String {
        if (depth > 4) return ""
        val ignored = setOf(".git", "build", ".gradle", "node_modules", ".idea", "__pycache__")
        val sb = StringBuilder()
        current.listFiles()
            ?.filter { it.name !in ignored && !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ it.isFile }, { it.name }))
            ?.forEach { file ->
                val indent = "  ".repeat(depth)
                val rel = file.relativeTo(root).path
                if (file.isDirectory) {
                    sb.appendLine("$indent $rel/")
                    sb.append(buildFileTree(root, file, depth + 1))
                } else {
                    val size = if (file.length() < 1024) "${file.length()}B" else "${file.length() / 1024}KB"
                    sb.appendLine("$indent $rel ($size)")
                }
            }
        return sb.toString()
    }

    fun openFile(filePath: String) {
        val fullPath = if (filePath.startsWith("/")) filePath
                       else if (projectPath.isNotBlank()) "$projectPath/$filePath"
                       else filePath
        viewModelScope.launch {
            val existing = _uiState.value.openTabs.indexOfFirst { it.filePath == fullPath }
            if (existing >= 0) {
                _uiState.value = _uiState.value.copy(activeTabIndex = existing)
                return@launch
            }
            fsRepository.readFile(fullPath).fold(
                onSuccess = { content ->
                    val tab = OpenTab(fileName = File(fullPath).name, filePath = fullPath, content = content)
                    val tabs = _uiState.value.openTabs + tab
                    _uiState.value = _uiState.value.copy(openTabs = tabs, activeTabIndex = tabs.size - 1)
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = "Cannot open: ${it.message}") }
            )
        }
    }

    fun updateTabContent(index: Int, newContent: String) {
        val tabs = _uiState.value.openTabs.toMutableList()
        if (index >= tabs.size) return
        tabs[index] = tabs[index].copy(content = newContent, isDirty = true)
        _uiState.value = _uiState.value.copy(openTabs = tabs)
        autoSaveJobs[index]?.cancel()
        autoSaveJobs[index] = viewModelScope.launch {
            delay(1500)
            saveTab(index)
        }
    }

    fun saveTab(index: Int) {
        val tab = _uiState.value.openTabs.getOrNull(index) ?: return
        if (!tab.isDirty) return
        viewModelScope.launch {
            fsRepository.writeFile(tab.filePath, tab.content).fold(
                onSuccess = {
                    val tabs = _uiState.value.openTabs.toMutableList()
                    if (index < tabs.size) {
                        tabs[index] = tabs[index].copy(isDirty = false)
                        _uiState.value = _uiState.value.copy(openTabs = tabs)
                    }
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = "Save failed: ${it.message}") }
            )
        }
    }

    fun closeTab(index: Int) {
        autoSaveJobs[index]?.cancel()
        val tabs = _uiState.value.openTabs.toMutableList()
        if (index < tabs.size && tabs[index].isDirty) saveTab(index)
        tabs.removeAt(index)
        val newIdx = if (_uiState.value.activeTabIndex >= tabs.size) maxOf(0, tabs.size - 1)
                     else _uiState.value.activeTabIndex
        _uiState.value = _uiState.value.copy(openTabs = tabs, activeTabIndex = newIdx)
    }

    fun setActiveTab(index: Int) { _uiState.value = _uiState.value.copy(activeTabIndex = index) }

    fun explainCurrentFile() {
        val tab = _uiState.value.openTabs.getOrNull(_uiState.value.activeTabIndex) ?: run {
            _uiState.value = _uiState.value.copy(error = "No file open to explain"); return
        }
        _uiState.value = _uiState.value.copy(
            inputText = "Explain what this file does and highlight any issues:",
            attachedFileName = tab.fileName,
            attachedFileContent = tab.content.take(8000)
        )
        sendMessage()
    }

    fun saveContentAsFile(fileName: String, content: String) {
        viewModelScope.launch {
            val savePath = if (projectPath.isNotBlank()) "$projectPath/$fileName" else "/sdcard/$fileName"
            fsRepository.writeFile(savePath, content).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(successMessage = "Saved to $savePath") },
                onFailure = { _uiState.value = _uiState.value.copy(error = "Save failed: ${it.message}") }
            )
        }
    }

    fun injectTerminalOutput(output: String) {
        _uiState.value = _uiState.value.copy(
            terminalOutput = output,
            inputText = "I got this terminal output. What went wrong and how do I fix it?",
            attachedFileName = "terminal_output.txt",
            attachedFileContent = output.takeLast(4000)
        )
    }

    fun updateInput(text: String) { _uiState.value = _uiState.value.copy(inputText = text) }
    fun attachFile(name: String, content: String) { _uiState.value = _uiState.value.copy(attachedFileName = name, attachedFileContent = content) }
    fun clearAttachment() { _uiState.value = _uiState.value.copy(attachedFileName = null, attachedFileContent = null) }
    fun showPersonaPicker() { _uiState.value = _uiState.value.copy(showPersonaPicker = true) }
    fun hidePersonaPicker() { _uiState.value = _uiState.value.copy(showPersonaPicker = false) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun clearSuccess() { _uiState.value = _uiState.value.copy(successMessage = null) }

    fun selectPersona(persona: AiPersona) {
        viewModelScope.launch {
            settingsRepository.savePersonaId(persona.id)
            _uiState.value = _uiState.value.copy(currentPersona = persona, showPersonaPicker = false)
        }
    }

    fun requestCodeReview() {
        if (_uiState.value.attachedFileContent == null && _uiState.value.inputText.isBlank()) return
        _uiState.value = _uiState.value.copy(inputText = "Please do a thorough code review. Check for bugs, security issues, performance problems, and suggest improvements.")
        sendMessage()
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank() && state.attachedFileContent == null) return
        if (state.isLoading) return

        val attachedName = state.attachedFileName
        val displayText = if (attachedName != null) "$text\n $attachedName" else text

        val projectContextSection = state.projectContext?.let { "\n\nProject file tree:\n$it" } ?: ""
        val enrichedSystemPrompt = state.currentPersona.systemPrompt + projectContextSection

        viewModelScope.launch {
            val userMessage = ChatMessage(
                projectId = currentProjectId, role = "user",
                content = displayText,
                tokenCount = aiRepository.estimateTokens(displayText),
                attachedFileName = attachedName
            )
            chatRepository.saveMessage(userMessage)
            val updatedMessages = state.messages + userMessage
            _uiState.value = state.copy(inputText = "", isLoading = true, attachedFileName = null, attachedFileContent = null)

            val result = aiRepository.sendMessage(
                history = updatedMessages,
                userMessage = text,
                attachedFileContent = state.attachedFileContent,
                systemPrompt = enrichedSystemPrompt
            )
            result.fold(
                onSuccess = { response ->
                    chatRepository.saveMessage(ChatMessage(
                        projectId = currentProjectId, role = "assistant",
                        content = response, tokenCount = aiRepository.estimateTokens(response)
                    ))
                    _uiState.value = _uiState.value.copy(isLoading = false)
                },
                onFailure = { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
            )
        }
    }

    fun clearHistory() { viewModelScope.launch { chatRepository.clearMessagesForProject(currentProjectId) } }
}
