package com.codemaster.aistudio.ui.screens.settings

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
    var showApiKey by remember { mutableStateOf(false) }
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
                Text(
                    "Get your free key at: console.groq.com",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
                    placeholder = { Text("codemaster-ai-studio") },
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
