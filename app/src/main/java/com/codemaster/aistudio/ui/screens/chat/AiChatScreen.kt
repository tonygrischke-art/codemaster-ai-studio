package com.codemaster.aistudio.ui.screens.chat

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codemaster.aistudio.data.model.AiProvider
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.model.MessageRole

// ─── Quick Prompt Templates ────────────────────────────────────
private val QUICK_PROMPTS = listOf(
    "🔧 Fix this error:" to "Fix this error:\n\n",
    "✍️ Write code for:" to "Write a complete implementation for:\n\n",
    "💡 Explain this:" to "Explain this code:\n\n```\n\n```",
    "🔨 Build Gradle" to "Generate a working build.gradle.kts for an Android app with:",
    "🐛 Debug crash:" to "Debug this Android crash log:\n\n"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit,
    viewModel: AiChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(projectId) {
        viewModel.init(projectId)
    }

    // Auto-scroll to latest message
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                selectedProvider = uiState.selectedProvider,
                onSwitchProvider = viewModel::switchProvider,
                onClearHistory = viewModel::clearHistory,
                onNavigateBack = onNavigateBack
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = uiState.inputText,
                isLoading = uiState.isLoading,
                onTextChange = viewModel::updateInput,
                onSend = viewModel::sendMessage
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Quick prompt chips
            QuickPromptRow(onPromptSelected = { viewModel.updateInput(it) })

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            // Messages list
            if (uiState.messages.isEmpty()) {
                WelcomeEmptyState(selectedProvider = uiState.selectedProvider)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatMessageBubble(
                            message = message,
                            onCopyCode = { code ->
                                clipboard.setText(AnnotatedString(code))
                            }
                        )
                    }
                    if (uiState.isLoading) {
                        item {
                            LoadingBubble(provider = uiState.selectedProvider)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    selectedProvider: AiProvider,
    onSwitchProvider: (AiProvider) -> Unit,
    onClearHistory: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI Chat", style = MaterialTheme.typography.titleLarge)
                ProviderToggleChip(
                    selectedProvider = selectedProvider,
                    onSwitch = { onSwitchProvider(if (selectedProvider == AiProvider.GEMINI) AiProvider.GROQ else AiProvider.GEMINI) }
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Clear history") },
                    onClick = { onClearHistory(); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun ProviderToggleChip(selectedProvider: AiProvider, onSwitch: () -> Unit) {
    val isGemini = selectedProvider == AiProvider.GEMINI
    AssistChip(
        onClick = onSwitch,
        label = {
            Text(
                text = if (isGemini) "Gemini" else "Kimi",
                style = MaterialTheme.typography.labelSmall
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (isGemini)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.height(28.dp)
    )
}

@Composable
fun QuickPromptRow(onPromptSelected: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(QUICK_PROMPTS) { (label, prompt) ->
            SuggestionChip(
                onClick = { onPromptSelected(prompt) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onCopyCode: (String) -> Unit
) {
    val isUser = message.role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // AI avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (message.provider == AiProvider.GEMINI)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondary
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (message.provider == AiProvider.GEMINI) "G" else "K",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(modifier = Modifier.widthIn(max = 300.dp)) {
            // Parse and render message content
            if (!isUser && message.hasCodeBlock) {
                MessageWithCodeBlocks(content = message.content, onCopyCode = onCopyCode, isError = message.isError)
            } else {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ),
                    color = when {
                        isUser -> MaterialTheme.colorScheme.primary
                        message.isError -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            isUser -> MaterialTheme.colorScheme.onPrimary
                            message.isError -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MessageWithCodeBlocks(
    content: String,
    onCopyCode: (String) -> Unit,
    isError: Boolean
) {
    // Simple parser: split on ```
    val parts = content.split(Regex("```(\\w*)\n?"))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var isCode = false
        var langHint = ""
        parts.forEachIndexed { idx, part ->
            if (idx == 0) {
                // Leading text
                if (part.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = part.trim(),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                isCode = false
            } else if (!isCode) {
                // This is the language hint between ```, next part is code
                langHint = part.trim()
                isCode = true
            } else {
                // Code block
                CodeBlock(code = part.trimEnd(), language = langHint, onCopy = onCopyCode)
                isCode = false
            }
        }
    }
}

@Composable
fun CodeBlock(code: String, language: String, onCopy: (String) -> Unit) {
    var copied by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF161B22), // Always dark for code
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column {
            // Code block header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8B949E)
                )
                TextButton(
                    onClick = {
                        onCopy(code)
                        copied = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(14.dp),
                        tint = if (copied) Color(0xFF3FB950) else Color(0xFF8B949E)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (copied) "Copied!" else "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (copied) Color(0xFF3FB950) else Color(0xFF8B949E)
                    )
                }
            }
            HorizontalDivider(color = Color(0xFF30363D))
            // Code content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFE6EDF3),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun LoadingBubble(provider: AiProvider) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (provider == AiProvider.GEMINI)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondary
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (provider == AiProvider.GEMINI) "G" else "K",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    "Thinking...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    inputText: String,
    isLoading: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask AI to write, fix, or explain code...") },
                minLines = 1,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default
                ),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            FilledIconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isLoading,
                modifier = Modifier.size(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun WelcomeEmptyState(selectedProvider: AiProvider) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🤖", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Ask ${selectedProvider.displayName} anything",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Write code, fix bugs, explain concepts,\ngenerate Android projects, and more.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
