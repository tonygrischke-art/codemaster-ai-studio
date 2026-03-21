package com.codemaster.aistudio.data.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class KimiMessage(val role: String, val content: String)
data class KimiChatRequest(
    val model: String = "moonshot-v1-128k",
    val messages: List<KimiMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 8000
)
data class KimiChoice(val message: KimiMessage)
data class KimiChatResponse(val choices: List<KimiChoice>)

interface KimiApiService {
    @POST("v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Body request: KimiChatRequest
    ): KimiChatResponse
}
