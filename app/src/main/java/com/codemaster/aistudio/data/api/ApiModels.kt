package com.codemaster.aistudio.data.api

import com.google.gson.annotations.SerializedName

data class GroqMessage(
    val role: String,
    val content: String
)

data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 4096,
    val temperature: Double = 0.7
)

data class GroqChatResponse(
    val id: String = "",
    val choices: List<GroqChoice> = emptyList(),
    val usage: GroqUsage? = null
)

data class GroqChoice(
    val message: GroqMessage,
    @SerializedName("finish_reason") val finishReason: String = ""
)

data class GroqUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0
)
