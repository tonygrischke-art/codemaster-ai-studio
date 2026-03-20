package com.codemaster.aistudio.ui.screens.git

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    projectId: Long,
    onBack: () -> Unit,
    viewModel: GitViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var commitMessage by remember { mutableStateOf("") }
    var showQuickCommit by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) { viewModel.init(projectId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Git", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, "Refresh") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status card
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountTree, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Repository Status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    HorizontalDivider()
                    if (uiState.currentBranch.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CallSplit, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("Branch: ${uiState.currentBranch}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                    Text(uiState.status.ifBlank { "No status" }, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // One-tap Quick Commit + Push
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Quick Commit & Push", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("Commit message") },
                        placeholder = { Text("feat: describe your changes") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (commitMessage.isNotBlank()) {
                                IconButton(onClick = { commitMessage = "" }) {
                                    Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                    // Quick message chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("feat: ", "fix: ", "chore: ", "docs: ").forEach { prefix ->
                            SuggestionChip(onClick = { commitMessage = prefix }, label = { Text(prefix, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    Button(
                        onClick = {
                            if (commitMessage.isNotBlank()) {
                                viewModel.commitAndPush(commitMessage)
                                commitMessage = ""
                            }
                        },
                        enabled = commitMessage.isNotBlank() && !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Committing & Pushing...")
                        } else {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Commit & Push", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Individual operations
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Git Operations", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.pull() }, modifier = Modifier.weight(1f), enabled = !uiState.isLoading) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pull")
                        }
                        OutlinedButton(onClick = { viewModel.push() }, modifier = Modifier.weight(1f), enabled = !uiState.isLoading) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Push")
                        }
                    }
                    OutlinedButton(onClick = { viewModel.stageAll() }, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isLoading) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stage All Changes (git add -A)")
                    }
                }
            }

            // Log output
            if (uiState.log.isNotEmpty()) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Output", style = MaterialTheme.typography.labelSmall, color = Color(0xFF58A6FF), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        uiState.log.takeLast(15).forEach { line ->
                            val color = when {
                                line.contains("error", ignoreCase = true) -> Color(0xFFFF5252)
                                line.contains("success", ignoreCase = true) || line.contains("pushed") -> Color(0xFF69F0AE)
                                else -> Color(0xFF58A6FF)
                            }
                            Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = color, lineHeight = 15.sp)
                        }
                    }
                }
            }

            uiState.error?.let { err ->
                Snackbar(action = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } }) { Text(err) }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
