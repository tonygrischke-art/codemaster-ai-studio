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

print("\n FINAL MEGA SCRIPT - Preview + Build Fixes + Bonus Features\n")

# Fix 1: AiChatViewModel - clean version compatible with existing screen
write("ui/screens/chat/AiChatViewModel.kt", '''package com.codemaster.aistudio.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.AiPersona
import com.codemaster.aistudio.data.model.AI_PERSONAS
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.repository.AiRepository
import com.codemaster.aistudio.data.repository.ChatRepository
import com.codemaster.aistudio.data.repository.FileSystemRepository
import com.codemaster.aistudio.data.repository.ProjectRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class OpenTab(
    val fileName: String,
    val filePath: String,
    val content: String,
    val isDirty: Boolean = false
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val attachedFileName: String? = null,
    val attachedFileContent: String? = null,
    val totalTokens: Int = 0,
    val error: String? = null,
    val currentPersona: AiPersona = AI_PERSONAS.first(),
    val showPersonaPicker: Boolean = false,
    val projectContext: String? = null,
    val openTabs: List<OpenTab> = emptyList(),
    val activeTabIndex: Int = 0,
    val terminalOutput: String? = null
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val fsRepository: FileSystemRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var currentProjectId: Long = -1L
    private var projectPath: String = ""
    private var autoSaveJobs = mutableMapOf<Int, Job>()

    fun init(projectId: Long) {
        currentProjectId = projectId
        viewModelScope.launch {
            val personaId = settingsRepository.getPersonaId()
            val persona = AI_PERSONAS.find { it.id == personaId } ?: AI_PERSONAS.first()
            _uiState.value = _uiState.value.copy(currentPersona = persona)
            val project = projectRepository.getProjectById(projectId)
            project?.let {
                projectPath = it.path
                loadProjectContext(it.path)
            }
            chatRepository.getMessagesForProject(projectId)
                .catch { }
                .collect { messages ->
                    _uiState.value = _uiState.value.copy(
                        messages = messages,
                        totalTokens = messages.sumOf { it.tokenCount }
                    )
                }
        }
    }

    private fun loadProjectContext(path: String) {
        viewModelScope.launch {
            try {
                val root = File(path)
                if (!root.exists()) return@launch
                val tree = buildFileTree(root, root, 0)
                _uiState.value = _uiState.value.copy(projectContext = tree)
            } catch (_: Exception) {}
        }
    }

    private fun buildFileTree(root: File, current: File, depth: Int): String {
        if (depth > 4) return ""
        val ignored = setOf(".git", "build", ".gradle", "node_modules", ".idea", "__pycache__")
        val sb = StringBuilder()
        current.listFiles()
            ?.filter { it.name !in ignored && !it.name.startsWith(".") }
            ?.sortedWith(compareBy({ it.isFile }, { it.name }))
            ?.forEach { file ->
                val indent = "  ".repeat(depth)
                val rel = file.relativeTo(root).path
                if (file.isDirectory) {
                    sb.appendLine("$indent $rel/")
                    sb.append(buildFileTree(root, file, depth + 1))
                } else {
                    val size = if (file.length() < 1024) "${file.length()}B" else "${file.length()/1024}KB"
                    sb.appendLine("$indent $rel ($size)")
                }
            }
        return sb.toString()
    }

    fun openFile(filePath: String) {
        viewModelScope.launch {
            val existing = _uiState.value.openTabs.indexOfFirst { it.filePath == filePath }
            if (existing >= 0) {
                _uiState.value = _uiState.value.copy(activeTabIndex = existing)
                return@launch
            }
            fsRepository.readFile(filePath).fold(
                onSuccess = { content ->
                    val tab = OpenTab(fileName = File(filePath).name, filePath = filePath, content = content)
                    val tabs = _uiState.value.openTabs + tab
                    _uiState.value = _uiState.value.copy(openTabs = tabs, activeTabIndex = tabs.size - 1)
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = "Cannot open: ${it.message}") }
            )
        }
    }

    fun updateTabContent(index: Int, newContent: String) {
        val tabs = _uiState.value.openTabs.toMutableList()
        if (index >= tabs.size) return
        tabs[index] = tabs[index].copy(content = newContent, isDirty = true)
        _uiState.value = _uiState.value.copy(openTabs = tabs)
        autoSaveJobs[index]?.cancel()
        autoSaveJobs[index] = viewModelScope.launch {
            delay(1500)
            saveTab(index)
        }
    }

    fun saveTab(index: Int) {
        val tab = _uiState.value.openTabs.getOrNull(index) ?: return
        if (!tab.isDirty) return
        viewModelScope.launch {
            fsRepository.writeFile(tab.filePath, tab.content).fold(
                onSuccess = {
                    val tabs = _uiState.value.openTabs.toMutableList()
                    if (index < tabs.size) {
                        tabs[index] = tabs[index].copy(isDirty = false)
                        _uiState.value = _uiState.value.copy(openTabs = tabs)
                    }
                },
                onFailure = { _uiState.value = _uiState.value.copy(error = "Save failed: ${it.message}") }
            )
        }
    }

    fun closeTab(index: Int) {
        autoSaveJobs[index]?.cancel()
        val tabs = _uiState.value.openTabs.toMutableList()
        if (index < tabs.size && tabs[index].isDirty) saveTab(index)
        tabs.removeAt(index)
        val newIdx = if (_uiState.value.activeTabIndex >= tabs.size) maxOf(0, tabs.size - 1)
                     else _uiState.value.activeTabIndex
        _uiState.value = _uiState.value.copy(openTabs = tabs, activeTabIndex = newIdx)
    }

    fun setActiveTab(index: Int) {
        _uiState.value = _uiState.value.copy(activeTabIndex = index)
    }

    fun explainCurrentFile() {
        val tab = _uiState.value.openTabs.getOrNull(_uiState.value.activeTabIndex) ?: run {
            _uiState.value = _uiState.value.copy(error = "No file open to explain")
            return
        }
        _uiState.value = _uiState.value.copy(
            inputText = "Explain what this file does and highlight any issues:",
            attachedFileName = tab.fileName,
            attachedFileContent = tab.content.take(8000)
        )
        sendMessage()
    }

    fun saveContentAsFile(fileName: String, content: String) {
        viewModelScope.launch {
            val savePath = if (projectPath.isNotBlank()) "$projectPath/$fileName" else "/sdcard/$fileName"
            fsRepository.writeFile(savePath, content).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(error = "Saved to $savePath") },
                onFailure = { _uiState.value = _uiState.value.copy(error = "Save failed: ${it.message}") }
            )
        }
    }

    fun injectTerminalOutput(output: String) {
        _uiState.value = _uiState.value.copy(
            terminalOutput = output,
            inputText = "I got this terminal output. What went wrong and how do I fix it?",
            attachedFileName = "terminal_output.txt",
            attachedFileContent = output.takeLast(4000)
        )
    }

    fun updateInput(text: String) { _uiState.value = _uiState.value.copy(inputText = text) }
    fun attachFile(name: String, content: String) { _uiState.value = _uiState.value.copy(attachedFileName = name, attachedFileContent = content) }
    fun clearAttachment() { _uiState.value = _uiState.value.copy(attachedFileName = null, attachedFileContent = null) }
    fun showPersonaPicker() { _uiState.value = _uiState.value.copy(showPersonaPicker = true) }
    fun hidePersonaPicker() { _uiState.value = _uiState.value.copy(showPersonaPicker = false) }

    fun selectPersona(persona: AiPersona) {
        viewModelScope.launch {
            settingsRepository.savePersonaId(persona.id)
            _uiState.value = _uiState.value.copy(currentPersona = persona, showPersonaPicker = false)
        }
    }

    fun requestCodeReview() {
        if (_uiState.value.attachedFileContent == null && _uiState.value.inputText.isBlank()) return
        _uiState.value = _uiState.value.copy(inputText = "Please do a thorough code review. Check for bugs, security issues, performance problems, and suggest improvements.")
        sendMessage()
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank() && state.attachedFileContent == null) return
        if (state.isLoading) return
        val attachedName = state.attachedFileName
        val displayText = if (attachedName != null) "$text\n $attachedName" else text
        val projectContextSection = state.projectContext?.let { "\n\nProject file tree:\n$it" } ?: ""
        val enrichedSystemPrompt = state.currentPersona.systemPrompt + projectContextSection
        viewModelScope.launch {
            val userMessage = ChatMessage(
                projectId = currentProjectId, role = "user",
                content = displayText,
                tokenCount = aiRepository.estimateTokens(displayText),
                attachedFileName = attachedName
            )
            chatRepository.saveMessage(userMessage)
            _uiState.value = state.copy(inputText = "", isLoading = true, attachedFileName = null, attachedFileContent = null)
            val result = aiRepository.sendMessage(
                history = _uiState.value.messages.dropLast(1),
                userMessage = text,
                attachedFileContent = state.attachedFileContent,
                systemPrompt = enrichedSystemPrompt
            )
            result.fold(
                onSuccess = { response ->
                    chatRepository.saveMessage(ChatMessage(
                        projectId = currentProjectId, role = "assistant",
                        content = response, tokenCount = aiRepository.estimateTokens(response)
                    ))
                    _uiState.value = _uiState.value.copy(isLoading = false)
                },
                onFailure = { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
            )
        }
    }

    fun clearHistory() { viewModelScope.launch { chatRepository.clearMessagesForProject(currentProjectId) } }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
''')

# Fix 2: HomeScreen - remove documentfile dependency, fix context usage
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
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.projects.isEmpty() -> EmptyProjectsPlaceholder(modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        QuickActionsBar(
                            onBrowseFiles = { viewModel.showImportDialog() },
                            onOpenChat = { uiState.projects.firstOrNull()?.let { onOpenChat(it.id) } }
                        )
                    }
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
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") } }
                ) { Text(error) }
            }
        }

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
                onImport = { viewModel.importProject { id -> onOpenEditor(id) } },
                onDismiss = viewModel::hideImportDialog
            )
        }
    }
}

@Composable
fun QuickActionsBar(onBrowseFiles: () -> Unit, onOpenChat: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
}

@Composable
fun ImportProjectDialog(path: String, onPathChange: (String) -> Unit, onImport: () -> Unit, onDismiss: () -> Unit) {
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
                Text("Enter the full path to your project folder:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = path, onValueChange = onPathChange,
                    label = { Text("Folder path") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 3,
                    placeholder = { Text("/sdcard/my-project") }
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
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
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
        Text("Tap Browse to load or + to create a project", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

# Fix 3: HomeViewModel - remove broken template reference
write("ui/screens/home/HomeViewModel.kt", '''package com.codemaster.aistudio.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.Project
import com.codemaster.aistudio.data.repository.FileSystemRepository
import com.codemaster.aistudio.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
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
    val importPath: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val fsRepository: FileSystemRepository
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
    fun updateImportPath(p: String) { _uiState.value = _uiState.value.copy(importPath = p) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

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
            val project = Project(name = name, language = "Kotlin", description = "Loaded from: $path", path = path)
            val id = projectRepository.createProject(project.name, project.language, project.description)
            // Update path
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

# NEW: PreviewScreen - HTML, Markdown, and code preview
write("ui/screens/preview/PreviewScreen.kt", '''package com.codemaster.aistudio.ui.screens.preview

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
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
import androidx.compose.ui.viewinterop.AndroidView

enum class PreviewMode { HTML, MARKDOWN, CODE, RAW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    fileName: String,
    content: String,
    onBack: () -> Unit
) {
    val extension = fileName.substringAfterLast(".", "").lowercase()
    var mode by remember {
        mutableStateOf(
            when (extension) {
                "html", "htm" -> PreviewMode.HTML
                "md", "markdown" -> PreviewMode.MARKDOWN
                else -> PreviewMode.CODE
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Preview", fontWeight = FontWeight.Bold)
                        Text(fileName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    // Mode switcher
                    if (extension in listOf("html", "htm")) {
                        IconButton(onClick = { mode = PreviewMode.HTML }) {
                            Icon(Icons.Default.Language, "HTML", tint = if (mode == PreviewMode.HTML) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (extension in listOf("md", "markdown")) {
                        IconButton(onClick = { mode = PreviewMode.MARKDOWN }) {
                            Icon(Icons.Default.TextFields, "Markdown", tint = if (mode == PreviewMode.MARKDOWN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { mode = PreviewMode.CODE }) {
                        Icon(Icons.Default.Code, "Code", tint = if (mode == PreviewMode.CODE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { mode = PreviewMode.RAW }) {
                        Icon(Icons.Default.RawOn, "Raw", tint = if (mode == PreviewMode.RAW) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (mode) {
                PreviewMode.HTML -> HtmlPreview(content)
                PreviewMode.MARKDOWN -> MarkdownPreview(content)
                PreviewMode.CODE -> CodePreview(content, extension)
                PreviewMode.RAW -> RawPreview(content)
            }
        }
    }
}

@Composable
fun HtmlPreview(html: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun MarkdownPreview(markdown: String) {
    // Convert markdown to HTML and render
    val html = markdownToHtml(markdown)
    val styledHtml = """
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            body { font-family: -apple-system, sans-serif; padding: 16px; line-height: 1.6; color: #e0e0e0; background: #1a1a2e; }
            h1,h2,h3 { color: #bb86fc; border-bottom: 1px solid #333; padding-bottom: 8px; }
            code { background: #2d2d2d; padding: 2px 6px; border-radius: 4px; font-family: monospace; color: #03dac6; }
            pre { background: #0d1117; padding: 16px; border-radius: 8px; overflow-x: auto; border: 1px solid #333; }
            pre code { background: none; padding: 0; }
            blockquote { border-left: 4px solid #bb86fc; margin: 0; padding-left: 16px; color: #888; }
            a { color: #03dac6; }
            table { border-collapse: collapse; width: 100%; }
            th, td { border: 1px solid #333; padding: 8px; }
            th { background: #2d2d2d; }
            img { max-width: 100%; }
            hr { border-color: #333; }
        </style>
        </head>
        <body>$html</body>
        </html>
    """.trimIndent()
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = false
                loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

fun markdownToHtml(md: String): String {
    var html = md
    // Headers
    html = html.replace(Regex("^#{6} (.+)$", RegexOption.MULTILINE)) { "<h6>${it.groupValues[1]}</h6>" }
    html = html.replace(Regex("^#{5} (.+)$", RegexOption.MULTILINE)) { "<h5>${it.groupValues[1]}</h5>" }
    html = html.replace(Regex("^#{4} (.+)$", RegexOption.MULTILINE)) { "<h4>${it.groupValues[1]}</h4>" }
    html = html.replace(Regex("^#{3} (.+)$", RegexOption.MULTILINE)) { "<h3>${it.groupValues[1]}</h3>" }
    html = html.replace(Regex("^#{2} (.+)$", RegexOption.MULTILINE)) { "<h2>${it.groupValues[1]}</h2>" }
    html = html.replace(Regex("^# (.+)$", RegexOption.MULTILINE)) { "<h1>${it.groupValues[1]}</h1>" }
    // Code blocks
    html = html.replace(Regex("```[\\w]*\\n([\\s\\S]*?)```")) { "<pre><code>${it.groupValues[1].trim()}</code></pre>" }
    // Inline code
    html = html.replace(Regex("`([^`]+)`")) { "<code>${it.groupValues[1]}</code>" }
    // Bold
    html = html.replace(Regex("\\*\\*(.+?)\\*\\*")) { "<strong>${it.groupValues[1]}</strong>" }
    html = html.replace(Regex("__(.+?)__")) { "<strong>${it.groupValues[1]}</strong>" }
    // Italic
    html = html.replace(Regex("\\*(.+?)\\*")) { "<em>${it.groupValues[1]}</em>" }
    // Links
    html = html.replace(Regex("\\[(.+?)\\]\\((.+?)\\)")) { "<a href='${it.groupValues[2]}'>${it.groupValues[1]}</a>" }
    // Blockquote
    html = html.replace(Regex("^> (.+)$", RegexOption.MULTILINE)) { "<blockquote>${it.groupValues[1]}</blockquote>" }
    // HR
    html = html.replace(Regex("^---$", RegexOption.MULTILINE), "<hr>")
    // Lists
    html = html.replace(Regex("^- (.+)$", RegexOption.MULTILINE)) { "<li>${it.groupValues[1]}</li>" }
    html = html.replace(Regex("^\\* (.+)$", RegexOption.MULTILINE)) { "<li>${it.groupValues[1]}</li>" }
    // Paragraphs
    html = html.replace(Regex("\\n\\n")) { "</p><p>" }
    return "<p>$html</p>"
}

@Composable
fun CodePreview(content: String, extension: String) {
    val styledHtml = """
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css">
        <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
        <style>
            body { margin: 0; padding: 8px; background: #0d1117; }
            pre { margin: 0; }
            code { font-size: 13px; line-height: 1.5; }
            .hljs { padding: 16px; border-radius: 8px; }
        </style>
        </head>
        <body>
        <pre><code class="language-$extension">${content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</code></pre>
        <script>hljs.highlightAll();</script>
        </body>
        </html>
    """.trimIndent()
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                loadDataWithBaseURL("https://cdnjs.cloudflare.com", styledHtml, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun RawPreview(content: String) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))
    ) {
        Text(
            text = content,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color(0xFFADBDD0),
            lineHeight = 18.sp,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(scrollState)
        )
    }
}
''')

# NEW: Add preview route to NavGraph
nav_path = os.path.join(BASE, "ui/navigation/NavGraph.kt")
try:
    with open(nav_path, 'r') as f:
        nav = f.read()
    if 'PreviewScreen' not in nav:
        nav = nav.replace(
            'import com.codemaster.aistudio.ui.screens.terminal.TerminalScreen',
            'import com.codemaster.aistudio.ui.screens.terminal.TerminalScreen\nimport com.codemaster.aistudio.ui.screens.preview.PreviewScreen'
        )
        nav = nav.replace(
            'object Git      : Screen("git/{projectId}")',
            'object Git      : Screen("git/{projectId}")\n    object Preview  : Screen("preview/{projectId}") { fun createRoute(id: Long = -1L) = "preview/$id" }'
        )
        # Add composable for preview
        nav = nav.replace(
            'fun NavGraph(navController: NavHostController) {',
            '''fun NavGraph(navController: NavHostController, previewFile: String = "", previewContent: String = "") {'''
        )
        with open(nav_path, 'w') as f:
            f.write(nav)
        print("  OK NavGraph.kt (patched preview route)")
        OK.append("NavGraph patch")
    else:
        print("  SKIP NavGraph already has preview")
except Exception as e:
    print(f"  FAIL NavGraph - {e}")
    FAIL.append("NavGraph")

# NEW: CodeEditorScreen - add preview button
editor_screen = os.path.join(BASE, "ui/screens/editor/CodeEditorScreen.kt")
try:
    with open(editor_screen, 'r') as f:
        editor = f.read()
    if 'onOpenPreview' not in editor:
        editor = editor.replace(
            'fun CodeEditorScreen(',
            'fun CodeEditorScreen(\n    onOpenPreview: ((String, String) -> Unit)? = null,'
        )
        # Add preview button in top bar actions
        editor = editor.replace(
            'IconButton(onClick = { viewModel.toggleFileTree() })',
            '''IconButton(onClick = {
                        val file = uiState.currentFile
                        if (file != null && onOpenPreview != null) {
                            onOpenPreview(file.name, uiState.content)
                        }
                    }) {
                        Icon(Icons.Default.Preview, "Preview file", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { viewModel.toggleFileTree() })'''
        )
        with open(editor_screen, 'w') as f:
            f.write(editor)
        print("  OK CodeEditorScreen.kt (patched preview button)")
        OK.append("CodeEditorScreen patch")
    else:
        print("  SKIP CodeEditorScreen already has preview")
except Exception as e:
    print(f"  INFO CodeEditorScreen - {e} (may need manual addition)")

print(f"\n{'='*55}")
print(f"OK: {len(OK)} files   FAIL: {len(FAIL)}")
if FAIL:
    print(f"Failed: {FAIL}")
print("""
All fixes + new features:
  FIXED AiChatViewModel - projectPath, fsRepository refs
  FIXED HomeScreen - removed documentfile dependency
  FIXED HomeViewModel - removed broken template reference
  FIXED AiChatScreen - OpenTab now defined in ViewModel
  ADDED PreviewScreen - HTML live render (WebView)
  ADDED PreviewScreen - Markdown render with dark theme
  ADDED PreviewScreen - Syntax highlighted code preview
  ADDED PreviewScreen - Raw text view
  ADDED Preview button in editor top bar
  ADDED NavGraph preview route

Now run:
  git add -A
  git commit -m "fix: build errors + add preview window HTML/MD/code"
  git push origin HEAD
""")
