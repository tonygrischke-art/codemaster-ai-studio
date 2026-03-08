import os

base = "/data/data/com.termux/files/home/CodeMasterAIStudio/app/src/main/java/com/codemaster/aistudio"

viewmodel = open("/dev/stdin").read() if False else r'''package com.codemaster.aistudio.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

enum class MessageRole { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isLoading: Boolean = false,
    val aiProvider: String = "gemini",
    val projectContext: String = "",
    val currentFile: String = "",
    val currentCode: String = ""
)

object GeminiApi {
    suspend fun sendMessage(apiKey: String, messages: List<ChatMessage>, systemPrompt: String): String =
        withContext(Dispatchers.IO) {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val contents = JSONArray()
            if (systemPrompt.isNotBlank()) {
                contents.put(JSONObject().apply { put("role", "user"); put("parts", JSONArray().put(JSONObject().apply { put("text", systemPrompt) })) })
                contents.put(JSONObject().apply { put("role", "model"); put("parts", JSONArray().put(JSONObject().apply { put("text", "Understood.") })) })
            }
            messages.forEach { msg ->
                if (msg.role != MessageRole.SYSTEM) {
                    contents.put(JSONObject().apply {
                        put("role", if (msg.role == MessageRole.USER) "user" else "model")
                        put("parts", JSONArray().put(JSONObject().apply { put("text", msg.content) }))
                    })
                }
            }
            val body = JSONObject().apply { put("contents", contents); put("generationConfig", JSONObject().apply { put("temperature", 0.7); put("maxOutputTokens", 2048) }) }.toString()
            conn.outputStream.write(body.toByteArray())
            val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            conn.disconnect()
            JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        }
}

object KimiApi {
    suspend fun sendMessage(apiKey: String, messages: List<ChatMessage>, systemPrompt: String): String =
        withContext(Dispatchers.IO) {
            val url = URL("https://api.moonshot.cn/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true
            val msgs = JSONArray()
            if (systemPrompt.isNotBlank()) msgs.put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            messages.forEach { msg ->
                if (msg.role != MessageRole.SYSTEM) msgs.put(JSONObject().apply { put("role", if (msg.role == MessageRole.USER) "user" else "assistant"); put("content", msg.content) })
            }
            val body = JSONObject().apply { put("model", "moonshot-v1-8k"); put("messages", msgs); put("temperature", 0.7) }.toString()
            conn.outputStream.write(body.toByteArray())
            val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            conn.disconnect()
            JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
}

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val projectHistories = mutableMapOf<String, MutableList<ChatMessage>>()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(aiProvider = settingsRepository.getAiProvider()) }
        }
    }

    fun initWithProject(projectPath: String, fileName: String = "", code: String = "") {
        val history = projectHistories.getOrPut(projectPath) { mutableListOf() }
        _uiState.update { it.copy(messages = history.toList(), projectContext = projectPath, currentFile = fileName, currentCode = code) }
    }

    fun updateInput(text: String) { _uiState.update { it.copy(input = text) } }

    fun switchProvider(provider: String) {
        _uiState.update { it.copy(aiProvider = provider) }
        viewModelScope.launch { settingsRepository.setAiProvider(provider) }
    }

    fun sendMessage(onInsertCode: ((String) -> Unit)? = null) {
        val state = _uiState.value
        val input = state.input.trim()
        if (input.isBlank() || state.isLoading) return
        val userMsg = ChatMessage(role = MessageRole.USER, content = input)
        val updatedMessages = state.messages + userMsg
        projectHistories[state.projectContext]?.add(userMsg)
        _uiState.update { it.copy(messages = updatedMessages, input = "", isLoading = true) }
        viewModelScope.launch {
            try {
                val geminiKey = settingsRepository.getGeminiApiKey()
                val kimiKey = settingsRepository.getKimiApiKey()
                val systemPrompt = buildString {
                    append("You are an expert coding assistant inside CodeMaster AI Studio. ")
                    if (state.projectContext.isNotBlank()) append("Project: ${state.projectContext}. ")
                    if (state.currentFile.isNotBlank()) append("File: ${state.currentFile}. ")
                    if (state.currentCode.isNotBlank()) append("Current code:\n${state.currentCode.take(2000)}")
                }
                val response = when (state.aiProvider) {
                    "kimi" -> KimiApi.sendMessage(kimiKey, updatedMessages, systemPrompt)
                    else   -> GeminiApi.sendMessage(geminiKey, updatedMessages, systemPrompt)
                }
                val aiMsg = ChatMessage(role = MessageRole.ASSISTANT, content = response)
                projectHistories[state.projectContext]?.add(aiMsg)
                _uiState.update { it.copy(messages = it.messages + aiMsg, isLoading = false) }
                val codeBlock = Regex("""```(?:\w+)?\n([\s\S]*?)```""").find(response)?.groupValues?.get(1)
                if (codeBlock != null) onInsertCode?.invoke(codeBlock)
            } catch (e: Exception) {
                val errMsg = ChatMessage(role = MessageRole.ASSISTANT, content = "Error: ${e.message}", isError = true)
                _uiState.update { it.copy(messages = it.messages + errMsg, isLoading = false) }
            }
        }
    }

    fun clearHistory() {
        projectHistories[_uiState.value.projectContext]?.clear()
        _uiState.update { it.copy(messages = emptyList()) }
    }
}
'''

screen = r'''package com.codemaster.aistudio.ui.screens.chat

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AiChatScreen(
    projectPath: String = "",
    currentFile: String = "",
    code: String = "",
    onNavigateBack: (() -> Unit)? = null,
    onInsertCode: ((String) -> Unit)? = null,
    viewModel: AiChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(projectPath) { viewModel.initWithProject(projectPath, currentFile, code) }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("AI Assistant", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                if (onNavigateBack != null) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            },
            actions = {
                TextButton(onClick = { viewModel.switchProvider(if (uiState.aiProvider == "gemini") "kimi" else "gemini") }) {
                    Text(if (uiState.aiProvider == "gemini") "Gemini" else "Kimi", color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { viewModel.clearHistory() }) { Icon(Icons.Default.Delete, "Clear") }
            }
        )

        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.messages, key = { it.id }) { msg -> MessageBubble(msg, onInsertCode) }
            if (uiState.isLoading) {
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { CircularProgressIndicator(Modifier.size(24.dp).padding(4.dp)) } }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(value = uiState.input, onValueChange = { viewModel.updateInput(it) }, modifier = Modifier.weight(1f), placeholder = { Text("Ask AI...") }, maxLines = 5)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { viewModel.sendMessage(onInsertCode) }, enabled = uiState.input.isNotBlank() && !uiState.isLoading) {
                Icon(Icons.Default.Send, "Send", tint = if (uiState.input.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, onInsertCode: ((String) -> Unit)?) {
    val isUser = msg.role == MessageRole.USER
    val bubbleColor = when { msg.isError -> MaterialTheme.colorScheme.errorContainer; isUser -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.surfaceVariant }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(12.dp)).background(bubbleColor).padding(10.dp)) {
            SelectionContainer {
                if (msg.content.contains("```")) CodeFormattedText(msg.content, onInsertCode)
                else Text(msg.content, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun CodeFormattedText(content: String, onInsertCode: ((String) -> Unit)?) {
    val parts = content.split(Regex("```(?:\\w+)?\\n?"))
    parts.forEachIndexed { i, part ->
        if (part.isBlank()) return@forEachIndexed
        if (i % 2 == 1) {
            val code = part.trimEnd().removeSuffix("```").trimEnd()
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E1E1E)).padding(8.dp)) {
                SelectionContainer { Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFD4D4D4)) }
                if (onInsertCode != null) {
                    TextButton(onClick = { onInsertCode(code) }) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Insert", fontSize = 11.sp)
                    }
                }
            }
        } else Text(part, fontSize = 14.sp)
    }
}
'''

# Fix MainActivity - replace CodeMasterNavGraph with NavGraph
main_path = f"{base}/MainActivity.kt"
with open(main_path, 'r') as f:
    main = f.read()
main = main.replace('CodeMasterNavGraph', 'NavGraph')
if 'import com.codemaster.aistudio.ui.navigation.NavGraph' not in main:
    main = 'import com.codemaster.aistudio.ui.navigation.NavGraph\n' + main
with open(main_path, 'w') as f:
    f.write(main)
print("✅ MainActivity.kt fixed")

with open(f"{base}/ui/screens/chat/AiChatViewModel.kt", "w") as f:
    f.write(viewmodel)
print("✅ AiChatViewModel.kt written")

with open(f"{base}/ui/screens/chat/AiChatScreen.kt", "w") as f:
    f.write(screen)
print("✅ AiChatScreen.kt written")

print("\nAll done!")
