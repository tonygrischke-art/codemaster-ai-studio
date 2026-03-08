package com.codemaster.aistudio.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codemaster.aistudio.data.repository.FileResult
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

// ─── Message model ─────────────────────────────────────────────
enum class MessageRole { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

// ─── AI providers ──────────────────────────────────────────────
object GeminiApi {
    suspend fun sendMessage(
        apiKey: String,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): String = withContext(Dispatchers.IO) {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        // Build contents array
        val contents = JSONArray()

        // Add system prompt as first user message
        if (systemPrompt.isNotBlank()) {
            contents.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().apply { put("text", systemPrompt) }))
            })
            contents.put(JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().put(JSONObject().apply { put("text", "Understood. I'm your AI coding assistant.") }))
            })
        }

        messages.forEach { msg ->
            if (msg.role != MessageRole.SYSTEM) {
                contents.put(JSONObject().apply {
                    put("role", if (msg.role == MessageRole.USER) "user" else "model")
                    put("parts", JSONArray().put(JSONObject().apply { put("text", msg.content) }))
                })
            }
        }

        val body = JSONObject().apply {
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 2048)
            })
        }.toString()

        conn.outputStream.write(body.toByteArray())

        val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
        conn.disconnect()

        val json = JSONObject(response)
        json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }
}

object KimiApi {
    suspend fun sendMessage(
        apiKey: String,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): String = withContext(Dispatchers.IO) {
        val url = URL("https://api.moonshot.cn/v1/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.doOutput = true

        val msgs = JSONArray()
        if (systemPrompt.isNotBlank()) {
            msgs.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }
        messages.forEach { msg ->
            if (msg.role != MessageRole.SYSTEM) {
                msgs.put(JSONObject().apply {
                    put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                    put("content", msg.content)
                })
            }
        }

        val body = JSONObject().apply {
            put("model", "moonshot-v1-8k")
            put("messages", msgs)
            put("temperature", 0.7)
        }.toString()

        conn.outputStream.write(body.toByteArray())

        val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
        conn.disconnect()

        val json = JSONObject(response)
        json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
}

// ─── Chat ViewModel ────────────────────────────────────────────
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isLoading: Boolean = false,
    val aiProvider: String = "gemini",
    val projectContext: String = "",
    val currentFile: String = "",
    val currentCode: String = ""
)
