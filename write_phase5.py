#!/usr/bin/env python3
import os

BASE = os.path.expanduser("~/codemaster-ai-studio/app/src/main/java/com/codemaster/aistudio")

def write(rel, content):
    path = os.path.join(BASE, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("OK " + rel)

# ─────────────────────────────────────────────────────────────────────────────
# 1. TERMINAL - fix --login flag crash
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/terminal/TerminalViewModel.kt", r"""package com.codemaster.aistudio.ui.screens.terminal

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
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject

data class TerminalLine(val text: String, val type: LineType = LineType.OUTPUT)
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

    private val isTermuxAvailable: Boolean
        get() = File("/data/data/com.termux/files/usr/bin/bash").exists()

    // Build the best available shell command - NO --login on system sh
    private fun buildShellCommand(): List<String> {
        return when {
            File("/data/data/com.termux/files/usr/bin/bash").exists() ->
                listOf("/data/data/com.termux/files/usr/bin/bash", "--login")
            File("/data/data/com.termux/files/usr/bin/sh").exists() ->
                listOf("/data/data/com.termux/files/usr/bin/sh")
            File("/system/bin/sh").exists() ->
                listOf("/system/bin/sh")   // NO --login, Android sh doesn't support it
            else ->
                listOf("/system/xbin/sh")
        }
    }

    fun init(projectId: Long) {
        val homeDir = if (isTermuxAvailable)
            "/data/data/com.termux/files/home"
        else
            context.filesDir.absolutePath

        val shellCmd = buildShellCommand()
        appendLine(TerminalLine("=== CodeMaster AI Studio Terminal ===", LineType.SYSTEM))
        appendLine(TerminalLine("Shell: " + shellCmd.first(), LineType.SYSTEM))
        appendLine(TerminalLine(
            if (isTermuxAvailable) "Termux detected - full shell available"
            else "Android system shell - built-in commands available",
            LineType.SYSTEM
        ))
        appendLine(TerminalLine("Type 'help' for commands", LineType.SYSTEM))
        appendLine(TerminalLine("", LineType.SYSTEM))
        startShell(homeDir, shellCmd)
    }

    private fun startShell(startDir: String, shellCmd: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder(shellCmd).apply {
                    directory(File(startDir).takeIf { it.exists() } ?: context.filesDir)
                    environment().apply {
                        if (isTermuxAvailable) {
                            put("HOME", "/data/data/com.termux/files/home")
                            put("PATH", "/data/data/com.termux/files/usr/bin:/system/bin:/system/xbin")
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
                    shell = shellCmd.first(),
                    cwd = startDir
                )

                readerJob = viewModelScope.launch(Dispatchers.IO) {
                    launch {
                        try {
                            val reader = BufferedReader(InputStreamReader(process!!.inputStream, Charsets.UTF_8))
                            while (isActive) {
                                val line = reader.readLine() ?: break
                                appendLine(TerminalLine(stripAnsi(line), LineType.OUTPUT))
                            }
                        } catch (_: Exception) {}
                    }
                    launch {
                        try {
                            val reader = BufferedReader(InputStreamReader(process!!.errorStream, Charsets.UTF_8))
                            while (isActive) {
                                val line = reader.readLine() ?: break
                                appendLine(TerminalLine(stripAnsi(line), LineType.ERROR))
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                appendLine(TerminalLine("Shell failed: " + e.message, LineType.ERROR))
                appendLine(TerminalLine("Using built-in commands only", LineType.SYSTEM))
                _uiState.value = _uiState.value.copy(isRunning = false, cwd = context.filesDir.absolutePath)
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
        when (cmd) {
            "clear", "cls" -> { _uiState.value = _uiState.value.copy(lines = emptyList()); return }
            "help"         -> { showHelp(); return }
            "exit"         -> { appendLine(TerminalLine("Use back button to exit", LineType.SYSTEM)); return }
        }
        if (_uiState.value.isRunning && writer != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try { writer?.write(cmd + "\n"); writer?.flush() }
                catch (e: Exception) { appendLine(TerminalLine("Write error: " + e.message, LineType.ERROR)) }
            }
        } else {
            runBuiltinCommand(cmd)
        }
    }

    fun historyUp() {
        val h = _uiState.value.history
        if (h.isEmpty()) return
        val i = (_uiState.value.historyIndex + 1).coerceAtMost(h.size - 1)
        _uiState.value = _uiState.value.copy(input = h[h.size - 1 - i], historyIndex = i)
    }

    fun historyDown() {
        val i = _uiState.value.historyIndex - 1
        if (i < 0) _uiState.value = _uiState.value.copy(input = "", historyIndex = -1)
        else {
            val h = _uiState.value.history
            _uiState.value = _uiState.value.copy(input = h[h.size - 1 - i], historyIndex = i)
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
                            if (f.isDirectory) f.name + "/" else f.name
                        } ?: "Cannot list directory"
                    }
                    "cat" -> {
                        if (parts.size < 2) "Usage: cat <file>"
                        else {
                            val f = File(_uiState.value.cwd, parts[1])
                            if (f.exists()) f.readText() else "No such file: " + parts[1]
                        }
                    }
                    "cd" -> {
                        val target = when {
                            parts.size < 2 || parts[1] == "~" ->
                                if (isTermuxAvailable) "/data/data/com.termux/files/home"
                                else context.filesDir.absolutePath
                            parts[1].startsWith("/") -> parts[1]
                            else -> _uiState.value.cwd + "/" + parts[1]
                        }
                        val dir = File(target)
                        if (dir.exists() && dir.isDirectory) {
                            _uiState.value = _uiState.value.copy(cwd = dir.canonicalPath); ""
                        } else "cd: " + target + ": No such directory"
                    }
                    "mkdir" -> { if (parts.size < 2) "Usage: mkdir <dir>" else { File(_uiState.value.cwd, parts[1]).mkdirs(); "" } }
                    "rm"    -> { if (parts.size < 2) "Usage: rm <file>"  else { File(_uiState.value.cwd, parts[1]).delete();  "" } }
                    "env"   -> System.getenv().entries.joinToString("\n") { it.key + "=" + it.value }
                    else -> {
                        val proc = Runtime.getRuntime().exec(cmd)
                        val out = proc.inputStream.bufferedReader().readText()
                        val err = proc.errorStream.bufferedReader().readText()
                        proc.waitFor()
                        if (err.isNotBlank()) appendLine(TerminalLine(err.trim(), LineType.ERROR))
                        out.trim()
                    }
                }
                if (result.isNotBlank()) result.lines().forEach { appendLine(TerminalLine(it, LineType.OUTPUT)) }
            } catch (e: Exception) {
                appendLine(TerminalLine(e.message ?: "command failed", LineType.ERROR))
            }
        }
    }

    private fun showHelp() {
        appendLine(TerminalLine("clear  cd  ls  cat  mkdir  rm  pwd  echo  env  uname  whoami  date", LineType.SYSTEM))
        appendLine(TerminalLine(
            if (isTermuxAvailable) "Termux shell active - use pkg, git, python, node etc."
            else "Install Termux app for full shell access",
            LineType.SYSTEM
        ))
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
""")

# ─────────────────────────────────────────────────────────────────────────────
# 2. TERMINAL SCREEN - keyboard pushes up
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/terminal/TerminalScreen.kt", r"""package com.codemaster.aistudio.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

private val TermBg    = Color(0xFF0D1117)
private val TermGreen = Color(0xFF3FB950)
private val TermRed   = Color(0xFFF85149)
private val TermYellow= Color(0xFFD29922)
private val TermBlue  = Color(0xFF58A6FF)
private val TermGray  = Color(0xFF8B949E)
private val TermWhite = Color(0xFFE6EDF3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    projectId: Long,
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(projectId) { viewModel.init(projectId) }
    LaunchedEffect(uiState.lines.size) {
        if (uiState.lines.isNotEmpty())
            scope.launch { listState.animateScrollToItem(uiState.lines.size - 1) }
    }

    Scaffold(
        modifier = Modifier.imePadding(),   // ← keyboard pushes content up
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Terminal", fontWeight = FontWeight.Bold, color = TermWhite)
                        Text(
                            uiState.cwd.takeLast(38),
                            style = MaterialTheme.typography.labelSmall,
                            color = TermGray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = TermWhite) }
                },
                actions = {
                    if (uiState.isRunning) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(TermGreen, RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(4.dp))
                            Text("LIVE", color = TermGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = { viewModel.submitCommand() }) {
                        Icon(Icons.Default.DeleteSweep, "Clear", tint = TermWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF161B22))
            )
        },
        containerColor = TermBg,
        bottomBar = {
            Column(modifier = Modifier.background(Color(0xFF161B22))) {
                // Quick keys
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF21262D)).padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TermKey("Tab",  onClick = { viewModel.sendTab() })
                    TermKey("↑",    onClick = { viewModel.historyUp() })
                    TermKey("↓",    onClick = { viewModel.historyDown() })
                    TermKey("^C",   onClick = { viewModel.sendCtrlC() }, color = TermRed)
                    TermKey("^D",   onClick = { viewModel.sendCtrlD() }, color = TermYellow)
                    TermKey("~",    onClick = { viewModel.updateInput(uiState.input + "~") })
                    TermKey("/",    onClick = { viewModel.updateInput(uiState.input + "/") })
                    TermKey("-",    onClick = { viewModel.updateInput(uiState.input + "-") })
                    TermKey("|",    onClick = { viewModel.updateInput(uiState.input + "|") })
                    TermKey("&&",   onClick = { viewModel.updateInput(uiState.input + " && ") })
                }
                // Input row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$ ", color = TermGreen, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    TextField(
                        value = uiState.input,
                        onValueChange = { viewModel.updateInput(it) },
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = TermWhite
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = TermGreen
                        ),
                        placeholder = { Text("Enter command...", color = TermGray, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { viewModel.submitCommand() })
                    )
                    IconButton(onClick = { viewModel.submitCommand() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Send, "Run", tint = TermGreen, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).background(TermBg),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            items(uiState.lines) { line ->
                Text(
                    text = line.text,
                    color = when (line.type) {
                        LineType.INPUT  -> TermBlue
                        LineType.OUTPUT -> TermWhite
                        LineType.ERROR  -> TermRed
                        LineType.SYSTEM -> TermYellow
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
fun TermKey(label: String, onClick: () -> Unit, color: Color = Color(0xFF8B949E)) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(28.dp).defaultMinSize(minWidth = 32.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color)
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
""")

# ─────────────────────────────────────────────────────────────────────────────
# 3. AI CHAT SCREEN - keyboard pushes up
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/chat/AiChatScreen.kt", r"""package com.codemaster.aistudio.ui.screens.chat

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
        if (uiState.messages.isNotEmpty())
            listState.animateScrollToItem(uiState.messages.size - 1)
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val fileName = context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst(); cursor.getString(idx)
            } ?: "attachment"
            val content = context.contentResolver.openInputStream(it)?.use { s ->
                BufferedReader(InputStreamReader(s)).readText()
            } ?: ""
            viewModel.attachFile(fileName, content.take(8000))
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),   // ← keyboard pushes content up
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Chat", fontWeight = FontWeight.Bold)
                        Text("~${uiState.totalTokens} tokens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { viewModel.clearHistory() }) { Icon(Icons.Default.DeleteSweep, "Clear") }
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
                    scope.launch { if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size) }
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
                    items(uiState.messages, key = { it.id }) { message -> ChatBubble(message = message) }
                    if (uiState.isLoading) { item { TypingIndicator() } }
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
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) { Text("AI", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.widthIn(max = 300.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 4.dp,
                        topEnd   = if (isUser) 4.dp else 16.dp,
                        bottomStart = 16.dp, bottomEnd = 16.dp
                    ))
                    .background(if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                SelectionContainer {
                    if (!isUser && message.content.contains("```")) {
                        CodeAwareText(message.content)
                    } else {
                        Text(
                            text = message.content,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            if (!isUser) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("~${message.tokenCount} tokens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { clipboard.setText(AnnotatedString(message.content)) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        if (isUser) Spacer(Modifier.width(40.dp))
    }
}

@Composable
fun CodeAwareText(content: String) {
    val clipboard = LocalClipboardManager.current
    val parts = content.split("```")
    Column {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 0) {
                if (part.isNotBlank()) Text(part.trim(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            } else {
                val lines = part.lines()
                val code = if (lines.firstOrNull()?.all { it.isLetter() } == true) lines.drop(1).joinToString("\n") else part
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).padding(8.dp)
                ) {
                    Text(code.trim(), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = { clipboard.setText(AnnotatedString(code.trim())) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, "Copy code", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
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
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("CodeMaster AI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Ask me anything about your code." + "\n" + "Attach files for context.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ChatInputBar(
    text: String, attachedFileName: String?, isLoading: Boolean,
    onTextChange: (String) -> Unit, onAttach: () -> Unit, onClearAttachment: () -> Unit, onSend: () -> Unit
) {
    Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
        Column {
            AnimatedVisibility(visible = attachedFileName != null) {
                attachedFileName?.let { name ->
                    Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSecondaryContainer)
                        IconButton(onClick = onClearAttachment, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp)) }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onAttach, enabled = !isLoading) {
                    Icon(Icons.Default.AttachFile, "Attach", tint = if (attachedFileName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = text, onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask AI anything...") },
                    maxLines = 5, shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = onSend, enabled = (text.isNotBlank() || attachedFileName != null) && !isLoading, modifier = Modifier.size(48.dp)) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Send, "Send")
                }
            }
        }
    }
}
""")

# ─────────────────────────────────────────────────────────────────────────────
# 4. SNIPPET MODEL + DAO
# ─────────────────────────────────────────────────────────────────────────────
write("data/model/Snippet.kt", r"""package com.codemaster.aistudio.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "snippets")
data class Snippet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val code: String,
    val language: String = "kotlin",
    val tags: String = "",          // comma-separated
    val createdAt: Long = System.currentTimeMillis()
)
""")

write("data/dao/SnippetDao.kt", r"""package com.codemaster.aistudio.data.dao

import androidx.room.*
import com.codemaster.aistudio.data.model.Snippet
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY createdAt DESC")
    fun getAllSnippets(): Flow<List<Snippet>>

    @Query("SELECT * FROM snippets WHERE title LIKE '%' || :q || '%' OR code LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%' ORDER BY createdAt DESC")
    fun searchSnippets(q: String): Flow<List<Snippet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: Snippet): Long

    @Update
    suspend fun updateSnippet(snippet: Snippet)

    @Delete
    suspend fun deleteSnippet(snippet: Snippet)
}
""")

# ─────────────────────────────────────────────────────────────────────────────
# 5. DATABASE - add snippets table
# ─────────────────────────────────────────────────────────────────────────────
write("data/CodeMasterDatabase.kt", r"""package com.codemaster.aistudio.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.codemaster.aistudio.data.dao.ChatMessageDao
import com.codemaster.aistudio.data.dao.CodeFileDao
import com.codemaster.aistudio.data.dao.ProjectDao
import com.codemaster.aistudio.data.dao.SnippetDao
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.model.CodeFile
import com.codemaster.aistudio.data.model.Project
import com.codemaster.aistudio.data.model.Snippet
import com.codemaster.aistudio.data.model.StringListConverter

@Database(
    entities = [Project::class, ChatMessage::class, CodeFile::class, Snippet::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class CodeMasterDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun codeFileDao(): CodeFileDao
    abstract fun snippetDao(): SnippetDao
}
""")

# ─────────────────────────────────────────────────────────────────────────────
# 6. DI MODULE - add SnippetDao + GitHubService
# ─────────────────────────────────────────────────────────────────────────────
write("di/AppModule.kt", r"""package com.codemaster.aistudio.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.codemaster.aistudio.data.CodeMasterDatabase
import com.codemaster.aistudio.data.api.GitHubApiService
import com.codemaster.aistudio.data.api.GroqApiService
import com.codemaster.aistudio.data.dao.ChatMessageDao
import com.codemaster.aistudio.data.dao.CodeFileDao
import com.codemaster.aistudio.data.dao.ProjectDao
import com.codemaster.aistudio.data.dao.SnippetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "codemaster_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CodeMasterDatabase =
        Room.databaseBuilder(context, CodeMasterDatabase::class.java, "codemaster_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton fun provideProjectDao(db: CodeMasterDatabase): ProjectDao = db.projectDao()
    @Provides @Singleton fun provideChatMessageDao(db: CodeMasterDatabase): ChatMessageDao = db.chatMessageDao()
    @Provides @Singleton fun provideCodeFileDao(db: CodeMasterDatabase): CodeFileDao = db.codeFileDao()
    @Provides @Singleton fun provideSnippetDao(db: CodeMasterDatabase): SnippetDao = db.snippetDao()

    @Provides @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton @Named("groq")
    fun provideGroqRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.groq.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton @Named("github")
    fun provideGitHubRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton
    fun provideGroqApiService(@Named("groq") retrofit: Retrofit): GroqApiService =
        retrofit.create(GroqApiService::class.java)

    @Provides @Singleton
    fun provideGitHubApiService(@Named("github") retrofit: Retrofit): GitHubApiService =
        retrofit.create(GitHubApiService::class.java)
}
""")

# ─────────────────────────────────────────────────────────────────────────────
# 7. SNIPPET SCREEN
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/snippets/SnippetViewModel.kt", r"""package com.codemaster.aistudio.ui.screens.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.dao.SnippetDao
import com.codemaster.aistudio.data.model.Snippet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SnippetUiState(
    val snippets: List<Snippet> = emptyList(),
    val query: String = "",
    val showAddDialog: Boolean = false,
    val editingSnippet: Snippet? = null,
    val newTitle: String = "",
    val newCode: String = "",
    val newLanguage: String = "kotlin",
    val newTags: String = ""
)

@HiltViewModel
class SnippetViewModel @Inject constructor(
    private val snippetDao: SnippetDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SnippetUiState())
    val uiState: StateFlow<SnippetUiState> = _uiState.asStateFlow()

    init { loadSnippets() }

    private fun loadSnippets() {
        viewModelScope.launch {
            snippetDao.getAllSnippets().catch { }.collect { snippets ->
                _uiState.value = _uiState.value.copy(snippets = snippets)
            }
        }
    }

    fun search(q: String) {
        _uiState.value = _uiState.value.copy(query = q)
        viewModelScope.launch {
            if (q.isBlank()) snippetDao.getAllSnippets()
            else snippetDao.searchSnippets(q)
                .catch { }.collect { snippets ->
                    _uiState.value = _uiState.value.copy(snippets = snippets)
                }
        }
    }

    fun showAddDialog(snippet: Snippet? = null) {
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            editingSnippet = snippet,
            newTitle = snippet?.title ?: "",
            newCode = snippet?.code ?: "",
            newLanguage = snippet?.language ?: "kotlin",
            newTags = snippet?.tags ?: ""
        )
    }

    fun hideDialog() { _uiState.value = _uiState.value.copy(showAddDialog = false, editingSnippet = null) }
    fun updateTitle(v: String)    { _uiState.value = _uiState.value.copy(newTitle = v) }
    fun updateCode(v: String)     { _uiState.value = _uiState.value.copy(newCode = v) }
    fun updateLanguage(v: String) { _uiState.value = _uiState.value.copy(newLanguage = v) }
    fun updateTags(v: String)     { _uiState.value = _uiState.value.copy(newTags = v) }

    fun saveSnippet() {
        val s = _uiState.value
        if (s.newTitle.isBlank() || s.newCode.isBlank()) return
        viewModelScope.launch {
            val snippet = s.editingSnippet?.copy(
                title = s.newTitle, code = s.newCode,
                language = s.newLanguage, tags = s.newTags
            ) ?: Snippet(title = s.newTitle, code = s.newCode, language = s.newLanguage, tags = s.newTags)
            if (s.editingSnippet != null) snippetDao.updateSnippet(snippet)
            else snippetDao.insertSnippet(snippet)
            hideDialog()
        }
    }

    fun deleteSnippet(snippet: Snippet) {
        viewModelScope.launch { snippetDao.deleteSnippet(snippet) }
    }
}
""")

write("ui/screens/snippets/SnippetScreen.kt", r"""package com.codemaster.aistudio.ui.screens.snippets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codemaster.aistudio.data.model.Snippet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetScreen(
    onBack: () -> Unit,
    viewModel: SnippetViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Snippet Library", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, "Add snippet")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search snippets...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.query.isNotBlank()) {
                        IconButton(onClick = { viewModel.search("") }) { Icon(Icons.Default.Clear, null) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            if (uiState.snippets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Code, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No snippets yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Tap + to save reusable code", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.snippets, key = { it.id }) { snippet ->
                        SnippetCard(
                            snippet = snippet,
                            onEdit = { viewModel.showAddDialog(snippet) },
                            onDelete = { viewModel.deleteSnippet(snippet) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (uiState.showAddDialog) {
        SnippetDialog(
            title = uiState.newTitle,
            code = uiState.newCode,
            language = uiState.newLanguage,
            tags = uiState.newTags,
            isEditing = uiState.editingSnippet != null,
            onTitleChange = viewModel::updateTitle,
            onCodeChange = viewModel::updateCode,
            onLanguageChange = viewModel::updateLanguage,
            onTagsChange = viewModel::updateTags,
            onSave = { viewModel.saveSnippet() },
            onDismiss = { viewModel.hideDialog() }
        )
    }
}

@Composable
fun SnippetCard(snippet: Snippet, onEdit: () -> Unit, onDelete: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(snippet.title, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SuggestionChip(onClick = {}, label = { Text(snippet.language, fontSize = 10.sp) }, modifier = Modifier.height(22.dp))
                        if (snippet.tags.isNotBlank()) {
                            snippet.tags.split(",").take(3).forEach { tag ->
                                SuggestionChip(onClick = {}, label = { Text(tag.trim(), fontSize = 10.sp) }, modifier = Modifier.height(22.dp))
                            }
                        }
                    }
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(snippet.code)) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(18.dp))
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
                    }
                }
            }
            // Code preview
            Spacer(Modifier.height(8.dp))
            val preview = if (expanded) snippet.code else snippet.code.lines().take(4).joinToString("\n")
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(preview, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(8.dp).fillMaxWidth())
            }
            if (snippet.code.lines().size > 4) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                    Text(if (expanded) "Show less" else "Show more", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun SnippetDialog(
    title: String, code: String, language: String, tags: String,
    isEditing: Boolean,
    onTitleChange: (String) -> Unit, onCodeChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit, onTagsChange: (String) -> Unit,
    onSave: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Snippet" else "New Snippet", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = onTitleChange, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = language, onValueChange = onLanguageChange, label = { Text("Language") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = tags, onValueChange = onTagsChange, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("api, network, util") })
                OutlinedTextField(
                    value = code, onValueChange = onCodeChange,
                    label = { Text("Code *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5, maxLines = 12,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )
            }
        },
        confirmButton = { Button(onClick = onSave, enabled = title.isNotBlank() && code.isNotBlank()) { Text(if (isEditing) "Update" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
""")

# ─────────────────────────────────────────────────────────────────────────────
# 8. AI PERSONAS - settings + viewmodel update
# ─────────────────────────────────────────────────────────────────────────────
write("data/model/AiPersona.kt", r"""package com.codemaster.aistudio.data.model

data class AiPersona(
    val id: String,
    val name: String,
    val emoji: String,
    val systemPrompt: String
)

val AI_PERSONAS = listOf(
    AiPersona(
        id = "codemaster",
        name = "CodeMaster",
        emoji = "🤖",
        systemPrompt = "You are CodeMaster AI, an expert coding assistant. Be concise, helpful, and provide working code examples. Always explain your code."
    ),
    AiPersona(
        id = "reviewer",
        name = "Code Reviewer",
        emoji = "🔍",
        systemPrompt = "You are a senior code reviewer. Analyze code for bugs, security issues, performance problems, and style violations. Be thorough but constructive. Rate severity as HIGH/MEDIUM/LOW."
    ),
    AiPersona(
        id = "architect",
        name = "Architect",
        emoji = "🏗️",
        systemPrompt = "You are a software architect. Focus on system design, patterns, scalability, and best practices. Think in terms of modules, interfaces, and long-term maintainability."
    ),
    AiPersona(
        id = "debugger",
        name = "Debugger",
        emoji = "🐛",
        systemPrompt = "You are an expert debugger. When given code or error messages, systematically identify root causes. Always explain WHY the bug occurs and provide a tested fix."
    ),
    AiPersona(
        id = "teacher",
        name = "Teacher",
        emoji = "📚",
        systemPrompt = "You are a patient programming teacher. Explain concepts clearly with analogies and examples. Break down complex topics step by step. Encourage and be supportive."
    ),
    AiPersona(
        id = "android",
        name = "Android Expert",
        emoji = "📱",
        systemPrompt = "You are an Android development expert specializing in Kotlin, Jetpack Compose, Hilt, Room, and modern Android architecture. Give practical, production-ready code following MVVM and clean architecture patterns."
    )
)
""")

# Update SettingsRepository to include persona
write("data/repository/SettingsRepository.kt", r"""package com.codemaster.aistudio.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val API_KEY       = stringPreferencesKey("groq_api_key")
        val MODEL         = stringPreferencesKey("groq_model")
        val DARK_THEME    = booleanPreferencesKey("dark_theme")
        val GITHUB_TOKEN  = stringPreferencesKey("github_token")
        val GITHUB_OWNER  = stringPreferencesKey("github_owner")
        val GITHUB_REPO   = stringPreferencesKey("github_repo")
        val GITHUB_BRANCH = stringPreferencesKey("github_branch")
        val AI_PERSONA    = stringPreferencesKey("ai_persona")
    }

    suspend fun getApiKey(): String        = dataStore.data.first()[API_KEY] ?: ""
    suspend fun saveApiKey(k: String)      { dataStore.edit { it[API_KEY] = k } }
    suspend fun getModel(): String         = dataStore.data.first()[MODEL] ?: "llama-3.3-70b-versatile"
    suspend fun saveModel(m: String)       { dataStore.edit { it[MODEL] = m } }
    suspend fun getDarkTheme(): Boolean    = dataStore.data.first()[DARK_THEME] ?: true
    suspend fun saveDarkTheme(d: Boolean)  { dataStore.edit { it[DARK_THEME] = d } }
    suspend fun getGitHubToken(): String   = dataStore.data.first()[GITHUB_TOKEN] ?: ""
    suspend fun saveGitHubToken(t: String) { dataStore.edit { it[GITHUB_TOKEN] = t } }
    suspend fun getGitHubOwner(): String   = dataStore.data.first()[GITHUB_OWNER] ?: ""
    suspend fun saveGitHubOwner(o: String) { dataStore.edit { it[GITHUB_OWNER] = o } }
    suspend fun getGitHubRepo(): String    = dataStore.data.first()[GITHUB_REPO] ?: ""
    suspend fun saveGitHubRepo(r: String)  { dataStore.edit { it[GITHUB_REPO] = r } }
    suspend fun getGitHubBranch(): String  = dataStore.data.first()[GITHUB_BRANCH] ?: "main"
    suspend fun saveGitHubBranch(b: String){ dataStore.edit { it[GITHUB_BRANCH] = b } }
    suspend fun getPersonaId(): String     = dataStore.data.first()[AI_PERSONA] ?: "codemaster"
    suspend fun savePersonaId(id: String)  { dataStore.edit { it[AI_PERSONA] = id } }
}
""")

# ─────────────────────────────────────────────────────────────────────────────
# 9. AI CHAT VIEWMODEL - persona + code review
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/chat/AiChatViewModel.kt", r"""package com.codemaster.aistudio.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.AiPersona
import com.codemaster.aistudio.data.model.AI_PERSONAS
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.repository.AiRepository
import com.codemaster.aistudio.data.repository.ChatRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
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
    val currentPersona: AiPersona = AI_PERSONAS.first(),
    val showPersonaPicker: Boolean = false
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var currentProjectId: Long = -1L

    fun init(projectId: Long) {
        currentProjectId = projectId
        viewModelScope.launch {
            val personaId = settingsRepository.getPersonaId()
            val persona = AI_PERSONAS.find { it.id == personaId } ?: AI_PERSONAS.first()
            _uiState.value = _uiState.value.copy(currentPersona = persona)

            chatRepository.getMessagesForProject(projectId)
                .catch { }
                .collect { messages ->
                    _uiState.value = _uiState.value.copy(
                        messages = messages,
                        totalTokens = messages.sumOf { it.tokenCount }
                    )
                }
        }
    }

    fun updateInput(text: String) { _uiState.value = _uiState.value.copy(inputText = text) }
    fun attachFile(fileName: String, content: String) { _uiState.value = _uiState.value.copy(attachedFileName = fileName, attachedFileContent = content) }
    fun clearAttachment() { _uiState.value = _uiState.value.copy(attachedFileName = null, attachedFileContent = null) }
    fun showPersonaPicker() { _uiState.value = _uiState.value.copy(showPersonaPicker = true) }
    fun hidePersonaPicker() { _uiState.value = _uiState.value.copy(showPersonaPicker = false) }

    fun selectPersona(persona: AiPersona) {
        viewModelScope.launch {
            settingsRepository.savePersonaId(persona.id)
            _uiState.value = _uiState.value.copy(currentPersona = persona, showPersonaPicker = false)
        }
    }

    fun requestCodeReview() {
        val attached = _uiState.value.attachedFileContent
        val input = _uiState.value.inputText
        if (attached == null && input.isBlank()) return
        val reviewPrompt = "Please do a thorough code review. Check for: bugs, security issues, performance problems, code style, and suggest improvements."
        _uiState.value = _uiState.value.copy(inputText = reviewPrompt)
        sendMessage()
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank() && state.attachedFileContent == null) return
        if (state.isLoading) return

        val attachedName = state.attachedFileName
        val newline = "\n"
        val displayText = if (attachedName != null) text + newline + "📎 " + attachedName else text

        viewModelScope.launch {
            val userMessage = ChatMessage(
                projectId = currentProjectId, role = "user",
                content = displayText,
                tokenCount = aiRepository.estimateTokens(displayText),
                attachedFileName = attachedName
            )
            chatRepository.saveMessage(userMessage)
            _uiState.value = state.copy(inputText = "", isLoading = true, attachedFileName = null, attachedFileContent = null)

            val result = aiRepository.sendMessage(
                history = _uiState.value.messages.dropLast(1),
                userMessage = text,
                attachedFileContent = state.attachedFileContent,
                systemPrompt = state.currentPersona.systemPrompt
            )

            result.fold(
                onSuccess = { response ->
                    chatRepository.saveMessage(ChatMessage(
                        projectId = currentProjectId, role = "assistant",
                        content = response, tokenCount = aiRepository.estimateTokens(response)
                    ))
                    _uiState.value = _uiState.value.copy(isLoading = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun clearHistory() { viewModelScope.launch { chatRepository.clearMessagesForProject(currentProjectId) } }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
""")

# ─────────────────────────────────────────────────────────────────────────────
# 10. GIT REPOSITORY - commit/push from app
# ─────────────────────────────────────────────────────────────────────────────
write("data/repository/GitRepository.kt", r"""package com.codemaster.aistudio.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class GitStatus(
    val branch: String = "",
    val staged: List<String> = emptyList(),
    val unstaged: List<String> = emptyList(),
    val untracked: List<String> = emptyList(),
    val isRepo: Boolean = false,
    val lastCommit: String = ""
)

@Singleton
class GitRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private fun findGitBinary(): String? {
        return listOf(
            "/data/data/com.termux/files/usr/bin/git",
            "/system/bin/git",
            "/system/xbin/git"
        ).firstOrNull { File(it).exists() }
    }

    private fun buildEnv(): Map<String, String> {
        val env = mutableMapOf<String, String>()
        if (File("/data/data/com.termux/files/usr/bin/git").exists()) {
            env["HOME"] = "/data/data/com.termux/files/home"
            env["PATH"] = "/data/data/com.termux/files/usr/bin:/system/bin"
            env["LD_LIBRARY_PATH"] = "/data/data/com.termux/files/usr/lib"
            env["GIT_EXEC_PATH"] = "/data/data/com.termux/files/usr/libexec/git-core"
            env["GIT_TEMPLATE_DIR"] = "/data/data/com.termux/files/usr/share/git-core/templates"
        } else {
            env["HOME"] = context.filesDir.absolutePath
            env["PATH"] = "/system/bin:/system/xbin"
        }
        return env
    }

    private suspend fun runGit(vararg args: String, workDir: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val git = findGitBinary()
                    ?: return@withContext Result.failure(Exception("git not found. Install Termux and run: pkg install git"))

                val cmd = mutableListOf(git) + args.toList()
                val pb = ProcessBuilder(cmd).apply {
                    workDir?.let { directory(File(it)) }
                    val env = environment()
                    buildEnv().forEach { (k, v) -> env[k] = v }
                    redirectErrorStream(false)
                }
                val proc = pb.start()
                val out = proc.inputStream.bufferedReader().readText().trim()
                val err = proc.errorStream.bufferedReader().readText().trim()
                proc.waitFor()
                if (proc.exitValue() != 0 && err.isNotBlank()) Result.failure(Exception(err))
                else Result.success(if (out.isNotBlank()) out else err)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getStatus(repoPath: String): Result<GitStatus> = withContext(Dispatchers.IO) {
        try {
            val isRepo = runGit("rev-parse", "--git-dir", workDir = repoPath).isSuccess
            if (!isRepo) return@withContext Result.success(GitStatus(isRepo = false))

            val branch = runGit("branch", "--show-current", workDir = repoPath).getOrElse { "unknown" }
            val statusOut = runGit("status", "--porcelain", workDir = repoPath).getOrElse { "" }
            val lastCommit = runGit("log", "--oneline", "-1", workDir = repoPath).getOrElse { "No commits" }

            val staged = mutableListOf<String>()
            val unstaged = mutableListOf<String>()
            val untracked = mutableListOf<String>()

            statusOut.lines().filter { it.isNotBlank() }.forEach { line ->
                val xy = line.take(2)
                val file = line.drop(3)
                when {
                    xy.startsWith("?") -> untracked.add(file)
                    xy[0] != ' '       -> staged.add(file)
                    xy[1] != ' '       -> unstaged.add(file)
                }
            }
            Result.success(GitStatus(
                branch = branch, staged = staged, unstaged = unstaged,
                untracked = untracked, isRepo = true, lastCommit = lastCommit
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stageAll(repoPath: String): Result<String> = runGit("add", "-A", workDir = repoPath)

    suspend fun commit(repoPath: String, message: String): Result<String> {
        val owner = settingsRepository.getGitHubOwner()
        val email = if (owner.isNotBlank()) "$owner@users.noreply.github.com" else "codemaster@app.local"
        runGit("config", "user.email", email, workDir = repoPath)
        runGit("config", "user.name", if (owner.isNotBlank()) owner else "CodeMaster", workDir = repoPath)
        return runGit("commit", "-m", message, workDir = repoPath)
    }

    suspend fun push(repoPath: String): Result<String> {
        val token = settingsRepository.getGitHubToken()
        val owner = settingsRepository.getGitHubOwner()
        val repo  = settingsRepository.getGitHubRepo()
        if (token.isBlank() || owner.isBlank() || repo.isBlank())
            return Result.failure(Exception("Configure GitHub token, username, and repo in Settings first."))
        val url = "https://$owner:$token@github.com/$owner/$repo.git"
        return runGit("push", url, "HEAD", workDir = repoPath)
    }

    suspend fun pull(repoPath: String): Result<String> = runGit("pull", workDir = repoPath)

    suspend fun log(repoPath: String, count: Int = 10): Result<String> =
        runGit("log", "--oneline", "-$count", workDir = repoPath)
}
""")

# ─────────────────────────────────────────────────────────────────────────────
# 11. GIT SCREEN
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/git/GitViewModel.kt", r"""package com.codemaster.aistudio.ui.screens.git

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.GitRepository
import com.codemaster.aistudio.data.repository.GitStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class GitUiState(
    val status: GitStatus = GitStatus(),
    val commitMessage: String = "",
    val isLoading: Boolean = false,
    val log: String = "",
    val result: String? = null,
    val error: String? = null,
    val repoPath: String = ""
)

@HiltViewModel
class GitViewModel @Inject constructor(
    private val gitRepository: GitRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitUiState())
    val uiState: StateFlow<GitUiState> = _uiState.asStateFlow()

    fun init(projectId: Long) {
        // Find repo - check Termux home first, then app files dir
        val paths = listOf(
            "/data/data/com.termux/files/home/codemaster-ai-studio",
            context.filesDir.absolutePath
        )
        val repoPath = paths.firstOrNull { File(it, ".git").exists() } ?: paths.first()
        _uiState.value = _uiState.value.copy(repoPath = repoPath)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = gitRepository.getStatus(_uiState.value.repoPath)
            result.fold(
                onSuccess = { status ->
                    val logResult = gitRepository.log(_uiState.value.repoPath)
                    _uiState.value = _uiState.value.copy(
                        status = status,
                        log = logResult.getOrElse { "" },
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun updateCommitMessage(msg: String) { _uiState.value = _uiState.value.copy(commitMessage = msg) }

    fun stageAndCommit() {
        val msg = _uiState.value.commitMessage.trim()
        if (msg.isBlank()) { _uiState.value = _uiState.value.copy(error = "Commit message cannot be empty"); return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            gitRepository.stageAll(_uiState.value.repoPath).fold(
                onSuccess = {
                    gitRepository.commit(_uiState.value.repoPath, msg).fold(
                        onSuccess = { out ->
                            _uiState.value = _uiState.value.copy(isLoading = false, result = "Committed: $out", commitMessage = "")
                            refresh()
                        },
                        onFailure = { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
                    )
                },
                onFailure = { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
            )
        }
    }

    fun push() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, result = null)
            gitRepository.push(_uiState.value.repoPath).fold(
                onSuccess = { out -> _uiState.value = _uiState.value.copy(isLoading = false, result = "Pushed! " + out) },
                onFailure = { e  -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
            )
        }
    }

    fun pull() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, result = null)
            gitRepository.pull(_uiState.value.repoPath).fold(
                onSuccess = { out -> _uiState.value = _uiState.value.copy(isLoading = false, result = out); refresh() },
                onFailure = { e  -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
            )
        }
    }

    fun clearMessages() { _uiState.value = _uiState.value.copy(result = null, error = null) }
}
""")

write("ui/screens/git/GitScreen.kt", r"""package com.codemaster.aistudio.ui.screens.git

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    projectId: Long,
    onBack: () -> Unit,
    viewModel: GitViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(projectId) { viewModel.init(projectId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Git", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status card
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountTree, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    HorizontalDivider()
                    if (!uiState.status.isRepo) {
                        Text("Not a git repository", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Text("Repo path: " + uiState.repoPath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        GitInfoRow("Branch", uiState.status.branch)
                        GitInfoRow("Last commit", uiState.status.lastCommit.take(60))
                        GitInfoRow("Staged", uiState.status.staged.size.toString() + " files")
                        GitInfoRow("Unstaged", uiState.status.unstaged.size.toString() + " files")
                        GitInfoRow("Untracked", uiState.status.untracked.size.toString() + " files")
                    }
                }
            }

            // Result/error messages
            uiState.result?.let { msg ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF69F0AE), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            uiState.error?.let { err ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) }
                    }
                }
            }

            // Commit section
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Commit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Commit", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    HorizontalDivider()
                    OutlinedTextField(
                        value = uiState.commitMessage,
                        onValueChange = { viewModel.updateCommitMessage(it) },
                        label = { Text("Commit message") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2, maxLines = 4,
                        placeholder = { Text("feat: add new feature") }
                    )
                    Button(
                        onClick = { viewModel.stageAndCommit() },
                        enabled = uiState.commitMessage.isNotBlank() && !uiState.isLoading && uiState.status.isRepo,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Stage All & Commit")
                    }
                }
            }

            // Push/Pull
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.push() },
                    enabled = !uiState.isLoading && uiState.status.isRepo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Push")
                }
                OutlinedButton(
                    onClick = { viewModel.pull() },
                    enabled = !uiState.isLoading && uiState.status.isRepo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pull")
                }
            }

            // Log
            if (uiState.log.isNotBlank()) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Recent Commits", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = Color(0xFF8B949E))
                        Spacer(Modifier.height(8.dp))
                        uiState.log.lines().forEach { line ->
                            Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF58A6FF), lineHeight = 18.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun GitInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    }
}
""")

# ─────────────────────────────────────────────────────────────────────────────
# 12. NAV GRAPH - add snippets + git routes
# ─────────────────────────────────────────────────────────────────────────────
write("ui/navigation/NavGraph.kt", r"""package com.codemaster.aistudio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.codemaster.aistudio.ui.screens.build.BuildScreen
import com.codemaster.aistudio.ui.screens.chat.AiChatScreen
import com.codemaster.aistudio.ui.screens.editor.CodeEditorScreen
import com.codemaster.aistudio.ui.screens.git.GitScreen
import com.codemaster.aistudio.ui.screens.home.HomeScreen
import com.codemaster.aistudio.ui.screens.settings.SettingsScreen
import com.codemaster.aistudio.ui.screens.snippets.SnippetScreen
import com.codemaster.aistudio.ui.screens.terminal.TerminalScreen

sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object Settings : Screen("settings")
    object Snippets : Screen("snippets")
    object Chat     : Screen("chat/{projectId}")     { fun createRoute(id: Long = -1L) = "chat/$id" }
    object Editor   : Screen("editor/{projectId}/{fileId}") { fun createRoute(pid: Long, fid: Long = -1L) = "editor/$pid/$fid" }
    object Build    : Screen("build/{projectId}")    { fun createRoute(id: Long = -1L) = "build/$id" }
    object Terminal : Screen("terminal/{projectId}") { fun createRoute(id: Long = -1L) = "terminal/$id" }
    object Git      : Screen("git/{projectId}")      { fun createRoute(id: Long = -1L) = "git/$id" }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onOpenChat      = { navController.navigate(Screen.Chat.createRoute(it)) },
                onOpenEditor    = { navController.navigate(Screen.Editor.createRoute(it)) },
                onOpenBuild     = { navController.navigate(Screen.Build.createRoute(it)) },
                onOpenTerminal  = { navController.navigate(Screen.Terminal.createRoute(it)) },
                onOpenGit       = { navController.navigate(Screen.Git.createRoute(it)) },
                onOpenSnippets  = { navController.navigate(Screen.Snippets.route) },
                onOpenSettings  = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Snippets.route) {
            SnippetScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Chat.route, arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })) { back ->
            AiChatScreen(projectId = back.arguments?.getLong("projectId") ?: -1L, onBack = { navController.popBackStack() })
        }

        composable(Screen.Editor.route, arguments = listOf(
            navArgument("projectId") { type = NavType.LongType },
            navArgument("fileId")    { type = NavType.LongType; defaultValue = -1L }
        )) { back ->
            CodeEditorScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                fileId    = back.arguments?.getLong("fileId") ?: -1L,
                onBack         = { navController.popBackStack() },
                onOpenChat     = { navController.navigate(Screen.Chat.createRoute(it)) },
                onOpenTerminal = { navController.navigate(Screen.Terminal.createRoute(it)) }
            )
        }

        composable(Screen.Build.route, arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })) { back ->
            BuildScreen(
                projectId      = back.arguments?.getLong("projectId") ?: -1L,
                onBack         = { navController.popBackStack() },
                onGoToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Terminal.route, arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })) { back ->
            TerminalScreen(projectId = back.arguments?.getLong("projectId") ?: -1L, onBack = { navController.popBackStack() })
        }

        composable(Screen.Git.route, arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })) { back ->
            GitScreen(projectId = back.arguments?.getLong("projectId") ?: -1L, onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
""")

# ─────────────────────────────────────────────────────────────────────────────
# 13. HOME SCREEN - add Git + Snippets buttons
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/home/HomeScreen.kt", r"""package com.codemaster.aistudio.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codemaster.aistudio.data.model.Project
import java.text.SimpleDateFormat
import java.util.*

val LANGUAGES = listOf("Kotlin","Java","Python","JavaScript","TypeScript","Rust","Go","C++","Swift","Dart","PHP","Ruby")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenChat: (Long) -> Unit,
    onOpenEditor: (Long) -> Unit,
    onOpenBuild: (Long) -> Unit,
    onOpenTerminal: (Long) -> Unit,
    onOpenGit: (Long) -> Unit,
    onOpenSnippets: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("CodeMaster AI", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSnippets) { Icon(Icons.Default.Bookmark, "Snippets") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showNewProjectDialog() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New Project") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.projects.isEmpty() -> EmptyProjectsPlaceholder(modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Text("Projects (${uiState.projects.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(uiState.projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onOpenChat     = { onOpenChat(project.id) },
                            onOpenEditor   = { onOpenEditor(project.id) },
                            onOpenBuild    = { onOpenBuild(project.id) },
                            onOpenTerminal = { onOpenTerminal(project.id) },
                            onOpenGit      = { onOpenGit(project.id) },
                            onDelete       = { viewModel.deleteProject(project) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
            uiState.error?.let { error ->
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp), action = { TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") } }) { Text(error) }
            }
        }

        if (uiState.showNewProjectDialog) {
            NewProjectDialog(
                name = uiState.newProjectName, language = uiState.newProjectLanguage, description = uiState.newProjectDescription,
                onNameChange = viewModel::updateProjectName, onLanguageChange = viewModel::updateProjectLanguage, onDescriptionChange = viewModel::updateProjectDescription,
                onCreate = { viewModel.createProject { id -> onOpenEditor(id) } },
                onDismiss = viewModel::hideNewProjectDialog
            )
        }
    }
}

@Composable
fun ProjectCard(
    project: Project,
    onOpenChat: () -> Unit, onOpenEditor: () -> Unit, onOpenBuild: () -> Unit,
    onOpenTerminal: () -> Unit, onOpenGit: () -> Unit, onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
                    contentAlignment = Alignment.Center
                ) { Text(project.language.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(project.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${project.language} • ${dateFormat.format(Date(project.lastModified))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
                    }
                }
            }
            if (project.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(project.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ProjectActionButton(Icons.Default.ChatBubbleOutline, "Chat",     onOpenChat)
                ProjectActionButton(Icons.Default.Code,              "Editor",   onOpenEditor)
                ProjectActionButton(Icons.Default.Terminal,          "Terminal", onOpenTerminal)
                ProjectActionButton(Icons.Default.AccountTree,       "Git",      onOpenGit)
                ProjectActionButton(Icons.Default.Build,             "Build",    onOpenBuild)
            }
        }
    }
}

@Composable
fun ProjectActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun EmptyProjectsPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))
        Text("No projects yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text("Tap + to create your first project", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectDialog(
    name: String, language: String, description: String,
    onNameChange: (String) -> Unit, onLanguageChange: (String) -> Unit, onDescriptionChange: (String) -> Unit,
    onCreate: () -> Unit, onDismiss: () -> Unit
) {
    var langExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Project", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("Project Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                ExposedDropdownMenuBox(expanded = langExpanded, onExpandedChange = { langExpanded = it }) {
                    OutlinedTextField(value = language, onValueChange = {}, readOnly = true, label = { Text("Language") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        LANGUAGES.forEach { lang -> DropdownMenuItem(text = { Text(lang) }, onClick = { onLanguageChange(lang); langExpanded = false }) }
                    }
                }
                OutlinedTextField(value = description, onValueChange = onDescriptionChange, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3)
            }
        },
        confirmButton = { Button(onClick = onCreate, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
""")

print("\n" + "="*52)
print("Phase 5 complete!")
print("\nNew features written:")
print("  Terminal: fixed --login crash")
print("  Keyboard: imePadding on chat + terminal")
print("  Snippets: full library with search/edit/copy")
print("  AI Personas: 6 personas (Reviewer, Debugger etc)")
print("  Code Review: one-tap review button")
print("  Git: commit/push/pull/status/log from app")
print("\nNow run:")
print("  git add -A")
print("  git commit -m 'feat: Phase 5 - snippets, personas, git, code review, keyboard fix, terminal fix'")
print("  git push origin HEAD")
print("="*52)
