package com.codemaster.aistudio.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val geminiApiKey: String = "",
    val kimiApiKey: String = "",
    val githubToken: String = "",
    val githubRepo: String = "",
    val darkTheme: Boolean = true,
    val fontSize: Int = 14,
    val aiProvider: String = "gemini",
    val isSaved: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    geminiApiKey = settingsRepository.getGeminiApiKey(),
                    kimiApiKey = settingsRepository.getKimiApiKey(),
                    githubToken = settingsRepository.getGitHubToken(),
                    githubRepo = settingsRepository.getGitHubRepo(),
                    darkTheme = settingsRepository.isDarkTheme(),
                    fontSize = settingsRepository.getFontSize(),
                    aiProvider = settingsRepository.getAiProvider()
                )
            }
        }
    }

    fun updateGeminiKey(key: String) = _uiState.update { it.copy(geminiApiKey = key, isSaved = false) }
    fun updateKimiKey(key: String) = _uiState.update { it.copy(kimiApiKey = key, isSaved = false) }
    fun updateGitHubToken(token: String) = _uiState.update { it.copy(githubToken = token, isSaved = false) }
    fun updateGitHubRepo(repo: String) = _uiState.update { it.copy(githubRepo = repo, isSaved = false) }
    fun updateFontSize(size: Int) = _uiState.update { it.copy(fontSize = size, isSaved = false) }
    fun updateAiProvider(p: String) = _uiState.update { it.copy(aiProvider = p, isSaved = false) }

    fun saveAll() {
        viewModelScope.launch {
            val s = _uiState.value
            settingsRepository.saveGeminiApiKey(s.geminiApiKey.trim())
            settingsRepository.saveKimiApiKey(s.kimiApiKey.trim())
            settingsRepository.saveGitHubToken(s.githubToken.trim())
            settingsRepository.saveGitHubRepo(s.githubRepo.trim())
            settingsRepository.setFontSize(s.fontSize)
            settingsRepository.setAiProvider(s.aiProvider)
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBuild: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) snackbarHostState.showSnackbar("Settings saved ✅")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.saveAll() }) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // AI Provider
            item {
                SettingsSection("🤖 AI Provider") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("gemini" to "Gemini", "kimi" to "Kimi").forEach { (id, label) ->
                            FilterChip(
                                selected = uiState.aiProvider == id,
                                onClick = { viewModel.updateAiProvider(id) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // Gemini
            item {
                SettingsSection("🔑 Gemini API Key") {
                    SecretField(
                        value = uiState.geminiApiKey,
                        onValueChange = viewModel::updateGeminiKey,
                        label = "Gemini API Key",
                        hint = "AIza..."
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Get from: aistudio.google.com",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            // Kimi
            item {
                SettingsSection("🔑 Kimi API Key") {
                    SecretField(
                        value = uiState.kimiApiKey,
                        onValueChange = viewModel::updateKimiKey,
                        label = "Kimi API Key",
                        hint = "sk-..."
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Get from: platform.moonshot.cn",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            // GitHub
            item {
                SettingsSection("🐙 GitHub (for Build trigger)") {
                    OutlinedTextField(
                        value = uiState.githubRepo,
                        onValueChange = viewModel::updateGitHubRepo,
                        label = { Text("Repository") },
                        placeholder = { Text("owner/repo-name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    SecretField(
                        value = uiState.githubToken,
                        onValueChange = viewModel::updateGitHubToken,
                        label = "GitHub Token",
                        hint = "ghp_..."
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Token needs: Actions (read/write) permission",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onNavigateToBuild,
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Go to Build Screen")
                    }
                }
            }

            // Editor
            item {
                SettingsSection("✏️ Editor") {
                    Text("Font Size: ${uiState.fontSize}sp",
                        style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = uiState.fontSize.toFloat(),
                        onValueChange = { viewModel.updateFontSize(it.toInt()) },
                        valueRange = 10f..24f,
                        steps = 13,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Save button at bottom
            item {
                Button(
                    onClick = { viewModel.saveAll() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save All Settings")
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
fun SecretField(value: String, onValueChange: (String) -> Unit, label: String, hint: String) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(hint) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle", modifier = Modifier.size(18.dp))
            }
        }
    )
}
