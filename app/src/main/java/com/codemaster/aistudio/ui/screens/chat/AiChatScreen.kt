package com.codemaster.aistudio.ui.screens.chat

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
    onBack: () -> Unit = {},
    onNavigateToSettings: () -> Unit = onBack,
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
                cursor.moveToFirst()
                if (idx >= 0) cursor.getString(idx) else null
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
                    action = {
                        if (uiState.errorRequiresSettings) {
                            TextButton(onClick = {
                                viewModel.clearError()
                                onNavigateToSettings()
                            }) { Text("Go to Settings") }
                        }
                        TextButton(onClick = viewModel::clearError) { Text("OK") }
                    },
                    dismissAction = {
                        IconButton(onClick = viewModel::clearError) {
                            Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
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
                val code = if (lines.firstOrNull()?.all { it.isLetter() } == true) lines.drop(1).joinToString("\n") else part
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
        Text("Ask me anything about your code.\nUse 📁 to add project files as context.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
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
