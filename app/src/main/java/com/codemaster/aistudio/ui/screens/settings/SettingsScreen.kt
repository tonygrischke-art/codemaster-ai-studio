package com.codemaster.aistudio.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    var geminiKey by remember { mutableStateOf("") }
    var groqKey by remember { mutableStateOf("") }

    LaunchedEffect(settings) {
        val s = settings ?: return@LaunchedEffect
        geminiKey = s.geminiApiKey
        groqKey = s.groqApiKey
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("API Keys", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = geminiKey,
                onValueChange = { geminiKey = it },
                label = { Text("Gemini API Key") },
                placeholder = { Text("Enter your Gemini key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = { viewModel.updateGeminiKey(geminiKey) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save Gemini Key")
            }

            OutlinedTextField(
                value = groqKey,
                onValueChange = { groqKey = it },
                label = { Text("Groq API Key") },
                placeholder = { Text("Enter your Groq key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = { viewModel.updateGroqKey(groqKey) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save Groq Key")
            }

            HorizontalDivider()

            Text("Appearance", style = MaterialTheme.typography.titleMedium)

            val s = settings
            if (s != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dark Theme")
                    Switch(
                        checked = s.darkTheme,
                        onCheckedChange = { viewModel.updateTheme(it) }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Auto Save")
                    Switch(
                        checked = s.autoSave,
                        onCheckedChange = { }
                    )
                }
            }
        }
    }
}
