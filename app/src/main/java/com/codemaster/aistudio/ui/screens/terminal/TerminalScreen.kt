package com.codemaster.aistudio.ui.screens.terminal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Terminal Colors ───────────────────────────────────────────
private val TermBg = Color(0xFF0C0C0C)
private val TermGreen = Color(0xFF00FF00)
private val TermBlue = Color(0xFF58A6FF)
private val TermYellow = Color(0xFFF0883E)
private val TermRed = Color(0xFFF85149)
private val TermGray = Color(0xFF8B949E)
private val TermWhite = Color(0xFFE6EDF3)

// ─── Terminal Line Types ───────────────────────────────────────
enum class LineType { SYSTEM, INPUT, OUTPUT, ERROR, SUCCESS, PROMPT }

data class TerminalLine(
    val text: String,
    val type: LineType = LineType.OUTPUT
)

// ─── Quick Command Templates ───────────────────────────────────
val QUICK_COMMANDS = listOf(
    "python3 main.py" to "🐍",
    "node index.js" to "💛",
    "gradle assembleDebug" to "🔨",
    "ls -la" to "📁",
    "git status" to "📊",
    "git push origin main" to "🚀"
)

// ─── Termux Intent Helper ──────────────────────────────────────
object TermuxIntentHelper {

    // Check if Termux is installed
    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.termux", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    // Run a command in Termux (opens Termux and runs it)
    // Requires Termux:API or RUN_COMMAND permission
    fun runCommand(context: Context, command: String): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_TERMINAL", true) // Open in terminal window
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            }
            context.startService(intent)
            true
        } catch (e: Exception) {
            // Fallback: just open Termux
            openTermux(context)
            false
        }
    }

    // Open Termux app directly
    fun openTermux(context: Context): Boolean {
        return try {
            val intent = context.packageManager
                .getLaunchIntentForPackage("com.termux")
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    // Open Termux Play Store page if not installed
    fun openTermuxInstallPage(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.termux"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // Share a file path to Termux
    fun openFileInTermux(context: Context, filePath: String): Boolean {
        return runCommand(context, "cd '${filePath.substringBeforeLast("/")}' && ls")
    }
}

// ─── Terminal ViewModel ────────────────────────────────────────
data class TerminalUiState(
    val lines: List<TerminalLine> = listOf(
        TerminalLine("CodeMaster Terminal", LineType.SYSTEM),
        TerminalLine("Commands run via Termux deep-link", LineType.SYSTEM),
        TerminalLine("Type a command or use quick actions below", LineType.SYSTEM),
        TerminalLine("─".repeat(40), LineType.SYSTEM),
    ),
    val input: String = "",
    val commandHistory: List<String> = emptyList(),
    val historyIndex: Int = -1,
    val isTermuxInstalled: Boolean = false,
    val projectPath: String = ""
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    init {
        val termuxInstalled = TermuxIntentHelper.isTermuxInstalled(context)
        _uiState.update { it.copy(isTermuxInstalled = termuxInstalled) }
        if (!termuxInstalled) {
            addLine("⚠️  Termux not found. Install it to run real commands.", LineType.ERROR)
            addLine("Tap 'Install Termux' below to get it from F-Droid.", LineType.SYSTEM)
        } else {
            addLine("✅ Termux detected. Commands will run in Termux.", LineType.SUCCESS)
        }
    }

    fun init(projectId: String, projectPath: String) {
        _uiState.update { it.copy(projectPath = projectPath) }
        if (projectPath.isNotBlank()) {
            addLine("📁 Project: $projectPath", LineType.SYSTEM)
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(input = text, historyIndex = -1) }
    }

    fun executeCommand() {
        val state = _uiState.value
        val cmd = state.input.trim()
        if (cmd.isBlank()) return

        // Add to history
        val newHistory = listOf(cmd) + state.commandHistory.take(49)
        _uiState.update { it.copy(input = "", commandHistory = newHistory, historyIndex = -1) }

        // Show the command in terminal
        addLine("$ $cmd", LineType.INPUT)

        // Handle built-in commands locally
        when {
            cmd == "clear" -> {
                _uiState.update { it.copy(lines = emptyList()) }
                return
            }
            cmd == "help" -> {
                addLine("""Available commands:
  clear        Clear terminal
  help         Show this help
  pwd          Show project path
  Any other command → sent to Termux""", LineType.OUTPUT)
                return
            }
            cmd == "pwd" -> {
                addLine(state.projectPath.ifBlank { "/data/data/com.termux/files/home" }, LineType.OUTPUT)
                return
            }
            else -> {
                // Send to Termux
                sendToTermux(cmd)
            }
        }
    }

    fun sendToTermux(command: String) {
        val state = _uiState.value
        val fullCommand = if (state.projectPath.isNotBlank()) {
            "cd '${state.projectPath}' && $command"
        } else {
            command
        }

        val success = TermuxIntentHelper.runCommand(context, fullCommand)
        if (success) {
            addLine("→ Sent to Termux: $command", LineType.SUCCESS)
            addLine("  (Check Termux for output)", LineType.SYSTEM)
        } else {
            addLine("⚠️  Could not reach Termux. Opening Termux app...", LineType.ERROR)
        }
    }

    fun runQuickCommand(command: String) {
        _uiState.update { it.copy(input = command) }
        executeCommand()
    }

    fun historyUp() {
        val state = _uiState.value
        if (state.commandHistory.isEmpty()) return
        val newIndex = minOf(state.historyIndex + 1, state.commandHistory.size - 1)
        _uiState.update {
            it.copy(
                historyIndex = newIndex,
                input = it.commandHistory[newIndex]
            )
        }
    }

    fun historyDown() {
        val state = _uiState.value
        val newIndex = state.historyIndex - 1
        _uiState.update {
            it.copy(
                historyIndex = newIndex,
                input = if (newIndex < 0) "" else it.commandHistory[newIndex]
            )
        }
    }

    fun openTermux() {
        TermuxIntentHelper.openTermux(context)
    }

    fun installTermux() {
        TermuxIntentHelper.openTermuxInstallPage(context)
    }

    fun clearTerminal() {
        _uiState.update { it.copy(lines = emptyList()) }
    }

    private fun addLine(text: String, type: LineType = LineType.OUTPUT) {
        _uiState.update { state ->
            state.copy(lines = state.lines + TerminalLine(text, type))
        }
    }
}

// ─── Terminal Screen ───────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(projectId) {
        viewModel.init(projectId, "")
    }

    // Auto scroll to bottom on new lines
    LaunchedEffect(uiState.lines.size) {
        if (uiState.lines.isNotEmpty()) {
            listState.animateScrollToItem(uiState.lines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal", fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Open Termux directly
                    IconButton(onClick = { viewModel.openTermux() }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open Termux")
                    }
                    // Clear terminal
                    IconButton(onClick = { viewModel.clearTerminal() }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF161B22)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(TermBg)
        ) {
            // Termux not installed banner
            if (!uiState.isTermuxInstalled) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF5C1C1C)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Termux required for real command execution",
                            color = TermWhite,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.installTermux() }) {
                            Text("Install", color = TermBlue)
                        }
                    }
                }
            }

            // Quick command chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_COMMANDS.forEach { (cmd, emoji) ->
                    SuggestionChip(
                        onClick = { viewModel.runQuickCommand(cmd) },
                        label = {
                            Text(
                                "$emoji $cmd",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFF21262D)
                        )
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF30363D))

            // Terminal output
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(items = uiState.lines) { line ->
                    TerminalLineView(line)
                }
            }

            HorizontalDivider(color = Color(0xFF30363D))

            // History navigation + input
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // History nav row
                if (uiState.commandHistory.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { viewModel.historyUp() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous",
                                modifier = Modifier.size(16.dp), tint = TermGray)
                            Text("prev", color = TermGray, fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace)
                        }
                        TextButton(
                            onClick = { viewModel.historyDown() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next",
                                modifier = Modifier.size(16.dp), tint = TermGray)
                            Text("next", color = TermGray, fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // Command input row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "$ ",
                        color = TermGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = uiState.input,
                        onValueChange = viewModel::updateInput,
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = TermWhite
                        ),
                        placeholder = {
                            Text("Enter command...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = TermGray)
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrect = false,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = { viewModel.executeCommand() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TermGreen,
                            unfocusedBorderColor = Color(0xFF30363D),
                            cursorColor = TermGreen
                        ),
                        shape = RoundedCornerShape(6.dp),
                        singleLine = true
                    )
                    FilledIconButton(
                        onClick = { viewModel.executeCommand() },
                        enabled = uiState.input.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = TermGreen,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Run",
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ─── Terminal Line Renderer ────────────────────────────────────
@Composable
fun TerminalLineView(line: TerminalLine) {
    val color = when (line.type) {
        LineType.SYSTEM -> TermYellow
        LineType.INPUT -> TermBlue
        LineType.OUTPUT -> TermWhite
        LineType.ERROR -> TermRed
        LineType.SUCCESS -> TermGreen
        LineType.PROMPT -> TermGreen
    }
    Text(
        text = line.text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

// ─── Missing import for collectAsStateWithLifecycle ────────────
// Add this import to the file:
// import androidx.lifecycle.compose.collectAsStateWithLifecycle
