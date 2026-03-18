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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codemaster.aistudio.data.repository.DiskFile
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
    var fontSize by remember { mutableStateOf(14) }
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(projectId, fileId) { viewModel.init(projectId, fileId) }

    // Push new file content into Monaco when file changes
    LaunchedEffect(uiState.currentFile?.path) {
        uiState.currentFile?.let {
            monacoState.setValue(uiState.content)
            monacoState.setLanguage(uiState.language)
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                uiState.currentFile?.name ?: uiState.projectName,
                                fontWeight = FontWeight.Bold, maxLines = 1
                            )
                            if (uiState.isDirty) {
                                Spacer(Modifier.width(6.dp))
                                Text("●", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                            }
                        }
                        if (uiState.cursorLine > 0) {
                            Text(
                                "Ln ${uiState.cursorLine}, Col ${uiState.cursorCol}  |  ${uiState.language}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Default.Search, "Search files") }
                    IconButton(onClick = { viewModel.toggleFileTree() }) { Icon(Icons.Default.AccountTree, "Files") }
                    if (uiState.isDirty) {
                        IconButton(onClick = { viewModel.saveCurrentFile() }) {
                            if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Save, "Save")
                        }
                    }
                    IconButton(onClick = { onOpenChat(projectId) }) { Icon(Icons.Default.Chat, "Chat") }
                    IconButton(onClick = { onOpenTerminal(projectId) }) { Icon(Icons.Default.Terminal, "Terminal") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Toolbar
            EditorToolbar(
                onUndo = { monacoState.undo() },
                onRedo = { monacoState.redo() },
                onFormat = { monacoState.format() },
                onFind = { monacoState.find() },
                onFontMinus = { fontSize = (fontSize - 1).coerceAtLeast(8); monacoState.setFontSize(fontSize) },
                onFontPlus  = { fontSize = (fontSize + 1).coerceAtMost(24); monacoState.setFontSize(fontSize) },
                onSave = { viewModel.saveCurrentFile() },
                onRefresh = { viewModel.scanFiles() },
                fontSize = fontSize,
                isDirty = uiState.isDirty
            )

            // File search bar
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearch(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    placeholder = { Text("Search files...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.updateSearch("") }) { Icon(Icons.Default.Clear, null) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp)
                )
            }

            Row(modifier = Modifier.weight(1f)) {
                // File tree
                AnimatedVisibility(
                    visible = uiState.showFileTree,
                    enter = slideInHorizontally() + fadeIn(),
                    exit = slideOutHorizontally() + fadeOut()
                ) {
                    FileTreePanel(
                        files = viewModel.getVisibleFiles(),
                        currentFile = uiState.currentFile,
                        expandedDirs = uiState.expandedDirs,
                        rootPath = uiState.rootPath,
                        isLoading = uiState.isLoadingFiles,
                        onFileClick = { viewModel.openFile(it) },
                        onDeleteFile = { viewModel.deleteFile(it) },
                        onNewFile = { dir -> viewModel.showNewFileDialog(dir) },
                        onRefresh = { viewModel.scanFiles() },
                        modifier = Modifier.width(200.dp)
                    )
                }

                // Monaco Editor
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (uiState.currentFile == null) {
                        NoFileOpen(
                            recentFiles = uiState.recentFiles,
                            onOpenRecent = { viewModel.openFile(it) },
                            onNewFile = { viewModel.showNewFileDialog() }
                        )
                    } else {
                        MonacoEditorView(
                            value = uiState.content,
                            language = uiState.language,
                            isDarkTheme = settingsState.isDarkTheme,
                            fontSize = fontSize,
                            onChange = { viewModel.updateContent(it) },
                            onCursorChange = { line, col -> viewModel.updateCursor(line, col) },
                            state = monacoState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // New file dialog
    if (uiState.showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideNewFileDialog() },
            title = { Text("New File", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("In: ${uiState.newFileDir.takeLast(40)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = uiState.newFileName,
                        onValueChange = { viewModel.updateNewFileName(it) },
                        label = { Text("Filename") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("MainActivity.kt") }
                    )
                }
            },
            confirmButton = { Button(onClick = { viewModel.createFile() }, enabled = uiState.newFileName.isNotBlank()) { Text("Create") } },
            dismissButton = { TextButton(onClick = { viewModel.hideNewFileDialog() }) { Text("Cancel") } }
        )
    }

    uiState.error?.let { error ->
        LaunchedEffect(error) {}
        Snackbar(modifier = Modifier.padding(16.dp), action = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } }) { Text(error) }
    }
}

@Composable
fun EditorToolbar(
    onUndo: () -> Unit, onRedo: () -> Unit, onFormat: () -> Unit, onFind: () -> Unit,
    onFontMinus: () -> Unit, onFontPlus: () -> Unit, onSave: () -> Unit, onRefresh: () -> Unit,
    fontSize: Int, isDirty: Boolean
) {
    Surface(tonalElevation = 2.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            EditorBtn(Icons.Default.Undo, "Undo", onUndo)
            EditorBtn(Icons.Default.Redo, "Redo", onRedo)
            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))
            EditorBtn(Icons.Default.AutoFixHigh, "Format", onFormat)
            EditorBtn(Icons.Default.Search, "Find", onFind)
            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))
            EditorBtn(Icons.Default.TextDecrease, "A-", onFontMinus)
            Text("$fontSize", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 2.dp))
            EditorBtn(Icons.Default.TextIncrease, "A+", onFontPlus)
            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))
            EditorBtn(Icons.Default.Refresh, "Refresh", onRefresh)
            if (isDirty) {
                EditorBtn(Icons.Default.Save, "Save", onSave)
            }
        }
    }
}

@Composable
fun EditorBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, label, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun FileTreePanel(
    files: List<DiskFile>,
    currentFile: DiskFile?,
    expandedDirs: Set<String>,
    rootPath: String,
    isLoading: Boolean,
    onFileClick: (DiskFile) -> Unit,
    onDeleteFile: (DiskFile) -> Unit,
    onNewFile: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text("EXPLORER", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onRefresh, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp)) }
            IconButton(onClick = { onNewFile(rootPath) }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp)) }
        }
        HorizontalDivider()

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("No files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn {
                items(files, key = { it.path }) { file ->
                    FileTreeItem(
                        file = file,
                        isSelected = currentFile?.path == file.path,
                        isExpanded = file.path in expandedDirs,
                        onClick = { onFileClick(file) },
                        onDelete = { onDeleteFile(file) },
                        onNewFile = { onNewFile(file.path) }
                    )
                }
            }
        }
    }
}

@Composable
fun FileTreeItem(
    file: DiskFile,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onNewFile: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val indent = (file.depth * 12).dp
    val icon = when {
        file.isDirectory -> if (isExpanded) "📂" else "📁"
        else -> when (file.extension.lowercase()) {
            "kt", "kts"       -> "🟣"
            "java"            -> "☕"
            "py"              -> "🐍"
            "js", "jsx"       -> "🟡"
            "ts", "tsx"       -> "🔷"
            "xml"             -> "📄"
            "json"            -> "📋"
            "md"              -> "📝"
            "gradle", "kts"   -> "🐘"
            "sh", "bash"      -> "⚡"
            "html"            -> "🌐"
            "css", "scss"     -> "🎨"
            "rs"              -> "🦀"
            "go"              -> "🐹"
            "dart"            -> "🎯"
            "yaml", "yml"     -> "⚙️"
            "toml"            -> "⚙️"
            "properties"      -> "⚙️"
            else              -> "📄"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { onClick() }
            .padding(start = 8.dp + indent, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            file.name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else if (file.isDirectory) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            fontWeight = if (file.isDirectory) FontWeight.Medium else FontWeight.Normal
        )
        if (!file.isDirectory && file.size > 0) {
            Text(
                formatSize(file.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 9.sp
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(12.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (file.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("New File Here") },
                        onClick = { showMenu = false; onNewFile(file.path) },
                        leadingIcon = { Icon(Icons.Default.Add, null) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}K"
    else -> "${bytes / (1024 * 1024)}M"
}

@Composable
fun NoFileOpen(
    recentFiles: List<DiskFile>,
    onOpenRecent: (DiskFile) -> Unit,
    onNewFile: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Code, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Text("No file open", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNewFile) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("New File")
        }

        if (recentFiles.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Recent Files", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            recentFiles.reversed().take(5).forEach { file ->
                TextButton(onClick = { onOpenRecent(file) }) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}
