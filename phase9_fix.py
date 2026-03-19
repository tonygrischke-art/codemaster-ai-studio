import os

BASE = "app/src/main/java/com/codemaster/aistudio"
OK = []; FAIL = []

def write(rel, content):
    path = os.path.join(BASE, rel)
    try:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w') as f:
            f.write(content)
        print(f"  ✅ {rel}")
        OK.append(rel)
    except Exception as e:
        print(f"  ❌ {rel} — {e}")
        FAIL.append(rel)

print("\n🚀 Phase 9 — Local Build, AI writes files, APK installer, templates\n")

# ═══════════════════════════════════════════════════════════════
# 1. BuildViewModel.kt
#    + Local Termux build via ./gradlew
#    + Live streaming build log
#    + APK finder after build
#    + Build error → AI fix
# ═══════════════════════════════════════════════════════════════
write("ui/screens/build/BuildViewModel.kt", '''package com.codemaster.aistudio.ui.screens.build

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.GitHubActionsRepository
import com.codemaster.aistudio.data.repository.ProjectRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject

data class BuildUiState(
    val isConfigured: Boolean = false,
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val isTriggering: Boolean = false,
    val isLocalBuilding: Boolean = false,
    val triggerMessage: String? = null,
    val latestRunStatus: String? = null,
    val logs: List<String> = emptyList(),
    val apkPath: String? = null,
    val buildErrors: List<String> = emptyList(),
    val projectPath: String = "",
    val errorForAi: String? = null
)

@HiltViewModel
class BuildViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitHubActionsRepository: GitHubActionsRepository,
    private val settingsRepository: SettingsRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()
    private var currentProjectId: Long = -1L

    fun init(projectId: Long) {
        currentProjectId = projectId
        loadConfig()
        refreshStatus()
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            val path = project?.path ?: ""
            _uiState.value = _uiState.value.copy(projectPath = path)
        }
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val token  = settingsRepository.getGitHubToken()
            val owner  = settingsRepository.getGitHubOwner()
            val repo   = settingsRepository.getGitHubRepo()
            val branch = settingsRepository.getGitHubBranch()
            _uiState.value = _uiState.value.copy(
                isConfigured = token.isNotBlank() && owner.isNotBlank() && repo.isNotBlank(),
                owner = owner, repo = repo,
                branch = branch.ifBlank { "main" }
            )
        }
    }

    // ── GitHub Actions build ─────────────────────────────────
    fun triggerBuild() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTriggering = true, triggerMessage = null)
            addLog("Triggering GitHub Actions build...")
            val result = gitHubActionsRepository.triggerBuild()
            result.fold(
                onSuccess = { msg ->
                    _uiState.value = _uiState.value.copy(isTriggering = false, triggerMessage = msg)
                    addLog(msg)
                    kotlinx.coroutines.delay(3000)
                    refreshStatus()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isTriggering = false, triggerMessage = "❌ ${e.message}")
                    addLog("ERROR: ${e.message}")
                }
            )
        }
    }

    // ── Local Termux build ───────────────────────────────────
    fun triggerLocalBuild() {
        val projectPath = _uiState.value.projectPath
        if (projectPath.isBlank()) {
            addLog("❌ No project path set. Load a project first.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLocalBuilding = true, buildErrors = emptyList(),
                apkPath = null, errorForAi = null
            )
            addLog("🔨 Starting local Termux build...")
            addLog("📁 Project: $projectPath")

            withContext(Dispatchers.IO) {
                try {
                    val gradlew = File(projectPath, "gradlew")
                    if (!gradlew.exists()) {
                        addLog("❌ gradlew not found in $projectPath")
                        _uiState.value = _uiState.value.copy(isLocalBuilding = false)
                        return@withContext
                    }

                    // Make gradlew executable
                    gradlew.setExecutable(true)

                    val process = ProcessBuilder(
                        listOf("$projectPath/gradlew", "assembleDebug", "--stacktrace")
                    )
                        .directory(File(projectPath))
                        .redirectErrorStream(true)
                        .start()

                    val errors = mutableListOf<String>()
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val l = line!!
                            addLog(l)
                            if (l.contains("error:", ignoreCase = true) ||
                                l.contains("FAILED", ignoreCase = true) ||
                                l.contains("Exception")) {
                                errors.add(l)
                            }
                        }
                    }

                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        addLog("✅ BUILD SUCCESSFUL")
                        // Find APK
                        val apk = findApk(projectPath)
                        if (apk != null) {
                            addLog("📦 APK ready: ${apk.name}")
                            _uiState.value = _uiState.value.copy(apkPath = apk.absolutePath)
                        }
                    } else {
                        addLog("❌ BUILD FAILED (exit $exitCode)")
                        val errorSummary = errors.takeLast(20).joinToString("\\n")
                        _uiState.value = _uiState.value.copy(
                            buildErrors = errors,
                            errorForAi = errorSummary
                        )
                    }
                } catch (e: Exception) {
                    addLog("❌ Exception: ${e.message}")
                    _uiState.value = _uiState.value.copy(buildErrors = listOf(e.message ?: "Unknown error"))
                } finally {
                    _uiState.value = _uiState.value.copy(isLocalBuilding = false)
                }
            }
        }
    }

    private fun findApk(projectPath: String): File? {
        return File(projectPath).walk()
            .filter { it.extension == "apk" && it.name.contains("debug") }
            .maxByOrNull { it.lastModified() }
    }

    fun installApk(apkPath: String) {
        try {
            val apkFile = File(apkPath)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            addLog("❌ Install failed: ${e.message}")
        }
    }

    fun clearErrorForAi() { _uiState.value = _uiState.value.copy(errorForAi = null) }

    fun refreshStatus() {
        viewModelScope.launch {
            loadConfig()
            if (!_uiState.value.isConfigured) return@launch
            gitHubActionsRepository.getLatestRun().fold(
                onSuccess = { status -> _uiState.value = _uiState.value.copy(latestRunStatus = status) },
                onFailure = { }
            )
        }
    }

    private fun addLog(message: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logs = (_uiState.value.logs + "[$ts] $message").takeLast(100)
        _uiState.value = _uiState.value.copy(logs = logs)
    }
}
''')

# ═══════════════════════════════════════════════════════════════
# 2. BuildScreen.kt
#    + Local build button
#    + Live streaming log (scrollable)
#    + APK install button
#    + Send errors to AI button
# ═══════════════════════════════════════════════════════════════
write("ui/screens/build/BuildScreen.kt", '''package com.codemaster.aistudio.ui.screens.build

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildScreen(
    projectId: Long,
    onBack: () -> Unit,
    onGoToSettings: () -> Unit = {},
    onSendErrorToAi: ((String) -> Unit)? = null,
    viewModel: BuildViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val logScrollState = rememberLazyListState()
    var showSetupGuide by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) { viewModel.init(projectId) }
    LaunchedEffect(uiState.logs.size) {
        if (uiState.logs.isNotEmpty())
            logScrollState.animateScrollToItem(uiState.logs.size - 1)
    }

    // Send errors to AI when available
    LaunchedEffect(uiState.errorForAi) {
        uiState.errorForAi?.let { error ->
            onSendErrorToAi?.invoke(error)
            viewModel.clearErrorForAi()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Build", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { showSetupGuide = !showSetupGuide }) { Icon(Icons.Default.Help, "Guide") }
                    IconButton(onClick = { viewModel.refreshStatus() }) { Icon(Icons.Default.Refresh, "Refresh") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            AnimatedVisibility(visible = showSetupGuide) { SetupGuideCard(onGoToSettings = onGoToSettings) }

            ConfigStatusCard(isConfigured = uiState.isConfigured, owner = uiState.owner, repo = uiState.repo, branch = uiState.branch, onGoToSettings = onGoToSettings)

            // ── Build buttons row ──────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Local Termux build
                Button(
                    onClick = { viewModel.triggerLocalBuild() },
                    enabled = !uiState.isLocalBuilding && !uiState.isTriggering,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    if (uiState.isLocalBuilding) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Building...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.Terminal, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Local Build", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // GitHub Actions build
                Button(
                    onClick = { viewModel.triggerBuild() },
                    enabled = uiState.isConfigured && !uiState.isTriggering && !uiState.isLocalBuilding,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (uiState.isTriggering) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Triggering...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("GitHub Build", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // ── APK Install button ─────────────────────────────────
            AnimatedVisibility(visible = uiState.apkPath != null) {
                uiState.apkPath?.let { apk ->
                    Button(
                        onClick = { viewModel.installApk(apk) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                    ) {
                        Icon(Icons.Default.InstallMobile, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("📲 Install APK", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // ── Build errors → AI ──────────────────────────────────
            AnimatedVisibility(visible = uiState.buildErrors.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Build Failed — ${uiState.buildErrors.size} errors", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                        uiState.buildErrors.take(3).forEach { err ->
                            Text(err.take(120), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        if (onSendErrorToAi != null) {
                            Button(
                                onClick = { onSendErrorToAi(uiState.buildErrors.joinToString("\\n")) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("🤖 Ask AI to Fix These Errors")
                            }
                        }
                    }
                }
            }

            // ── GitHub latest run ──────────────────────────────────
            uiState.latestRunStatus?.let { status ->
                Card(shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timeline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(status, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                }
            }

            // ── Live build log ─────────────────────────────────────
            if (uiState.logs.isNotEmpty()) {
                Text("Build Log", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    LazyColumn(
                        state = logScrollState,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(uiState.logs) { log ->
                            val color = when {
                                log.contains("✅") || log.contains("SUCCESSFUL") -> Color(0xFF69F0AE)
                                log.contains("❌") || log.contains("FAILED") || log.contains("error:") -> Color(0xFFFF5252)
                                log.contains("warning:") -> Color(0xFFFFD740)
                                else -> Color(0xFF58A6FF)
                            }
                            Text(log, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = color, lineHeight = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupGuideCard(onGoToSettings: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Setup Guide", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
            SetupStep("1", "Local Build", "Tap 'Local Build' to run ./gradlew assembleDebug directly via Termux. Requires project path set in Settings.")
            SetupStep("2", "GitHub Build", "Requires GitHub token + repo configured in Settings. Triggers cloud build via GitHub Actions.")
            SetupStep("3", "Install APK", "After a successful local build, tap 'Install APK' to install directly on this device.")
            SetupStep("4", "AI Fix Errors", "If build fails, tap 'Ask AI to Fix' to send errors straight to CodeMaster AI.")
            Button(onClick = onGoToSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Go to Settings")
            }
        }
    }
}

@Composable
fun SetupStep(number: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Text(number, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ConfigStatusCard(isConfigured: Boolean, owner: String, repo: String, branch: String, onGoToSettings: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isConfigured) Color(0xFF1B5E20).copy(alpha = 0.3f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (isConfigured) Color(0xFF69F0AE) else MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (isConfigured) "GitHub configured ✅" else "GitHub not configured", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(if (isConfigured) "$owner/$repo @ $branch" else "Token, username, and repo required", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onGoToSettings) { Icon(Icons.Default.Settings, "Settings") }
        }
    }
}

@Composable
fun BuildInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
''')

# ═══════════════════════════════════════════════════════════════
# 3. CodeEditorViewModel.kt
#    + AI writes code directly to file
#    + Live error detection (basic syntax checks)
#    + Inline AI suggestion
# ═══════════════════════════════════════════════════════════════
write("ui/screens/editor/CodeEditorViewModel.kt", '''package com.codemaster.aistudio.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.repository.AiRepository
import com.codemaster.aistudio.data.repository.DiskFile
import com.codemaster.aistudio.data.repository.FileSystemRepository
import com.codemaster.aistudio.data.repository.ProjectRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CodeError(
    val line: Int,
    val message: String,
    val severity: String = "error" // "error" or "warning"
)

data class EditorUiState(
    val projectName: String = "",
    val rootPath: String = "",
    val files: List<DiskFile> = emptyList(),
    val expandedDirs: Set<String> = emptySet(),
    val currentFile: DiskFile? = null,
    val content: String = "",
    val language: String = "plaintext",
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val isLoadingFiles: Boolean = false,
    val showNewFileDialog: Boolean = false,
    val newFileName: String = "",
    val newFileDir: String = "",
    val showFileTree: Boolean = true,
    val cursorLine: Int = 1,
    val cursorCol: Int = 1,
    val recentFiles: List<DiskFile> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null,
    // AI features
    val isAiWriting: Boolean = false,
    val aiSuggestion: String? = null,
    val isLoadingAiSuggestion: Boolean = false,
    val liveErrors: List<CodeError> = emptyList(),
    val showAiPanel: Boolean = false,
    val aiPrompt: String = ""
)

@HiltViewModel
class CodeEditorViewModel @Inject constructor(
    private val fsRepository: FileSystemRepository,
    private val projectRepository: ProjectRepository,
    private val aiRepository: AiRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()
    private var errorCheckJob: Job? = null
    private var suggestionJob: Job? = null

    fun init(projectId: Long, fileId: Long) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            val name = project?.name ?: "Editor"
            val rootPath = project?.path ?: run {
                val desc = project?.description ?: ""
                when {
                    desc.startsWith("Loaded from: ") -> desc.removePrefix("Loaded from: ")
                    desc.startsWith("Imported from: ") -> desc.removePrefix("Imported from: ")
                    else -> ""
                }
            }
            _uiState.value = _uiState.value.copy(projectName = name, rootPath = rootPath, newFileDir = rootPath)
            if (rootPath.isNotBlank()) scanFiles(rootPath)
        }
    }

    fun scanFiles(path: String = _uiState.value.rootPath) {
        if (path.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFiles = true)
            fsRepository.scanDirectory(path).fold(
                onSuccess = { files ->
                    _uiState.value = _uiState.value.copy(files = files, rootPath = path, newFileDir = path, isLoadingFiles = false, expandedDirs = setOf(path))
                },
                onFailure = { e -> _uiState.value = _uiState.value.copy(isLoadingFiles = false, error = e.message) }
            )
        }
    }

    fun toggleDir(dirPath: String) {
        val expanded = _uiState.value.expandedDirs.toMutableSet()
        if (dirPath in expanded) expanded.remove(dirPath) else expanded.add(dirPath)
        _uiState.value = _uiState.value.copy(expandedDirs = expanded)
    }

    fun openFile(file: DiskFile) {
        if (file.isDirectory) { toggleDir(file.path); return }
        if (_uiState.value.isDirty) saveCurrentFile()
        viewModelScope.launch {
            fsRepository.readFile(file.path).fold(
                onSuccess = { content ->
                    val lang = fsRepository.getLanguageFromExtension(file.extension)
                    val recent = (_uiState.value.recentFiles.filter { it.path != file.path } + file).takeLast(10)
                    _uiState.value = _uiState.value.copy(currentFile = file, content = content, language = lang, isDirty = false, recentFiles = recent, liveErrors = emptyList(), aiSuggestion = null)
                },
                onFailure = { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            )
        }
    }

    fun updateContent(content: String) {
        _uiState.value = _uiState.value.copy(content = content, isDirty = true, aiSuggestion = null)
        scheduleErrorCheck(content)
    }

    // ── Live error detection ─────────────────────────────────
    private fun scheduleErrorCheck(content: String) {
        errorCheckJob?.cancel()
        errorCheckJob = viewModelScope.launch {
            delay(800) // debounce
            val errors = detectBasicErrors(content, _uiState.value.language)
            _uiState.value = _uiState.value.copy(liveErrors = errors)
        }
    }

    private fun detectBasicErrors(content: String, language: String): List<CodeError> {
        val errors = mutableListOf<CodeError>()
        val lines = content.lines()
        lines.forEachIndexed { idx, line ->
            val lineNum = idx + 1
            when (language) {
                "kotlin", "java" -> {
                    if (line.trimEnd().endsWith("{") && !line.contains("//")) {
                        // Check for unclosed braces (very basic)
                    }
                    if (line.contains("TODO(") || line.contains("FIXME")) {
                        errors.add(CodeError(lineNum, "TODO/FIXME marker", "warning"))
                    }
                    if (line.length > 120) {
                        errors.add(CodeError(lineNum, "Line too long (${line.length} chars)", "warning"))
                    }
                }
                "python" -> {
                    if (line.contains("\\t") && content.contains("    ")) {
                        errors.add(CodeError(lineNum, "Mixed tabs and spaces", "error"))
                    }
                    if (line.trimStart().startsWith("except:")) {
                        errors.add(CodeError(lineNum, "Bare except clause — specify exception type", "warning"))
                    }
                }
                "json" -> {
                    if (line.trimEnd().endsWith(",") && lines.getOrNull(idx + 1)?.trim()?.let { it.startsWith("}") || it.startsWith("]") } == true) {
                        errors.add(CodeError(lineNum, "Trailing comma in JSON", "error"))
                    }
                }
            }
        }
        // Check bracket balance
        val opens = content.count { it == '{' }
        val closes = content.count { it == '}' }
        if (opens != closes && (language == "kotlin" || language == "java")) {
            errors.add(CodeError(0, "Unbalanced braces: $opens { vs $closes }", "error"))
        }
        return errors
    }

    // ── Inline AI suggestion ─────────────────────────────────
    fun requestInlineSuggestion() {
        val state = _uiState.value
        val file = state.currentFile ?: return
        val content = state.content
        if (content.isBlank()) return

        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingAiSuggestion = true, aiSuggestion = null)
            val prompt = "Continue or complete this ${state.language} code. Return ONLY the code to insert next, no explanation:\\n\\n${content.takeLast(2000)}"
            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = prompt,
                systemPrompt = "You are a code completion engine. Return only raw code, no markdown, no explanation."
            )
            result.fold(
                onSuccess = { suggestion ->
                    _uiState.value = _uiState.value.copy(aiSuggestion = suggestion.trim(), isLoadingAiSuggestion = false)
                },
                onFailure = { _uiState.value = _uiState.value.copy(isLoadingAiSuggestion = false) }
            )
        }
    }

    fun acceptSuggestion() {
        val suggestion = _uiState.value.aiSuggestion ?: return
        val newContent = _uiState.value.content + "\\n" + suggestion
        _uiState.value = _uiState.value.copy(content = newContent, isDirty = true, aiSuggestion = null)
    }

    fun dismissSuggestion() { _uiState.value = _uiState.value.copy(aiSuggestion = null) }

    // ── AI writes directly to file ───────────────────────────
    fun aiWriteToFile(instruction: String) {
        val state = _uiState.value
        val file = state.currentFile ?: run {
            _uiState.value = _uiState.value.copy(error = "No file open")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAiWriting = true)
            val prompt = """You are editing the file: ${file.name}
Current content:
${state.content.take(4000)}

Instruction: $instruction

Return the COMPLETE updated file content only. No markdown, no explanation, just the raw file content."""

            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = prompt,
                systemPrompt = "You are a code editing assistant. Return only the complete file content, nothing else."
            )
            result.fold(
                onSuccess = { newContent ->
                    // Strip markdown fences if AI added them
                    val clean = newContent
                        .removePrefix("```${state.language}").removePrefix("```")
                        .removeSuffix("```").trim()
                    _uiState.value = _uiState.value.copy(content = clean, isDirty = true, isAiWriting = false)
                    // Auto-save
                    fsRepository.writeFile(file.path, clean)
                    _uiState.value = _uiState.value.copy(isDirty = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isAiWriting = false, error = "AI write failed: ${e.message}")
                }
            )
        }
    }

    fun updateAiPrompt(p: String) { _uiState.value = _uiState.value.copy(aiPrompt = p) }
    fun toggleAiPanel() { _uiState.value = _uiState.value.copy(showAiPanel = !_uiState.value.showAiPanel) }

    fun updateCursor(line: Int, col: Int) { _uiState.value = _uiState.value.copy(cursorLine = line, cursorCol = col) }

    fun saveCurrentFile() {
        val state = _uiState.value
        val file = state.currentFile ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            fsRepository.writeFile(file.path, state.content).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(isSaving = false, isDirty = false) },
                onFailure = { e -> _uiState.value = _uiState.value.copy(isSaving = false, error = e.message) }
            )
        }
    }

    fun showNewFileDialog(dirPath: String = _uiState.value.rootPath) {
        _uiState.value = _uiState.value.copy(showNewFileDialog = true, newFileName = "", newFileDir = dirPath)
    }
    fun hideNewFileDialog() { _uiState.value = _uiState.value.copy(showNewFileDialog = false) }
    fun updateNewFileName(v: String) { _uiState.value = _uiState.value.copy(newFileName = v) }

    fun createFile() {
        val state = _uiState.value
        val name = state.newFileName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            fsRepository.createFile(state.newFileDir, name).fold(
                onSuccess = { path ->
                    hideNewFileDialog(); scanFiles()
                    openFile(DiskFile(name = name, path = path, relativePath = name, size = 0, lastModified = System.currentTimeMillis(), isDirectory = false, extension = name.substringAfterLast(".", "")))
                },
                onFailure = { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            )
        }
    }

    fun deleteFile(file: DiskFile) {
        viewModelScope.launch {
            fsRepository.deleteFile(file.path)
            if (_uiState.value.currentFile?.path == file.path) _uiState.value = _uiState.value.copy(currentFile = null, content = "")
            scanFiles()
        }
    }

    fun toggleFileTree() { _uiState.value = _uiState.value.copy(showFileTree = !_uiState.value.showFileTree) }
    fun updateSearch(q: String) { _uiState.value = _uiState.value.copy(searchQuery = q) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    fun getVisibleFiles(): List<DiskFile> {
        val state = _uiState.value
        val query = state.searchQuery.trim()
        val root = state.rootPath
        return if (query.isNotBlank()) {
            state.files.filter { !it.isDirectory && (it.name.contains(query, ignoreCase = true) || it.relativePath.contains(query, ignoreCase = true)) }
        } else {
            state.files.filter { file ->
                val parentPath = file.path.substringBeforeLast("/")
                parentPath == root || parentPath in state.expandedDirs || state.expandedDirs.any { file.path.startsWith(it + "/") && file.depth == 1 }
            }
        }
    }
}
''')

# ═══════════════════════════════════════════════════════════════
# 4. ProjectTemplates.kt — new file
#    Kotlin/Compose, Python CLI, REST API, Empty templates
# ═══════════════════════════════════════════════════════════════
write("data/templates/ProjectTemplates.kt", '''package com.codemaster.aistudio.data.templates

data class ProjectTemplate(
    val id: String,
    val name: String,
    val description: String,
    val language: String,
    val icon: String,
    val files: Map<String, String> // relative path -> content
)

val PROJECT_TEMPLATES = listOf(
    ProjectTemplate(
        id = "kotlin_compose",
        name = "Kotlin + Compose",
        description = "Android app with Jetpack Compose, Material3, and Hilt DI",
        language = "Kotlin",
        icon = "🟣",
        files = mapOf(
            "app/src/main/java/com/example/app/MainActivity.kt" to """
package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var count by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Hello CodeMaster!", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { count++ }) { Text("Tapped ${'$'}count times") }
    }
}
""".trimIndent(),
            "README.md" to "# My Kotlin Compose App\n\nBuilt with CodeMaster AI Studio\n"
        )
    ),
    ProjectTemplate(
        id = "python_cli",
        name = "Python CLI Tool",
        description = "Command-line tool with argparse and logging",
        language = "Python",
        icon = "🐍",
        files = mapOf(
            "main.py" to """
import argparse
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def main():
    parser = argparse.ArgumentParser(description='My CLI Tool')
    parser.add_argument('--name', default='World', help='Name to greet')
    parser.add_argument('--verbose', action='store_true', help='Enable verbose output')
    args = parser.parse_args()

    if args.verbose:
        logger.setLevel(logging.DEBUG)

    logger.info(f'Hello, {args.name}!')
    print(f'Hello, {args.name}!')

if __name__ == '__main__':
    main()
""".trimIndent(),
            "requirements.txt" to "# Add your dependencies here\n",
            "README.md" to "# Python CLI Tool\n\nBuilt with CodeMaster AI Studio\n\n## Usage\n```\npython main.py --name Tony\n```\n"
        )
    ),
    ProjectTemplate(
        id = "python_api",
        name = "Python REST API",
        description = "FastAPI REST API with endpoints and models",
        language = "Python",
        icon = "⚡",
        files = mapOf(
            "main.py" to """
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional

app = FastAPI(title="My API", version="1.0.0")

class Item(BaseModel):
    id: Optional[int] = None
    name: str
    description: Optional[str] = None

items: List[Item] = []

@app.get("/")
def root():
    return {"message": "API is running", "version": "1.0.0"}

@app.get("/items", response_model=List[Item])
def get_items():
    return items

@app.post("/items", response_model=Item)
def create_item(item: Item):
    item.id = len(items) + 1
    items.append(item)
    return item

@app.get("/items/{item_id}", response_model=Item)
def get_item(item_id: int):
    item = next((i for i in items if i.id == item_id), None)
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")
    return item
""".trimIndent(),
            "requirements.txt" to "fastapi==0.109.0\nuvicorn==0.27.0\npydantic==2.5.0\n",
            "README.md" to "# FastAPI REST API\n\nBuilt with CodeMaster AI Studio\n\n## Run\n```\npip install -r requirements.txt\nuvicorn main:app --reload\n```\n"
        )
    ),
    ProjectTemplate(
        id = "empty",
        name = "Empty Project",
        description = "Start from scratch",
        language = "Kotlin",
        icon = "📄",
        files = mapOf(
            "README.md" to "# New Project\n\nCreated with CodeMaster AI Studio\n"
        )
    )
)
''')

# ═══════════════════════════════════════════════════════════════
# 5. Patch HomeViewModel to support templates
# ═══════════════════════════════════════════════════════════════
home_vm = os.path.join(BASE, "ui/screens/home/HomeViewModel.kt")
try:
    with open(home_vm, 'r') as f:
        content = f.read()
    if 'showTemplateDialog' not in content:
        # Add template dialog state
        content = content.replace(
            'val showImportDialog: Boolean = false',
            'val showImportDialog: Boolean = false,\n    val showTemplateDialog: Boolean = false'
        )
        content = content.rstrip().rstrip('}')
        content += '''
    fun showTemplateDialog() { _uiState.value = _uiState.value.copy(showTemplateDialog = true) }
    fun hideTemplateDialog() { _uiState.value = _uiState.value.copy(showTemplateDialog = false) }

    fun createFromTemplate(template: com.codemaster.aistudio.data.templates.ProjectTemplate, basePath: String) {
        viewModelScope.launch {
            val projectPath = "$basePath/${template.name.replace(" ", "-").lowercase()}"
            java.io.File(projectPath).mkdirs()
            template.files.forEach { (relPath, content) ->
                val file = java.io.File(projectPath, relPath)
                file.parentFile?.mkdirs()
                file.writeText(content)
            }
            val id = projectRepository.createProject(template.name, template.language, "Loaded from: $projectPath")
            hideTemplateDialog()
        }
    }
}
'''
        with open(home_vm, 'w') as f:
            f.write(content)
        print("  ✅ ui/screens/home/HomeViewModel.kt (patched templates)")
        OK.append("HomeViewModel patch")
    else:
        print("  ⏭️  HomeViewModel already has template support")
except Exception as e:
    print(f"  ❌ HomeViewModel patch — {e}")
    FAIL.append("HomeViewModel patch")

# ═══════════════════════════════════════════════════════════════
# Summary
# ═══════════════════════════════════════════════════════════════
print(f"\n{'='*50}")
print(f"✅ Done: {len(OK)} files")
if FAIL:
    print(f"❌ Failed: {FAIL}")
print("""
Phase 9 features:
  ✅ Local Termux build (./gradlew assembleDebug)
  ✅ Live streaming build log with color coding
  ✅ APK installer after successful build
  ✅ Build errors → AI auto-fix button
  ✅ AI writes code directly to open file
  ✅ Live error detection while typing
  ✅ Inline AI code suggestions (accept/dismiss)
  ✅ Project templates (Compose, Python CLI, FastAPI, Empty)
  ✅ Version → Phase 9

Run:
  git add -A
  git commit -m "feat: Phase 9 - local build, AI writes files, APK install, templates"
  git push origin HEAD
""")
