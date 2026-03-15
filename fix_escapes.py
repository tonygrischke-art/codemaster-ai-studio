#!/usr/bin/env python3
import os

BASE = os.path.expanduser("~/codemaster-ai-studio/app/src/main/java/com/codemaster/aistudio")

files = {}

# ─────────────────────────────────────────────────────────────────────────────
# FIX 1: AiRepository.kt - \\n in string literal
# ─────────────────────────────────────────────────────────────────────────────
files["data/repository/AiRepository.kt"] = """package com.codemaster.aistudio.data.repository

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

            val finalUserMessage = if (attachedFileContent != null) {
                "$userMessage\n\n<attached_file>\n$attachedFileContent\n</attached_file>"
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
"""

# ─────────────────────────────────────────────────────────────────────────────
# FIX 2: AiChatViewModel.kt - \\n in string
# ─────────────────────────────────────────────────────────────────────────────
files["ui/screens/chat/AiChatViewModel.kt"] = """package com.codemaster.aistudio.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.repository.AiRepository
import com.codemaster.aistudio.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val attachedFileName: String? = null,
    val attachedFileContent: String? = null,
    val totalTokens: Int = 0,
    val error: String? = null,
    val projectName: String = "Chat"
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentProjectId: Long = -1L

    fun init(projectId: Long) {
        currentProjectId = projectId
        viewModelScope.launch {
            chatRepository.getMessagesForProject(projectId)
                .catch { }
                .collect { messages ->
                    val tokens = messages.sumOf { it.tokenCount }
                    _uiState.value = _uiState.value.copy(messages = messages, totalTokens = tokens)
                }
        }
    }

    fun updateInput(text: String) { _uiState.value = _uiState.value.copy(inputText = text) }

    fun attachFile(fileName: String, content: String) {
        _uiState.value = _uiState.value.copy(
            attachedFileName = fileName,
            attachedFileContent = content
        )
    }

    fun clearAttachment() {
        _uiState.value = _uiState.value.copy(attachedFileName = null, attachedFileContent = null)
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank() && state.attachedFileContent == null) return
        if (state.isLoading) return

        val attachedName = state.attachedFileName
        val displayText = if (attachedName != null) "$text\n📎 $attachedName" else text

        viewModelScope.launch {
            val userMessage = ChatMessage(
                projectId = currentProjectId,
                role = "user",
                content = displayText,
                tokenCount = aiRepository.estimateTokens(displayText),
                attachedFileName = attachedName
            )
            chatRepository.saveMessage(userMessage)
            _uiState.value = state.copy(
                inputText = "",
                isLoading = true,
                attachedFileName = null,
                attachedFileContent = null
            )

            val result = aiRepository.sendMessage(
                history = _uiState.value.messages.dropLast(1),
                userMessage = text,
                attachedFileContent = state.attachedFileContent
            )

            result.fold(
                onSuccess = { response ->
                    val aiMessage = ChatMessage(
                        projectId = currentProjectId,
                        role = "assistant",
                        content = response,
                        tokenCount = aiRepository.estimateTokens(response)
                    )
                    chatRepository.saveMessage(aiMessage)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch { chatRepository.clearMessagesForProject(currentProjectId) }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
"""

# ─────────────────────────────────────────────────────────────────────────────
# FIX 3: AiChatScreen.kt - \\n in string literal
# ─────────────────────────────────────────────────────────────────────────────
files["ui/screens/chat/AiChatScreen.kt"] = """package com.codemaster.aistudio.ui.screens.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codemaster.aistudio.data.model.ChatMessage
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    projectId: Long,
    onBack: () -> Unit,
    viewModel: AiChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(projectId) { viewModel.init(projectId) }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val fileName = context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "attachment"
            val content = context.contentResolver.openInputStream(it)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            } ?: ""
            viewModel.attachFile(fileName, content.take(8000))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Chat", fontWeight = FontWeight.Bold)
                        Text(
                            "~${uiState.totalTokens} tokens used",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearHistory() }) {
                        Icon(Icons.Default.DeleteSweep, "Clear history")
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                text = uiState.inputText,
                attachedFileName = uiState.attachedFileName,
                isLoading = uiState.isLoading,
                onTextChange = viewModel::updateInput,
                onAttach = { filePicker.launch("*/*") },
                onClearAttachment = viewModel::clearAttachment,
                onSend = {
                    viewModel.sendMessage()
                    scope.launch {
                        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.messages.isEmpty()) {
                ChatEmptyState(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatBubble(message = message)
                    }
                    if (uiState.isLoading) {
                        item { TypingIndicator() }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    action = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
                ) { Text(error) }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("AI", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(modifier = Modifier.widthIn(max = 300.dp)) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isUser) 16.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(12.dp)
            ) {
                SelectionContainer {
                    if (!isUser && message.content.contains("```")) {
                        CodeAwareText(message.content)
                    } else {
                        Text(
                            text = message.content,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (!isUser) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "~${message.tokenCount} tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(message.content)) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy, "Copy",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        if (isUser) Spacer(Modifier.width(40.dp))
    }
}

@Composable
fun CodeAwareText(content: String) {
    val clipboardManager = LocalClipboardManager.current
    val parts = content.split("```")
    Column {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 0) {
                if (part.isNotBlank()) {
                    Text(
                        text = part.trim(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                val lines = part.lines()
                val code = if (lines.firstOrNull()?.all { it.isLetter() } == true) {
                    lines.drop(1).joinToString("\n")
                } else {
                    part
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                ) {
                    Text(
                        text = code.trim(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(code.trim())) },
                        modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Copy code", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text("AI", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Card(shape = RoundedCornerShape(16.dp)) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Thinking...", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ChatEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("CodeMaster AI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ask me anything about your code.\nAttach files for context.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ChatInputBar(
    text: String,
    attachedFileName: String?,
    isLoading: Boolean,
    onTextChange: (String) -> Unit,
    onAttach: () -> Unit,
    onClearAttachment: () -> Unit,
    onSend: () -> Unit
) {
    Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            AnimatedVisibility(visible = attachedFileName != null) {
                attachedFileName?.let { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSecondaryContainer)
                        IconButton(onClick = onClearAttachment, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = onAttach, enabled = !isLoading) {
                    Icon(
                        Icons.Default.AttachFile, "Attach file",
                        tint = if (attachedFileName != null) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask AI anything...") },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onSend,
                    enabled = (text.isNotBlank() || attachedFileName != null) && !isLoading,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Send, "Send")
                    }
                }
            }
        }
    }
}
"""

# ─────────────────────────────────────────────────────────────────────────────
# FIX 4: TerminalViewModel.kt - \\n in string literals
# ─────────────────────────────────────────────────────────────────────────────
files["ui/screens/terminal/TerminalViewModel.kt"] = """package com.codemaster.aistudio.ui.screens.terminal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject

data class TerminalLine(
    val text: String,
    val type: LineType = LineType.OUTPUT
)

enum class LineType { INPUT, OUTPUT, ERROR, SYSTEM }

data class TerminalUiState(
    val lines: List<TerminalLine> = emptyList(),
    val input: String = "",
    val isRunning: Boolean = false,
    val cwd: String = "",
    val shell: String = "",
    val history: List<String> = emptyList(),
    val historyIndex: Int = -1
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var readerJob: Job? = null
    private val maxLines = 2000

    private val shellPath: String by lazy {
        listOf(
            "/data/data/com.termux/files/usr/bin/bash",
            "/data/data/com.termux/files/usr/bin/sh",
            "/system/bin/sh",
            "/system/xbin/sh"
        ).firstOrNull { File(it).exists() } ?: "/system/bin/sh"
    }

    private val isTermuxAvailable: Boolean
        get() = File("/data/data/com.termux/files/usr/bin/bash").exists()

    fun init(projectId: Long) {
        val homeDir = if (isTermuxAvailable) {
            "/data/data/com.termux/files/home"
        } else {
            context.filesDir.absolutePath
        }

        appendLine(TerminalLine("╔════════════════════════════════════════╗", LineType.SYSTEM))
        appendLine(TerminalLine("║     CodeMaster AI Studio Terminal      ║", LineType.SYSTEM))
        appendLine(TerminalLine("╚════════════════════════════════════════╝", LineType.SYSTEM))
        appendLine(TerminalLine("Shell: $shellPath", LineType.SYSTEM))
        appendLine(TerminalLine(
            if (isTermuxAvailable) "Termux environment detected" else "Using Android system shell",
            LineType.SYSTEM
        ))
        appendLine(TerminalLine("Type 'help' for built-in commands", LineType.SYSTEM))
        appendLine(TerminalLine("", LineType.SYSTEM))

        startShell(homeDir)
    }

    private fun startShell(startDir: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder(shellPath, "--login").apply {
                    directory(File(startDir).takeIf { it.exists() } ?: context.filesDir)
                    environment().apply {
                        if (isTermuxAvailable) {
                            put("HOME", "/data/data/com.termux/files/home")
                            put("PATH", "/data/data/com.termux/files/usr/bin:/data/data/com.termux/files/usr/bin/applets:/system/bin:/system/xbin")
                            put("LD_LIBRARY_PATH", "/data/data/com.termux/files/usr/lib")
                            put("PREFIX", "/data/data/com.termux/files/usr")
                            put("TMPDIR", "/data/data/com.termux/files/usr/tmp")
                        } else {
                            put("PATH", "/system/bin:/system/xbin")
                            put("HOME", context.filesDir.absolutePath)
                        }
                        put("TERM", "xterm-256color")
                        put("LANG", "en_US.UTF-8")
                    }
                    redirectErrorStream(false)
                }

                process = pb.start()
                writer = OutputStreamWriter(process!!.outputStream, Charsets.UTF_8)

                _uiState.value = _uiState.value.copy(
                    isRunning = true,
                    shell = shellPath,
                    cwd = startDir
                )

                readerJob = viewModelScope.launch(Dispatchers.IO) {
                    val stdout = BufferedReader(InputStreamReader(process!!.inputStream, Charsets.UTF_8))
                    val stderr = BufferedReader(InputStreamReader(process!!.errorStream, Charsets.UTF_8))

                    launch {
                        try {
                            var line: String?
                            while (isActive) {
                                line = stdout.readLine() ?: break
                                appendLine(TerminalLine(stripAnsi(line), LineType.OUTPUT))
                            }
                        } catch (_: Exception) {}
                    }

                    launch {
                        try {
                            var line: String?
                            while (isActive) {
                                line = stderr.readLine() ?: break
                                appendLine(TerminalLine(stripAnsi(line), LineType.ERROR))
                            }
                        } catch (_: Exception) {}
                    }
                }

            } catch (e: Exception) {
                appendLine(TerminalLine("Failed to start shell: ${e.message}", LineType.ERROR))
                appendLine(TerminalLine("Falling back to built-in command handler", LineType.SYSTEM))
                _uiState.value = _uiState.value.copy(isRunning = false)
            }
        }
    }

    fun updateInput(text: String) { _uiState.value = _uiState.value.copy(input = text) }

    fun submitCommand() {
        val cmd = _uiState.value.input.trim()
        if (cmd.isEmpty()) return

        appendLine(TerminalLine("$ $cmd", LineType.INPUT))
        val newHistory = (_uiState.value.history + cmd).takeLast(100)
        _uiState.value = _uiState.value.copy(input = "", history = newHistory, historyIndex = -1)

        when {
            cmd == "clear" || cmd == "cls" -> {
                _uiState.value = _uiState.value.copy(lines = emptyList())
                return
            }
            cmd == "help" -> { showHelp(); return }
            cmd == "exit" -> {
                appendLine(TerminalLine("Type 'clear' to reset or use device back button", LineType.SYSTEM))
                return
            }
        }

        if (_uiState.value.isRunning && writer != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    writer?.write("$cmd\n")
                    writer?.flush()
                } catch (e: Exception) {
                    appendLine(TerminalLine("Error: ${e.message}", LineType.ERROR))
                }
            }
        } else {
            runBuiltinCommand(cmd)
        }
    }

    fun historyUp() {
        val history = _uiState.value.history
        if (history.isEmpty()) return
        val newIndex = (_uiState.value.historyIndex + 1).coerceAtMost(history.size - 1)
        _uiState.value = _uiState.value.copy(input = history[history.size - 1 - newIndex], historyIndex = newIndex)
    }

    fun historyDown() {
        val newIndex = _uiState.value.historyIndex - 1
        if (newIndex < 0) {
            _uiState.value = _uiState.value.copy(input = "", historyIndex = -1)
        } else {
            val history = _uiState.value.history
            _uiState.value = _uiState.value.copy(input = history[history.size - 1 - newIndex], historyIndex = newIndex)
        }
    }

    fun sendCtrlC() {
        viewModelScope.launch(Dispatchers.IO) {
            try { writer?.write("\u0003"); writer?.flush() } catch (_: Exception) {}
        }
        appendLine(TerminalLine("^C", LineType.SYSTEM))
    }

    fun sendCtrlD() {
        viewModelScope.launch(Dispatchers.IO) {
            try { writer?.write("\u0004"); writer?.flush() } catch (_: Exception) {}
        }
    }

    fun sendTab() {
        viewModelScope.launch(Dispatchers.IO) {
            try { writer?.write("\t"); writer?.flush() } catch (_: Exception) {}
        }
    }

    private fun runBuiltinCommand(cmd: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parts = cmd.trim().split("\\s+".toRegex())
                val result = when (parts[0]) {
                    "echo"   -> parts.drop(1).joinToString(" ")
                    "pwd"    -> _uiState.value.cwd
                    "whoami" -> "codemaster"
                    "uname"  -> "Linux (Android)"
                    "date"   -> java.util.Date().toString()
                    "ls" -> {
                        val dir = File(if (parts.size > 1) parts[1] else _uiState.value.cwd)
                        dir.listFiles()?.joinToString("  ") { f ->
                            if (f.isDirectory) "${f.name}/" else f.name
                        } ?: "Cannot list directory"
                    }
                    "cat" -> {
                        if (parts.size < 2) "Usage: cat <file>"
                        else File(_uiState.value.cwd, parts[1]).let {
                            if (it.exists()) it.readText() else "No such file: ${parts[1]}"
                        }
                    }
                    "cd" -> {
                        val target = when {
                            parts.size < 2 || parts[1] == "~" ->
                                if (isTermuxAvailable) "/data/data/com.termux/files/home"
                                else context.filesDir.absolutePath
                            parts[1].startsWith("/") -> parts[1]
                            else -> "${_uiState.value.cwd}/${parts[1]}"
                        }
                        val dir = File(target)
                        if (dir.exists() && dir.isDirectory) {
                            _uiState.value = _uiState.value.copy(cwd = dir.canonicalPath)
                            ""
                        } else "cd: $target: No such directory"
                    }
                    "mkdir" -> {
                        if (parts.size < 2) "Usage: mkdir <dir>"
                        else { File(_uiState.value.cwd, parts[1]).mkdirs(); "" }
                    }
                    "rm" -> {
                        if (parts.size < 2) "Usage: rm <file>"
                        else { File(_uiState.value.cwd, parts[1]).delete(); "" }
                    }
                    "env" -> System.getenv().entries.joinToString("\n") { "${it.key}=${it.value}" }
                    else -> {
                        val proc = Runtime.getRuntime().exec(cmd)
                        val out = proc.inputStream.bufferedReader().readText()
                        val err = proc.errorStream.bufferedReader().readText()
                        proc.waitFor()
                        if (err.isNotBlank()) appendLine(TerminalLine(err.trim(), LineType.ERROR))
                        out.trim()
                    }
                }
                if (result.isNotBlank()) {
                    result.lines().forEach { line -> appendLine(TerminalLine(line, LineType.OUTPUT)) }
                }
            } catch (e: Exception) {
                appendLine(TerminalLine("${e.message ?: "command failed"}", LineType.ERROR))
            }
        }
    }

    private fun showHelp() {
        listOf(
            "Built-in: clear, cd, ls, cat, mkdir, rm, pwd, echo, env, uname, whoami, date",
            if (isTermuxAvailable) "Termux shell active - all pkg commands available"
            else "No Termux detected - install Termux for full shell access"
        ).forEach { appendLine(TerminalLine(it, LineType.SYSTEM)) }
    }

    private fun appendLine(line: TerminalLine) {
        val current = _uiState.value.lines.toMutableList()
        current.add(line)
        if (current.size > maxLines) current.removeAt(0)
        _uiState.value = _uiState.value.copy(lines = current)
    }

    private fun stripAnsi(text: String): String =
        text.replace(Regex("\u001B\\[[;\\d]*[A-Za-z]"), "")
            .replace(Regex("\u001B\\][^\u0007]*\u0007"), "")

    override fun onCleared() {
        super.onCleared()
        readerJob?.cancel()
        try { writer?.close(); process?.destroy() } catch (_: Exception) {}
    }
}
"""

# ─────────────────────────────────────────────────────────────────────────────
# FIX 5: GitHubActionsRepository.kt - \\n in string literals
# ─────────────────────────────────────────────────────────────────────────────
files["data/repository/GitHubActionsRepository.kt"] = """package com.codemaster.aistudio.data.repository

import com.codemaster.aistudio.data.api.GitHubApiService
import com.codemaster.aistudio.data.api.WorkflowDispatchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubActionsRepository @Inject constructor(
    private val gitHubApiService: GitHubApiService,
    private val settingsRepository: SettingsRepository
) {
    suspend fun triggerBuild(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token  = settingsRepository.getGitHubToken()
            val owner  = settingsRepository.getGitHubOwner()
            val repo   = settingsRepository.getGitHubRepo()
            val branch = settingsRepository.getGitHubBranch()

            if (token.isBlank())  return@withContext Result.failure(Exception("GitHub token not set. Go to Settings."))
            if (owner.isBlank())  return@withContext Result.failure(Exception("GitHub username not set. Go to Settings."))
            if (repo.isBlank())   return@withContext Result.failure(Exception("Repository name not set. Go to Settings."))

            val workflowsResult = runCatching {
                gitHubApiService.listWorkflows("Bearer $token", owner, repo)
            }
            if (workflowsResult.isFailure)
                return@withContext Result.failure(Exception("Could not list workflows. Check token and repo name."))

            val workflows = workflowsResult.getOrThrow()
            val workflow = workflows.workflows.firstOrNull {
                it.name.contains("Build", ignoreCase = true) ||
                it.path.contains("build", ignoreCase = true)
            } ?: workflows.workflows.firstOrNull()
            ?: return@withContext Result.failure(Exception("No workflows found in repo."))

            gitHubApiService.triggerWorkflow(
                token      = "Bearer $token",
                owner      = owner,
                repo       = repo,
                workflowId = workflow.id.toString(),
                body       = WorkflowDispatchRequest(ref = branch)
            )

            Result.success("Build triggered! Workflow: ${workflow.name}\nCheck GitHub Actions for progress.")
        } catch (e: Exception) {
            Result.failure(Exception("Trigger failed: ${e.message}"))
        }
    }

    suspend fun getLatestRun(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = settingsRepository.getGitHubToken()
            val owner = settingsRepository.getGitHubOwner()
            val repo  = settingsRepository.getGitHubRepo()

            if (token.isBlank() || owner.isBlank() || repo.isBlank())
                return@withContext Result.failure(Exception("Configure GitHub settings first."))

            val runs = gitHubApiService.getWorkflowRuns("Bearer $token", owner, repo)
            val latest = runs.workflowRuns.firstOrNull()
                ?: return@withContext Result.success("No runs found yet.")

            val status = when {
                latest.conclusion == "success"   -> "SUCCESS"
                latest.conclusion == "failure"   -> "FAILED"
                latest.conclusion == "cancelled" -> "CANCELLED"
                latest.status == "in_progress"   -> "IN PROGRESS"
                latest.status == "queued"        -> "QUEUED"
                else -> latest.status.uppercase()
            }

            Result.success("$status\n${latest.name}\nCommit: ${latest.headSha.take(7)}\nStarted: ${latest.createdAt}")
        } catch (e: Exception) {
            Result.failure(Exception("Status check failed: ${e.message}"))
        }
    }
}
"""

# ─────────────────────────────────────────────────────────────────────────────
# WRITE ALL FILES
# ─────────────────────────────────────────────────────────────────────────────
written = 0
errors = []

for relative_path, content in files.items():
    full_path = os.path.join(BASE, relative_path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    try:
        with open(full_path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"✅ {relative_path}")
        written += 1
    except Exception as e:
        print(f"❌ {relative_path}: {e}")
        errors.append(relative_path)

print(f"\n{'='*52}")
print(f"✅ Fixed: {written} files")
if errors:
    print(f"❌ Errors: {len(errors)}")
else:
    print("🚀 All escape fixes applied — ready to commit!")
print(f"{'='*52}")
