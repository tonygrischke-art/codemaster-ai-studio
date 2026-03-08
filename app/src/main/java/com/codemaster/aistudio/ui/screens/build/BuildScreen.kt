package com.codemaster.aistudio.ui.screens.build

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codemaster.aistudio.data.repository.BuildArtifact
import com.codemaster.aistudio.data.repository.BuildStatus
import com.codemaster.aistudio.data.repository.FileResult
import com.codemaster.aistudio.data.repository.GitHubActionsRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── UI State ──────────────────────────────────────────────────
data class BuildUiState(
    val builds: List<BuildStatus> = emptyList(),
    val artifacts: Map<Long, List<BuildArtifact>> = emptyMap(),
    val isLoading: Boolean = false,
    val isTriggering: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val githubToken: String = "",
    val githubRepo: String = "",
    val isConfigured: Boolean = false,
    val autoRefresh: Boolean = false
)

// ─── ViewModel ─────────────────────────────────────────────────
@HiltViewModel
class BuildViewModel @Inject constructor(
    private val githubActionsRepository: GitHubActionsRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val token = settingsRepository.getGitHubToken()
            val repo = settingsRepository.getGitHubRepo()
            _uiState.update {
                it.copy(
                    githubToken = token,
                    githubRepo = repo,
                    isConfigured = token.isNotBlank() && repo.isNotBlank()
                )
            }
            if (token.isNotBlank() && repo.isNotBlank()) {
                loadBuilds()
            }
        }
    }

    fun loadBuilds() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = githubActionsRepository.getRecentBuilds()) {
                is FileResult.Success -> {
                    _uiState.update { it.copy(builds = result.data, isLoading = false) }
                }
                is FileResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun triggerBuild() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTriggering = true, errorMessage = null) }
            when (val result = githubActionsRepository.triggerBuild()) {
                is FileResult.Success -> {
                    _uiState.update {
                        it.copy(isTriggering = false, successMessage = result.data)
                    }
                    // Wait a bit then refresh builds
                    delay(3000)
                    loadBuilds()
                }
                is FileResult.Error -> {
                    _uiState.update {
                        it.copy(isTriggering = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun loadArtifacts(runId: Long) {
        viewModelScope.launch {
            when (val result = githubActionsRepository.getArtifacts(runId)) {
                is FileResult.Success -> {
                    _uiState.update { state ->
                        state.copy(artifacts = state.artifacts + (runId to result.data))
                    }
                }
                is FileResult.Error -> { /* silently fail */ }
            }
        }
    }

    fun saveGitHubConfig(token: String, repo: String) {
        viewModelScope.launch {
            settingsRepository.saveGitHubToken(token)
            settingsRepository.saveGitHubRepo(repo)
            _uiState.update {
                it.copy(
                    githubToken = token,
                    githubRepo = repo,
                    isConfigured = token.isNotBlank() && repo.isNotBlank()
                )
            }
            if (token.isNotBlank() && repo.isNotBlank()) loadBuilds()
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}

// ─── Build Screen ──────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildScreen(
    onNavigateBack: () -> Unit,
    viewModel: BuildViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showConfigDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Build", fontWeight = FontWeight.Bold)
                        if (uiState.githubRepo.isNotBlank()) {
                            Text(
                                uiState.githubRepo,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showConfigDialog = true }) {
                        Icon(Icons.Default.Key, contentDescription = "Configure")
                    }
                    IconButton(onClick = { viewModel.loadBuilds() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!uiState.isTriggering) viewModel.triggerBuild() },
                icon = {
                    if (uiState.isTriggering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null)
                    }
                },
                text = { Text(if (uiState.isTriggering) "Triggering..." else "Build APK") },
                containerColor = if (uiState.isConfigured)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Not configured warning
            if (!uiState.isConfigured) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("GitHub not configured",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error)
                                Text("Tap the key icon to add your GitHub token and repo",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { showConfigDialog = true }) {
                                Text("Setup")
                            }
                        }
                    }
                }
            }

            // Loading
            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Build list header
            if (uiState.builds.isNotEmpty()) {
                item {
                    Text("Recent Builds",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }
            }

            // Build cards
            items(uiState.builds, key = { it.id }) { build ->
                BuildCard(
                    build = build,
                    artifacts = uiState.artifacts[build.id],
                    onLoadArtifacts = { viewModel.loadArtifacts(build.id) },
                    onOpenInBrowser = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(build.htmlUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }

            // Bottom padding for FAB
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // Config dialog
    if (showConfigDialog) {
        GitHubConfigDialog(
            currentToken = uiState.githubToken,
            currentRepo = uiState.githubRepo,
            onSave = { token, repo ->
                viewModel.saveGitHubConfig(token, repo)
                showConfigDialog = false
            },
            onDismiss = { showConfigDialog = false }
        )
    }
}

// ─── Build Card ────────────────────────────────────────────────
@Composable
fun BuildCard(
    build: BuildStatus,
    artifacts: List<BuildArtifact>?,
    onLoadArtifacts: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status indicator
                BuildStatusDot(build.status, build.conclusion)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        build.name.take(50),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        buildStatusText(build.status, build.conclusion),
                        style = MaterialTheme.typography.labelSmall,
                        color = buildStatusColor(build.status, build.conclusion)
                    )
                    Text(
                        formatBuildTime(build.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Actions
                Row {
                    IconButton(onClick = onOpenInBrowser, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open in browser",
                            modifier = Modifier.size(18.dp))
                    }
                    if (build.conclusion == "success") {
                        IconButton(
                            onClick = {
                                expanded = !expanded
                                if (expanded && artifacts == null) onLoadArtifacts()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle artifacts",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Artifacts expansion
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Artifacts", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    if (artifacts == null) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else if (artifacts.isEmpty()) {
                        Text("No artifacts found", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        artifacts.forEach { artifact ->
                            ArtifactRow(artifact)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtifactRow(artifact: BuildArtifact) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Android, contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF3DDC84))
            Column {
                Text(artifact.name, style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium)
                Text(formatBytes(artifact.sizeInBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(
            onClick = {
                // Open download URL in browser
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(artifact.downloadUrl))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = null,
                modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Download", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun BuildStatusDot(status: String, conclusion: String?) {
    val color = buildStatusColor(status, conclusion)
    val isAnimating = status == "in_progress" || status == "queued"

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}

// ─── GitHub Config Dialog ──────────────────────────────────────
@Composable
fun GitHubConfigDialog(
    currentToken: String,
    currentRepo: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var token by remember { mutableStateOf(currentToken) }
    var repo by remember { mutableStateOf(currentRepo) }
    var showToken by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub Configuration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Add your GitHub Personal Access Token (with workflow permissions) and your repo in owner/repo format.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it },
                    label = { Text("Repository") },
                    placeholder = { Text("tonygrischke-art/codemaster-ai-studio") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null,
                        modifier = Modifier.size(18.dp)) }
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("GitHub Token") },
                    placeholder = { Text("ghp_...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showToken)
                        androidx.compose.ui.text.input.VisualTransformation.None
                    else
                        androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null,
                        modifier = Modifier.size(18.dp)) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(token.trim(), repo.trim()) },
                enabled = token.isNotBlank() && repo.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─── Helpers ───────────────────────────────────────────────────
@Composable
fun buildStatusColor(status: String, conclusion: String?): Color {
    return when {
        conclusion == "success" -> Color(0xFF3FB950)
        conclusion == "failure" -> Color(0xFFF85149)
        conclusion == "cancelled" -> Color(0xFF8B949E)
        status == "in_progress" -> Color(0xFFF0883E)
        status == "queued" -> Color(0xFF58A6FF)
        else -> Color(0xFF8B949E)
    }
}

fun buildStatusText(status: String, conclusion: String?): String {
    return when {
        conclusion == "success" -> "✅ Success"
        conclusion == "failure" -> "❌ Failed"
        conclusion == "cancelled" -> "⚫ Cancelled"
        status == "in_progress" -> "🟡 Building..."
        status == "queued" -> "🔵 Queued"
        else -> status
    }
}

fun formatBuildTime(isoTime: String): String {
    return try {
        isoTime.replace("T", " ").replace("Z", " UTC").take(19) + " UTC"
    } catch (e: Exception) {
        isoTime
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
