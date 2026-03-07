package com.codemaster.aistudio.ui.screens.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codemaster.aistudio.ui.components.MonacoEditorView
import com.codemaster.aistudio.ui.components.detectLanguage
import com.codemaster.aistudio.ui.components.rememberMonacoEditorState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onOpenChat: () -> Unit,
    viewModel: CodeEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editorState = rememberMonacoEditorState()
    var cursorInfo by remember { mutableStateOf("Ln 1, Col 1") }

    LaunchedEffect(projectId) {
        viewModel.init(projectId)
    }

    // When active file changes, push new content into Monaco imperatively
    LaunchedEffect(uiState.activeFileName) {
        uiState.activeFileContent?.let { content ->
            editorState.setValue(content)
            editorState.setLanguage(detectLanguage(uiState.activeFileName ?: ""))
        }
    }

    Scaffold(
        topBar = {
            EditorTopBar(
                fileName = uiState.activeFileName ?: "No file",
                hasUnsavedChanges = uiState.hasUnsavedChanges,
                onNavigateBack = onNavigateBack,
                onSave = {
                    viewModel.saveFile { editorState.getValue() }
                },
                onFormat = { editorState.format() },
                onUndo = { editorState.undo() },
                onRedo = { editorState.redo() },
                onFind = { editorState.find() },
                onOpenChat = onOpenChat
            )
        },
        bottomBar = {
            // Status bar showing cursor position
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = detectLanguage(uiState.activeFileName ?: "").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = cursorInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // File tabs
            if (uiState.files.isNotEmpty()) {
                FileTabs(
                    files = uiState.files,
                    activeFile = uiState.activeFileName,
                    onSelectFile = { fileName ->
                        viewModel.switchFile(fileName) { editorState.getValue() }
                    }
                )
                HorizontalDivider()
            }

            // Monaco Editor — takes all remaining space
            MonacoEditorView(
                value = uiState.activeFileContent ?: "",
                language = detectLanguage(uiState.activeFileName ?: ""),
                isDarkTheme = true, // TODO: wire to settings
                fontSize = 14,      // TODO: wire to settings
                onChange = { newContent ->
                    viewModel.onContentChange(newContent)
                },
                onCursorChange = { line, col ->
                    cursorInfo = "Ln $line, Col $col"
                },
                state = editorState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    fileName: String,
    hasUnsavedChanges: Boolean,
    onNavigateBack: () -> Unit,
    onSave: () -> Unit,
    onFormat: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFind: () -> Unit,
    onOpenChat: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium
                )
                if (hasUnsavedChanges) {
                    Text(
                        text = " •",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            // Scrollable action row for small screens
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onUndo) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRedo) {
                    Icon(Icons.Default.Redo, contentDescription = "Redo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onFind) {
                    Icon(Icons.Default.Search, contentDescription = "Find",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onFormat) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = "Format",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onSave) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = "Save",
                        tint = if (hasUnsavedChanges)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenChat) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI Chat",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun FileTabs(
    files: List<String>,
    activeFile: String?,
    onSelectFile: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        files.forEach { fileName ->
            val isActive = fileName == activeFile
            FilterChip(
                selected = isActive,
                onClick = { onSelectFile(fileName) },
                label = {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
