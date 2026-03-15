package com.codemaster.aistudio.ui.screens.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.repository.AiRepository
import com.codemaster.aistudio.data.repository.ChatRepository
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
    val projectName: String = "Chat"
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentProjectId: Long = -1L

    fun init(projectId: Long) {
        currentProjectId = projectId
        viewModelScope.launch {
            chatRepository.getMessagesForProject(projectId)
                .catch { }
                .collect { messages ->
                    val tokens = messages.sumOf { it.tokenCount }
                    _uiState.value = _uiState.value.copy(messages = messages, totalTokens = tokens)
                }
        }
    }

    fun updateInput(text: String) { _uiState.value = _uiState.value.copy(inputText = text) }

    fun attachFile(fileName: String, content: String) {
        _uiState.value = _uiState.value.copy(
            attachedFileName = fileName,
            attachedFileContent = content
        )
    }

    fun clearAttachment() {
        _uiState.value = _uiState.value.copy(attachedFileName = null, attachedFileContent = null)
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank() && state.attachedFileContent == null) return
        if (state.isLoading) return

        val displayText = if (state.attachedFileName != null) "$text
📎 ${state.attachedFileName}" else text

        viewModelScope.launch {
            val userMessage = ChatMessage(
                projectId = currentProjectId,
                role = "user",
                content = displayText,
                tokenCount = aiRepository.estimateTokens(displayText),
                attachedFileName = state.attachedFileName
            )
            chatRepository.saveMessage(userMessage)
            _uiState.value = state.copy(inputText = "", isLoading = true, attachedFileName = null, attachedFileContent = null)

            val result = aiRepository.sendMessage(
                history = _uiState.value.messages.dropLast(1),
                userMessage = text,
                attachedFileContent = state.attachedFileContent
            )

            result.fold(
                onSuccess = { response ->
                    val aiMessage = ChatMessage(
                        projectId = currentProjectId,
                        role = "assistant",
                        content = response,
                        tokenCount = aiRepository.estimateTokens(response)
                    )
                    chatRepository.saveMessage(aiMessage)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            chatRepository.clearMessagesForProject(currentProjectId)
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
