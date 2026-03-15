package com.codemaster.aistudio.ui.screens.editor

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
