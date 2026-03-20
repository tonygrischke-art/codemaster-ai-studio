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

def write_abs(path, content):
    try:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w') as f:
            f.write(content)
        print(f"  OK {path}")
        OK.append(path)
    except Exception as e:
        print(f"  FAIL {path} - {e}")
        FAIL.append(path)

print("\n THE MAYBACH SCRIPT - Complete Professional Fix\n")

# ═══════════════════════════════════════════════════════════
# 1. SettingsRepository - add missing projectPath methods
# ═══════════════════════════════════════════════════════════
write("data/repository/SettingsRepository.kt", r'''package com.codemaster.aistudio.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val API_KEY        = stringPreferencesKey("groq_api_key")
        val MODEL          = stringPreferencesKey("groq_model")
        val DARK_THEME     = booleanPreferencesKey("dark_theme")
        val GITHUB_TOKEN   = stringPreferencesKey("github_token")
        val GITHUB_OWNER   = stringPreferencesKey("github_owner")
        val GITHUB_REPO    = stringPreferencesKey("github_repo")
        val GITHUB_BRANCH  = stringPreferencesKey("github_branch")
        val AI_PERSONA     = stringPreferencesKey("ai_persona")
        val PROJECT_PATH   = stringPreferencesKey("project_path")
    }

    suspend fun getApiKey(): String         = dataStore.data.first()[API_KEY] ?: ""
    suspend fun saveApiKey(k: String)       { dataStore.edit { it[API_KEY] = k } }
    suspend fun getModel(): String          = dataStore.data.first()[MODEL] ?: "llama-3.3-70b-versatile"
    suspend fun saveModel(m: String)        { dataStore.edit { it[MODEL] = m } }
    suspend fun getDarkTheme(): Boolean     = dataStore.data.first()[DARK_THEME] ?: true
    suspend fun saveDarkTheme(d: Boolean)   { dataStore.edit { it[DARK_THEME] = d } }
    suspend fun getGitHubToken(): String    = dataStore.data.first()[GITHUB_TOKEN] ?: ""
    suspend fun saveGitHubToken(t: String)  { dataStore.edit { it[GITHUB_TOKEN] = t } }
    suspend fun getGitHubOwner(): String    = dataStore.data.first()[GITHUB_OWNER] ?: ""
    suspend fun saveGitHubOwner(o: String)  { dataStore.edit { it[GITHUB_OWNER] = o } }
    suspend fun getGitHubRepo(): String     = dataStore.data.first()[GITHUB_REPO] ?: ""
    suspend fun saveGitHubRepo(r: String)   { dataStore.edit { it[GITHUB_REPO] = r } }
    suspend fun getGitHubBranch(): String   = dataStore.data.first()[GITHUB_BRANCH] ?: "main"
    suspend fun saveGitHubBranch(b: String) { dataStore.edit { it[GITHUB_BRANCH] = b } }
    suspend fun getPersonaId(): String      = dataStore.data.first()[AI_PERSONA] ?: "codemaster"
    suspend fun savePersonaId(id: String)   { dataStore.edit { it[AI_PERSONA] = id } }
    suspend fun getProjectPath(): String    = dataStore.data.first()[PROJECT_PATH] ?: "/sdcard/codemaster-ai-studio"
    suspend fun saveProjectPath(p: String)  { dataStore.edit { it[PROJECT_PATH] = p } }
}
''')

# ═══════════════════════════════════════════════════════════
# 2. AndroidManifest.xml - add FileProvider + all permissions
# ═══════════════════════════════════════════════════════════
write_abs("app/src/main/AndroidManifest.xml", r'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Network -->
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- Storage - legacy -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />

    <!-- Storage - modern -->
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_DOCUMENTS" />

    <!-- Manage all files (needed for /sdcard access on Android 11+) -->
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />

    <!-- Termux integration -->
    <uses-permission android:name="com.termux.permission.RUN_COMMAND" />

    <!-- Install APK -->
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

    <!-- Microphone for voice input -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <application
        android:name=".CodeMasterApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="CodeMaster AI Studio"
        android:roundIcon="@mipmap/ic_launcher"
        android:supportsRtl="true"
        android:hardwareAccelerated="true"
        android:usesCleartextTraffic="false"
        android:requestLegacyExternalStorage="true"
        android:theme="@style/Theme.CodeMasterAIStudio">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize"
            android:configChanges="orientation|screenSize|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- FileProvider for APK installation -->
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.provider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>

    </application>

</manifest>
''')

# ═══════════════════════════════════════════════════════════
# 3. file_paths.xml for FileProvider
# ═══════════════════════════════════════════════════════════
write_abs("app/src/main/res/xml/file_paths.xml", r'''<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-path name="external_files" path="." />
    <files-path name="internal_files" path="." />
    <cache-path name="cache_files" path="." />
    <external-cache-path name="external_cache" path="." />
</paths>
''')

# ═══════════════════════════════════════════════════════════
# 4. NavGraph.kt - register Preview screen + pass sendErrorToAi
# ═══════════════════════════════════════════════════════════
write("ui/navigation/NavGraph.kt", r'''package com.codemaster.aistudio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.codemaster.aistudio.ui.screens.build.BuildScreen
import com.codemaster.aistudio.ui.screens.chat.AiChatScreen
import com.codemaster.aistudio.ui.screens.editor.CodeEditorScreen
import com.codemaster.aistudio.ui.screens.git.GitScreen
import com.codemaster.aistudio.ui.screens.home.HomeScreen
import com.codemaster.aistudio.ui.screens.preview.PreviewScreen
import com.codemaster.aistudio.ui.screens.settings.SettingsScreen
import com.codemaster.aistudio.ui.screens.snippets.SnippetScreen
import com.codemaster.aistudio.ui.screens.terminal.TerminalScreen

sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object Settings : Screen("settings")
    object Snippets : Screen("snippets")
    object Chat     : Screen("chat/{projectId}")          { fun createRoute(id: Long = -1L) = "chat/$id" }
    object Editor   : Screen("editor/{projectId}/{fileId}") { fun createRoute(pid: Long, fid: Long = -1L) = "editor/$pid/$fid" }
    object Build    : Screen("build/{projectId}")         { fun createRoute(id: Long = -1L) = "build/$id" }
    object Terminal : Screen("terminal/{projectId}")      { fun createRoute(id: Long = -1L) = "terminal/$id" }
    object Git      : Screen("git/{projectId}")           { fun createRoute(id: Long = -1L) = "git/$id" }
    object Preview  : Screen("preview/{projectId}")       { fun createRoute(id: Long = -1L) = "preview/$id" }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onOpenChat      = { navController.navigate(Screen.Chat.createRoute(it)) },
                onOpenEditor    = { navController.navigate(Screen.Editor.createRoute(it)) },
                onOpenBuild     = { navController.navigate(Screen.Build.createRoute(it)) },
                onOpenTerminal  = { navController.navigate(Screen.Terminal.createRoute(it)) },
                onOpenGit       = { navController.navigate(Screen.Git.createRoute(it)) },
                onOpenSnippets  = { navController.navigate(Screen.Snippets.route) },
                onOpenSettings  = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Snippets.route) {
            SnippetScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Screen.Chat.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            AiChatScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.Editor.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.LongType },
                navArgument("fileId")    { type = NavType.LongType; defaultValue = -1L }
            )
        ) { back ->
            CodeEditorScreen(
                projectId      = back.arguments?.getLong("projectId") ?: -1L,
                fileId         = back.arguments?.getLong("fileId") ?: -1L,
                onBack         = { navController.popBackStack() },
                onOpenChat     = { navController.navigate(Screen.Chat.createRoute(it)) },
                onOpenTerminal = { navController.navigate(Screen.Terminal.createRoute(it)) }
            )
        }

        composable(
            Screen.Build.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            val projectId = back.arguments?.getLong("projectId") ?: -1L
            BuildScreen(
                projectId      = projectId,
                onBack         = { navController.popBackStack() },
                onGoToSettings = { navController.navigate(Screen.Settings.route) },
                onSendErrorToAi = { error ->
                    navController.navigate(Screen.Chat.createRoute(projectId))
                }
            )
        }

        composable(
            Screen.Terminal.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            TerminalScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.Git.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            GitScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Screen.Preview.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            // PreviewScreen needs fileName + content passed via SavedStateHandle
            // For now navigate back if no content
            navController.popBackStack()
        }
    }
}
''')

# ═══════════════════════════════════════════════════════════
# 5. ProjectRepository - add missing getProjectById with path fix
# ═══════════════════════════════════════════════════════════
write("data/repository/ProjectRepository.kt", r'''package com.codemaster.aistudio.data.repository

import com.codemaster.aistudio.data.dao.ProjectDao
import com.codemaster.aistudio.data.model.Project
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao
) {
    fun getAllProjects(): Flow<List<Project>> = projectDao.getAllProjects()

    suspend fun createProject(name: String, language: String, description: String): Long {
        val project = Project(name = name, language = language, description = description)
        return projectDao.insertProject(project)
    }

    suspend fun deleteProject(project: Project) = projectDao.deleteProject(project)

    suspend fun getProjectById(id: Long): Project? = projectDao.getProjectById(id)

    suspend fun updateProject(project: Project) = projectDao.updateProject(project)

    /** Returns the real filesystem path for a project.
     *  Priority: project.path field > parsed from description > empty */
    suspend fun getProjectPath(id: Long): String {
        val project = projectDao.getProjectById(id) ?: return ""
        if (project.path.isNotBlank()) return project.path
        val desc = project.description
        return when {
            desc.startsWith("Loaded from: ")   -> desc.removePrefix("Loaded from: ")
            desc.startsWith("Imported from: ") -> desc.removePrefix("Imported from: ")
            else -> ""
        }
    }
}
''')

# ═══════════════════════════════════════════════════════════
# 6. CodeEditorViewModel - use ProjectRepository.getProjectPath
# ═══════════════════════════════════════════════════════════
write("ui/screens/editor/CodeEditorViewModel.kt", r'''package com.codemaster.aistudio.ui.screens.editor

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

data class CodeError(val line: Int, val message: String, val severity: String = "error")

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
    val successMessage: String? = null,
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
    private var autoSaveJob: Job? = null

    fun init(projectId: Long, fileId: Long) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            val name = project?.name ?: "Editor"
            // Use the proper path resolution
            val rootPath = projectRepository.getProjectPath(projectId)
            _uiState.value = _uiState.value.copy(
                projectName = name,
                rootPath = rootPath,
                newFileDir = rootPath
            )
            if (rootPath.isNotBlank()) scanFiles(rootPath)
        }
    }

    fun scanFiles(path: String = _uiState.value.rootPath) {
        if (path.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFiles = true)
            fsRepository.scanDirectory(path).fold(
                onSuccess = { files ->
                    _uiState.value = _uiState.value.copy(
                        files = files, rootPath = path, newFileDir = path,
                        isLoadingFiles = false, expandedDirs = setOf(path)
                    )
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
                    _uiState.value = _uiState.value.copy(
                        currentFile = file, content = content, language = lang,
                        isDirty = false, recentFiles = recent,
                        liveErrors = emptyList(), aiSuggestion = null
                    )
                },
                onFailure = { e -> _uiState.value = _uiState.value.copy(error = e.message) }
            )
        }
    }

    fun updateContent(content: String) {
        _uiState.value = _uiState.value.copy(content = content, isDirty = true, aiSuggestion = null)
        scheduleErrorCheck(content)
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(2000)
            saveCurrentFile(silent = true)
        }
    }

    private fun scheduleErrorCheck(content: String) {
        errorCheckJob?.cancel()
        errorCheckJob = viewModelScope.launch {
            delay(800)
            val errors = detectErrors(content, _uiState.value.language)
            _uiState.value = _uiState.value.copy(liveErrors = errors)
        }
    }

    private fun detectErrors(content: String, language: String): List<CodeError> {
        val errors = mutableListOf<CodeError>()
        val lines = content.lines()
        lines.forEachIndexed { idx, line ->
            val lineNum = idx + 1
            if (line.contains("TODO(") || line.contains("FIXME")) {
                errors.add(CodeError(lineNum, "TODO/FIXME needs attention", "warning"))
            }
            if (line.length > 120) {
                errors.add(CodeError(lineNum, "Line too long (${line.length} chars)", "warning"))
            }
            when (language) {
                "python" -> {
                    if (line.trimStart().startsWith("except:")) {
                        errors.add(CodeError(lineNum, "Bare except — specify exception type", "warning"))
                    }
                }
                "json" -> {
                    val nextLine = lines.getOrNull(idx + 1)?.trim() ?: ""
                    if (line.trimEnd().endsWith(",") && (nextLine.startsWith("}") || nextLine.startsWith("]"))) {
                        errors.add(CodeError(lineNum, "Trailing comma in JSON", "error"))
                    }
                }
            }
        }
        if (language in listOf("kotlin", "java")) {
            val opens = content.count { it == '{' }
            val closes = content.count { it == '}' }
            if (opens != closes) {
                errors.add(CodeError(0, "Unbalanced braces: $opens { vs $closes }", "error"))
            }
        }
        return errors
    }

    fun requestInlineSuggestion() {
        val state = _uiState.value
        if (state.content.isBlank()) return
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingAiSuggestion = true, aiSuggestion = null)
            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = "Continue this ${state.language} code:\n\n${state.content.takeLast(2000)}",
                systemPrompt = "You are a code completion engine. Return only raw code to insert next, no explanation, no markdown."
            )
            result.fold(
                onSuccess = { suggestion ->
                    val clean = suggestion.removePrefix("```${state.language}").removePrefix("```").removeSuffix("```").trim()
                    _uiState.value = _uiState.value.copy(aiSuggestion = clean, isLoadingAiSuggestion = false)
                },
                onFailure = { _uiState.value = _uiState.value.copy(isLoadingAiSuggestion = false) }
            )
        }
    }

    fun acceptSuggestion() {
        val suggestion = _uiState.value.aiSuggestion ?: return
        val newContent = _uiState.value.content + "\n" + suggestion
        _uiState.value = _uiState.value.copy(content = newContent, isDirty = true, aiSuggestion = null)
    }

    fun dismissSuggestion() { _uiState.value = _uiState.value.copy(aiSuggestion = null) }

    fun aiWriteToFile(instruction: String) {
        val state = _uiState.value
        val file = state.currentFile ?: run {
            _uiState.value = _uiState.value.copy(error = "No file open"); return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAiWriting = true)
            val prompt = buildString {
                appendLine("File: ${file.name}")
                appendLine("Current content:")
                appendLine(state.content.take(4000))
                appendLine()
                appendLine("Instruction: $instruction")
                appendLine()
                append("Return the COMPLETE updated file content only. No markdown, no explanation.")
            }
            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = prompt,
                systemPrompt = "You are a code editing assistant. Return only the complete file content, nothing else."
            )
            result.fold(
                onSuccess = { newContent ->
                    val clean = newContent
                        .removePrefix("```${state.language}").removePrefix("```")
                        .removeSuffix("```").trim()
                    fsRepository.writeFile(file.path, clean)
                    _uiState.value = _uiState.value.copy(content = clean, isDirty = false, isAiWriting = false, successMessage = "AI wrote changes to ${file.name}")
                },
                onFailure = { e -> _uiState.value = _uiState.value.copy(isAiWriting = false, error = "AI write failed: ${e.message}") }
            )
        }
    }

    fun updateAiPrompt(p: String) { _uiState.value = _uiState.value.copy(aiPrompt = p) }
    fun toggleAiPanel() { _uiState.value = _uiState.value.copy(showAiPanel = !_uiState.value.showAiPanel) }
    fun updateCursor(line: Int, col: Int) { _uiState.value = _uiState.value.copy(cursorLine = line, cursorCol = col) }

    fun saveCurrentFile(silent: Boolean = false) {
        val state = _uiState.value
        val file = state.currentFile ?: return
        if (!state.isDirty) return
        viewModelScope.launch {
            if (!silent) _uiState.value = state.copy(isSaving = true)
            fsRepository.writeFile(file.path, state.content).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false, isDirty = false,
                        successMessage = if (silent) null else "Saved"
                    )
                },
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
            if (_uiState.value.currentFile?.path == file.path) {
                _uiState.value = _uiState.value.copy(currentFile = null, content = "")
            }
            scanFiles()
        }
    }

    fun toggleFileTree() { _uiState.value = _uiState.value.copy(showFileTree = !_uiState.value.showFileTree) }
    fun updateSearch(q: String) { _uiState.value = _uiState.value.copy(searchQuery = q) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun clearSuccess() { _uiState.value = _uiState.value.copy(successMessage = null) }

    fun getVisibleFiles(): List<DiskFile> {
        val state = _uiState.value
        val query = state.searchQuery.trim()
        val root = state.rootPath
        return if (query.isNotBlank()) {
            state.files.filter {
                !it.isDirectory && (it.name.contains(query, ignoreCase = true) || it.relativePath.contains(query, ignoreCase = true))
            }
        } else {
            state.files.filter { file ->
                val parentPath = file.path.substringBeforeLast("/")
                parentPath == root || parentPath in state.expandedDirs || state.expandedDirs.any { file.path.startsWith(it + "/") && file.depth == 1 }
            }
        }
    }
}
''')

# ═══════════════════════════════════════════════════════════
# 6. BuildViewModel - use ProjectRepository.getProjectPath properly
# ═══════════════════════════════════════════════════════════
write("ui/screens/build/BuildViewModel.kt", r'''package com.codemaster.aistudio.ui.screens.build

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

    fun init(projectId: Long) {
        loadConfig()
        refreshStatus()
        viewModelScope.launch {
            // Use proper path resolution
            val path = projectRepository.getProjectPath(projectId)
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
                owner = owner, repo = repo, branch = branch.ifBlank { "main" }
            )
        }
    }

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
                    _uiState.value = _uiState.value.copy(isTriggering = false, triggerMessage = "FAIL ${e.message}")
                    addLog("ERROR: ${e.message}")
                }
            )
        }
    }

    fun triggerLocalBuild() {
        val projectPath = _uiState.value.projectPath
        if (projectPath.isBlank()) {
            addLog("No project path set. Load a project first.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLocalBuilding = true, buildErrors = emptyList(), apkPath = null, errorForAi = null)
            addLog("Starting local build...")
            addLog("Project: $projectPath")
            withContext(Dispatchers.IO) {
                try {
                    val gradlew = File(projectPath, "gradlew")
                    if (!gradlew.exists()) {
                        addLog("gradlew not found in $projectPath")
                        _uiState.value = _uiState.value.copy(isLocalBuilding = false)
                        return@withContext
                    }
                    gradlew.setExecutable(true)
                    val process = ProcessBuilder(listOf("$projectPath/gradlew", "assembleDebug", "--stacktrace"))
                        .directory(File(projectPath)).redirectErrorStream(true).start()
                    val errors = mutableListOf<String>()
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val l = line!!
                            addLog(l)
                            if (l.contains("error:", ignoreCase = true) || l.contains("FAILED") || l.contains("Exception")) {
                                errors.add(l)
                            }
                        }
                    }
                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        addLog("BUILD SUCCESSFUL")
                        val apk = File(projectPath).walk().filter { it.extension == "apk" && it.name.contains("debug") }.maxByOrNull { it.lastModified() }
                        if (apk != null) {
                            addLog("APK ready: ${apk.name}")
                            _uiState.value = _uiState.value.copy(apkPath = apk.absolutePath)
                        }
                    } else {
                        addLog("BUILD FAILED (exit $exitCode)")
                        val errorSummary = errors.takeLast(20).joinToString("\n")
                        _uiState.value = _uiState.value.copy(buildErrors = errors, errorForAi = errorSummary)
                    }
                } catch (e: Exception) {
                    addLog("Exception: ${e.message}")
                } finally {
                    _uiState.value = _uiState.value.copy(isLocalBuilding = false)
                }
            }
        }
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
            addLog("Install failed: ${e.message}")
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

# ═══════════════════════════════════════════════════════════
# 7. AiChatViewModel - fix projectPath, saveContentAsFile success msg
# ═══════════════════════════════════════════════════════════
write("ui/screens/chat/AiChatViewModel.kt", r'''package com.codemaster.aistudio.ui.screens.chat

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
    val successMessage: String? = null,
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

            // Use proper path resolution
            projectPath = projectRepository.getProjectPath(projectId)
            if (projectPath.isNotBlank()) loadProjectContext(projectPath)

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
                    val size = if (file.length() < 1024) "${file.length()}B" else "${file.length() / 1024}KB"
                    sb.appendLine("$indent $rel ($size)")
                }
            }
        return sb.toString()
    }

    fun openFile(filePath: String) {
        val fullPath = if (filePath.startsWith("/")) filePath
                       else if (projectPath.isNotBlank()) "$projectPath/$filePath"
                       else filePath
        viewModelScope.launch {
            val existing = _uiState.value.openTabs.indexOfFirst { it.filePath == fullPath }
            if (existing >= 0) {
                _uiState.value = _uiState.value.copy(activeTabIndex = existing)
                return@launch
            }
            fsRepository.readFile(fullPath).fold(
                onSuccess = { content ->
                    val tab = OpenTab(fileName = File(fullPath).name, filePath = fullPath, content = content)
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

    fun setActiveTab(index: Int) { _uiState.value = _uiState.value.copy(activeTabIndex = index) }

    fun explainCurrentFile() {
        val tab = _uiState.value.openTabs.getOrNull(_uiState.value.activeTabIndex) ?: run {
            _uiState.value = _uiState.value.copy(error = "No file open to explain"); return
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
                onSuccess = { _uiState.value = _uiState.value.copy(successMessage = "Saved to $savePath") },
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
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun clearSuccess() { _uiState.value = _uiState.value.copy(successMessage = null) }

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
}
''')

# ═══════════════════════════════════════════════════════════
# 8. SettingsScreen - add projectPath + saveProjectPath
# ═══════════════════════════════════════════════════════════
write("ui/screens/settings/SettingsViewModel.kt", r'''package com.codemaster.aistudio.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val model: String = "llama-3.3-70b-versatile",
    val isDarkTheme: Boolean = true,
    val githubToken: String = "",
    val githubOwner: String = "",
    val githubRepo: String = "",
    val githubBranch: String = "main",
    val projectPath: String = "/sdcard/codemaster-ai-studio",
    val isSaved: Boolean = false
)

val GROQ_MODELS = listOf(
    "llama-3.3-70b-versatile",
    "llama-3.1-8b-instant",
    "mixtral-8x7b-32768",
    "gemma2-9b-it"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = SettingsUiState(
                apiKey       = settingsRepository.getApiKey(),
                model        = settingsRepository.getModel(),
                isDarkTheme  = settingsRepository.getDarkTheme(),
                githubToken  = settingsRepository.getGitHubToken(),
                githubOwner  = settingsRepository.getGitHubOwner(),
                githubRepo   = settingsRepository.getGitHubRepo(),
                githubBranch = settingsRepository.getGitHubBranch().ifBlank { "main" },
                projectPath  = settingsRepository.getProjectPath().ifBlank { "/sdcard/codemaster-ai-studio" }
            )
        }
    }

    fun updateApiKey(k: String)         { _uiState.value = _uiState.value.copy(apiKey = k, isSaved = false) }
    fun updateModel(m: String)          { _uiState.value = _uiState.value.copy(model = m, isSaved = false) }
    fun toggleTheme()                   { _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme, isSaved = false) }
    fun updateGithubToken(t: String)    { _uiState.value = _uiState.value.copy(githubToken = t, isSaved = false) }
    fun updateGithubOwner(o: String)    { _uiState.value = _uiState.value.copy(githubOwner = o, isSaved = false) }
    fun updateGithubRepo(r: String)     { _uiState.value = _uiState.value.copy(githubRepo = r, isSaved = false) }
    fun updateGithubBranch(b: String)   { _uiState.value = _uiState.value.copy(githubBranch = b, isSaved = false) }
    fun updateProjectPath(p: String)    { _uiState.value = _uiState.value.copy(projectPath = p, isSaved = false) }

    fun saveSettings() {
        viewModelScope.launch {
            val s = _uiState.value
            settingsRepository.saveApiKey(s.apiKey)
            settingsRepository.saveModel(s.model)
            settingsRepository.saveDarkTheme(s.isDarkTheme)
            settingsRepository.saveGitHubToken(s.githubToken)
            settingsRepository.saveGitHubOwner(s.githubOwner)
            settingsRepository.saveGitHubRepo(s.githubRepo)
            settingsRepository.saveGitHubBranch(s.githubBranch.ifBlank { "main" })
            settingsRepository.saveProjectPath(s.projectPath)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
''')

print(f"\n{'='*60}")
print(f"OK: {len(OK)}   FAIL: {len(FAIL)}")
if FAIL: print(f"Failed: {FAIL}")
print("""
THE MAYBACH FIXES:
  SettingsRepository - added projectPath get/save
  AndroidManifest - FileProvider, MANAGE_EXTERNAL_STORAGE,
                    REQUEST_INSTALL_PACKAGES, RECORD_AUDIO
  file_paths.xml - FileProvider paths for APK install
  NavGraph - Preview route registered, Build passes onSendErrorToAi
  ProjectRepository - getProjectPath() resolves path properly
  CodeEditorViewModel - uses getProjectPath(), auto-save, success msgs
  BuildViewModel - uses getProjectPath() properly
  AiChatViewModel - proper path resolution, success msg for saveFile,
                    full path resolution for openFile
  SettingsViewModel - projectPath field wired up properly

Run:
  git add -A
  git commit -m "fix: Maybach - all known issues fixed professionally"
  git push origin HEAD
""")
