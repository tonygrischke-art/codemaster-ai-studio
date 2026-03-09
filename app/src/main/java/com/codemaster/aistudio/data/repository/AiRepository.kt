package com.codemaster.aistudio.data.repository

import com.codemaster.aistudio.data.api.*
import com.codemaster.aistudio.data.model.AiProvider
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class AiResult {
    data class Success(val content: String) : AiResult()
    data class Error(val message: String) : AiResult()
    object Loading : AiResult()
}

@Singleton
class AiRepository @Inject constructor(
    private val geminiService: GeminiApiService,
    private val groqService: GroqApiService,
    private val settingsRepository: SettingsRepository
) {

    // System prompt for CodeMaster context
    private val systemPrompt = """
        You are CodeMaster AI, an expert coding assistant embedded in a mobile IDE for Android.
        Your primary goals:
        1. Help users write, debug, fix, and explain code in any language
        2. Generate complete, working code snippets and files
        3. Provide clear explanations suitable for beginners AND experts
        4. When writing code, always wrap it in proper markdown code blocks with language tags
        5. For Android/Kotlin/Gradle issues, provide production-ready solutions
        6. Keep responses concise but complete
        Format: Use markdown. Wrap ALL code in ```language ... ``` blocks.
    """.trimIndent()

    suspend fun sendMessage(
        userMessage: String,
        history: List<ChatMessage>,
        provider: AiProvider
    ): AiResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getSettings()
        return@withContext when (provider) {
            AiProvider.GEMINI -> sendToGemini(userMessage, history, settings.geminiApiKey)
            AiProvider.GROQ -> sendToGroq(userMessage, history, settings.groqApiKey)
        }
    }

    private suspend fun sendToGemini(
        userMessage: String,
        history: List<ChatMessage>,
        apiKey: String
    ): AiResult {
        if (apiKey.isBlank()) return AiResult.Error("Gemini API key not set. Go to Settings to add your key.")
        return try {
            // Build conversation history in Gemini format
            val contents = mutableListOf<GeminiContent>()
            history.takeLast(20).forEach { msg ->
                val role = if (msg.role == MessageRole.USER) "user" else "model"
                contents.add(GeminiContent(role = role, parts = listOf(GeminiPart(msg.content))))
            }
            contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(userMessage))))

            val request = GeminiRequest(
                contents = contents,
                systemInstruction = GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(systemPrompt))
                )
            )
            val response = geminiService.generateContent(
                model = "gemini-1.5-flash",
                apiKey = apiKey,
                request = request
            )
            if (response.error != null) {
                AiResult.Error("Gemini error: ${response.error.message}")
            } else {
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "No response from Gemini."
                AiResult.Success(text)
            }
        } catch (e: Exception) {
            AiResult.Error("Network error: ${e.localizedMessage}")
        }
    }

    private suspend fun sendToGroq(
        userMessage: String,
        history: List<ChatMessage>,
        apiKey: String
    ): AiResult {
        if (apiKey.isBlank()) return AiResult.Error("Groq API key not set. Go to Settings to add your key.")
        return try {
            val messages = mutableListOf<GroqMessage>()
            messages.add(GroqMessage(role = "system", content = systemPrompt))
            history.takeLast(20).forEach { msg ->
                val role = if (msg.role == MessageRole.USER) "user" else "assistant"
                messages.add(GroqMessage(role = role, content = msg.content))
            }
            messages.add(GroqMessage(role = "user", content = userMessage))

            val request = GroqRequest(
                model = "llama-3.3-70b-versatile",
                messages = messages
            )
            val response = groqService.chatCompletions(
                request = request,
                auth = "Bearer $apiKey"
            )
            if (response.error != null) {
                AiResult.Error("Groq error: ${response.error.message}")
            } else {
                val text = response.choices?.firstOrNull()?.message?.content
                    ?: "No response from Groq."
                AiResult.Success(text)
            }
        } catch (e: Exception) {
            AiResult.Error("Network error: ${e.localizedMessage}")
        }
    }
}
