package com.codemaster.aistudio.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showGeminiKey by remember { mutableStateOf(false) }
    var showGroqKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("API Keys", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your keys are stored securely on-device and never sent anywhere except the respective AI APIs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                OutlinedTextField(
                    value = settings?.geminiApiKey ?: "",
                    onValueChange = viewModel::updateGeminiKey,
                    label = { Text("Google Gemini API Key") },
                    placeholder = { Text("AIza...") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                            Icon(
                                if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    supportingText = { Text("Get your key at aistudio.google.com") }
                )
            }

            item {
                OutlinedTextField(
                    value = settings?.groqApiKey ?: "",
                    onValueChange = viewModel::updateGroqKey,
                    label = { Text("Moonshot Groq API Key") },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showGroqKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showGroqKey = !showGroqKey }) {
                            Icon(
                                if (showGroqKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    supportingText = { Text("Get your key at platform.moonshot.cn") }
                )
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Preferences", style = MaterialTheme.typography.titleMedium)
            }

            item {
                ListItem(
                    headlineContent = { Text("Sandbox Mode") },
                    supportingContent = { Text("Isolate code execution from your device files") },
                    trailingContent = {
                        Switch(
                            checked = settings?.sandboxMode ?: true,
                            onCheckedChange = viewModel::updateSandboxMode
                        )
                    }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Auto Save") },
                    supportingContent = { Text("Automatically save changes to files") },
                    trailingContent = {
                        Switch(
                            checked = settings?.autoSave ?: true,
                            onCheckedChange = viewModel::updateAutoSave
                        )
                    }
                )
            }
        }
    }
}
