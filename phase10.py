import os

BASE = "app/src/main/java/com/codemaster/aistudio"
OK = []; FAIL = []

def write(rel, content):
    path = os.path.join(BASE, rel)
    try:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w') as f:
            f.write(content)
        print(f"  OK {rel}")
        OK.append(rel)
    except Exception as e:
        print(f"  FAIL {rel} - {e}")
        FAIL.append(rel)

def patch(rel, old, new):
    path = os.path.join(BASE, rel)
    try:
        with open(path) as f:
            content = f.read()
        if old in content:
            with open(path, 'w') as f:
                f.write(content.replace(old, new))
            print(f"  PATCHED {rel}")
            OK.append(f"patch:{rel}")
        else:
            print(f"  SKIP {rel} (already patched or not found)")
    except Exception as e:
        print(f"  FAIL patch {rel} - {e}")
        FAIL.append(f"patch:{rel}")

print("\n PHASE 10 - The Ultimate Polish\n")

# ═══════════════════════════════════════════════════════════
# 1. FileBrowserDialog - custom folder picker
# ═══════════════════════════════════════════════════════════
write("ui/components/FileBrowser.kt", '''package com.codemaster.aistudio.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserDialog(
    startPath: String = "/sdcard",
    onPathSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPath by remember { mutableStateOf(startPath) }
    var entries by remember { mutableStateOf(listOf<File>()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentPath) {
        try {
            val dir = File(currentPath)
            entries = dir.listFiles()
                ?.filter { !it.name.startsWith(".") }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList()
            error = null
        } catch (e: Exception) {
            error = e.message
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Browse", fontWeight = FontWeight.Bold)
                Text(
                    currentPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                // Quick paths
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "SDCard" to "/sdcard",
                        "Termux" to "/data/data/com.termux/files/home",
                        "Up" to File(currentPath).parent
                    ).forEach { (label, path) ->
                        if (path != null) {
                            SuggestionChip(
                                onClick = { currentPath = path },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                if (error != null) {
                    Text("Cannot read: $error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(entries) { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (file.isDirectory) currentPath = file.absolutePath
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    null,
                                    tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (file.isDirectory) {
                                    Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPathSelected(currentPath) }) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Select This Folder")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(16.dp)
    )
}
''')

# ═══════════════════════════════════════════════════════════
# 2. HomeViewModel - add fileBrowser + AI app builder state
# ═══════════════════════════════════════════════════════════
write("ui/screens/home/HomeViewModel.kt", '''package com.codemaster.aistudio.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.Project
import com.codemaster.aistudio.data.repository.AiRepository
import com.codemaster.aistudio.data.repository.FileSystemRepository
import com.codemaster.aistudio.data.repository.ProjectRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showNewProjectDialog: Boolean = false,
    val newProjectName: String = "",
    val newProjectLanguage: String = "Kotlin",
    val newProjectDescription: String = "",
    val showImportDialog: Boolean = false,
    val showFileBrowserDialog: Boolean = false,
    val importPath: String = "",
    // AI App Builder
    val showAiBuilderDialog: Boolean = false,
    val aiBuilderPrompt: String = "",
    val aiBuilderLanguage: String = "Kotlin",
    val isAiBuilding: Boolean = false,
    val aiBuilderResult: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val fsRepository: FileSystemRepository,
    private val aiRepository: AiRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepository.getAllProjects()
                .catch { e -> _uiState.value = _uiState.value.copy(error = e.message, isLoading = false) }
                .collect { projects -> _uiState.value = _uiState.value.copy(projects = projects, isLoading = false) }
        }
    }

    fun showNewProjectDialog() { _uiState.value = _uiState.value.copy(showNewProjectDialog = true, newProjectName = "", newProjectLanguage = "Kotlin", newProjectDescription = "") }
    fun hideNewProjectDialog() { _uiState.value = _uiState.value.copy(showNewProjectDialog = false) }
    fun updateProjectName(n: String) { _uiState.value = _uiState.value.copy(newProjectName = n) }
    fun updateProjectLanguage(l: String) { _uiState.value = _uiState.value.copy(newProjectLanguage = l) }
    fun updateProjectDescription(d: String) { _uiState.value = _uiState.value.copy(newProjectDescription = d) }
    fun showImportDialog() { _uiState.value = _uiState.value.copy(showImportDialog = true) }
    fun hideImportDialog() { _uiState.value = _uiState.value.copy(showImportDialog = false) }
    fun showFileBrowserDialog() { _uiState.value = _uiState.value.copy(showFileBrowserDialog = true) }
    fun hideFileBrowserDialog() { _uiState.value = _uiState.value.copy(showFileBrowserDialog = false) }
    fun updateImportPath(p: String) { _uiState.value = _uiState.value.copy(importPath = p) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    // AI App Builder
    fun showAiBuilder() { _uiState.value = _uiState.value.copy(showAiBuilderDialog = true, aiBuilderPrompt = "", aiBuilderResult = null) }
    fun hideAiBuilder() { _uiState.value = _uiState.value.copy(showAiBuilderDialog = false) }
    fun updateAiBuilderPrompt(p: String) { _uiState.value = _uiState.value.copy(aiBuilderPrompt = p) }
    fun updateAiBuilderLanguage(l: String) { _uiState.value = _uiState.value.copy(aiBuilderLanguage = l) }

    fun buildAppWithAi(onCreated: (Long) -> Unit) {
        val state = _uiState.value
        if (state.aiBuilderPrompt.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAiBuilding = true)
            val prompt = """You are an expert ${state.aiBuilderLanguage} developer.
Create a complete, working project for: ${state.aiBuilderPrompt}

Respond with a JSON object like this:
{
  "projectName": "MyApp",
  "description": "Brief description",
  "files": {
    "MainActivity.kt": "full file content here",
    "README.md": "content here"
  }
}

Return ONLY valid JSON, no markdown, no explanation."""

            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = prompt,
                systemPrompt = "You are a code generation engine. Return only valid JSON."
            )
            result.fold(
                onSuccess = { response ->
                    try {
                        // Parse the JSON response
                        val clean = response.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        val nameMatch = Regex("\"projectName\"\\s*:\\s*\"([^\"]+)\"").find(clean)
                        val descMatch = Regex("\"description\"\\s*:\\s*\"([^\"]+)\"").find(clean)
                        val projectName = nameMatch?.groupValues?.get(1) ?: "AI Generated App"
                        val description = descMatch?.groupValues?.get(1) ?: state.aiBuilderPrompt

                        // Create project
                        val id = projectRepository.createProject(projectName, state.aiBuilderLanguage, description)

                        // Create files on sdcard
                        val projectPath = "/sdcard/$projectName"
                        File(projectPath).mkdirs()

                        // Extract files from JSON
                        val filesMatch = Regex("\"files\"\\s*:\\s*\\{(.+)\\}", RegexOption.DOT_MATCHES_ALL).find(clean)
                        filesMatch?.let { fm ->
                            val filesContent = fm.groupValues[1]
                            val filePattern = Regex("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                            filePattern.findAll(filesContent).forEach { fileMatch ->
                                val fileName = fileMatch.groupValues[1]
                                val fileContent = fileMatch.groupValues[2]
                                    .replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"")
                                File(projectPath, fileName).apply {
                                    parentFile?.mkdirs()
                                    writeText(fileContent)
                                }
                            }
                        }

                        // Update project path
                        projectRepository.getProjectById(id)?.let {
                            projectRepository.updateProject(it.copy(path = projectPath, description = "Loaded from: $projectPath"))
                        }

                        _uiState.value = _uiState.value.copy(isAiBuilding = false, aiBuilderResult = "Created $projectName at $projectPath")
                        hideAiBuilder()
                        onCreated(id)
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(isAiBuilding = false, error = "AI Builder failed: ${e.message}")
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isAiBuilding = false, error = "AI error: ${e.message}")
                }
            )
        }
    }

    fun createProject(onCreated: (Long) -> Unit) {
        val state = _uiState.value
        if (state.newProjectName.isBlank()) return
        viewModelScope.launch {
            val id = projectRepository.createProject(state.newProjectName, state.newProjectLanguage, state.newProjectDescription)
            hideNewProjectDialog()
            onCreated(id)
        }
    }

    fun importProject(onImported: (Long) -> Unit) {
        val path = _uiState.value.importPath.trim()
        if (path.isBlank()) return
        viewModelScope.launch {
            val name = path.substringAfterLast("/").ifBlank { "Imported Project" }
            val id = projectRepository.createProject(name, "Kotlin", "Loaded from: $path")
            projectRepository.getProjectById(id)?.let {
                projectRepository.updateProject(it.copy(path = path))
            }
            hideImportDialog()
            onImported(id)
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch { projectRepository.deleteProject(project) }
    }
}
''')

# ═══════════════════════════════════════════════════════════
# 3. HomeScreen - file browser + AI builder + polish
# ═══════════════════════════════════════════════════════════
write("ui/screens/home/HomeScreen.kt", '''package com.codemaster.aistudio.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codemaster.aistudio.data.model.Project
import com.codemaster.aistudio.ui.components.FileBrowserDialog
import java.text.SimpleDateFormat
import java.util.*

val LANGUAGES = listOf("Kotlin","Java","Python","JavaScript","TypeScript","Rust","Go","C++","Swift","Dart","PHP","Ruby")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenChat: (Long) -> Unit,
    onOpenEditor: (Long) -> Unit,
    onOpenBuild: (Long) -> Unit,
    onOpenTerminal: (Long) -> Unit,
    onOpenGit: (Long) -> Unit,
    onOpenSnippets: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("CodeMaster AI", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSnippets) { Icon(Icons.Default.Bookmark, "Snippets") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // AI App Builder FAB
                SmallFloatingActionButton(
                    onClick = { viewModel.showAiBuilder() },
                    containerColor = MaterialTheme.colorScheme.tertiary
                ) {
                    Icon(Icons.Default.AutoAwesome, "AI Build", tint = MaterialTheme.colorScheme.onTertiary)
                }
                SmallFloatingActionButton(
                    onClick = { viewModel.showImportDialog() },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.FolderOpen, "Load project", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                ExtendedFloatingActionButton(
                    onClick = { viewModel.showNewProjectDialog() },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("New Project") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick Actions - ALWAYS visible
                item {
                    QuickActionsBar(
                        onBrowseFiles = { viewModel.showFileBrowserDialog() },
                        onOpenChat = { uiState.projects.firstOrNull()?.let { onOpenChat(it.id) } },
                        onAiBuilder = { viewModel.showAiBuilder() }
                    )
                }

                if (uiState.isLoading) {
                    item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else if (uiState.projects.isEmpty()) {
                    item { EmptyProjectsPlaceholder() }
                } else {
                    item {
                        Text("Projects (${uiState.projects.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(uiState.projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onOpenChat = { onOpenChat(project.id) },
                            onOpenEditor = { onOpenEditor(project.id) },
                            onOpenBuild = { onOpenBuild(project.id) },
                            onOpenTerminal = { onOpenTerminal(project.id) },
                            onOpenGit = { onOpenGit(project.id) },
                            onDelete = { viewModel.deleteProject(project) }
                        )
                    }
                }
                item { Spacer(Modifier.height(100.dp)) }
            }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") } }
                ) { Text(error) }
            }
        }

        // Dialogs
        if (uiState.showNewProjectDialog) {
            NewProjectDialog(
                name = uiState.newProjectName, language = uiState.newProjectLanguage,
                description = uiState.newProjectDescription,
                onNameChange = viewModel::updateProjectName,
                onLanguageChange = viewModel::updateProjectLanguage,
                onDescriptionChange = viewModel::updateProjectDescription,
                onCreate = { viewModel.createProject { id -> onOpenEditor(id) } },
                onDismiss = viewModel::hideNewProjectDialog
            )
        }

        if (uiState.showImportDialog) {
            ImportProjectDialog(
                path = uiState.importPath,
                onPathChange = viewModel::updateImportPath,
                onBrowse = { viewModel.showFileBrowserDialog() },
                onImport = { viewModel.importProject { id -> onOpenEditor(id) } },
                onDismiss = viewModel::hideImportDialog
            )
        }

        if (uiState.showFileBrowserDialog) {
            FileBrowserDialog(
                startPath = "/sdcard",
                onPathSelected = { path ->
                    viewModel.updateImportPath(path)
                    viewModel.hideFileBrowserDialog()
                    viewModel.showImportDialog()
                },
                onDismiss = { viewModel.hideFileBrowserDialog() }
            )
        }

        if (uiState.showAiBuilderDialog) {
            AiAppBuilderDialog(
                prompt = uiState.aiBuilderPrompt,
                language = uiState.aiBuilderLanguage,
                isBuilding = uiState.isAiBuilding,
                onPromptChange = viewModel::updateAiBuilderPrompt,
                onLanguageChange = viewModel::updateAiBuilderLanguage,
                onBuild = { viewModel.buildAppWithAi { id -> onOpenEditor(id) } },
                onDismiss = viewModel::hideAiBuilder
            )
        }
    }
}

@Composable
fun QuickActionsBar(
    onBrowseFiles: () -> Unit,
    onOpenChat: () -> Unit,
    onAiBuilder: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onOpenChat,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("AI Chat", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onBrowseFiles,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Browse", fontWeight = FontWeight.Bold)
            }
        }
        // AI App Builder button - full width
        Button(
            onClick = onAiBuilder,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Icon(Icons.Default.Build, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Build App with AI", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAppBuilderDialog(
    prompt: String,
    language: String,
    isBuilding: Boolean,
    onPromptChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onBuild: () -> Unit,
    onDismiss: () -> Unit
) {
    var langExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!isBuilding) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("AI App Builder", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Describe the app you want to build and AI will generate the complete project.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    label = { Text("Describe your app") },
                    placeholder = { Text("e.g. A todo list app with categories and due dates") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3, maxLines = 6,
                    enabled = !isBuilding
                )
                ExposedDropdownMenuBox(expanded = langExpanded, onExpandedChange = { langExpanded = it }) {
                    OutlinedTextField(
                        value = language, onValueChange = {}, readOnly = true,
                        label = { Text("Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        enabled = !isBuilding
                    )
                    ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        LANGUAGES.forEach { lang ->
                            DropdownMenuItem(text = { Text(lang) }, onClick = { onLanguageChange(lang); langExpanded = false })
                        }
                    }
                }
                if (isBuilding) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("AI is building your app...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onBuild, enabled = prompt.isNotBlank() && !isBuilding) {
                if (isBuilding) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Build It")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isBuilding) { Text("Cancel") } }
    )
}

@Composable
fun ImportProjectDialog(
    path: String,
    onPathChange: (String) -> Unit,
    onBrowse: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Load Project", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter path or tap Browse to navigate:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = path, onValueChange = onPathChange,
                    label = { Text("Folder path") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 3,
                    placeholder = { Text("/sdcard/my-project") },
                    trailingIcon = {
                        IconButton(onClick = onBrowse) {
                            Icon(Icons.Default.FolderOpen, "Browse", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
                Text("Quick paths:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    "codemaster-ai-studio" to (android.os.Environment.getExternalStorageDirectory().absolutePath + "/codemaster-ai-studio"),
                    "Termux home" to "/data/data/com.termux/files/home",
                    "SDCard" to android.os.Environment.getExternalStorageDirectory().absolutePath
                ).forEach { (label, quickPath) ->
                    SuggestionChip(onClick = { onPathChange(quickPath) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                }
            }
        },
        confirmButton = {
            Button(onClick = onImport, enabled = path.isNotBlank()) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Load")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ProjectCard(
    project: Project,
    onOpenChat: () -> Unit, onOpenEditor: () -> Unit, onOpenBuild: () -> Unit,
    onOpenTerminal: () -> Unit, onOpenGit: () -> Unit, onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
                    contentAlignment = Alignment.Center
                ) { Text(project.language.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(project.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${project.language} - ${dateFormat.format(Date(project.lastModified))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (project.path.isNotBlank()) {
                        Text(project.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
            if (project.description.isNotBlank() && !project.description.startsWith("Loaded from:")) {
                Spacer(Modifier.height(8.dp))
                Text(project.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ProjectActionButton(Icons.Default.AutoAwesome, "Chat", onOpenChat)
                ProjectActionButton(Icons.Default.Code, "Editor", onOpenEditor)
                ProjectActionButton(Icons.Default.Terminal, "Terminal", onOpenTerminal)
                ProjectActionButton(Icons.Default.AccountTree, "Git", onOpenGit)
                ProjectActionButton(Icons.Default.Build, "Build", onOpenBuild)
            }
        }
    }
}

@Composable
fun ProjectActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun EmptyProjectsPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))
        Text("No projects yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text("Browse to load, + to create, or let AI build one for you", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectDialog(
    name: String, language: String, description: String,
    onNameChange: (String) -> Unit, onLanguageChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit, onCreate: () -> Unit, onDismiss: () -> Unit
) {
    var langExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Project", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("Project Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                ExposedDropdownMenuBox(expanded = langExpanded, onExpandedChange = { langExpanded = it }) {
                    OutlinedTextField(value = language, onValueChange = {}, readOnly = true, label = { Text("Language") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        LANGUAGES.forEach { lang -> DropdownMenuItem(text = { Text(lang) }, onClick = { onLanguageChange(lang); langExpanded = false }) }
                    }
                }
                OutlinedTextField(value = description, onValueChange = onDescriptionChange, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3)
            }
        },
        confirmButton = { Button(onClick = onCreate, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
''')

# ═══════════════════════════════════════════════════════════
# 4. GitScreen - one-tap commit + push, branch info
# ═══════════════════════════════════════════════════════════
write("ui/screens/git/GitScreen.kt", '''package com.codemaster.aistudio.ui.screens.git

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
''')

# ═══════════════════════════════════════════════════════════
# 5. GitViewModel - add commitAndPush, pull, push, stageAll
# ═══════════════════════════════════════════════════════════
write("ui/screens/git/GitViewModel.kt", '''package com.codemaster.aistudio.ui.screens.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.ProjectRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class GitUiState(
    val projectPath: String = "",
    val currentBranch: String = "",
    val status: String = "",
    val log: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GitViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitUiState())
    val uiState: StateFlow<GitUiState> = _uiState.asStateFlow()

    fun init(projectId: Long) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            val path = project?.path ?: return@launch
            _uiState.value = _uiState.value.copy(projectPath = path)
            refresh()
        }
    }

    fun refresh() {
        val path = _uiState.value.projectPath
        if (path.isBlank()) return
        viewModelScope.launch {
            val branch = runGit(path, listOf("git", "branch", "--show-current")).trim()
            val status = runGit(path, listOf("git", "status", "--short"))
            _uiState.value = _uiState.value.copy(currentBranch = branch, status = status.ifBlank { "Working tree clean" })
        }
    }

    fun stageAll() {
        runGitCommand(listOf("git", "add", "-A"), "Staged all changes")
    }

    fun commitAndPush(message: String) {
        val path = _uiState.value.projectPath
        if (path.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            addLog("Staging all changes...")
            runGit(path, listOf("git", "add", "-A"))
            addLog("Committing: $message")
            val commitOut = runGit(path, listOf("git", "commit", "-m", message))
            addLog(commitOut.trim())
            addLog("Pushing to origin...")
            val pushOut = runGit(path, listOf("git", "push", "origin", "HEAD"))
            addLog(pushOut.trim())
            addLog("Done!")
            _uiState.value = _uiState.value.copy(isLoading = false)
            refresh()
        }
    }

    fun push() = runGitCommand(listOf("git", "push", "origin", "HEAD"), "Pushing...")
    fun pull() = runGitCommand(listOf("git", "pull"), "Pulling...")

    private fun runGitCommand(cmd: List<String>, label: String) {
        val path = _uiState.value.projectPath
        if (path.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            addLog(label)
            val out = runGit(path, cmd)
            addLog(out.trim().ifBlank { "Done" })
            _uiState.value = _uiState.value.copy(isLoading = false)
            refresh()
        }
    }

    private suspend fun runGit(path: String, cmd: List<String>): String = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(cmd).directory(File(path)).redirectErrorStream(true).start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun addLog(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logs = (_uiState.value.log + "[$ts] $msg").takeLast(30)
        _uiState.value = _uiState.value.copy(log = logs)
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
''')

print(f"\n{'='*55}")
print(f"OK: {len(OK)}   FAIL: {len(FAIL)}")
if FAIL: print(f"Failed: {FAIL}")
print("""
Phase 10 features:
  Custom folder browser (navigates real filesystem)
  AI App Builder (describe -> AI generates full project)
  One-tap Commit & Push in Git screen
  Commit prefix chips (feat/fix/chore/docs)
  Pull + Push + Stage All buttons
  Git log output with color coding
  Browse button opens real folder navigator
  Quick Actions bar always visible on home screen
  Build App with AI button on home screen

Run:
  git add -A
  git commit -m "feat: Phase 10 - file browser, AI app builder, git one-tap"
  git push origin HEAD
""")
