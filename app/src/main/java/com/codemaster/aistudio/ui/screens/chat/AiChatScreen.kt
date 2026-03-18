package com.codemaster.aistudio.ui.screens.chat

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
