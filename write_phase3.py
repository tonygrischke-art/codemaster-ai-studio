#!/usr/bin/env python3
import os

BASE = os.path.expanduser("~/codemaster-ai-studio/app/src/main/java/com/codemaster/aistudio")
RES  = os.path.expanduser("~/codemaster-ai-studio/app/src/main")

files = {}

# ─────────────────────────────────────────────────────────────────────────────
# 1. CODE EDITOR SCREEN — wired to real MonacoEditorView
# ─────────────────────────────────────────────────────────────────────────────
files["ui/screens/editor/CodeEditorScreen.kt"] = r'''package com.codemaster.aistudio.ui.screens.editor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codemaster.aistudio.data.model.CodeFile
import com.codemaster.aistudio.ui.components.MonacoEditorView
import com.codemaster.aistudio.ui.components.detectLanguage
import com.codemaster.aistudio.ui.components.rememberMonacoEditorState
import com.codemaster.aistudio.ui.screens.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    projectId: Long,
    fileId: Long,
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenTerminal: (Long) -> Unit,
    viewModel: CodeEditorViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val monacoState = rememberMonacoEditorState()
    var showEditorToolbar by remember { mutableStateOf(true) }
    var fontSize by remember { mutableStateOf(14) }

    LaunchedEffect(projectId, fileId) { viewModel.init(projectId, fileId) }

    // When switching files push new content into Monaco imperatively (no reload)
    LaunchedEffect(uiState.currentFile?.id) {
        uiState.currentFile?.let { file ->
            monacoState.setValue(file.content)
            monacoState.setLanguage(detectLanguage(file.name))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            uiState.currentFile?.name ?: uiState.projectName,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (uiState.isDirty) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    " ● ",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    // Cursor position
                    if (uiState.cursorLine > 0) {
                        Text(
                            "${uiState.cursorLine}:${uiState.cursorCol}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.toggleFileTree() }) {
                        Icon(Icons.Default.AccountTree, "Files")
                    }
                    IconButton(onClick = { showEditorToolbar = !showEditorToolbar }) {
                        Icon(Icons.Default.Tune, "Toolbar")
                    }
                    if (uiState.isDirty) {
                        IconButton(onClick = {
                            viewModel.saveCurrentFile()
                        }) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, "Save")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Editor action toolbar ──────────────────────────────────
            AnimatedVisibility(visible = showEditorToolbar) {
                EditorToolbar(
                    onUndo = { monacoState.undo() },
                    onRedo = { monacoState.redo() },
                    onFormat = { monacoState.format() },
                    onFind = { monacoState.find() },
                    onFontPlus = { fontSize = (fontSize + 1).coerceAtMost(24); monacoState.setFontSize(fontSize) },
                    onFontMinus = { fontSize = (fontSize - 1).coerceAtLeast(8); monacoState.setFontSize(fontSize) },
                    onTerminal = { onOpenTerminal(projectId) },
                    onChat = { onOpenChat(projectId) },
                    onInsertTab = { monacoState.insertAtCursor("  ") },
                    fontSize = fontSize
                )
            }

            Row(modifier = Modifier.weight(1f)) {
                // ── File tree ──────────────────────────────────────────
                AnimatedVisibility(
                    visible = uiState.showFileTree,
                    enter = slideInHorizontally() + fadeIn(),
                    exit = slideOutHorizontally() + fadeOut()
                ) {
                    FileTreePanel(
                        files = uiState.files,
                        currentFile = uiState.currentFile,
                        onFileClick = { file ->
                            // Save current before switching
                            if (uiState.isDirty) viewModel.saveCurrentFile()
                            viewModel.openFile(file)
                        },
                        onDeleteFile = { viewModel.deleteFile(it) },
                        onNewFile = { viewModel.showNewFileDialog() },
                        modifier = Modifier.width(190.dp)
                    )
                }

                // ── Monaco Editor ──────────────────────────────────────
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (uiState.currentFile == null) {
                        NoFileOpen(onNewFile = { viewModel.showNewFileDialog() })
                    } else {
                        MonacoEditorView(
                            value = uiState.content,
                            language = detectLanguage(uiState.currentFile?.name ?: ""),
                            isDarkTheme = settingsState.isDarkTheme,
                            fontSize = fontSize,
                            onChange = { newContent ->
                                viewModel.updateContent(newContent)
                            },
                            onCursorChange = { line, col ->
                                viewModel.updateCursor(line, col)
                            },
                            state = monacoState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // ── New file dialog ────────────────────────────────────────────────────
    if (uiState.showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideNewFileDialog() },
            title = { Text("New File", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.newFileName,
                        onValueChange = { viewModel.updateNewFileName(it) },
                        label = { Text("Filename (e.g. Main.kt)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("MainActivity.kt") }
                    )
                    Text(
                        "Language will be auto-detected from extension",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.createFile() },
                    enabled = uiState.newFileName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideNewFileDialog() }) { Text("Cancel") }
            }
        )
    }

    // ── Error snackbar ─────────────────────────────────────────────────────
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // auto-dismiss after showing
        }
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } }
        ) { Text(error) }
    }
}

// ── Editor Toolbar ─────────────────────────────────────────────────────────
@Composable
fun EditorToolbar(
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFormat: () -> Unit,
    onFind: () -> Unit,
    onFontPlus: () -> Unit,
    onFontMinus: () -> Unit,
    onTerminal: () -> Unit,
    onChat: () -> Unit,
    onInsertTab: () -> Unit,
    fontSize: Int
) {
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Undo / Redo
            EditorToolbarButton(icon = Icons.Default.Undo, label = "Undo", onClick = onUndo)
            EditorToolbarButton(icon = Icons.Default.Redo, label = "Redo", onClick = onRedo)

            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

            // Format / Find
            EditorToolbarButton(icon = Icons.Default.AutoFixHigh, label = "Format", onClick = onFormat)
            EditorToolbarButton(icon = Icons.Default.Search, label = "Find", onClick = onFind)

            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

            // Font size
            EditorToolbarButton(icon = Icons.Default.TextDecrease, label = "Font-", onClick = onFontMinus)
            Text("$fontSize", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 2.dp))
            EditorToolbarButton(icon = Icons.Default.TextIncrease, label = "Font+", onClick = onFontPlus)

            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))

            // Tab insert (mobile-friendly)
            EditorToolbarButton(icon = Icons.Default.KeyboardTab, label = "Tab", onClick = onInsertTab)

            Spacer(Modifier.weight(1f))

            // Navigation shortcuts
            EditorToolbarButton(icon = Icons.Default.Terminal, label = "Terminal", onClick = onTerminal)
            EditorToolbarButton(icon = Icons.Default.Chat, label = "AI Chat", onClick = onChat)
        }
    }
}

@Composable
fun EditorToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
    }
}

// ── File Tree Panel ────────────────────────────────────────────────────────
@Composable
fun FileTreePanel(
    files: List<CodeFile>,
    currentFile: CodeFile?,
    onFileClick: (CodeFile) -> Unit,
    onDeleteFile: (CodeFile) -> Unit,
    onNewFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text("EXPLORER", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onNewFile, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Default.Add, "New file", modifier = Modifier.size(14.dp))
            }
        }
        HorizontalDivider()

        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("No files yet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn {
                items(files, key = { it.id }) { file ->
                    FileTreeItem(
                        file = file,
                        isSelected = currentFile?.id == file.id,
                        onClick = { onFileClick(file) },
                        onDelete = { onDeleteFile(file) }
                    )
                }
            }
        }
    }
}

@Composable
fun FileTreeItem(file: CodeFile, isSelected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val icon = when (file.name.substringAfterLast('.').lowercase()) {
        "kt", "kts" -> "🟣"
        "java"      -> "☕"
        "py"        -> "🐍"
        "js","jsx"  -> "🟡"
        "ts","tsx"  -> "🔷"
        "xml"       -> "📄"
        "json"      -> "📋"
        "md"        -> "📝"
        "gradle"    -> "🐘"
        "sh","bash" -> "⚡"
        "html"      -> "🌐"
        "css","scss"-> "🎨"
        "rs"        -> "🦀"
        "go"        -> "🐹"
        "dart"      -> "🎯"
        else        -> "📄"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { onClick() }
            .padding(start = 8.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 13.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            file.name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(12.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }
}

@Composable
fun NoFileOpen(onNewFile: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Code, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.outlineVariant)
            Text("No file open", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            Button(onClick = onNewFile) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("New File")
            }
        }
    }
}
'''

# ─────────────────────────────────────────────────────────────────────────────
# 2. EDITOR VIEWMODEL — updated with cursor tracking
# ─────────────────────────────────────────────────────────────────────────────
files["ui/screens/editor/CodeEditorViewModel.kt"] = '''package com.codemaster.aistudio.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.dao.CodeFileDao
import com.codemaster.aistudio.data.model.CodeFile
import com.codemaster.aistudio.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val files: List<CodeFile> = emptyList(),
    val currentFile: CodeFile? = null,
    val content: String = "",
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val showFileTree: Boolean = true,
    val showNewFileDialog: Boolean = false,
    val newFileName: String = "",
    val cursorLine: Int = 1,
    val cursorCol: Int = 1,
    val error: String? = null,
    val projectName: String = ""
)

@HiltViewModel
class CodeEditorViewModel @Inject constructor(
    private val codeFileDao: CodeFileDao,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var currentProjectId: Long = -1L

    fun init(projectId: Long, fileId: Long) {
        currentProjectId = projectId
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            _uiState.value = _uiState.value.copy(projectName = project?.name ?: "Editor")
            codeFileDao.getFilesForProject(projectId)
                .catch { }
                .collect { files ->
                    _uiState.value = _uiState.value.copy(files = files)
                    if (_uiState.value.currentFile == null && files.isNotEmpty()) {
                        val target = if (fileId != -1L) files.find { it.id == fileId } else files.first()
                        target?.let { openFile(it) }
                    }
                }
        }
    }

    fun openFile(file: CodeFile) {
        _uiState.value = _uiState.value.copy(
            currentFile = file,
            content = file.content,
            isDirty = false,
            cursorLine = 1,
            cursorCol = 1
        )
    }

    fun updateContent(content: String) {
        _uiState.value = _uiState.value.copy(content = content, isDirty = true)
    }

    fun updateCursor(line: Int, col: Int) {
        _uiState.value = _uiState.value.copy(cursorLine = line, cursorCol = col)
    }

    fun saveCurrentFile() {
        val state = _uiState.value
        val file = state.currentFile ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            codeFileDao.updateFile(
                file.copy(content = state.content, lastModified = System.currentTimeMillis())
            )
            _uiState.value = _uiState.value.copy(isSaving = false, isDirty = false)
        }
    }

    fun toggleFileTree() { _uiState.value = _uiState.value.copy(showFileTree = !_uiState.value.showFileTree) }
    fun showNewFileDialog() { _uiState.value = _uiState.value.copy(showNewFileDialog = true) }
    fun hideNewFileDialog() { _uiState.value = _uiState.value.copy(showNewFileDialog = false, newFileName = "") }
    fun updateNewFileName(name: String) { _uiState.value = _uiState.value.copy(newFileName = name) }

    fun createFile() {
        val name = _uiState.value.newFileName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            val language = when (name.substringAfterLast('.').lowercase()) {
                "kt","kts" -> "kotlin"; "java" -> "java"; "py" -> "python"
                "js","jsx","ts","tsx" -> "javascript"; "xml" -> "xml"
                "json" -> "json"; "md" -> "markdown"; "sh" -> "shell"
                "html" -> "html"; "css","scss" -> "css"; "rs" -> "rust"
                "go" -> "go"; "dart" -> "dart"; else -> "text"
            }
            val id = codeFileDao.insertFile(
                CodeFile(projectId = currentProjectId, name = name, path = name, language = language)
            )
            hideNewFileDialog()
            codeFileDao.getFileById(id)?.let { openFile(it) }
        }
    }

    fun deleteFile(file: CodeFile) {
        viewModelScope.launch {
            codeFileDao.deleteFile(file)
            if (_uiState.value.currentFile?.id == file.id) {
                _uiState.value = _uiState.value.copy(currentFile = null, content = "")
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
'''

# ─────────────────────────────────────────────────────────────────────────────
# 3. TERMINAL SCREEN — PTY-based, Termux-aware
# ─────────────────────────────────────────────────────────────────────────────
files["ui/screens/terminal/TerminalViewModel.kt"] = '''package com.codemaster.aistudio.ui.screens.terminal

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

    // Detect the best available shell
    private val shellPath: String by lazy {
        listOf(
            "/data/data/com.termux/files/usr/bin/bash",   // Termux bash (best)
            "/data/data/com.termux/files/usr/bin/sh",     // Termux sh
            "/system/bin/sh",                              // Android sh (fallback)
            "/system/xbin/sh"                              // Some ROMs
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
        appendLine(TerminalLine(if (isTermuxAvailable) "✅ Termux environment detected" else "⚠️ Using Android system shell", LineType.SYSTEM))
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
                        put("COLORTERM", "truecolor")
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

                // Read stdout
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

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(input = text)
    }

    fun submitCommand() {
        val cmd = _uiState.value.input.trim()
        if (cmd.isEmpty()) return

        appendLine(TerminalLine("$ $cmd", LineType.INPUT))

        val newHistory = (_uiState.value.history + cmd).takeLast(100)
        _uiState.value = _uiState.value.copy(
            input = "",
            history = newHistory,
            historyIndex = -1
        )

        // Handle built-in commands first
        when {
            cmd == "clear" || cmd == "cls" -> {
                _uiState.value = _uiState.value.copy(lines = emptyList())
                return
            }
            cmd == "help" -> {
                showHelp()
                return
            }
            cmd == "exit" -> {
                appendLine(TerminalLine("Type 'clear' to reset, or use device back button", LineType.SYSTEM))
                return
            }
        }

        if (_uiState.value.isRunning && writer != null) {
            // Send to real shell process
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    writer?.write("$cmd\n")
                    writer?.flush()
                } catch (e: Exception) {
                    appendLine(TerminalLine("Error: ${e.message}", LineType.ERROR))
                }
            }
        } else {
            // Fallback built-in executor
            runBuiltinCommand(cmd)
        }
    }

    fun historyUp() {
        val history = _uiState.value.history
        if (history.isEmpty()) return
        val newIndex = (_uiState.value.historyIndex + 1).coerceAtMost(history.size - 1)
        val cmd = history[history.size - 1 - newIndex]
        _uiState.value = _uiState.value.copy(input = cmd, historyIndex = newIndex)
    }

    fun historyDown() {
        val newIndex = _uiState.value.historyIndex - 1
        if (newIndex < 0) {
            _uiState.value = _uiState.value.copy(input = "", historyIndex = -1)
        } else {
            val history = _uiState.value.history
            val cmd = history[history.size - 1 - newIndex]
            _uiState.value = _uiState.value.copy(input = cmd, historyIndex = newIndex)
        }
    }

    fun sendCtrlC() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writer?.write("\u0003")
                writer?.flush()
            } catch (_: Exception) {}
        }
        appendLine(TerminalLine("^C", LineType.SYSTEM))
    }

    fun sendCtrlD() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writer?.write("\u0004")
                writer?.flush()
            } catch (_: Exception) {}
        }
    }

    fun sendTab() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writer?.write("\t")
                writer?.flush()
            } catch (_: Exception) {}
        }
    }

    private fun runBuiltinCommand(cmd: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parts = cmd.trim().split("\\s+".toRegex())
                val result = when (parts[0]) {
                    "echo" -> parts.drop(1).joinToString(" ")
                    "pwd" -> _uiState.value.cwd
                    "ls" -> {
                        val dir = File(if (parts.size > 1) parts[1] else _uiState.value.cwd)
                        dir.listFiles()?.joinToString("  ") { f ->
                            if (f.isDirectory) "${f.name}/" else f.name
                        } ?: "Cannot list directory"
                    }
                    "cat" -> {
                        if (parts.size < 2) "Usage: cat <file>"
                        else {
                            val f = File(_uiState.value.cwd, parts[1])
                            if (f.exists()) f.readText() else "No such file: ${parts[1]}"
                        }
                    }
                    "cd" -> {
                        val target = if (parts.size < 2 || parts[1] == "~") {
                            if (isTermuxAvailable) "/data/data/com.termux/files/home" else context.filesDir.absolutePath
                        } else if (parts[1].startsWith("/")) parts[1]
                        else "${_uiState.value.cwd}/${parts[1]}"
                        val dir = File(target)
                        if (dir.exists() && dir.isDirectory) {
                            _uiState.value = _uiState.value.copy(cwd = dir.canonicalPath)
                            ""
                        } else "cd: $target: No such directory"
                    }
                    "mkdir" -> {
                        if (parts.size < 2) "Usage: mkdir <dir>"
                        else {
                            File(_uiState.value.cwd, parts[1]).mkdirs()
                            ""
                        }
                    }
                    "rm" -> {
                        if (parts.size < 2) "Usage: rm <file>"
                        else {
                            File(_uiState.value.cwd, parts[1]).delete()
                            ""
                        }
                    }
                    "uname" -> "Linux (Android)"
                    "whoami" -> "codemaster"
                    "date" -> java.util.Date().toString()
                    "env" -> System.getenv().entries.joinToString("\n") { "${it.key}=${it.value}" }
                    else -> {
                        // Try running as system command
                        val proc = Runtime.getRuntime().exec(cmd)
                        val out = proc.inputStream.bufferedReader().readText()
                        val err = proc.errorStream.bufferedReader().readText()
                        proc.waitFor()
                        if (err.isNotBlank()) {
                            withContext(Dispatchers.Main) { appendLine(TerminalLine(err.trim(), LineType.ERROR)) }
                        }
                        out.trim()
                    }
                }
                if (result.isNotBlank()) {
                    result.lines().forEach { line ->
                        appendLine(TerminalLine(line, LineType.OUTPUT))
                    }
                }
            } catch (e: Exception) {
                appendLine(TerminalLine("${parts.firstOrNull() ?: cmd}: ${e.message ?: "command failed"}", LineType.ERROR))
            }
        }
    }

    private fun showHelp() {
        val helpLines = listOf(
            "━━━ Built-in Commands ━━━━━━━━━━━━━━━━━━━",
            "  clear / cls   Clear terminal",
            "  cd <dir>      Change directory",
            "  ls [dir]      List files",
            "  cat <file>    Print file contents",
            "  mkdir <dir>   Create directory",
            "  rm <file>     Delete file",
            "  pwd           Print working directory",
            "  echo <text>   Print text",
            "  env           Show environment",
            "  uname         System info",
            "  whoami        Current user",
            "  date          Current date/time",
            "  help          This help",
            "",
            if (isTermuxAvailable)
                "✅ Full Termux shell active — all pkg commands available"
            else
                "⚠️ No Termux detected — install Termux for full shell access",
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        )
        helpLines.forEach { appendLine(TerminalLine(it, LineType.SYSTEM)) }
    }

    private fun appendLine(line: TerminalLine) {
        val current = _uiState.value.lines.toMutableList()
        current.add(line)
        if (current.size > maxLines) current.removeAt(0)
        _uiState.value = _uiState.value.copy(lines = current)
    }

    // Strip basic ANSI escape codes for cleaner display
    private fun stripAnsi(text: String): String =
        text.replace(Regex("\u001B\\[[;\\d]*[A-Za-z]"), "")
            .replace(Regex("\u001B\\][^\u0007]*\u0007"), "")

    override fun onCleared() {
        super.onCleared()
        readerJob?.cancel()
        try {
            writer?.close()
            process?.destroy()
        } catch (_: Exception) {}
    }
}
'''

files["ui/screens/terminal/TerminalScreen.kt"] = r'''package com.codemaster.aistudio.ui.screens.terminal

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

// Terminal color palette (VS Code dark terminal style)
private val TermBg       = Color(0xFF0D1117)
private val TermGreen    = Color(0xFF3FB950)
private val TermRed      = Color(0xFFF85149)
private val TermYellow   = Color(0xFFD29922)
private val TermBlue     = Color(0xFF58A6FF)
private val TermCyan     = Color(0xFF39C5CF)
private val TermGray     = Color(0xFF8B949E)
private val TermWhite    = Color(0xFFE6EDF3)

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

    // Auto-scroll to bottom on new output
    LaunchedEffect(uiState.lines.size) {
        if (uiState.lines.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(uiState.lines.size - 1) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Terminal,
                            null,
                            tint = TermGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Terminal", fontWeight = FontWeight.Bold, color = TermWhite)
                            Text(
                                uiState.cwd.takeLast(40),
                                style = MaterialTheme.typography.labelSmall,
                                color = TermGray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TermWhite)
                    }
                },
                actions = {
                    // Running indicator
                    if (uiState.isRunning) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(TermGreen, shape = RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("LIVE", color = TermGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = {
                        scope.launch { viewModel.submitCommand() }
                    }) {
                        Icon(Icons.Default.DeleteSweep, "Clear", tint = TermWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF161B22))
            )
        },
        containerColor = TermBg,
        bottomBar = {
            TerminalInputBar(
                input = uiState.input,
                onInputChange = { viewModel.updateInput(it) },
                onSubmit = { viewModel.submitCommand() },
                onCtrlC = { viewModel.sendCtrlC() },
                onCtrlD = { viewModel.sendCtrlD() },
                onTab = { viewModel.sendTab() },
                onHistoryUp = { viewModel.historyUp() },
                onHistoryDown = { viewModel.historyDown() },
                isRunning = uiState.isRunning
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(TermBg)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                items(uiState.lines) { line ->
                    TerminalLineView(line)
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
fun TerminalLineView(line: TerminalLine) {
    val color = when (line.type) {
        LineType.INPUT  -> TermCyan
        LineType.OUTPUT -> TermWhite
        LineType.ERROR  -> TermRed
        LineType.SYSTEM -> TermYellow
    }
    val prefix = when (line.type) {
        LineType.INPUT  -> ""    // already has "$ " prefix from viewmodel
        LineType.OUTPUT -> ""
        LineType.ERROR  -> ""
        LineType.SYSTEM -> ""
    }

    Text(
        text = "$prefix${line.text}",
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun TerminalInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCtrlC: () -> Unit,
    onCtrlD: () -> Unit,
    onTab: () -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit,
    isRunning: Boolean
) {
    Column(
        modifier = Modifier
            .background(Color(0xFF161B22))
            .navigationBarsPadding()
    ) {
        // Quick-action keys row (mobile keyboard helpers)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF21262D))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TermKeyButton("Tab", onClick = onTab)
            TermKeyButton("↑", onClick = onHistoryUp)
            TermKeyButton("↓", onClick = onHistoryDown)
            TermKeyButton("^C", onClick = onCtrlC, color = TermRed)
            TermKeyButton("^D", onClick = onCtrlD, color = TermYellow)
            TermKeyButton("~", onClick = { onInputChange("$input~") })
            TermKeyButton("/", onClick = { onInputChange("$input/") })
            TermKeyButton("-", onClick = { onInputChange("$input-") })
            TermKeyButton("|", onClick = { onInputChange("$input|") })
            TermKeyButton("&&", onClick = { onInputChange("$input && ") })
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$ ",
                color = TermGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            TextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TermWhite
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = TermGreen,
                    focusedTextColor = TermWhite,
                    unfocusedTextColor = TermWhite
                ),
                placeholder = {
                    Text("Enter command...", color = TermGray, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() })
            )
            IconButton(
                onClick = onSubmit,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Send,
                    "Run",
                    tint = TermGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TermKeyButton(
    label: String,
    onClick: () -> Unit,
    color: Color = TermGray
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(28.dp).defaultMinSize(minWidth = 32.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color)
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
'''

# ─────────────────────────────────────────────────────────────────────────────
# 4. NAVIGATION — add Terminal route
# ─────────────────────────────────────────────────────────────────────────────
files["ui/navigation/NavGraph.kt"] = '''package com.codemaster.aistudio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.codemaster.aistudio.ui.screens.build.BuildScreen
import com.codemaster.aistudio.ui.screens.chat.AiChatScreen
import com.codemaster.aistudio.ui.screens.editor.CodeEditorScreen
import com.codemaster.aistudio.ui.screens.home.HomeScreen
import com.codemaster.aistudio.ui.screens.settings.SettingsScreen
import com.codemaster.aistudio.ui.screens.terminal.TerminalScreen

sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object Settings : Screen("settings")
    object Chat : Screen("chat/{projectId}") {
        fun createRoute(projectId: Long = -1L) = "chat/$projectId"
    }
    object Editor : Screen("editor/{projectId}/{fileId}") {
        fun createRoute(projectId: Long, fileId: Long = -1L) = "editor/$projectId/$fileId"
    }
    object Build : Screen("build/{projectId}") {
        fun createRoute(projectId: Long = -1L) = "build/$projectId"
    }
    object Terminal : Screen("terminal/{projectId}") {
        fun createRoute(projectId: Long = -1L) = "terminal/$projectId"
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onOpenChat     = { navController.navigate(Screen.Chat.createRoute(it)) },
                onOpenEditor   = { navController.navigate(Screen.Editor.createRoute(it)) },
                onOpenBuild    = { navController.navigate(Screen.Build.createRoute(it)) },
                onOpenTerminal = { navController.navigate(Screen.Terminal.createRoute(it)) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            Screen.Chat.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            AiChatScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.Editor.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.LongType },
                navArgument("fileId")    { type = NavType.LongType; defaultValue = -1L }
            )
        ) { back ->
            CodeEditorScreen(
                projectId     = back.arguments?.getLong("projectId") ?: -1L,
                fileId        = back.arguments?.getLong("fileId") ?: -1L,
                onBack        = { navController.popBackStack() },
                onOpenChat    = { navController.navigate(Screen.Chat.createRoute(it)) },
                onOpenTerminal = { navController.navigate(Screen.Terminal.createRoute(it)) }
            )
        }

        composable(
            Screen.Build.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            BuildScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                onBack    = { navController.popBackStack() }
            )
        }

        composable(
            Screen.Terminal.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            TerminalScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                onBack    = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
'''

# ─────────────────────────────────────────────────────────────────────────────
# 5. HOME SCREEN — add Terminal button to project cards
# ─────────────────────────────────────────────────────────────────────────────
files["ui/screens/home/HomeScreen.kt"] = '''package com.codemaster.aistudio.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
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
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                "Projects (${uiState.projects.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(uiState.projects, key = { it.id }) { project ->
                            ProjectCard(
                                project = project,
                                onOpenChat     = { onOpenChat(project.id) },
                                onOpenEditor   = { onOpenEditor(project.id) },
                                onOpenBuild    = { onOpenBuild(project.id) },
                                onOpenTerminal = { onOpenTerminal(project.id) },
                                onDelete       = { viewModel.deleteProject(project) }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") } }
                ) { Text(error) }
            }
        }

        if (uiState.showNewProjectDialog) {
            NewProjectDialog(
                name        = uiState.newProjectName,
                language    = uiState.newProjectLanguage,
                description = uiState.newProjectDescription,
                onNameChange        = viewModel::updateProjectName,
                onLanguageChange    = viewModel::updateProjectLanguage,
                onDescriptionChange = viewModel::updateProjectDescription,
                onCreate   = { viewModel.createProject { id -> onOpenEditor(id) } },
                onDismiss  = viewModel::hideNewProjectDialog
            )
        }
    }
}

@Composable
fun ProjectCard(
    project: Project,
    onOpenChat: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenBuild: () -> Unit,
    onOpenTerminal: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(project.language.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(project.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${project.language} • ${dateFormat.format(Date(project.lastModified))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
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
                        LANGUAGES.forEach { lang ->
                            DropdownMenuItem(text = { Text(lang) }, onClick = { onLanguageChange(lang); langExpanded = false })
                        }
                    }
                }
                OutlinedTextField(value = description, onValueChange = onDescriptionChange, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3)
            }
        },
        confirmButton = { Button(onClick = onCreate, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
'''

# ─────────────────────────────────────────────────────────────────────────────
# 6. MANIFEST — add permissions needed for shell/process execution
# ─────────────────────────────────────────────────────────────────────────────
manifest_path = os.path.join(RES, "AndroidManifest.xml")
files_abs = {}
files_abs[manifest_path] = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_DOCUMENTS" />

    <!-- Termux API integration (optional — works if Termux is installed) -->
    <uses-permission android:name="com.termux.permission.RUN_COMMAND" />

    <application
        android:name=".CodeMasterApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="CodeMaster AI Studio"
        android:roundIcon="@mipmap/ic_launcher"
        android:supportsRtl="true"
        android:hardwareAccelerated="true"
        android:usesCleartextTraffic="false"
        android:theme="@style/Theme.CodeMasterAIStudio">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize"
            android:configChanges="orientation|screenSize|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
'''

# ─────────────────────────────────────────────────────────────────────────────
# WRITE ALL FILES
# ─────────────────────────────────────────────────────────────────────────────
written = 0
errors = []

# Relative files under BASE
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

# Absolute path files (manifest etc.)
for abs_path, content in files_abs.items():
    os.makedirs(os.path.dirname(abs_path), exist_ok=True)
    try:
        with open(abs_path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"✅ {os.path.basename(abs_path)}")
        written += 1
    except Exception as e:
        print(f"❌ {abs_path}: {e}")
        errors.append(abs_path)

print(f"\n{'='*52}")
print(f"✅ Written : {written} files")
if errors:
    print(f"❌ Errors  : {len(errors)}")
    for e in errors: print(f"   - {e}")
else:
    print("🚀 Phase 3 complete — ready to commit!")
print(f"{'='*52}")
