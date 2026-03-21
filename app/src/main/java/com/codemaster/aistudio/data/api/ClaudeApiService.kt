package com.codemaster.aistudio.data.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class ClaudeMessage(val role: String, val content: String)
data class ClaudeChatRequest(
    val model: String = "claude-opus-4-5",
    val max_tokens: Int = 8000,
    val system: String? = null,
    val messages: List<ClaudeMessage>
)
data class ClaudeContent(val type: String, val text: String)
data class ClaudeChatResponse(val content: List<ClaudeContent>)

interface ClaudeApiService {
    @POST("v1/messages")
    suspend fun chat(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Header("content-type") contentType: String = "application/json",
        @Body request: ClaudeChatRequest
    ): ClaudeChatResponse
}
