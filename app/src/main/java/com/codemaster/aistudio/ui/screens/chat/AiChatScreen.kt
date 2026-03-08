package com.codemaster.aistudio.ui.screens.chat

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AiChatScreen(
    projectPath: String = "",
    currentFile: String = "",
    code: String = "",
    onNavigateBack: (() -> Unit)? = null,
    onInsertCode: ((String) -> Unit)? = null,
    viewModel: AiChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(projectPath) { viewModel.initWithProject(projectPath, currentFile, code) }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("AI Assistant", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                if (onNavigateBack != null) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            },
            actions = {
                TextButton(onClick = { viewModel.switchProvider(if (uiState.aiProvider == "gemini") "kimi" else "gemini") }) {
                    Text(if (uiState.aiProvider == "gemini") "Gemini" else "Kimi", color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { viewModel.clearHistory() }) { Icon(Icons.Default.Delete, "Clear") }
            }
        )

        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.messages, key = { it.id }) { msg -> MessageBubble(msg, onInsertCode) }
            if (uiState.isLoading) {
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { CircularProgressIndicator(Modifier.size(24.dp).padding(4.dp)) } }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(value = uiState.input, onValueChange = { viewModel.updateInput(it) }, modifier = Modifier.weight(1f), placeholder = { Text("Ask AI...") }, maxLines = 5)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { viewModel.sendMessage(onInsertCode) }, enabled = uiState.input.isNotBlank() && !uiState.isLoading) {
                Icon(Icons.Default.Send, "Send", tint = if (uiState.input.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, onInsertCode: ((String) -> Unit)?) {
    val isUser = msg.role == MessageRole.USER
    val bubbleColor = when { msg.isError -> MaterialTheme.colorScheme.errorContainer; isUser -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.surfaceVariant }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(12.dp)).background(bubbleColor).padding(10.dp)) {
            SelectionContainer {
                if (msg.content.contains("```")) CodeFormattedText(msg.content, onInsertCode)
                else Text(msg.content, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun CodeFormattedText(content: String, onInsertCode: ((String) -> Unit)?) {
    val parts = content.split(Regex("```(?:\\w+)?\\n?"))
    parts.forEachIndexed { i, part ->
        if (part.isBlank()) return@forEachIndexed
        if (i % 2 == 1) {
            val code = part.trimEnd().removeSuffix("```").trimEnd()
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E1E1E)).padding(8.dp)) {
                SelectionContainer { Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFD4D4D4)) }
                if (onInsertCode != null) {
                    TextButton(onClick = { onInsertCode(code) }) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Insert", fontSize = 11.sp)
                    }
                }
            }
        } else Text(part, fontSize = 14.sp)
    }
}
