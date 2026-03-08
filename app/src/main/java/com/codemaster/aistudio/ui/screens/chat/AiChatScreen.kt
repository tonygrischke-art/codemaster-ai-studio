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

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Conversation histories keyed by projectPath
    private val projectHistories = mutableMapOf<String, MutableList<ChatMessage>>()

    init {
        viewModelScope.launch {
            val provider = settingsRepository.getAiProvider()
            _uiState.update { it.copy(aiProvider = provider) }
        }
    }

    fun initWithProject(projectPath: String, fileName: String = "", code: String = "") {
        val history = projectHistories.getOrPut(projectPath) { mutableListOf() }
        _uiState.update {
            it.copy(
                messages = history.toList(),
                projectContext = projectPath,
                currentFile = fileName,
                currentCode = code
            )
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    fun sendMessage(onInsertCode: ((String) -> Unit)? = null) {
        val state = _uiState.value
        val input = state.input.trim()
        if (input.isBlank() || state.isLoading) return

        val userMsg = ChatMessage(role = MessageRole.USER, content = input)
        val updatedMessages = state.messages + userMsg

        projectHistories[state.projectContext]?.add(userMsg)

        _uiState.update {
            it.copy(messages = updatedMessages, input = "", isLoading = true)
        }

        viewModelScope.launch {
            try {
                val systemPrompt = buildSystemPrompt(state)
                val apiKey: String
                val response: String

                if (state.aiProvider == "kimi") {
                    apiKey = settingsRepository.getKimiApiKey()
                    if (apiKey.isBlank()) throw Exception("Kimi API key not set in Settings")
                    response = KimiApi.sendMessage(apiKey, updatedMessages, systemPrompt)
                } else {
                    apiKey = settingsRepository.getGeminiApiKey()
                    if (apiKey.isBlank()) throw Exception("Gemini API key not set in Settings")
                    response = GeminiApi.sendMessage(apiKey, updatedMessages, systemPrompt)
                }

                val assistantMsg = ChatMessage(role = MessageRole.ASSISTANT, content = response)
                projectHistories[state.projectContext]?.add(assistantMsg)

                _uiState.update { s ->
                    s.copy(messages = s.messages + assistantMsg, isLoading = false)
                }

            } catch (e: Exception) {
                val errorMsg = ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = "Error: ${e.message}",
                    isError = true
                )
                _uiState.update { s ->
                    s.copy(messages = s.messages + errorMsg, isLoading = false)
                }
            }
        }
    }

    fun clearHistory() {
        val state = _uiState.value
        projectHistories[state.projectContext]?.clear()
        _uiState.update { it.copy(messages = emptyList()) }
    }

    fun switchProvider(provider: String) {
        viewModelScope.launch {
            settingsRepository.setAiProvider(provider)
            _uiState.update { it.copy(aiProvider = provider) }
        }
    }

    // Extract code blocks from AI response
    fun extractCodeBlocks(content: String): List<String> {
        val regex = Regex("```(?:\\w+)?\\n([\\s\\S]*?)```")
        return regex.findAll(content).map { it.groupValues[1].trim() }.toList()
    }

    private fun buildSystemPrompt(state: ChatUiState): String {
        val sb = StringBuilder()
        sb.append("You are an expert AI coding assistant inside CodeMaster AI Studio, an Android IDE app. ")
        sb.append("Be concise, technical, and helpful. Format code with markdown code blocks (```language).\n\n")

        if (state.projectContext.isNotBlank()) {
            sb.append("Current project: ${state.projectContext}\n")
        }
        if (state.currentFile.isNotBlank()) {
            sb.append("Current file: ${state.currentFile}\n")
        }
        if (state.currentCode.isNotBlank()) {
            sb.append("Current code context:\n```\n${state.currentCode.take(2000)}\n```\n")
        }
        return sb.toString()
    }
}

// ─── Chat Screen ───────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    projectPath: String = "",
    currentFile: String = "",
    currentCode: String = "",
    onNavigateBack: () -> Unit,
    onInsertCode: ((String) -> Unit)? = null,
    viewModel: AiChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(projectPath) {
        viewModel.initWithProject(projectPath, currentFile, currentCode)
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Chat", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProviderChip("Gemini", uiState.aiProvider == "gemini",
                                Color(0xFF4285F4)) { viewModel.switchProvider("gemini") }
                            ProviderChip("Kimi", uiState.aiProvider == "kimi",
                                Color(0xFF1DB954)) { viewModel.switchProvider("kimi") }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.messages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearHistory() }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear chat")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Context banner
            if (currentFile.isNotBlank()) {
                Surface(tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text("Context: $currentFile",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Empty state
            if (uiState.messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text("✨", fontSize = 48.sp)
                        Text("Ask anything about your code",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "Explain this code",
                                "Fix the bug in my function",
                                "Add error handling",
                                "Write unit tests for this",
                                "Optimize for performance"
                            ).forEach { suggestion ->
                                SuggestionChip(
                                    onClick = {
                                        viewModel.updateInput(suggestion)
                                        viewModel.sendMessage(onInsertCode)
                                    },
                                    label = { Text(suggestion, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Message list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatMessageBubble(
                            message = message,
                            onInsertCode = onInsertCode,
                            onCopyCode = { /* handled in bubble */ },
                            extractCodeBlocks = viewModel::extractCodeBlocks
                        )
                    }

                    if (uiState.isLoading) {
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp)
                                Text("Thinking...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Input area
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = uiState.input,
                    onValueChange = viewModel::updateInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about your code...") },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp)
                )
                FilledIconButton(
                    onClick = { viewModel.sendMessage(onInsertCode) },
                    enabled = uiState.input.isNotBlank() && !uiState.isLoading
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

// ─── Message Bubble ────────────────────────────────────────────
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onInsertCode: ((String) -> Unit)?,
    onCopyCode: (String) -> Unit,
    extractCodeBlocks: (String) -> List<String>
) {
    val isUser = message.role == MessageRole.USER
    val codeBlocks = if (!isUser) extractCodeBlocks(message.content) else emptyList()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // User bubble
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(12.dp, 10.dp)
            ) {
                Text(message.content,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            // AI bubble — render with code block detection
            Card(
                shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (message.isError)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp, 10.dp)) {
                    SelectionContainer {
                        RenderMessageContent(message.content)
                    }

                    // Code block action buttons
                    if (codeBlocks.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            codeBlocks.take(1).forEach { code ->
                                if (onInsertCode != null) {
                                    OutlinedButton(
                                        onClick = { onInsertCode(code) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.Code, contentDescription = null,
                                            modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Insert Code", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenderMessageContent(content: String) {
    // Simple code block renderer — splits on ``` markers
    val parts = content.split(Regex("```(?:\\w+)?\\n|```"))
    var isCode = false

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { part ->
            if (part.isNotBlank()) {
                if (isCode) {
                    // Code block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF161B22))
                            .padding(12.dp)
                    ) {
                        Text(
                            part.trimEnd(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFFE6EDF3),
                            lineHeight = 18.sp
                        )
                    }
                } else {
                    // Regular text
                    Text(
                        part.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                }
            }
            isCode = !isCode
        }
    }
}

@Composable
fun ProviderChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.2f),
            selectedLabelColor = color
        ),
        modifier = Modifier.height(24.dp)
    )
}
