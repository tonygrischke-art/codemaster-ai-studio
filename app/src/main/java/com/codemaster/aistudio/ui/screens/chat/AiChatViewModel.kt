package com.codemaster.aistudio.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.AiPersona
import com.codemaster.aistudio.data.model.AI_PERSONAS
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.repository.AiRepository
import com.codemaster.aistudio.data.repository.ChatRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val attachedFileName: String? = null,
    val attachedFileContent: String? = null,
    val totalTokens: Int = 0,
    val error: String? = null,
    val currentPersona: AiPersona = AI_PERSONAS.first(),
    val showPersonaPicker: Boolean = false
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var currentProjectId: Long = -1L

    fun init(projectId: Long) {
        currentProjectId = projectId
        viewModelScope.launch {
            val personaId = settingsRepository.getPersonaId()
            val persona = AI_PERSONAS.find { it.id == personaId } ?: AI_PERSONAS.first()
            _uiState.value = _uiState.value.copy(currentPersona = persona)

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

    fun updateInput(text: String) { _uiState.value = _uiState.value.copy(inputText = text) }
    fun attachFile(fileName: String, content: String) { _uiState.value = _uiState.value.copy(attachedFileName = fileName, attachedFileContent = content) }
    fun clearAttachment() { _uiState.value = _uiState.value.copy(attachedFileName = null, attachedFileContent = null) }
    fun showPersonaPicker() { _uiState.value = _uiState.value.copy(showPersonaPicker = true) }
    fun hidePersonaPicker() { _uiState.value = _uiState.value.copy(showPersonaPicker = false) }

    fun selectPersona(persona: AiPersona) {
        viewModelScope.launch {
            settingsRepository.savePersonaId(persona.id)
            _uiState.value = _uiState.value.copy(currentPersona = persona, showPersonaPicker = false)
        }
    }

    fun requestCodeReview() {
        val attached = _uiState.value.attachedFileContent
        val input = _uiState.value.inputText
        if (attached == null && input.isBlank()) return
        val reviewPrompt = "Please do a thorough code review. Check for: bugs, security issues, performance problems, code style, and suggest improvements."
        _uiState.value = _uiState.value.copy(inputText = reviewPrompt)
        sendMessage()
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank() && state.attachedFileContent == null) return
        if (state.isLoading) return

        val attachedName = state.attachedFileName
        val newline = "\n"
        val displayText = if (attachedName != null) text + newline + "📎 " + attachedName else text

        viewModelScope.launch {
            val userMessage = ChatMessage(
                projectId = currentProjectId, role = "user",
                content = displayText,
                tokenCount = aiRepository.estimateTokens(displayText),
                attachedFileName = attachedName
            )
            chatRepository.saveMessage(userMessage)
            _uiState.value = state.copy(inputText = "", isLoading = true, attachedFileName = null, attachedFileContent = null)

            val result = aiRepository.sendMessage(
                history = _uiState.value.messages.dropLast(1),
                userMessage = text,
                attachedFileContent = state.attachedFileContent,
                systemPrompt = state.currentPersona.systemPrompt
            )

            result.fold(
                onSuccess = { response ->
                    chatRepository.saveMessage(ChatMessage(
                        projectId = currentProjectId, role = "assistant",
                        content = response, tokenCount = aiRepository.estimateTokens(response)
                    ))
                    _uiState.value = _uiState.value.copy(isLoading = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun clearHistory() { viewModelScope.launch { chatRepository.clearMessagesForProject(currentProjectId) } }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    fun saveContentAsFile(fileName: String, content: String) {
        viewModelScope.launch {
            val savePath = if (projectPath.isNotBlank()) "$projectPath/$fileName"
                           else "/sdcard/$fileName"
            fsRepository.writeFile(savePath, content).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(error = "Saved to $savePath") },
                onFailure = { _uiState.value = _uiState.value.copy(error = "Save failed: ${it.message}") }
            )
        }
    }

}
