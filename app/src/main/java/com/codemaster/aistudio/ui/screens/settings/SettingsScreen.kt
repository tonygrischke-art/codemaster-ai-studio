package com.codemaster.aistudio.ui.screens.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showApiKey by remember { mutableStateOf(false) }
    var showClaudeKey by remember { mutableStateOf(false) }
    var showKimiKey by remember { mutableStateOf(false) }
    var showGithubToken by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    if (uiState.isSaved) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("🔑 API Keys — Free Setup", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "CodeMaster is free to use with your own API keys. Groq is completely free to sign up. Claude and Kimi are pay-as-you-go with free credits on signup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // ── AI Configuration ───────────────────────────────────────
            SettingsSection(title = "AI Configuration", icon = Icons.Default.AutoAwesome) {
                OutlinedTextField(
                    value = uiState.apiKey,
                    onValueChange = { viewModel.updateApiKey(it) },
                    label = { Text("Groq API Key") },
                    placeholder = { Text("gsk_...") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    singleLine = true
                )
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com"))
                        context.startActivity(intent)
                    }
                ) {
                    Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Get free Groq key at console.groq.com", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = uiState.claudeApiKey,
                    onValueChange = { viewModel.updateClaudeApiKey(it) },
                    label = { Text("Claude / Anthropic API Key") },
                    placeholder = { Text("sk-ant-...") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showClaudeKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showClaudeKey = !showClaudeKey }) {
                            Icon(if (showClaudeKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    singleLine = true
                )
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com"))
                        context.startActivity(intent)
                    }
                ) {
                    Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Get Claude key at console.anthropic.com", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = uiState.kimiApiKey,
                    onValueChange = { viewModel.updateKimiApiKey(it) },
                    label = { Text("Kimi / Moonshot API Key") },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showKimiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKimiKey = !showKimiKey }) {
                            Icon(if (showKimiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    singleLine = true
                )
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.moonshot.cn"))
                        context.startActivity(intent)
                    }
                ) {
                    Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Get Kimi key at platform.moonshot.cn", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.height(4.dp))

                ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = it }) {
                    OutlinedTextField(
                        value = uiState.model,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("AI Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        GROQ_MODELS.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = { viewModel.updateModel(model); modelExpanded = false },
                                trailingIcon = { if (model == uiState.model) Icon(Icons.Default.Check, null) }
                            )
                        }
                    }
                }
            }

            // ── GitHub Configuration ───────────────────────────────────
            SettingsSection(title = "GitHub — Build Trigger", icon = Icons.Default.CloudUpload) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("How to get your token:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text("1. Go to github.com → your avatar → Settings", style = MaterialTheme.typography.labelSmall)
                        Text("2. Developer settings → Personal access tokens → Tokens (classic)", style = MaterialTheme.typography.labelSmall)
                        Text("3. Generate new token → check: repo + workflow", style = MaterialTheme.typography.labelSmall)
                        Text("4. Copy and paste below — it's only shown ONCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = uiState.githubToken,
                    onValueChange = { viewModel.updateGithubToken(it) },
                    label = { Text("GitHub Personal Access Token") },
                    placeholder = { Text("github_pat_...") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showGithubToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showGithubToken = !showGithubToken }) {
                            Icon(if (showGithubToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.githubOwner,
                    onValueChange = { viewModel.updateGithubOwner(it) },
                    label = { Text("GitHub Username") },
                    placeholder = { Text("tonygrischke-art") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, null) }
                )

                OutlinedTextField(
                    value = uiState.githubRepo,
                    onValueChange = { viewModel.updateGithubRepo(it) },
                    label = { Text("Repository Name") },
                    placeholder = { Text("e.g. /data/data/com.termux/files/home/myproject") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.FolderOpen, null) }
                )

                OutlinedTextField(
                    value = uiState.githubBranch,
                    onValueChange = { viewModel.updateGithubBranch(it) },
                    label = { Text("Branch") },
                    placeholder = { Text("main") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.AccountTree, null) }
                )
            }

            // ── Appearance ─────────────────────────────────────────────
            SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (uiState.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        null, tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (uiState.isDarkTheme) "Dark Theme" else "Light Theme", fontWeight = FontWeight.Medium)
                        Text("Toggle app theme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = uiState.isDarkTheme, onCheckedChange = { viewModel.toggleTheme() })
                }
            }

            // ── About ──────────────────────────────────────────────────
            SettingsSection(title = "About", icon = Icons.Default.Info) {
                AboutRow("Version", "2.0.0 Phase 4")
                AboutRow("AI",      "Groq API")
                AboutRow("Build",   "GitHub Actions")
                AboutRow("Package", "com.codemaster.aistudio")
            }

            // ── Save Button ────────────────────────────────────────────
            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save All Settings", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
            HorizontalDivider()
            content()
        }
    }
}

@Composable
fun AboutRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
