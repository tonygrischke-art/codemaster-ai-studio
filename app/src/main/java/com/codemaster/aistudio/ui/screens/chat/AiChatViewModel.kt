package com.codemaster.aistudio.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.CodeMasterDatabase
import com.codemaster.aistudio.data.model.AiProvider
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.model.MessageRole
import com.codemaster.aistudio.data.repository.AiRepository
import com.codemaster.aistudio.data.repository.AiResult
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val selectedProvider: AiProvider = AiProvider.GEMINI,
    val errorMessage: String? = null,
    val projectId: String = ""
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val settingsRepository: SettingsRepository,
    private val db: CodeMasterDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun init(projectId: String) {
        _uiState.update { it.copy(projectId = projectId) }
        // Load settings for default provider
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            _uiState.update { it.copy(selectedProvider = settings.defaultProvider) }
        }
        // Observe messages for project
        viewModelScope.launch {
            db.chatMessageDao().getMessagesForProject(projectId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun switchProvider(provider: AiProvider) {
        _uiState.update { it.copy(selectedProvider = provider) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank() || state.isLoading) return

        viewModelScope.launch {
            // Save user message
            val userMsg = ChatMessage(
                projectId = state.projectId,
                role = MessageRole.USER,
                content = text,
                provider = state.selectedProvider
            )
            db.chatMessageDao().insertMessage(userMsg)
            _uiState.update { it.copy(inputText = "", isLoading = true, errorMessage = null) }

            // Call AI
            val result = aiRepository.sendMessage(
                userMessage = text,
                history = state.messages,
                provider = state.selectedProvider
            )

            when (result) {
                is AiResult.Success -> {
                    val aiMsg = ChatMessage(
                        projectId = state.projectId,
                        role = MessageRole.ASSISTANT,
                        content = result.content,
                        provider = state.selectedProvider,
                        hasCodeBlock = result.content.contains("```")
                    )
                    db.chatMessageDao().insertMessage(aiMsg)
                    _uiState.update { it.copy(isLoading = false) }
                }
                is AiResult.Error -> {
                    val errMsg = ChatMessage(
                        projectId = state.projectId,
                        role = MessageRole.ASSISTANT,
                        content = result.message,
                        provider = state.selectedProvider,
                        isError = true
                    )
                    db.chatMessageDao().insertMessage(errMsg)
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                else -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            db.chatMessageDao().clearProjectHistory(_uiState.value.projectId)
        }
    }

    // Quick prompt shortcuts
    fun sendQuickPrompt(prompt: String) {
        _uiState.update { it.copy(inputText = prompt) }
        sendMessage()
    }
}
