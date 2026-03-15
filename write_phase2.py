#!/usr/bin/env python3
import os

BASE = os.path.expanduser("~/codemaster-ai-studio/app/src/main/java/com/codemaster/aistudio")

files = {}

# ─────────────────────────────────────────────
# 1. DATA MODELS - Models.kt (extended)
# ─────────────────────────────────────────────
files["data/model/Models.kt"] = '''package com.codemaster.aistudio.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val language: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val path: String = ""
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long = -1L,
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0,
    val attachedFileName: String? = null
)

@Entity(tableName = "code_files")
data class CodeFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val path: String,
    val content: String = "",
    val language: String = "kotlin",
    val lastModified: Long = System.currentTimeMillis()
)

data class BuildStatus(
    val runId: String = "",
    val status: String = "idle", // idle, queued, in_progress, completed
    val conclusion: String? = null, // success, failure, cancelled
    val message: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

class StringListConverter {
    @TypeConverter
    fun fromList(list: List<String>): String = Gson().toJson(list)
    @TypeConverter
    fun toList(json: String): List<String> =
        Gson().fromJson(json, object : TypeToken<List<String>>() {}.type)
}
'''

# ─────────────────────────────────────────────
# 2. DATABASE
# ─────────────────────────────────────────────
files["data/CodeMasterDatabase.kt"] = '''package com.codemaster.aistudio.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.codemaster.aistudio.data.dao.ChatMessageDao
import com.codemaster.aistudio.data.dao.CodeFileDao
import com.codemaster.aistudio.data.dao.ProjectDao
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.model.CodeFile
import com.codemaster.aistudio.data.model.Project
import com.codemaster.aistudio.data.model.StringListConverter

@Database(
    entities = [Project::class, ChatMessage::class, CodeFile::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class CodeMasterDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun codeFileDao(): CodeFileDao
}
'''

# ─────────────────────────────────────────────
# 3. DAOs
# ─────────────────────────────────────────────
files["data/dao/ProjectDao.kt"] = '''package com.codemaster.aistudio.data.dao

import androidx.room.*
import com.codemaster.aistudio.data.model.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastModified DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project): Long

    @Update
    suspend fun updateProject(project: Project)

    @Delete
    suspend fun deleteProject(project: Project)

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Long): Project?
}
'''

files["data/dao/ChatMessageDao.kt"] = '''package com.codemaster.aistudio.data.dao

import androidx.room.*
import com.codemaster.aistudio.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE projectId = :projectId ORDER BY timestamp ASC")
    fun getMessagesForProject(projectId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages WHERE projectId = :projectId")
    suspend fun clearMessagesForProject(projectId: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()

    @Query("SELECT SUM(tokenCount) FROM chat_messages WHERE projectId = :projectId")
    suspend fun getTotalTokensForProject(projectId: Long): Int?
}
'''

files["data/dao/CodeFileDao.kt"] = '''package com.codemaster.aistudio.data.dao

import androidx.room.*
import com.codemaster.aistudio.data.model.CodeFile
import kotlinx.coroutines.flow.Flow

@Dao
interface CodeFileDao {
    @Query("SELECT * FROM code_files WHERE projectId = :projectId ORDER BY name ASC")
    fun getFilesForProject(projectId: Long): Flow<List<CodeFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: CodeFile): Long

    @Update
    suspend fun updateFile(file: CodeFile)

    @Delete
    suspend fun deleteFile(file: CodeFile)

    @Query("SELECT * FROM code_files WHERE id = :id")
    suspend fun getFileById(id: Long): CodeFile?
}
'''

# ─────────────────────────────────────────────
# 4. REPOSITORIES
# ─────────────────────────────────────────────
files["data/repository/ProjectRepository.kt"] = '''package com.codemaster.aistudio.data.repository

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
        val project = Project(
            name = name,
            language = language,
            description = description
        )
        return projectDao.insertProject(project)
    }

    suspend fun deleteProject(project: Project) = projectDao.deleteProject(project)

    suspend fun getProjectById(id: Long): Project? = projectDao.getProjectById(id)

    suspend fun updateProject(project: Project) = projectDao.updateProject(project)
}
'''

files["data/repository/ChatRepository.kt"] = '''package com.codemaster.aistudio.data.repository

import com.codemaster.aistudio.data.dao.ChatMessageDao
import com.codemaster.aistudio.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatMessageDao: ChatMessageDao
) {
    fun getMessagesForProject(projectId: Long): Flow<List<ChatMessage>> =
        chatMessageDao.getMessagesForProject(projectId)

    fun getAllMessages(): Flow<List<ChatMessage>> = chatMessageDao.getAllMessages()

    suspend fun saveMessage(message: ChatMessage): Long =
        chatMessageDao.insertMessage(message)

    suspend fun clearMessagesForProject(projectId: Long) =
        chatMessageDao.clearMessagesForProject(projectId)

    suspend fun clearAll() = chatMessageDao.clearAll()

    suspend fun getTotalTokens(projectId: Long): Int =
        chatMessageDao.getTotalTokensForProject(projectId) ?: 0
}
'''

files["data/repository/AiRepository.kt"] = '''package com.codemaster.aistudio.data.repository

import com.codemaster.aistudio.data.api.GroqApiService
import com.codemaster.aistudio.data.api.GroqChatRequest
import com.codemaster.aistudio.data.api.GroqMessage
import com.codemaster.aistudio.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val groqApiService: GroqApiService,
    private val settingsRepository: SettingsRepository
) {
    suspend fun sendMessage(
        history: List<ChatMessage>,
        userMessage: String,
        attachedFileContent: String? = null,
        systemPrompt: String = "You are CodeMaster AI, an expert coding assistant. Be concise, helpful, and provide working code examples."
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = settingsRepository.getApiKey()
            if (apiKey.isBlank()) return@withContext Result.failure(Exception("No API key set. Go to Settings."))

            val model = settingsRepository.getModel()

            val messages = mutableListOf<GroqMessage>()
            messages.add(GroqMessage("system", systemPrompt))

            history.takeLast(20).forEach { msg ->
                messages.add(GroqMessage(msg.role, msg.content))
            }

            val finalUserMessage = if (attachedFileContent != null) {
                "$userMessage\n\n<attached_file>\n$attachedFileContent\n</attached_file>"
            } else {
                userMessage
            }
            messages.add(GroqMessage("user", finalUserMessage))

            val request = GroqChatRequest(model = model, messages = messages)
            val response = groqApiService.chat("Bearer $apiKey", request)
            val content = response.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(Exception("Empty response from AI"))

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)
}
'''

# ─────────────────────────────────────────────
# 5. API MODELS
# ─────────────────────────────────────────────
files["data/api/ApiModels.kt"] = '''package com.codemaster.aistudio.data.api

import com.google.gson.annotations.SerializedName

data class GroqMessage(
    val role: String,
    val content: String
)

data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 4096,
    val temperature: Double = 0.7
)

data class GroqChatResponse(
    val id: String = "",
    val choices: List<GroqChoice> = emptyList(),
    val usage: GroqUsage? = null
)

data class GroqChoice(
    val message: GroqMessage,
    @SerializedName("finish_reason") val finishReason: String = ""
)

data class GroqUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0
)
'''

files["data/api/GroqApiService.kt"] = '''package com.codemaster.aistudio.data.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GroqApiService {
    @POST("openai/v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") authorization: String,
        @Body request: GroqChatRequest
    ): GroqChatResponse
}
'''

# ─────────────────────────────────────────────
# 6. DI MODULE
# ─────────────────────────────────────────────
files["di/AppModule.kt"] = '''package com.codemaster.aistudio.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.codemaster.aistudio.data.CodeMasterDatabase
import com.codemaster.aistudio.data.api.GroqApiService
import com.codemaster.aistudio.data.dao.ChatMessageDao
import com.codemaster.aistudio.data.dao.CodeFileDao
import com.codemaster.aistudio.data.dao.ProjectDao
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
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "codemaster_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CodeMasterDatabase =
        Room.databaseBuilder(context, CodeMasterDatabase::class.java, "codemaster_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun provideProjectDao(db: CodeMasterDatabase): ProjectDao = db.projectDao()

    @Provides @Singleton
    fun provideChatMessageDao(db: CodeMasterDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides @Singleton
    fun provideCodeFileDao(db: CodeMasterDatabase): CodeFileDao = db.codeFileDao()

    @Provides @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.groq.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton
    fun provideGroqApiService(retrofit: Retrofit): GroqApiService =
        retrofit.create(GroqApiService::class.java)
}
'''

# ─────────────────────────────────────────────
# 7. NAVIGATION
# ─────────────────────────────────────────────
files["ui/navigation/NavGraph.kt"] = '''package com.codemaster.aistudio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.codemaster.aistudio.ui.screens.build.BuildScreen
import com.codemaster.aistudio.ui.screens.chat.AiChatScreen
import com.codemaster.aistudio.ui.screens.editor.CodeEditorScreen
import com.codemaster.aistudio.ui.screens.home.HomeScreen
import com.codemaster.aistudio.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Chat : Screen("chat/{projectId}") {
        fun createRoute(projectId: Long = -1L) = "chat/$projectId"
    }
    object Editor : Screen("editor/{projectId}/{fileId}") {
        fun createRoute(projectId: Long, fileId: Long = -1L) = "editor/$projectId/$fileId"
    }
    object Build : Screen("build/{projectId}") {
        fun createRoute(projectId: Long = -1L) = "build/$projectId"
    }
    object Settings : Screen("settings")
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onOpenChat = { projectId -> navController.navigate(Screen.Chat.createRoute(projectId)) },
                onOpenEditor = { projectId -> navController.navigate(Screen.Editor.createRoute(projectId)) },
                onOpenBuild = { projectId -> navController.navigate(Screen.Build.createRoute(projectId)) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
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
                navArgument("fileId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { back ->
            CodeEditorScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                fileId = back.arguments?.getLong("fileId") ?: -1L,
                onBack = { navController.popBackStack() },
                onOpenChat = { projectId -> navController.navigate(Screen.Chat.createRoute(projectId)) }
            )
        }
        composable(
            Screen.Build.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            BuildScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
'''

# ─────────────────────────────────────────────
# 8. HOME SCREEN (with New Project FAB)
# ─────────────────────────────────────────────
files["ui/screens/home/HomeViewModel.kt"] = '''package com.codemaster.aistudio.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.Project
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
    val isLoading: Boolean = false,
    val showNewProjectDialog: Boolean = false,
    val newProjectName: String = "",
    val newProjectLanguage: String = "Kotlin",
    val newProjectDescription: String = "",
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            projectRepository.getAllProjects()
                .catch { e -> _uiState.value = _uiState.value.copy(error = e.message, isLoading = false) }
                .collect { projects ->
                    _uiState.value = _uiState.value.copy(projects = projects, isLoading = false)
                }
        }
    }

    fun showNewProjectDialog() { _uiState.value = _uiState.value.copy(showNewProjectDialog = true) }
    fun hideNewProjectDialog() { _uiState.value = _uiState.value.copy(showNewProjectDialog = false, newProjectName = "", newProjectLanguage = "Kotlin", newProjectDescription = "") }
    fun updateProjectName(name: String) { _uiState.value = _uiState.value.copy(newProjectName = name) }
    fun updateProjectLanguage(lang: String) { _uiState.value = _uiState.value.copy(newProjectLanguage = lang) }
    fun updateProjectDescription(desc: String) { _uiState.value = _uiState.value.copy(newProjectDescription = desc) }

    fun createProject(onCreated: (Long) -> Unit) {
        val state = _uiState.value
        if (state.newProjectName.isBlank()) {
            _uiState.value = state.copy(error = "Project name cannot be empty")
            return
        }
        viewModelScope.launch {
            val id = projectRepository.createProject(
                name = state.newProjectName.trim(),
                language = state.newProjectLanguage,
                description = state.newProjectDescription.trim()
            )
            hideNewProjectDialog()
            onCreated(id)
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch { projectRepository.deleteProject(project) }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
'''

files["ui/screens/home/HomeScreen.kt"] = '''package com.codemaster.aistudio.ui.screens.home

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

val LANGUAGES = listOf("Kotlin", "Java", "Python", "JavaScript", "TypeScript", "Rust", "Go", "C++", "Swift", "Dart")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenChat: (Long) -> Unit,
    onOpenEditor: (Long) -> Unit,
    onOpenBuild: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("CodeMaster AI", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showNewProjectDialog() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Project") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.projects.isEmpty() -> {
                    EmptyProjectsPlaceholder(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                "Projects (${uiState.projects.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(uiState.projects, key = { it.id }) { project ->
                            ProjectCard(
                                project = project,
                                onOpenChat = { onOpenChat(project.id) },
                                onOpenEditor = { onOpenEditor(project.id) },
                                onOpenBuild = { onOpenBuild(project.id) },
                                onDelete = { viewModel.deleteProject(project) }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
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
                name = uiState.newProjectName,
                language = uiState.newProjectLanguage,
                description = uiState.newProjectDescription,
                onNameChange = viewModel::updateProjectName,
                onLanguageChange = viewModel::updateProjectLanguage,
                onDescriptionChange = viewModel::updateProjectDescription,
                onCreate = { viewModel.createProject { id -> onOpenEditor(id) } },
                onDismiss = viewModel::hideNewProjectDialog
            )
        }
    }
}

@Composable
fun ProjectCard(
    project: Project,
    onOpenChat: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenBuild: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = project.language.take(2).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(project.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${project.language} • ${dateFormat.format(Date(project.lastModified))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            if (project.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    project.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProjectActionButton(icon = Icons.Default.ChatBubbleOutline, label = "Chat", onClick = onOpenChat)
                ProjectActionButton(icon = Icons.Default.Code, label = "Editor", onClick = onOpenEditor)
                ProjectActionButton(icon = Icons.Default.Build, label = "Build", onClick = onOpenBuild)
            }
        }
    }
}

@Composable
fun ProjectActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun EmptyProjectsPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(Modifier.height(16.dp))
        Text("No projects yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text("Tap + to create your first project", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectDialog(
    name: String,
    language: String,
    description: String,
    onNameChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    var langExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Project", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Project Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenuBox(
                    expanded = langExpanded,
                    onExpandedChange = { langExpanded = it }
                ) {
                    OutlinedTextField(
                        value = language,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        LANGUAGES.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang) },
                                onClick = { onLanguageChange(lang); langExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = onCreate, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
'''

# ─────────────────────────────────────────────
# 9. AI CHAT SCREEN (with file attachment + token counter + history)
# ─────────────────────────────────────────────
files["ui/screens/chat/AiChatViewModel.kt"] = '''package com.codemaster.aistudio.ui.screens.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.repository.AiRepository
import com.codemaster.aistudio.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val attachedFileName: String? = null,
    val attachedFileContent: String? = null,
    val totalTokens: Int = 0,
    val error: String? = null,
    val projectName: String = "Chat"
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentProjectId: Long = -1L

    fun init(projectId: Long) {
        currentProjectId = projectId
        viewModelScope.launch {
            chatRepository.getMessagesForProject(projectId)
                .catch { }
                .collect { messages ->
                    val tokens = messages.sumOf { it.tokenCount }
                    _uiState.value = _uiState.value.copy(messages = messages, totalTokens = tokens)
                }
        }
    }

    fun updateInput(text: String) { _uiState.value = _uiState.value.copy(inputText = text) }

    fun attachFile(fileName: String, content: String) {
        _uiState.value = _uiState.value.copy(
            attachedFileName = fileName,
            attachedFileContent = content
        )
    }

    fun clearAttachment() {
        _uiState.value = _uiState.value.copy(attachedFileName = null, attachedFileContent = null)
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank() && state.attachedFileContent == null) return
        if (state.isLoading) return

        val displayText = if (state.attachedFileName != null) "$text\n📎 ${state.attachedFileName}" else text

        viewModelScope.launch {
            val userMessage = ChatMessage(
                projectId = currentProjectId,
                role = "user",
                content = displayText,
                tokenCount = aiRepository.estimateTokens(displayText),
                attachedFileName = state.attachedFileName
            )
            chatRepository.saveMessage(userMessage)
            _uiState.value = state.copy(inputText = "", isLoading = true, attachedFileName = null, attachedFileContent = null)

            val result = aiRepository.sendMessage(
                history = _uiState.value.messages.dropLast(1),
                userMessage = text,
                attachedFileContent = state.attachedFileContent
            )

            result.fold(
                onSuccess = { response ->
                    val aiMessage = ChatMessage(
                        projectId = currentProjectId,
                        role = "assistant",
                        content = response,
                        tokenCount = aiRepository.estimateTokens(response)
                    )
                    chatRepository.saveMessage(aiMessage)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            chatRepository.clearMessagesForProject(currentProjectId)
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
'''

files["ui/screens/chat/AiChatScreen.kt"] = '''package com.codemaster.aistudio.ui.screens.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codemaster.aistudio.data.model.ChatMessage
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    projectId: Long,
    onBack: () -> Unit,
    viewModel: AiChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(projectId) { viewModel.init(projectId) }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val fileName = context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "attachment"
            val content = context.contentResolver.openInputStream(it)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            } ?: ""
            viewModel.attachFile(fileName, content.take(8000))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Chat", fontWeight = FontWeight.Bold)
                        Text(
                            "~${uiState.totalTokens} tokens used",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearHistory() }) {
                        Icon(Icons.Default.DeleteSweep, "Clear history")
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                text = uiState.inputText,
                attachedFileName = uiState.attachedFileName,
                isLoading = uiState.isLoading,
                onTextChange = viewModel::updateInput,
                onAttach = { filePicker.launch("*/*") },
                onClearAttachment = viewModel::clearAttachment,
                onSend = {
                    viewModel.sendMessage()
                    scope.launch {
                        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.messages.isEmpty()) {
                ChatEmptyState(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatBubble(message = message)
                    }
                    if (uiState.isLoading) {
                        item { TypingIndicator() }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    action = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
                ) { Text(error) }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("AI", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(modifier = Modifier.widthIn(max = 300.dp)) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isUser) 16.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(12.dp)
            ) {
                SelectionContainer {
                    if (!isUser && message.content.contains("```")) {
                        CodeAwareText(message.content, isUser)
                    } else {
                        Text(
                            text = message.content,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (!isUser) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "~${message.tokenCount} tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(message.content)) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        if (isUser) Spacer(Modifier.width(40.dp))
    }
}

@Composable
fun CodeAwareText(content: String, isUser: Boolean) {
    val clipboardManager = LocalClipboardManager.current
    val parts = content.split("```")
    Column {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 0) {
                if (part.isNotBlank()) {
                    Text(
                        text = part.trim(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                val lines = part.lines()
                val code = if (lines.firstOrNull()?.all { it.isLetter() } == true) lines.drop(1).joinToString("\\n") else part
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                ) {
                    Text(
                        text = code.trim(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(code.trim())) },
                        modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Copy code", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text("AI", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Card(shape = RoundedCornerShape(16.dp)) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Thinking...", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ChatEmptyState(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("CodeMaster AI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Ask me anything about your code.\nAttach files for context.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ChatInputBar(
    text: String,
    attachedFileName: String?,
    isLoading: Boolean,
    onTextChange: (String) -> Unit,
    onAttach: () -> Unit,
    onClearAttachment: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            AnimatedVisibility(visible = attachedFileName != null) {
                attachedFileName?.let { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSecondaryContainer)
                        IconButton(onClick = onClearAttachment, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = onAttach, enabled = !isLoading) {
                    Icon(
                        Icons.Default.AttachFile,
                        "Attach file",
                        tint = if (attachedFileName != null) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask AI anything...") },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onSend,
                    enabled = (text.isNotBlank() || attachedFileName != null) && !isLoading,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Send, "Send")
                    }
                }
            }
        }
    }
}
'''

# ─────────────────────────────────────────────
# 10. CODE EDITOR SCREEN (with file tree + syntax highlighting)
# ─────────────────────────────────────────────
files["ui/screens/editor/CodeEditorViewModel.kt"] = '''package com.codemaster.aistudio.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.CodeFile
import com.codemaster.aistudio.data.repository.ProjectRepository
import com.codemaster.aistudio.data.dao.CodeFileDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val files: List<CodeFile> = emptyList(),
    val currentFile: CodeFile? = null,
    val content: String = "",
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val showFileTree: Boolean = true,
    val showNewFileDialog: Boolean = false,
    val newFileName: String = "",
    val error: String? = null,
    val projectName: String = ""
)

@HiltViewModel
class CodeEditorViewModel @Inject constructor(
    private val codeFileDao: CodeFileDao,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var currentProjectId: Long = -1L

    fun init(projectId: Long, fileId: Long) {
        currentProjectId = projectId
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            _uiState.value = _uiState.value.copy(projectName = project?.name ?: "Editor")

            codeFileDao.getFilesForProject(projectId)
                .catch { }
                .collect { files ->
                    _uiState.value = _uiState.value.copy(files = files)
                    if (_uiState.value.currentFile == null && files.isNotEmpty()) {
                        val target = if (fileId != -1L) files.find { it.id == fileId } else files.first()
                        target?.let { openFile(it) }
                    }
                }
        }
    }

    fun openFile(file: CodeFile) {
        _uiState.value = _uiState.value.copy(currentFile = file, content = file.content, isDirty = false)
    }

    fun updateContent(content: String) {
        _uiState.value = _uiState.value.copy(content = content, isDirty = true)
    }

    fun saveCurrentFile() {
        val state = _uiState.value
        val file = state.currentFile ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            codeFileDao.updateFile(file.copy(content = state.content, lastModified = System.currentTimeMillis()))
            _uiState.value = _uiState.value.copy(isSaving = false, isDirty = false)
        }
    }

    fun toggleFileTree() { _uiState.value = _uiState.value.copy(showFileTree = !_uiState.value.showFileTree) }
    fun showNewFileDialog() { _uiState.value = _uiState.value.copy(showNewFileDialog = true) }
    fun hideNewFileDialog() { _uiState.value = _uiState.value.copy(showNewFileDialog = false, newFileName = "") }
    fun updateNewFileName(name: String) { _uiState.value = _uiState.value.copy(newFileName = name) }

    fun createFile() {
        val name = _uiState.value.newFileName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            val language = when {
                name.endsWith(".kt") -> "kotlin"
                name.endsWith(".java") -> "java"
                name.endsWith(".py") -> "python"
                name.endsWith(".js") || name.endsWith(".ts") -> "javascript"
                name.endsWith(".xml") -> "xml"
                else -> "text"
            }
            val id = codeFileDao.insertFile(
                CodeFile(projectId = currentProjectId, name = name, path = name, language = language)
            )
            hideNewFileDialog()
            val file = codeFileDao.getFileById(id)
            file?.let { openFile(it) }
        }
    }

    fun deleteFile(file: CodeFile) {
        viewModelScope.launch {
            codeFileDao.deleteFile(file)
            if (_uiState.value.currentFile?.id == file.id) {
                _uiState.value = _uiState.value.copy(currentFile = null, content = "")
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
'''

files["ui/screens/editor/CodeEditorScreen.kt"] = '''package com.codemaster.aistudio.ui.screens.editor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codemaster.aistudio.data.model.CodeFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    projectId: Long,
    fileId: Long,
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit,
    viewModel: CodeEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(projectId, fileId) { viewModel.init(projectId, fileId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            uiState.currentFile?.name ?: uiState.projectName,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (uiState.isDirty) {
                            Spacer(Modifier.width(4.dp))
                            Text("●", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFileTree() }) {
                        Icon(Icons.Default.AccountTree, "File tree")
                    }
                    if (uiState.isDirty) {
                        IconButton(onClick = { viewModel.saveCurrentFile() }) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, "Save")
                            }
                        }
                    }
                    IconButton(onClick = { onOpenChat(projectId) }) {
                        Icon(Icons.Default.ChatBubbleOutline, "Chat")
                    }
                }
            )
        }
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(
                visible = uiState.showFileTree,
                enter = slideInHorizontally() + fadeIn(),
                exit = slideOutHorizontally() + fadeOut()
            ) {
                FileTreePanel(
                    files = uiState.files,
                    currentFile = uiState.currentFile,
                    onFileClick = { viewModel.openFile(it) },
                    onDeleteFile = { viewModel.deleteFile(it) },
                    onNewFile = { viewModel.showNewFileDialog() },
                    modifier = Modifier.width(200.dp)
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (uiState.currentFile == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Code, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("Select or create a file", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.showNewFileDialog() }) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New File")
                            }
                        }
                    }
                } else {
                    CodeEditorArea(
                        content = uiState.content,
                        onContentChange = { viewModel.updateContent(it) },
                        language = uiState.currentFile?.language ?: "text"
                    )
                }
            }
        }

        if (uiState.showNewFileDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.hideNewFileDialog() },
                title = { Text("New File") },
                text = {
                    OutlinedTextField(
                        value = uiState.newFileName,
                        onValueChange = { viewModel.updateNewFileName(it) },
                        label = { Text("File name (e.g. Main.kt)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = { viewModel.createFile() }, enabled = uiState.newFileName.isNotBlank()) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideNewFileDialog() }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun FileTreePanel(
    files: List<CodeFile>,
    currentFile: CodeFile?,
    onFileClick: (CodeFile) -> Unit,
    onDeleteFile: (CodeFile) -> Unit,
    onNewFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("FILES", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onNewFile, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Add, "New file", modifier = Modifier.size(16.dp))
            }
        }
        HorizontalDivider()
        LazyColumn {
            items(files, key = { it.id }) { file ->
                FileTreeItem(
                    file = file,
                    isSelected = currentFile?.id == file.id,
                    onClick = { onFileClick(file) },
                    onDelete = { onDeleteFile(file) }
                )
            }
        }
    }
}

@Composable
fun FileTreeItem(file: CodeFile, isSelected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val icon = when {
        file.name.endsWith(".kt") -> "🟣"
        file.name.endsWith(".java") -> "☕"
        file.name.endsWith(".py") -> "🐍"
        file.name.endsWith(".js") || file.name.endsWith(".ts") -> "🟡"
        file.name.endsWith(".xml") -> "📄"
        file.name.endsWith(".json") -> "📋"
        file.name.endsWith(".md") -> "📝"
        else -> "📄"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 14.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            file.name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(14.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }
}

@Composable
fun CodeEditorArea(content: String, onContentChange: (String) -> Unit, language: String) {
    val scrollState = rememberScrollState()
    val hScrollState = rememberScrollState()
    val lineCount = content.lines().size

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Line numbers
        Column(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .verticalScroll(scrollState)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.End
        ) {
            repeat(lineCount.coerceAtLeast(1)) { i ->
                Text(
                    text = "${i + 1}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        // Code area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(hScrollState)
                .verticalScroll(scrollState)
                .padding(8.dp)
        ) {
            BasicTextField(
                value = content,
                onValueChange = onContentChange,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 400.dp)
            )
        }
    }
}
'''

# ─────────────────────────────────────────────
# 11. BUILD SCREEN
# ─────────────────────────────────────────────
files["ui/screens/build/BuildScreen.kt"] = '''package com.codemaster.aistudio.ui.screens.build

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
import com.codemaster.aistudio.ui.screens.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildScreen(
    projectId: Long,
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val settingsState by settingsViewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val buildLogs = remember { mutableStateListOf<String>() }
    var buildStatus by remember { mutableStateOf("idle") }

    LaunchedEffect(Unit) {
        buildLogs.add("[CodeMaster] Build system ready.")
        buildLogs.add("[CodeMaster] Configure your GitHub token in Settings to trigger builds.")
        buildLogs.add("[CodeMaster] Repository: github.com/tonygrischke-art/codemaster-ai-studio")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Build", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (buildStatus) {
                        "success" -> Color(0xFF1B5E20)
                        "failure" -> MaterialTheme.colorScheme.errorContainer
                        "in_progress" -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when (buildStatus) {
                            "success" -> Icons.Default.CheckCircle
                            "failure" -> Icons.Default.Error
                            "in_progress" -> Icons.Default.Sync
                            else -> Icons.Default.Circle
                        },
                        contentDescription = null,
                        tint = when (buildStatus) {
                            "success" -> Color(0xFF69F0AE)
                            "failure" -> MaterialTheme.colorScheme.error
                            "in_progress" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Build Status", fontWeight = FontWeight.Bold)
                        Text(
                            buildStatus.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        buildStatus = "in_progress"
                        buildLogs.add("[${System.currentTimeMillis()}] Triggering GitHub Actions build...")
                        buildLogs.add("Push a commit to main branch or configure workflow_dispatch.")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Trigger Build")
                }
                OutlinedButton(
                    onClick = {
                        buildLogs.clear()
                        buildStatus = "idle"
                        buildLogs.add("[CodeMaster] Logs cleared.")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }

            // Build Info
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Build Configuration", fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    BuildInfoRow("Workflow", "Build CodeMaster AI Studio APK")
                    BuildInfoRow("Branch", "main")
                    BuildInfoRow("Runner", "ubuntu-latest")
                    BuildInfoRow("JDK", "17")
                    BuildInfoRow("Gradle", "8.4")
                    BuildInfoRow("Output", "app-debug.apk")
                }
            }

            // Log Output
            Text("Build Log", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    buildLogs.forEach { log ->
                        Text(
                            text = log,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF00FF41),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // GitHub Actions Link info
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Monitor live builds at:\ngithub.com/tonygrischke-art/codemaster-ai-studio/actions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun BuildInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
'''

# ─────────────────────────────────────────────
# 12. SETTINGS SCREEN + VIEWMODEL
# ─────────────────────────────────────────────
files["ui/screens/settings/SettingsViewModel.kt"] = '''package com.codemaster.aistudio.ui.screens.settings

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
            _uiState.value = _uiState.value.copy(
                apiKey = settingsRepository.getApiKey(),
                model = settingsRepository.getModel(),
                isDarkTheme = settingsRepository.getDarkTheme()
            )
        }
    }

    fun updateApiKey(key: String) { _uiState.value = _uiState.value.copy(apiKey = key, isSaved = false) }
    fun updateModel(model: String) { _uiState.value = _uiState.value.copy(model = model, isSaved = false) }
    fun toggleTheme() { _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme, isSaved = false) }

    fun saveSettings() {
        viewModelScope.launch {
            settingsRepository.saveApiKey(_uiState.value.apiKey)
            settingsRepository.saveModel(_uiState.value.model)
            settingsRepository.saveDarkTheme(_uiState.value.isDarkTheme)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
'''

files["ui/screens/settings/SettingsScreen.kt"] = '''package com.codemaster.aistudio.ui.screens.settings

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
            // AI Settings
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

                Spacer(Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = it }
                ) {
                    OutlinedTextField(
                        value = uiState.model,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        GROQ_MODELS.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = { viewModel.updateModel(model); modelExpanded = false },
                                trailingIcon = {
                                    if (model == uiState.model) Icon(Icons.Default.Check, null)
                                }
                            )
                        }
                    }
                }
            }

            // Appearance
            SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (uiState.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (uiState.isDarkTheme) "Dark Theme" else "Light Theme", fontWeight = FontWeight.Medium)
                        Text("Toggle app theme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = uiState.isDarkTheme, onCheckedChange = { viewModel.toggleTheme() })
                }
            }

            // About
            SettingsSection(title = "About", icon = Icons.Default.Info) {
                AboutRow("Version", "1.0.0 Phase 2")
                AboutRow("Build", "Groq-powered")
                AboutRow("Package", "com.codemaster.aistudio")
                AboutRow("Repo", "github.com/tonygrischke-art/codemaster-ai-studio")
            }

            // Save Button
            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Settings", fontWeight = FontWeight.Bold)
            }
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
'''

# ─────────────────────────────────────────────
# 13. SETTINGS REPOSITORY (extended)
# ─────────────────────────────────────────────
files["data/repository/SettingsRepository.kt"] = '''package com.codemaster.aistudio.data.repository

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
        val API_KEY = stringPreferencesKey("groq_api_key")
        val MODEL = stringPreferencesKey("groq_model")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    suspend fun getApiKey(): String =
        dataStore.data.first()[API_KEY] ?: ""

    suspend fun saveApiKey(key: String) {
        dataStore.edit { it[API_KEY] = key }
    }

    suspend fun getModel(): String =
        dataStore.data.first()[MODEL] ?: "llama-3.3-70b-versatile"

    suspend fun saveModel(model: String) {
        dataStore.edit { it[MODEL] = model }
    }

    suspend fun getDarkTheme(): Boolean =
        dataStore.data.first()[DARK_THEME] ?: true

    suspend fun saveDarkTheme(dark: Boolean) {
        dataStore.edit { it[DARK_THEME] = dark }
    }
}
'''

# ─────────────────────────────────────────────
# 14. THEME (with dark/light toggle support)
# ─────────────────────────────────────────────
files["ui/theme/Theme.kt"] = '''package com.codemaster.aistudio.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    secondary = Color(0xFF00BCD4),
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF1C2128),
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
    onSurfaceVariant = Color(0xFF8B949E),
    primaryContainer = Color(0xFF2D1F63),
    secondaryContainer = Color(0xFF003947),
    outline = Color(0xFF30363D),
    outlineVariant = Color(0xFF21262D)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6200EE),
    secondary = Color(0xFF03DAC6),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3F4F6),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFFEDE7F6),
    secondaryContainer = Color(0xFFE0F7FA),
    outline = Color(0xFFD0D7DE)
)

@Composable
fun CodeMasterTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
'''

# ─────────────────────────────────────────────
# 15. MAIN ACTIVITY
# ─────────────────────────────────────────────
files["MainActivity.kt"] = '''package com.codemaster.aistudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.codemaster.aistudio.ui.navigation.NavGraph
import com.codemaster.aistudio.ui.screens.settings.SettingsViewModel
import com.codemaster.aistudio.ui.theme.CodeMasterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()
            CodeMasterTheme(darkTheme = settingsState.isDarkTheme) {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}
'''

# ─────────────────────────────────────────────
# WRITE ALL FILES
# ─────────────────────────────────────────────
written = 0
errors = []

for relative_path, content in files.items():
    full_path = os.path.join(BASE, relative_path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    try:
        with open(full_path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"✅ {relative_path}")
        written += 1
    except Exception as e:
        print(f"❌ {relative_path}: {e}")
        errors.append(relative_path)

print(f"\n{'='*50}")
print(f"✅ Written: {written} files")
if errors:
    print(f"❌ Errors:  {len(errors)} files")
    for e in errors:
        print(f"   - {e}")
else:
    print("🚀 All files written successfully!")
print(f"{'='*50}")
