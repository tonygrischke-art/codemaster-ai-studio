package com.codemaster.aistudio.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// ─── Gemini API ────────────────────────────────────────────────

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String = "gemini-1.5-flash",
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerializedName("generationConfig") val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig(),
    @SerializedName("systemInstruction") val systemInstruction: GeminiContent? = null
)

data class GeminiContent(
    val role: String, // "user" | "model"
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiGenerationConfig(
    val temperature: Float = 0.7f,
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int = 8192,
    @SerializedName("topK") val topK: Int = 40,
    @SerializedName("topP") val topP: Float = 0.95f
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?,
    val error: GeminiError?
)

data class GeminiCandidate(
    val content: GeminiContent?,
    @SerializedName("finishReason") val finishReason: String?
)

data class GeminiError(
    val code: Int,
    val message: String,
    val status: String
)

// ─── Kimi (Moonshot) API ───────────────────────────────────────
// Kimi uses OpenAI-compatible chat completions format

interface KimiApiService {
    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Body request: KimiRequest,
        @retrofit2.http.Header("Authorization") auth: String
    ): KimiResponse
}

data class KimiRequest(
    val model: String = "moonshot-v1-8k",
    val messages: List<KimiMessage>,
    val temperature: Float = 0.7f,
    @SerializedName("max_tokens") val maxTokens: Int = 8192
)

data class KimiMessage(
    val role: String, // "system" | "user" | "assistant"
    val content: String
)

data class KimiResponse(
    val choices: List<KimiChoice>?,
    val error: KimiError?
)

data class KimiChoice(
    val message: KimiMessage?,
    @SerializedName("finish_reason") val finishReason: String?
)

data class KimiError(
    val message: String,
    val type: String
)
