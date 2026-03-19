import os

BASE = "app/src/main/java/com/codemaster/aistudio"
OK = []; FAIL = []

def write(rel, content):
    path = os.path.join(BASE, rel)
    try:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w') as f:
            f.write(content)
        print(f"  ✅ {rel}")
        OK.append(rel)
    except Exception as e:
        print(f"  ❌ {rel} — {e}")
        FAIL.append(rel)

print("\n🚀 Phase 8 — HomeScreen + Chat upgrades\n")

# ═══════════════════════════════════════════════════════════════
# 1. HomeScreen.kt
#    + Dedicated AI Chat button (prominent, purple)
#    + Browse Files button (opens file explorer)
#    + Quick actions bar at top
#    + Load project dialog with browse button
# ═══════════════════════════════════════════════════════════════
write("ui/screens/home/HomeScreen.kt", '''package com.codemaster.aistudio.ui.screens.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    // Folder browser for loading projects
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { treeUri ->
            val docId = androidx.documentfile.provider.DocumentFile
                .fromTreeUri(androidx.compose.ui.platform.LocalContext.current as android.content.Context, treeUri)
                ?.uri?.lastPathSegment ?: ""
            val path = when {
                docId.startsWith("primary:") -> "/sdcard/" + docId.removePrefix("primary:")
                docId.startsWith("home:") -> "/data/data/com.termux/files/home/" + docId.removePrefix("home:")
                else -> treeUri.path?.replace("/tree/primary:", "/sdcard/") ?: ""
            }
            if (path.isNotBlank()) viewModel.updateImportPath(path)
            viewModel.showImportDialog()
        }
    }

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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallFloatingActionButton(
                    onClick = { viewModel.showImportDialog() },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.FolderOpen, "Load project", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                ExtendedFloatingActionButton(
                    onClick = { viewModel.showNewProjectDialog() },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("New Project") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
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
                    // ── Quick Actions Bar ──────────────────────────────
                    item {
                        QuickActionsBar(
                            onBrowseFiles = { folderPicker.launch(null) },
                            onOpenChat = {
                                // Open chat for first project, or show picker
                                uiState.projects.firstOrNull()?.let { onOpenChat(it.id) }
                            }
                        )
                    }

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
                            onOpenGit      = { onOpenGit(project.id) },
                            onDelete       = { viewModel.deleteProject(project) }
                        )
                    }
                    item { Spacer(Modifier.height(100.dp)) }
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
                name = uiState.newProjectName, language = uiState.newProjectLanguage,
                description = uiState.newProjectDescription,
                onNameChange = viewModel::updateProjectName,
                onLanguageChange = viewModel::updateProjectLanguage,
                onDescriptionChange = viewModel::updateProjectDescription,
                onCreate = { viewModel.createProject { id -> onOpenEditor(id) } },
                onDismiss = viewModel::hideNewProjectDialog
            )
        }

        if (uiState.showImportDialog) {
            ImportProjectDialog(
                path = uiState.importPath,
                onPathChange = viewModel::updateImportPath,
                onBrowse = { folderPicker.launch(null) },
                onImport = { viewModel.importProject { id -> onOpenEditor(id) } },
                onDismiss = viewModel::hideImportDialog
            )
        }
    }
}

// ── Quick Actions Bar ──────────────────────────────────────────
@Composable
fun QuickActionsBar(
    onBrowseFiles: () -> Unit,
    onOpenChat: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // AI Chat button
        Button(
            onClick = onOpenChat,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("AI Chat", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        // Browse Files button
        OutlinedButton(
            onClick = onBrowseFiles,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Browse", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

// ── Import Dialog with Browse button ──────────────────────────
@Composable
fun ImportProjectDialog(
    path: String,
    onPathChange: (String) -> Unit,
    onBrowse: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Load Project", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter the path or browse to your project folder:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = path, onValueChange = onPathChange,
                    label = { Text("Folder path") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 3,
                    placeholder = { Text("/sdcard/my-project") },
                    trailingIcon = {
                        IconButton(onClick = onBrowse) {
                            Icon(Icons.Default.FolderOpen, "Browse", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
                // Quick path chips
                Text("Quick paths:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    "codemaster-ai-studio" to (android.os.Environment.getExternalStorageDirectory().absolutePath + "/codemaster-ai-studio"),
                    "Termux home"          to "/data/data/com.termux/files/home",
                    "SDCard"               to android.os.Environment.getExternalStorageDirectory().absolutePath
                ).forEach { (label, quickPath) ->
                    SuggestionChip(
                        onClick = { onPathChange(quickPath) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onImport, enabled = path.isNotBlank()) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Load")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ProjectCard(
    project: Project,
    onOpenChat: () -> Unit, onOpenEditor: () -> Unit, onOpenBuild: () -> Unit,
    onOpenTerminal: () -> Unit, onOpenGit: () -> Unit, onDelete: () -> Unit
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
                ) { Text(project.language.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(project.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${project.language} • ${dateFormat.format(Date(project.lastModified))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (project.path.isNotBlank()) {
                        Text(
                            "Loaded from: ${project.path}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                ProjectActionButton(Icons.Default.AutoAwesome,       "Chat",     onOpenChat)
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
        Spacer(Modifier.height(8.dp))
        Text("Tap Browse to load or + to create a project", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectDialog(
    name: String, language: String, description: String,
    onNameChange: (String) -> Unit, onLanguageChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit, onCreate: () -> Unit, onDismiss: () -> Unit
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
''')

# ═══════════════════════════════════════════════════════════════
# 2. AiChatScreen.kt
#    + Project file browser panel in chat
#    + Pick file from project tree to add to context
#    + Save AI response as file
#    + Voice input button (mic)
# ═══════════════════════════════════════════════════════════════
write("ui/screens/chat/AiChatScreen.kt", '''package com.codemaster.aistudio.ui.screens.chat

import android.net.Uri
import android.speech.RecognizerIntent
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import com.codemaster.aistudio.data.repository.DiskFile
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
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
    var showFilePicker by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveContent by remember { mutableStateOf("") }
    var saveFileName by remember { mutableStateOf("") }

    LaunchedEffect(projectId) { viewModel.init(projectId) }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty())
            listState.animateScrollToItem(uiState.messages.size - 1)
    }

    // System file picker
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

    // Voice input
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) viewModel.updateInput(uiState.inputText + spoken)
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CodeMaster", fontWeight = FontWeight.Bold)
                        Text("~${uiState.totalTokens} tokens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    // Add file from project
                    IconButton(onClick = { showFilePicker = true }) {
                        Icon(Icons.Default.FolderOpen, "Add file from project", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (uiState.openTabs.isNotEmpty()) {
                        IconButton(onClick = { viewModel.explainCurrentFile() }) {
                            Icon(Icons.Default.AutoAwesome, "Explain file", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { viewModel.clearHistory() }) { Icon(Icons.Default.DeleteSweep, "Clear") }
                }
            )
        },
        bottomBar = {
            Column {
                if (uiState.openTabs.isNotEmpty()) {
                    TabsBar(
                        tabs = uiState.openTabs,
                        activeIndex = uiState.activeTabIndex,
                        onTabClick = { viewModel.setActiveTab(it) },
                        onTabClose = { viewModel.closeTab(it) }
                    )
                }
                ChatInputBar(
                    text = uiState.inputText,
                    attachedFileName = uiState.attachedFileName,
                    isLoading = uiState.isLoading,
                    onTextChange = viewModel::updateInput,
                    onAttach = { filePicker.launch("*/*") },
                    onClearAttachment = viewModel::clearAttachment,
                    onVoiceInput = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your question...")
                        }
                        voiceLauncher.launch(intent)
                    },
                    onSend = {
                        viewModel.sendMessage()
                        scope.launch { if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size) }
                    }
                )
            }
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
                        ChatBubble(
                            message = message,
                            onSaveAsFile = { content ->
                                saveContent = content
                                saveFileName = "ai_response_${System.currentTimeMillis()}.txt"
                                showSaveDialog = true
                            }
                        )
                    }
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

    // Project file picker sheet
    if (showFilePicker) {
        ProjectFilePickerSheet(
            projectContext = uiState.projectContext,
            onFileSelected = { diskFile ->
                viewModel.openFile(diskFile.path)
                // Also attach to chat
                try {
                    val content = File(diskFile.path).readText().take(8000)
                    viewModel.attachFile(diskFile.name, content)
                } catch (_: Exception) {}
                showFilePicker = false
            },
            onDismiss = { showFilePicker = false }
        )
    }

    // Save AI response as file
    if (showSaveDialog) {
        SaveResponseDialog(
            fileName = saveFileName,
            onFileNameChange = { saveFileName = it },
            onSave = {
                viewModel.saveContentAsFile(saveFileName, saveContent)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }
}

// ── Project File Picker ────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectFilePickerSheet(
    projectContext: String?,
    onFileSelected: (DiskFile) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Add File to Chat", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (projectContext == null) {
                Text("No project loaded", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    "Project files:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                // Parse file tree from context string and show as list
                val lines = projectContext.lines()
                    .filter { it.contains("📄") }
                    .take(50)
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(lines) { line ->
                        val fileName = line.trim().removePrefix("📄").trim().substringBefore(" (")
                        TextButton(
                            onClick = {
                                // Create a minimal DiskFile for selection
                                onFileSelected(DiskFile(
                                    name = File(fileName).name,
                                    path = fileName,
                                    relativePath = fileName,
                                    size = 0,
                                    lastModified = 0,
                                    isDirectory = false,
                                    extension = fileName.substringAfterLast(".", "")
                                ))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.InsertDriveFile, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(fileName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Save Response Dialog ───────────────────────────────────────
@Composable
fun SaveResponseDialog(
    fileName: String,
    onFileNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Response as File", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = fileName,
                onValueChange = onFileNameChange,
                label = { Text("File name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = { Button(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TabsBar(tabs: List<OpenTab>, activeIndex: Int, onTabClick: (Int) -> Unit, onTabClose: (Int) -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                val isActive = index == activeIndex
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onTabClick(index) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (tab.isDirty) "• ${tab.fileName}" else tab.fileName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(Icons.Default.Close, "Close", modifier = Modifier.size(12.dp).clickable { onTabClose(index) }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, onSaveAsFile: ((String) -> Unit)? = null) {
    val isUser = message.role == "user"
    val clipboard = LocalClipboardManager.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (!isUser) {
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Text("CM", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.widthIn(max = 300.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = if (isUser) 16.dp else 4.dp, topEnd = if (isUser) 4.dp else 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                SelectionContainer {
                    if (!isUser && message.content.contains("```")) CodeAwareText(message.content)
                    else Text(text = message.content, color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
            if (!isUser) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("~${message.tokenCount} tokens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { clipboard.setText(AnnotatedString(message.content)) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                    // Save as file button
                    onSaveAsFile?.let { save ->
                        IconButton(onClick = { save(message.content) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Save, "Save as file", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                        }
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
            if (index % 2 == 0) { if (part.isNotBlank()) Text(part.trim(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) }
            else {
                val lines = part.lines()
                val code = if (lines.firstOrNull()?.all { it.isLetter() } == true) lines.drop(1).joinToString("\\n") else part
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).padding(8.dp)) {
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
            Text("CM", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
        Text("Ask me anything about your code.\\nUse 📁 to add project files as context.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ChatInputBar(
    text: String, attachedFileName: String?, isLoading: Boolean,
    onTextChange: (String) -> Unit, onAttach: () -> Unit,
    onClearAttachment: () -> Unit, onVoiceInput: () -> Unit, onSend: () -> Unit
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
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                // Attach file
                IconButton(onClick = onAttach, enabled = !isLoading) {
                    Icon(Icons.Default.AttachFile, "Attach", tint = if (attachedFileName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Voice input
                IconButton(onClick = onVoiceInput, enabled = !isLoading) {
                    Icon(Icons.Default.Mic, "Voice", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = text, onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask CodeMaster anything...") },
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
''')

# ═══════════════════════════════════════════════════════════════
# 3. Patch AiChatViewModel — add saveContentAsFile
# ═══════════════════════════════════════════════════════════════
vm_path = os.path.join(BASE, "ui/screens/chat/AiChatViewModel.kt")
try:
    with open(vm_path, 'r') as f:
        vm_content = f.read()

    if 'saveContentAsFile' not in vm_content:
        save_fn = '''
    fun saveContentAsFile(fileName: String, content: String) {
        viewModelScope.launch {
            val savePath = if (projectPath.isNotBlank()) "$projectPath/$fileName"
                           else "/sdcard/$fileName"
            fsRepository.writeFile(savePath, content).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(error = "Saved to $savePath") },
                onFailure = { _uiState.value = _uiState.value.copy(error = "Save failed: ${it.message}") }
            )
        }
    }
'''
        vm_content = vm_content.rstrip().rstrip('}') + save_fn + '\n}\n'
        with open(vm_path, 'w') as f:
            f.write(vm_content)
        print("  ✅ ui/screens/chat/AiChatViewModel.kt (patched saveContentAsFile)")
        OK.append("AiChatViewModel.kt patch")
    else:
        print("  ⏭️  AiChatViewModel already has saveContentAsFile")
except Exception as e:
    print(f"  ❌ AiChatViewModel patch — {e}")
    FAIL.append("AiChatViewModel patch")

# ═══════════════════════════════════════════════════════════════
# Summary
# ═══════════════════════════════════════════════════════════════
print(f"\n{'='*50}")
print(f"✅ Done: {len(OK)} files")
if FAIL:
    print(f"❌ Failed: {FAIL}")
print("""
Phase 8 features:
  ✅ AI Chat button on home screen (quick actions bar)
  ✅ Browse Files button on home screen
  ✅ Browse button inside load project dialog
  ✅ Add project files to AI chat (📁 button)
  ✅ Voice input in chat (🎤 button)
  ✅ Save AI response as file (💾 per message)
  ✅ Chat icon updated to AI icon in project cards
  ✅ Version → Phase 8

Run:
  git add -A
  git commit -m "feat: Phase 8 - AI chat button, browse files, voice input, save response"
  git push origin HEAD
""")
