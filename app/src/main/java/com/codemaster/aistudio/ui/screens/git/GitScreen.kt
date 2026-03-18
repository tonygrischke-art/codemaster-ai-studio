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

    LaunchedEffect(projectId) { viewModel.init(projectId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Git", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status card
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountTree, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    HorizontalDivider()
                    if (!uiState.status.isRepo) {
                        Text("Not a git repository", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Text("Repo path: " + uiState.repoPath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        GitInfoRow("Branch", uiState.status.branch)
                        GitInfoRow("Last commit", uiState.status.lastCommit.take(60))
                        GitInfoRow("Staged", uiState.status.staged.size.toString() + " files")
                        GitInfoRow("Unstaged", uiState.status.unstaged.size.toString() + " files")
                        GitInfoRow("Untracked", uiState.status.untracked.size.toString() + " files")
                    }
                }
            }

            // Result/error messages
            uiState.result?.let { msg ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF69F0AE), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            uiState.error?.let { err ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) }
                    }
                }
            }

            // Commit section
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Commit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Commit", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    HorizontalDivider()
                    OutlinedTextField(
                        value = uiState.commitMessage,
                        onValueChange = { viewModel.updateCommitMessage(it) },
                        label = { Text("Commit message") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2, maxLines = 4,
                        placeholder = { Text("feat: add new feature") }
                    )
                    Button(
                        onClick = { viewModel.stageAndCommit() },
                        enabled = uiState.commitMessage.isNotBlank() && !uiState.isLoading && uiState.status.isRepo,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Stage All & Commit")
                    }
                }
            }

            // Push/Pull
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.push() },
                    enabled = !uiState.isLoading && uiState.status.isRepo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Push")
                }
                OutlinedButton(
                    onClick = { viewModel.pull() },
                    enabled = !uiState.isLoading && uiState.status.isRepo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pull")
                }
            }

            // Log
            if (uiState.log.isNotBlank()) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Recent Commits", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = Color(0xFF8B949E))
                        Spacer(Modifier.height(8.dp))
                        uiState.log.lines().forEach { line ->
                            Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF58A6FF), lineHeight = 18.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun GitInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    }
}
