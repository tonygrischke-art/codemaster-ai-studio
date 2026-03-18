package com.codemaster.aistudio.ui.screens.snippets

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codemaster.aistudio.data.model.Snippet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetScreen(
    onBack: () -> Unit,
    viewModel: SnippetViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Snippet Library", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, "Add snippet")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search snippets...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.query.isNotBlank()) {
                        IconButton(onClick = { viewModel.search("") }) { Icon(Icons.Default.Clear, null) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            if (uiState.snippets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Code, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No snippets yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Tap + to save reusable code", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.snippets, key = { it.id }) { snippet ->
                        SnippetCard(
                            snippet = snippet,
                            onEdit = { viewModel.showAddDialog(snippet) },
                            onDelete = { viewModel.deleteSnippet(snippet) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (uiState.showAddDialog) {
        SnippetDialog(
            title = uiState.newTitle,
            code = uiState.newCode,
            language = uiState.newLanguage,
            tags = uiState.newTags,
            isEditing = uiState.editingSnippet != null,
            onTitleChange = viewModel::updateTitle,
            onCodeChange = viewModel::updateCode,
            onLanguageChange = viewModel::updateLanguage,
            onTagsChange = viewModel::updateTags,
            onSave = { viewModel.saveSnippet() },
            onDismiss = { viewModel.hideDialog() }
        )
    }
}

@Composable
fun SnippetCard(snippet: Snippet, onEdit: () -> Unit, onDelete: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(snippet.title, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SuggestionChip(onClick = {}, label = { Text(snippet.language, fontSize = 10.sp) }, modifier = Modifier.height(22.dp))
                        if (snippet.tags.isNotBlank()) {
                            snippet.tags.split(",").take(3).forEach { tag ->
                                SuggestionChip(onClick = {}, label = { Text(tag.trim(), fontSize = 10.sp) }, modifier = Modifier.height(22.dp))
                            }
                        }
                    }
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(snippet.code)) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(18.dp))
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
                    }
                }
            }
            // Code preview
            Spacer(Modifier.height(8.dp))
            val preview = if (expanded) snippet.code else snippet.code.lines().take(4).joinToString("\n")
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(preview, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(8.dp).fillMaxWidth())
            }
            if (snippet.code.lines().size > 4) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                    Text(if (expanded) "Show less" else "Show more", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun SnippetDialog(
    title: String, code: String, language: String, tags: String,
    isEditing: Boolean,
    onTitleChange: (String) -> Unit, onCodeChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit, onTagsChange: (String) -> Unit,
    onSave: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Snippet" else "New Snippet", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = onTitleChange, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = language, onValueChange = onLanguageChange, label = { Text("Language") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = tags, onValueChange = onTagsChange, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("api, network, util") })
                OutlinedTextField(
                    value = code, onValueChange = onCodeChange,
                    label = { Text("Code *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5, maxLines = 12,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )
            }
        },
        confirmButton = { Button(onClick = onSave, enabled = title.isNotBlank() && code.isNotBlank()) { Text(if (isEditing) "Update" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
