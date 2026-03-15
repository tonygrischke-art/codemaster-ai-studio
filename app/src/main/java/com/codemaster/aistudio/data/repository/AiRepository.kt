package com.codemaster.aistudio.data.repository

import com.codemaster.aistudio.data.api.GroqApiService
import com.codemaster.aistudio.data.api.GroqChatRequest
import com.codemaster.aistudio.data.api.GroqMessage
import com.codemaster.aistudio.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val groqApiService: GroqApiService,
    private val settingsRepository: SettingsRepository
) {
    suspend fun sendMessage(
        history: List<ChatMessage>,
        userMessage: String,
        attachedFileContent: String? = null,
        systemPrompt: String = "You are CodeMaster AI, an expert coding assistant. Be concise, helpful, and provide working code examples."
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = settingsRepository.getApiKey()
            if (apiKey.isBlank()) return@withContext Result.failure(Exception("No API key set. Go to Settings."))

            val model = settingsRepository.getModel()
            val messages = mutableListOf<GroqMessage>()
            messages.add(GroqMessage("system", systemPrompt))

            history.takeLast(20).forEach { msg ->
                messages.add(GroqMessage(msg.role, msg.content))
            }

            val nl = "\n"
            val finalUserMessage = if (attachedFileContent != null) {
                userMessage + nl + nl + "<attached_file>" + nl + attachedFileContent + nl + "</attached_file>"
            } else {
                userMessage
            }
            messages.add(GroqMessage("user", finalUserMessage))

            val request = GroqChatRequest(model = model, messages = messages)
            val response = groqApiService.chat("Bearer $apiKey", request)
            val content = response.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(Exception("Empty response from AI"))

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)
}
