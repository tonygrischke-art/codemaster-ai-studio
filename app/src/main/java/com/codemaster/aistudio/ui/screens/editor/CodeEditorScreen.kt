package com.codemaster.aistudio.ui.screens.editor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codemaster.aistudio.data.model.CodeFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    projectId: Long,
    fileId: Long,
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit,
    viewModel: CodeEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(projectId, fileId) { viewModel.init(projectId, fileId) }

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
                            Spacer(Modifier.width(4.dp))
                            Text("●", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFileTree() }) {
                        Icon(Icons.Default.AccountTree, "File tree")
                    }
                    if (uiState.isDirty) {
                        IconButton(onClick = { viewModel.saveCurrentFile() }) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, "Save")
                            }
                        }
                    }
                    IconButton(onClick = { onOpenChat(projectId) }) {
                        Icon(Icons.Default.ChatBubbleOutline, "Chat")
                    }
                }
            )
        }
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(
                visible = uiState.showFileTree,
                enter = slideInHorizontally() + fadeIn(),
                exit = slideOutHorizontally() + fadeOut()
            ) {
                FileTreePanel(
                    files = uiState.files,
                    currentFile = uiState.currentFile,
                    onFileClick = { viewModel.openFile(it) },
                    onDeleteFile = { viewModel.deleteFile(it) },
                    onNewFile = { viewModel.showNewFileDialog() },
                    modifier = Modifier.width(200.dp)
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (uiState.currentFile == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Code, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("Select or create a file", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.showNewFileDialog() }) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New File")
                            }
                        }
                    }
                } else {
                    CodeEditorArea(
                        content = uiState.content,
                        onContentChange = { viewModel.updateContent(it) },
                        language = uiState.currentFile?.language ?: "text"
                    )
                }
            }
        }

        if (uiState.showNewFileDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.hideNewFileDialog() },
                title = { Text("New File") },
                text = {
                    OutlinedTextField(
                        value = uiState.newFileName,
                        onValueChange = { viewModel.updateNewFileName(it) },
                        label = { Text("File name (e.g. Main.kt)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = { viewModel.createFile() }, enabled = uiState.newFileName.isNotBlank()) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideNewFileDialog() }) { Text("Cancel") }
                }
            )
        }
    }
}

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
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("FILES", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onNewFile, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Add, "New file", modifier = Modifier.size(16.dp))
            }
        }
        HorizontalDivider()
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

@Composable
fun FileTreeItem(file: CodeFile, isSelected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val icon = when {
        file.name.endsWith(".kt") -> "🟣"
        file.name.endsWith(".java") -> "☕"
        file.name.endsWith(".py") -> "🐍"
        file.name.endsWith(".js") || file.name.endsWith(".ts") -> "🟡"
        file.name.endsWith(".xml") -> "📄"
        file.name.endsWith(".json") -> "📋"
        file.name.endsWith(".md") -> "📝"
        else -> "📄"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 14.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            file.name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(14.dp))
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
fun CodeEditorArea(content: String, onContentChange: (String) -> Unit, language: String) {
    val scrollState = rememberScrollState()
    val hScrollState = rememberScrollState()
    val lineCount = content.lines().size

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Line numbers
        Column(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .verticalScroll(scrollState)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.End
        ) {
            repeat(lineCount.coerceAtLeast(1)) { i ->
                Text(
                    text = "${i + 1}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        // Code area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(hScrollState)
                .verticalScroll(scrollState)
                .padding(8.dp)
        ) {
            BasicTextField(
                value = content,
                onValueChange = onContentChange,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 400.dp)
            )
        }
    }
}
