#!/usr/bin/env python3
import os

BASE = os.path.expanduser("~/codemaster-ai-studio/app/src/main/java/com/codemaster/aistudio")

def write(rel, content):
    path = os.path.join(BASE, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("OK " + rel)

# ─────────────────────────────────────────────────────────────────────────────
# 1. FILESYSTEM REPOSITORY - scan real files from disk
# ─────────────────────────────────────────────────────────────────────────────
write("data/repository/FileSystemRepository.kt", r"""package com.codemaster.aistudio.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DiskFile(
    val name: String,
    val path: String,
    val relativePath: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val extension: String,
    val depth: Int = 0
)

@Singleton
class FileSystemRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ignoredDirs = setOf(
        ".git", "build", ".gradle", ".idea", "node_modules",
        "__pycache__", ".DS_Store", "out", "dist", ".dart_tool"
    )
    private val ignoredExtensions = setOf(
        "class", "jar", "aar", "apk", "so", "o", "a",
        "png", "jpg", "jpeg", "gif", "webp", "ico",
        "zip", "tar", "gz", "rar", "7z",
        "pdf", "doc", "docx", "xls", "xlsx"
    )
    private val codeExtensions = setOf(
        "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx",
        "dart", "cpp", "c", "h", "cs", "go", "rs", "swift",
        "rb", "php", "html", "css", "scss", "xml", "json",
        "yaml", "yml", "md", "txt", "sh", "bash", "gradle",
        "toml", "properties", "sql", "graphql", "proto"
    )

    suspend fun scanDirectory(rootPath: String, maxDepth: Int = 6): Result<List<DiskFile>> =
        withContext(Dispatchers.IO) {
            try {
                val root = File(rootPath)
                if (!root.exists()) return@withContext Result.failure(Exception("Path does not exist: $rootPath"))
                if (!root.isDirectory) return@withContext Result.failure(Exception("Not a directory: $rootPath"))

                val files = mutableListOf<DiskFile>()
                scanRecursive(root, root.absolutePath, files, 0, maxDepth)
                Result.success(files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun scanRecursive(dir: File, rootPath: String, result: MutableList<DiskFile>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val files = try { dir.listFiles() ?: return } catch (_: Exception) { return }

        files.sortedWith(compareBy({ !it.isDirectory }, { it.name })).forEach { file ->
            if (file.name.startsWith(".") && file.name != ".env") return@forEach
            if (file.isDirectory && file.name in ignoredDirs) return@forEach
            val ext = file.extension.lowercase()
            if (!file.isDirectory && ext in ignoredExtensions) return@forEach

            val relative = file.absolutePath.removePrefix(rootPath).trimStart('/')
            result.add(DiskFile(
                name = file.name,
                path = file.absolutePath,
                relativePath = relative,
                size = if (file.isFile) file.length() else 0,
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                extension = ext,
                depth = depth
            ))

            if (file.isDirectory) {
                scanRecursive(file, rootPath, result, depth + 1, maxDepth)
            }
        }
    }

    suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) return@withContext Result.failure(Exception("File not found: $path"))
            if (file.length() > 500_000) return@withContext Result.failure(Exception("File too large to open (>500KB)"))
            Result.success(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeFile(path: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFile(dirPath: String, fileName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(dirPath, fileName)
            file.parentFile?.mkdirs()
            if (!file.exists()) file.createNewFile()
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try { File(path).delete(); Result.success(Unit) }
        catch (e: Exception) { Result.failure(e) }
    }

    fun getLanguageFromExtension(ext: String): String = when (ext.lowercase()) {
        "kt", "kts"          -> "kotlin"
        "java"               -> "java"
        "py"                 -> "python"
        "js", "jsx"          -> "javascript"
        "ts", "tsx"          -> "typescript"
        "dart"               -> "dart"
        "cpp", "cc", "cxx"   -> "cpp"
        "c"                  -> "c"
        "cs"                 -> "csharp"
        "go"                 -> "go"
        "rs"                 -> "rust"
        "swift"              -> "swift"
        "rb"                 -> "ruby"
        "php"                -> "php"
        "html"               -> "html"
        "css"                -> "css"
        "scss"               -> "scss"
        "xml"                -> "xml"
        "json"               -> "json"
        "yaml", "yml"        -> "yaml"
        "md"                 -> "markdown"
        "sh", "bash"         -> "shell"
        "gradle", "groovy"   -> "groovy"
        "sql"                -> "sql"
        "toml"               -> "toml"
        else                 -> "plaintext"
    }

    fun formatFileSize(bytes: Long): String = when {
        bytes < 1024        -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else                -> "${bytes / (1024 * 1024)}MB"
    }
}
""")

# ─────────────────────────────────────────────────────────────────────────────
# 2. CODE EDITOR VIEWMODEL - real filesystem
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/editor/CodeEditorViewModel.kt", r"""package com.codemaster.aistudio.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.DiskFile
import com.codemaster.aistudio.data.repository.FileSystemRepository
import com.codemaster.aistudio.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val error: String? = null
)

@HiltViewModel
class CodeEditorViewModel @Inject constructor(
    private val fsRepository: FileSystemRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun init(projectId: Long, fileId: Long) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            val name = project?.name ?: "Editor"
            val desc = project?.description ?: ""

            // Extract path from description if it was an imported project
            val rootPath = when {
                desc.startsWith("Loaded from: ") -> desc.removePrefix("Loaded from: ")
                desc.startsWith("Imported from: ") -> desc.removePrefix("Imported from: ")
                else -> ""
            }

            _uiState.value = _uiState.value.copy(
                projectName = name,
                rootPath = rootPath,
                newFileDir = rootPath
            )

            if (rootPath.isNotBlank()) {
                scanFiles(rootPath)
            }
        }
    }

    fun scanFiles(path: String = _uiState.value.rootPath) {
        if (path.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFiles = true)
            fsRepository.scanDirectory(path).fold(
                onSuccess = { files ->
                    _uiState.value = _uiState.value.copy(
                        files = files,
                        rootPath = path,
                        newFileDir = path,
                        isLoadingFiles = false,
                        // Auto-expand root
                        expandedDirs = setOf(path)
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoadingFiles = false, error = e.message)
                }
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
        // Save current file first
        if (_uiState.value.isDirty) saveCurrentFile()

        viewModelScope.launch {
            fsRepository.readFile(file.path).fold(
                onSuccess = { content ->
                    val lang = fsRepository.getLanguageFromExtension(file.extension)
                    val recent = (_uiState.value.recentFiles.filter { it.path != file.path } + file).takeLast(10)
                    _uiState.value = _uiState.value.copy(
                        currentFile = file,
                        content = content,
                        language = lang,
                        isDirty = false,
                        recentFiles = recent
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            )
        }
    }

    fun updateContent(content: String) {
        _uiState.value = _uiState.value.copy(content = content, isDirty = true)
    }

    fun updateCursor(line: Int, col: Int) {
        _uiState.value = _uiState.value.copy(cursorLine = line, cursorCol = col)
    }

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
                    hideNewFileDialog()
                    scanFiles()
                    // Open the new file
                    val newFile = DiskFile(
                        name = name, path = path,
                        relativePath = name,
                        size = 0, lastModified = System.currentTimeMillis(),
                        isDirectory = false,
                        extension = name.substringAfterLast('.', "")
                    )
                    openFile(newFile)
                },
                onFailure = { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            )
        }
    }

    fun deleteFile(file: DiskFile) {
        viewModelScope.launch {
            fsRepository.deleteFile(file.path)
            if (_uiState.value.currentFile?.path == file.path) {
                _uiState.value = _uiState.value.copy(currentFile = null, content = "")
            }
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
            // Search mode - show all matching files flat
            state.files.filter {
                !it.isDirectory && (
                    it.name.contains(query, ignoreCase = true) ||
                    it.relativePath.contains(query, ignoreCase = true)
                )
            }
        } else {
            // Tree mode - only show files whose parent dirs are expanded
            state.files.filter { file ->
                val parentPath = file.path.substringBeforeLast('/')
                parentPath == root || parentPath in state.expandedDirs ||
                    state.expandedDirs.any { file.path.startsWith(it + "/") && file.depth == 1 }
            }
        }
    }
}
""")

# ─────────────────────────────────────────────────────────────────────────────
# 3. CODE EDITOR SCREEN - real filesystem tree
# ─────────────────────────────────────────────────────────────────────────────
write("ui/screens/editor/CodeEditorScreen.kt", r"""package com.codemaster.aistudio.ui.screens.editor

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codemaster.aistudio.data.repository.DiskFile
import com.codemaster.aistudio.ui.components.MonacoEditorView
import com.codemaster.aistudio.ui.components.detectLanguage
import com.codemaster.aistudio.ui.components.rememberMonacoEditorState
import com.codemaster.aistudio.ui.screens.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    projectId: Long,
    fileId: Long,
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenTerminal: (Long) -> Unit,
    viewModel: CodeEditorViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val monacoState = rememberMonacoEditorState()
    var fontSize by remember { mutableStateOf(14) }
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(projectId, fileId) { viewModel.init(projectId, fileId) }

    // Push new file content into Monaco when file changes
    LaunchedEffect(uiState.currentFile?.path) {
        uiState.currentFile?.let {
            monacoState.setValue(uiState.content)
            monacoState.setLanguage(uiState.language)
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                uiState.currentFile?.name ?: uiState.projectName,
                                fontWeight = FontWeight.Bold, maxLines = 1
                            )
                            if (uiState.isDirty) {
                                Spacer(Modifier.width(6.dp))
                                Text("●", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                            }
                        }
                        if (uiState.cursorLine > 0) {
                            Text(
                                "Ln ${uiState.cursorLine}, Col ${uiState.cursorCol}  |  ${uiState.language}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Default.Search, "Search files") }
                    IconButton(onClick = { viewModel.toggleFileTree() }) { Icon(Icons.Default.AccountTree, "Files") }
                    if (uiState.isDirty) {
                        IconButton(onClick = { viewModel.saveCurrentFile() }) {
                            if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Save, "Save")
                        }
                    }
                    IconButton(onClick = { onOpenChat(projectId) }) { Icon(Icons.Default.Chat, "Chat") }
                    IconButton(onClick = { onOpenTerminal(projectId) }) { Icon(Icons.Default.Terminal, "Terminal") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Toolbar
            EditorToolbar(
                onUndo = { monacoState.undo() },
                onRedo = { monacoState.redo() },
                onFormat = { monacoState.format() },
                onFind = { monacoState.find() },
                onFontMinus = { fontSize = (fontSize - 1).coerceAtLeast(8); monacoState.setFontSize(fontSize) },
                onFontPlus  = { fontSize = (fontSize + 1).coerceAtMost(24); monacoState.setFontSize(fontSize) },
                onSave = { viewModel.saveCurrentFile() },
                onRefresh = { viewModel.scanFiles() },
                fontSize = fontSize,
                isDirty = uiState.isDirty
            )

            // File search bar
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearch(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    placeholder = { Text("Search files...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.updateSearch("") }) { Icon(Icons.Default.Clear, null) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp)
                )
            }

            Row(modifier = Modifier.weight(1f)) {
                // File tree
                AnimatedVisibility(
                    visible = uiState.showFileTree,
                    enter = slideInHorizontally() + fadeIn(),
                    exit = slideOutHorizontally() + fadeOut()
                ) {
                    FileTreePanel(
                        files = viewModel.getVisibleFiles(),
                        currentFile = uiState.currentFile,
                        expandedDirs = uiState.expandedDirs,
                        rootPath = uiState.rootPath,
                        isLoading = uiState.isLoadingFiles,
                        onFileClick = { viewModel.openFile(it) },
                        onDeleteFile = { viewModel.deleteFile(it) },
                        onNewFile = { dir -> viewModel.showNewFileDialog(dir) },
                        onRefresh = { viewModel.scanFiles() },
                        modifier = Modifier.width(200.dp)
                    )
                }

                // Monaco Editor
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (uiState.currentFile == null) {
                        NoFileOpen(
                            recentFiles = uiState.recentFiles,
                            onOpenRecent = { viewModel.openFile(it) },
                            onNewFile = { viewModel.showNewFileDialog() }
                        )
                    } else {
                        MonacoEditorView(
                            value = uiState.content,
                            language = uiState.language,
                            isDarkTheme = settingsState.isDarkTheme,
                            fontSize = fontSize,
                            onChange = { viewModel.updateContent(it) },
                            onCursorChange = { line, col -> viewModel.updateCursor(line, col) },
                            state = monacoState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // New file dialog
    if (uiState.showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideNewFileDialog() },
            title = { Text("New File", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("In: ${uiState.newFileDir.takeLast(40)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = uiState.newFileName,
                        onValueChange = { viewModel.updateNewFileName(it) },
                        label = { Text("Filename") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("MainActivity.kt") }
                    )
                }
            },
            confirmButton = { Button(onClick = { viewModel.createFile() }, enabled = uiState.newFileName.isNotBlank()) { Text("Create") } },
            dismissButton = { TextButton(onClick = { viewModel.hideNewFileDialog() }) { Text("Cancel") } }
        )
    }

    uiState.error?.let { error ->
        LaunchedEffect(error) {}
        Snackbar(modifier = Modifier.padding(16.dp), action = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } }) { Text(error) }
    }
}

@Composable
fun EditorToolbar(
    onUndo: () -> Unit, onRedo: () -> Unit, onFormat: () -> Unit, onFind: () -> Unit,
    onFontMinus: () -> Unit, onFontPlus: () -> Unit, onSave: () -> Unit, onRefresh: () -> Unit,
    fontSize: Int, isDirty: Boolean
) {
    Surface(tonalElevation = 2.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            EditorBtn(Icons.Default.Undo, "Undo", onUndo)
            EditorBtn(Icons.Default.Redo, "Redo", onRedo)
            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))
            EditorBtn(Icons.Default.AutoFixHigh, "Format", onFormat)
            EditorBtn(Icons.Default.Search, "Find", onFind)
            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))
            EditorBtn(Icons.Default.TextDecrease, "A-", onFontMinus)
            Text("$fontSize", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 2.dp))
            EditorBtn(Icons.Default.TextIncrease, "A+", onFontPlus)
            VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))
            EditorBtn(Icons.Default.Refresh, "Refresh", onRefresh)
            if (isDirty) {
                EditorBtn(Icons.Default.Save, "Save", onSave)
            }
        }
    }
}

@Composable
fun EditorBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, label, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun FileTreePanel(
    files: List<DiskFile>,
    currentFile: DiskFile?,
    expandedDirs: Set<String>,
    rootPath: String,
    isLoading: Boolean,
    onFileClick: (DiskFile) -> Unit,
    onDeleteFile: (DiskFile) -> Unit,
    onNewFile: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text("EXPLORER", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onRefresh, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp)) }
            IconButton(onClick = { onNewFile(rootPath) }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp)) }
        }
        HorizontalDivider()

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("No files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn {
                items(files, key = { it.path }) { file ->
                    FileTreeItem(
                        file = file,
                        isSelected = currentFile?.path == file.path,
                        isExpanded = file.path in expandedDirs,
                        onClick = { onFileClick(file) },
                        onDelete = { onDeleteFile(file) },
                        onNewFile = { onNewFile(file.path) }
                    )
                }
            }
        }
    }
}

@Composable
fun FileTreeItem(
    file: DiskFile,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onNewFile: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val indent = (file.depth * 12).dp
    val icon = when {
        file.isDirectory -> if (isExpanded) "📂" else "📁"
        else -> when (file.extension.lowercase()) {
            "kt", "kts"       -> "🟣"
            "java"            -> "☕"
            "py"              -> "🐍"
            "js", "jsx"       -> "🟡"
            "ts", "tsx"       -> "🔷"
            "xml"             -> "📄"
            "json"            -> "📋"
            "md"              -> "📝"
            "gradle", "kts"   -> "🐘"
            "sh", "bash"      -> "⚡"
            "html"            -> "🌐"
            "css", "scss"     -> "🎨"
            "rs"              -> "🦀"
            "go"              -> "🐹"
            "dart"            -> "🎯"
            "yaml", "yml"     -> "⚙️"
            "toml"            -> "⚙️"
            "properties"      -> "⚙️"
            else              -> "📄"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { onClick() }
            .padding(start = 8.dp + indent, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            file.name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else if (file.isDirectory) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            fontWeight = if (file.isDirectory) FontWeight.Medium else FontWeight.Normal
        )
        if (!file.isDirectory && file.size > 0) {
            Text(
                formatSize(file.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 9.sp
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(12.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (file.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("New File Here") },
                        onClick = { showMenu = false; onNewFile(file.path) },
                        leadingIcon = { Icon(Icons.Default.Add, null) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}K"
    else -> "${bytes / (1024 * 1024)}M"
}

@Composable
fun NoFileOpen(
    recentFiles: List<DiskFile>,
    onOpenRecent: (DiskFile) -> Unit,
    onNewFile: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Code, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Text("No file open", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNewFile) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("New File")
        }

        if (recentFiles.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Recent Files", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            recentFiles.reversed().take(5).forEach { file ->
                TextButton(onClick = { onOpenRecent(file) }) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}
""")

# ─────────────────────────────────────────────────────────────────────────────
# 4. DI - add FileSystemRepository
# ─────────────────────────────────────────────────────────────────────────────
write("di/AppModule.kt", r"""package com.codemaster.aistudio.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.codemaster.aistudio.data.CodeMasterDatabase
import com.codemaster.aistudio.data.api.GitHubApiService
import com.codemaster.aistudio.data.api.GroqApiService
import com.codemaster.aistudio.data.dao.ChatMessageDao
import com.codemaster.aistudio.data.dao.CodeFileDao
import com.codemaster.aistudio.data.dao.ProjectDao
import com.codemaster.aistudio.data.dao.SnippetDao
import com.codemaster.aistudio.data.repository.FileSystemRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "codemaster_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CodeMasterDatabase =
        Room.databaseBuilder(context, CodeMasterDatabase::class.java, "codemaster_db")
            .fallbackToDestructiveMigration().build()

    @Provides @Singleton fun provideProjectDao(db: CodeMasterDatabase): ProjectDao = db.projectDao()
    @Provides @Singleton fun provideChatMessageDao(db: CodeMasterDatabase): ChatMessageDao = db.chatMessageDao()
    @Provides @Singleton fun provideCodeFileDao(db: CodeMasterDatabase): CodeFileDao = db.codeFileDao()
    @Provides @Singleton fun provideSnippetDao(db: CodeMasterDatabase): SnippetDao = db.snippetDao()

    @Provides @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides @Singleton
    fun provideFileSystemRepository(@ApplicationContext context: Context): FileSystemRepository =
        FileSystemRepository(context)

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    @Provides @Singleton @Named("groq")
    fun provideGroqRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.groq.com/").client(client)
        .addConverterFactory(GsonConverterFactory.create()).build()

    @Provides @Singleton @Named("github")
    fun provideGitHubRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/").client(client)
        .addConverterFactory(GsonConverterFactory.create()).build()

    @Provides @Singleton
    fun provideGroqApiService(@Named("groq") retrofit: Retrofit): GroqApiService =
        retrofit.create(GroqApiService::class.java)

    @Provides @Singleton
    fun provideGitHubApiService(@Named("github") retrofit: Retrofit): GitHubApiService =
        retrofit.create(GitHubApiService::class.java)
}
""")

# ─────────────────────────────────────────────────────────────────────────────
# 5. GRADLE PROPERTIES - suppress compileSdk warning + perf tweaks
# ─────────────────────────────────────────────────────────────────────────────
gradle_props = os.path.expanduser("~/codemaster-ai-studio/gradle.properties")
with open(gradle_props, 'w') as f:
    f.write("""# Project-wide Gradle settings
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=true
android.useAndroidX=true
android.enableJetifier=false
kotlin.code.style=official
android.suppressUnsupportedCompileSdk=35
android.nonTransitiveRClass=true
""")
print("OK gradle.properties")

# ─────────────────────────────────────────────────────────────────────────────
# 6. WORKFLOW - fix assembleDebu typo once and for all
# ─────────────────────────────────────────────────────────────────────────────
workflow_path = os.path.expanduser("~/codemaster-ai-studio/.github/workflows/build.yml")
os.makedirs(os.path.dirname(workflow_path), exist_ok=True)
with open(workflow_path, 'w') as f:
    f.write("""name: Build CodeMaster AI Studio APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 45

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Grant execute permission
        run: chmod +x gradlew

      - name: Build Debug APK
        run: ./gradlew assembleDebug --no-daemon --stacktrace

      - name: Upload Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: CodeMaster-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 14
""")
print("OK build.yml - fixed assembleDebug typo")

print("\n" + "="*52)
print("Phase 6 complete!")
print("\nWhat this adds:")
print("  Real filesystem scanning from disk")
print("  File tree shows actual .kt/.py/.js files")
print("  Files open/save directly to disk")
print("  Indented folder tree with expand/collapse")
print("  File search across all project files")
print("  Recent files list")
print("  New file in any subfolder")
print("  File size shown in tree")
print("  Gradle parallel build + caching")
print("  Fixed assembleDebug typo in workflow")
print("  Suppressed compileSdk=35 warning")
print("\nRun:")
print("  git add -A")
print("  git commit -m 'feat: Phase 6 - real filesystem editor, file tree, search, recent files'")
print("  git push origin HEAD")
print("="*52)
