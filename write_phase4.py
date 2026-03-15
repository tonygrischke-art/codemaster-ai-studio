#!/usr/bin/env python3
import os

BASE = os.path.expanduser("~/codemaster-ai-studio/app/src/main/java/com/codemaster/aistudio")
RES  = os.path.expanduser("~/codemaster-ai-studio/app/src/main")

files = {}

# ─────────────────────────────────────────────────────────────────────────────
# 1. SETTINGS REPOSITORY — add GitHub token + owner + repo
# ─────────────────────────────────────────────────────────────────────────────
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
        val API_KEY       = stringPreferencesKey("groq_api_key")
        val MODEL         = stringPreferencesKey("groq_model")
        val DARK_THEME    = booleanPreferencesKey("dark_theme")
        val GITHUB_TOKEN  = stringPreferencesKey("github_token")
        val GITHUB_OWNER  = stringPreferencesKey("github_owner")
        val GITHUB_REPO   = stringPreferencesKey("github_repo")
        val GITHUB_BRANCH = stringPreferencesKey("github_branch")
    }

    suspend fun getApiKey(): String       = dataStore.data.first()[API_KEY] ?: ""
    suspend fun saveApiKey(k: String)     { dataStore.edit { it[API_KEY] = k } }

    suspend fun getModel(): String        = dataStore.data.first()[MODEL] ?: "llama-3.3-70b-versatile"
    suspend fun saveModel(m: String)      { dataStore.edit { it[MODEL] = m } }

    suspend fun getDarkTheme(): Boolean   = dataStore.data.first()[DARK_THEME] ?: true
    suspend fun saveDarkTheme(d: Boolean) { dataStore.edit { it[DARK_THEME] = d } }

    suspend fun getGitHubToken(): String  = dataStore.data.first()[GITHUB_TOKEN] ?: ""
    suspend fun saveGitHubToken(t: String){ dataStore.edit { it[GITHUB_TOKEN] = t } }

    suspend fun getGitHubOwner(): String  = dataStore.data.first()[GITHUB_OWNER] ?: ""
    suspend fun saveGitHubOwner(o: String){ dataStore.edit { it[GITHUB_OWNER] = o } }

    suspend fun getGitHubRepo(): String   = dataStore.data.first()[GITHUB_REPO] ?: ""
    suspend fun saveGitHubRepo(r: String) { dataStore.edit { it[GITHUB_REPO] = r } }

    suspend fun getGitHubBranch(): String = dataStore.data.first()[GITHUB_BRANCH] ?: "main"
    suspend fun saveGitHubBranch(b: String){ dataStore.edit { it[GITHUB_BRANCH] = b } }
}
'''

# ─────────────────────────────────────────────────────────────────────────────
# 2. GITHUB ACTIONS REPOSITORY — trigger workflow via API
# ─────────────────────────────────────────────────────────────────────────────
files["data/repository/GitHubActionsRepository.kt"] = '''package com.codemaster.aistudio.data.repository

import com.codemaster.aistudio.data.api.GitHubApiService
import com.codemaster.aistudio.data.api.WorkflowDispatchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubActionsRepository @Inject constructor(
    private val gitHubApiService: GitHubApiService,
    private val settingsRepository: SettingsRepository
) {
    suspend fun triggerBuild(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token  = settingsRepository.getGitHubToken()
            val owner  = settingsRepository.getGitHubOwner()
            val repo   = settingsRepository.getGitHubRepo()
            val branch = settingsRepository.getGitHubBranch()

            if (token.isBlank())  return@withContext Result.failure(Exception("GitHub token not set. Go to Settings → GitHub."))
            if (owner.isBlank())  return@withContext Result.failure(Exception("GitHub username not set. Go to Settings → GitHub."))
            if (repo.isBlank())   return@withContext Result.failure(Exception("Repository name not set. Go to Settings → GitHub."))

            // Find the workflow file name
            val workflowsResult = runCatching {
                gitHubApiService.listWorkflows("Bearer $token", owner, repo)
            }
            if (workflowsResult.isFailure)
                return@withContext Result.failure(Exception("Could not list workflows. Check token and repo name."))

            val workflows = workflowsResult.getOrThrow()
            val workflow = workflows.workflows.firstOrNull {
                it.name.contains("Build", ignoreCase = true) ||
                it.path.contains("build", ignoreCase = true)
            } ?: workflows.workflows.firstOrNull()
            ?: return@withContext Result.failure(Exception("No workflows found in repo. Push your .github/workflows/build.yml first."))

            gitHubApiService.triggerWorkflow(
                token    = "Bearer $token",
                owner    = owner,
                repo     = repo,
                workflowId = workflow.id.toString(),
                body     = WorkflowDispatchRequest(ref = branch)
            )

            Result.success("✅ Build triggered! Workflow: ${workflow.name}\\nCheck GitHub Actions for progress.")
        } catch (e: Exception) {
            Result.failure(Exception("Trigger failed: ${e.message}"))
        }
    }

    suspend fun getLatestRun(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = settingsRepository.getGitHubToken()
            val owner = settingsRepository.getGitHubOwner()
            val repo  = settingsRepository.getGitHubRepo()

            if (token.isBlank() || owner.isBlank() || repo.isBlank())
                return@withContext Result.failure(Exception("Configure GitHub settings first."))

            val runs = gitHubApiService.getWorkflowRuns("Bearer $token", owner, repo)
            val latest = runs.workflowRuns.firstOrNull()
                ?: return@withContext Result.success("No runs found yet.")

            val status = when {
                latest.conclusion == "success"    -> "✅ SUCCESS"
                latest.conclusion == "failure"    -> "❌ FAILED"
                latest.conclusion == "cancelled"  -> "⚠️ CANCELLED"
                latest.status == "in_progress"    -> "🔄 IN PROGRESS"
                latest.status == "queued"         -> "⏳ QUEUED"
                else -> latest.status.uppercase()
            }

            Result.success("$status\\n${latest.name}\\nCommit: ${latest.headSha.take(7)}\\nStarted: ${latest.createdAt}")
        } catch (e: Exception) {
            Result.failure(Exception("Status check failed: ${e.message}"))
        }
    }
}
'''

# ─────────────────────────────────────────────────────────────────────────────
# 3. GITHUB API SERVICE + MODELS
# ─────────────────────────────────────────────────────────────────────────────
files["data/api/GitHubApiService.kt"] = '''package com.codemaster.aistudio.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

// ── Request bodies ──────────────────────────────────────────────────────────
data class WorkflowDispatchRequest(val ref: String, val inputs: Map<String, String> = emptyMap())

// ── Response models ─────────────────────────────────────────────────────────
data class WorkflowsResponse(val workflows: List<WorkflowItem> = emptyList())

data class WorkflowItem(
    val id: Long = 0,
    val name: String = "",
    val path: String = "",
    val state: String = ""
)

data class WorkflowRunsResponse(
    @SerializedName("workflow_runs") val workflowRuns: List<WorkflowRun> = emptyList()
)

data class WorkflowRun(
    val id: Long = 0,
    val name: String = "",
    val status: String = "",
    val conclusion: String? = null,
    @SerializedName("head_sha") val headSha: String = "",
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("html_url") val htmlUrl: String = ""
)

// ── Retrofit interface ───────────────────────────────────────────────────────
interface GitHubApiService {

    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun listWorkflows(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): WorkflowsResponse

    @POST("repos/{owner}/{repo}/actions/workflows/{workflowId}/dispatches")
    suspend fun triggerWorkflow(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflowId") workflowId: String,
        @Body body: WorkflowDispatchRequest
    )

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getWorkflowRuns(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 5
    ): WorkflowRunsResponse
}
'''

# ─────────────────────────────────────────────────────────────────────────────
# 4. DI MODULE — add GitHub Retrofit client
# ─────────────────────────────────────────────────────────────────────────────
files["di/AppModule.kt"] = '''package com.codemaster.aistudio.di

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
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton fun provideProjectDao(db: CodeMasterDatabase): ProjectDao = db.projectDao()
    @Provides @Singleton fun provideChatMessageDao(db: CodeMasterDatabase): ChatMessageDao = db.chatMessageDao()
    @Provides @Singleton fun provideCodeFileDao(db: CodeMasterDatabase): CodeFileDao = db.codeFileDao()

    @Provides @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton @Named("groq")
    fun provideGroqRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.groq.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton @Named("github")
    fun provideGitHubRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton
    fun provideGroqApiService(@Named("groq") retrofit: Retrofit): GroqApiService =
        retrofit.create(GroqApiService::class.java)

    @Provides @Singleton
    fun provideGitHubApiService(@Named("github") retrofit: Retrofit): GitHubApiService =
        retrofit.create(GitHubApiService::class.java)
}
'''

# ─────────────────────────────────────────────────────────────────────────────
# 5. BUILD SCREEN — 1-button trigger + live status + setup instructions
# ─────────────────────────────────────────────────────────────────────────────
files["ui/screens/build/BuildScreen.kt"] = '''package com.codemaster.aistudio.ui.screens.build

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
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
    onGoToSettings: () -> Unit = {},
    viewModel: BuildViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showSetupGuide by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Build", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { showSetupGuide = !showSetupGuide }) {
                        Icon(Icons.Default.Help, "Setup guide")
                    }
                    IconButton(onClick = { viewModel.refreshStatus() }) {
                        Icon(Icons.Default.Refresh, "Refresh status")
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
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Setup guide (expandable) ───────────────────────────────
            AnimatedVisibility(visible = showSetupGuide) {
                SetupGuideCard(onGoToSettings = onGoToSettings)
            }

            // ── Config status card ─────────────────────────────────────
            ConfigStatusCard(
                isConfigured = uiState.isConfigured,
                owner = uiState.owner,
                repo = uiState.repo,
                branch = uiState.branch,
                onGoToSettings = onGoToSettings
            )

            // ── BIG TRIGGER BUTTON ─────────────────────────────────────
            Button(
                onClick = { viewModel.triggerBuild() },
                enabled = uiState.isConfigured && !uiState.isTriggering,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (uiState.isTriggering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Triggering Build...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("🚀 Trigger GitHub Build", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!uiState.isConfigured) {
                Text(
                    "⚠️ Configure GitHub settings first — tap ⚙️ or the Help button above",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // ── Trigger result message ────────────────────────────────
            uiState.triggerMessage?.let { msg ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (msg.startsWith("✅"))
                            Color(0xFF1B5E20) else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            if (msg.startsWith("✅")) Icons.Default.CheckCircle else Icons.Default.Error,
                            null,
                            tint = if (msg.startsWith("✅")) Color(0xFF69F0AE) else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            msg,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // ── Latest run status ──────────────────────────────────────
            uiState.latestRunStatus?.let { status ->
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timeline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Latest Run", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(status, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }

            // ── Build config info ──────────────────────────────────────
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Build Configuration", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    HorizontalDivider()
                    BuildInfoRow("Runner",   "ubuntu-latest")
                    BuildInfoRow("JDK",      "17 (Temurin)")
                    BuildInfoRow("Gradle",   "8.4")
                    BuildInfoRow("Output",   "app-debug.apk")
                    BuildInfoRow("Retained", "14 days")
                    BuildInfoRow("Owner",    uiState.owner.ifBlank { "Not set" })
                    BuildInfoRow("Repo",     uiState.repo.ifBlank { "Not set" })
                    BuildInfoRow("Branch",   uiState.branch.ifBlank { "main" })
                }
            }

            // ── Log output ─────────────────────────────────────────────
            if (uiState.logs.isNotEmpty()) {
                Text("Activity Log", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        uiState.logs.forEach { log ->
                            Text(log, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF58A6FF), lineHeight = 16.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Setup Guide Card ───────────────────────────────────────────────────────
@Composable
fun SetupGuideCard(onGoToSettings: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Setup Guide", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()

            SetupStep(
                number = "1",
                title = "Create GitHub Account",
                description = "Go to github.com and sign up if you haven't already."
            )
            SetupStep(
                number = "2",
                title = "Create a Repository",
                description = "On GitHub: New → Repository name: codemaster-ai-studio → Public → Create. Or use your existing repo."
            )
            SetupStep(
                number = "3",
                title = "Add Workflow File",
                description = "Your repo needs .github/workflows/build.yml — this is already in your repo from the setup we did."
            )
            SetupStep(
                number = "4",
                title = "Create Personal Access Token",
                description = "GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token → Check: repo + workflow scopes → Generate → COPY IT NOW (shown once only)."
            )
            SetupStep(
                number = "5",
                title = "Enter Settings in This App",
                description = "Go to Settings → GitHub section → Enter your token, GitHub username, repo name, and branch (main)."
            )
            SetupStep(
                number = "6",
                title = "Trigger Your First Build",
                description = "Come back here and tap 🚀 Trigger GitHub Build. Watch GitHub Actions at github.com/YOUR_USERNAME/YOUR_REPO/actions"
            )
            SetupStep(
                number = "7",
                title = "Download Your APK",
                description = "When build turns green ✅ → click the run → Artifacts → Download CodeMaster-debug-apk → install on device."
            )

            Button(
                onClick = onGoToSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Go to Settings to Enter Token")
            }
        }
    }
}

@Composable
fun SetupStep(number: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
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
fun ConfigStatusCard(
    isConfigured: Boolean,
    owner: String,
    repo: String,
    branch: String,
    onGoToSettings: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConfigured)
                Color(0xFF1B5E20).copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                null,
                tint = if (isConfigured) Color(0xFF69F0AE) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isConfigured) "GitHub configured ✅" else "GitHub not configured",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (isConfigured) {
                    Text(
                        "$owner/$repo @ $branch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Token, username, and repo required",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            IconButton(onClick = onGoToSettings) {
                Icon(Icons.Default.Settings, "Settings")
            }
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
'''

# ─────────────────────────────────────────────────────────────────────────────
# 6. BUILD VIEWMODEL
# ─────────────────────────────────────────────────────────────────────────────
files["ui/screens/build/BuildViewModel.kt"] = '''package com.codemaster.aistudio.ui.screens.build

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.GitHubActionsRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BuildUiState(
    val isConfigured: Boolean = false,
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val isTriggering: Boolean = false,
    val triggerMessage: String? = null,
    val latestRunStatus: String? = null,
    val logs: List<String> = emptyList()
)

@HiltViewModel
class BuildViewModel @Inject constructor(
    private val gitHubActionsRepository: GitHubActionsRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
        refreshStatus()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val token  = settingsRepository.getGitHubToken()
            val owner  = settingsRepository.getGitHubOwner()
            val repo   = settingsRepository.getGitHubRepo()
            val branch = settingsRepository.getGitHubBranch()
            _uiState.value = _uiState.value.copy(
                isConfigured = token.isNotBlank() && owner.isNotBlank() && repo.isNotBlank(),
                owner = owner,
                repo = repo,
                branch = branch.ifBlank { "main" }
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
                    _uiState.value = _uiState.value.copy(
                        isTriggering = false,
                        triggerMessage = msg
                    )
                    addLog(msg)
                    // Auto-refresh status after trigger
                    kotlinx.coroutines.delay(3000)
                    refreshStatus()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isTriggering = false,
                        triggerMessage = "❌ ${e.message}"
                    )
                    addLog("ERROR: ${e.message}")
                }
            )
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            loadConfig()
            val state = _uiState.value
            if (!state.isConfigured) return@launch

            val result = gitHubActionsRepository.getLatestRun()
            result.fold(
                onSuccess = { status ->
                    _uiState.value = _uiState.value.copy(latestRunStatus = status)
                },
                onFailure = { }
            )
        }
    }

    private fun addLog(message: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logs = (_uiState.value.logs + "[$ts] $message").takeLast(20)
        _uiState.value = _uiState.value.copy(logs = logs)
    }
}
'''

# ─────────────────────────────────────────────────────────────────────────────
# 7. SETTINGS SCREEN — add GitHub section
# ─────────────────────────────────────────────────────────────────────────────
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
    val githubToken: String = "",
    val githubOwner: String = "",
    val githubRepo: String = "",
    val githubBranch: String = "main",
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
                githubBranch = settingsRepository.getGitHubBranch().ifBlank { "main" }
            )
        }
    }

    fun updateApiKey(k: String)       { _uiState.value = _uiState.value.copy(apiKey = k, isSaved = false) }
    fun updateModel(m: String)        { _uiState.value = _uiState.value.copy(model = m, isSaved = false) }
    fun toggleTheme()                 { _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme, isSaved = false) }
    fun updateGithubToken(t: String)  { _uiState.value = _uiState.value.copy(githubToken = t, isSaved = false) }
    fun updateGithubOwner(o: String)  { _uiState.value = _uiState.value.copy(githubOwner = o, isSaved = false) }
    fun updateGithubRepo(r: String)   { _uiState.value = _uiState.value.copy(githubRepo = r, isSaved = false) }
    fun updateGithubBranch(b: String) { _uiState.value = _uiState.value.copy(githubBranch = b, isSaved = false) }

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
    var showGithubToken by remember { mutableStateOf(false) }
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

            // ── AI Configuration ───────────────────────────────────────
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
                Text(
                    "Get your free key at: console.groq.com",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))

                ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = it }) {
                    OutlinedTextField(
                        value = uiState.model,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("AI Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        GROQ_MODELS.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = { viewModel.updateModel(model); modelExpanded = false },
                                trailingIcon = { if (model == uiState.model) Icon(Icons.Default.Check, null) }
                            )
                        }
                    }
                }
            }

            // ── GitHub Configuration ───────────────────────────────────
            SettingsSection(title = "GitHub — Build Trigger", icon = Icons.Default.CloudUpload) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("How to get your token:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text("1. Go to github.com → your avatar → Settings", style = MaterialTheme.typography.labelSmall)
                        Text("2. Developer settings → Personal access tokens → Tokens (classic)", style = MaterialTheme.typography.labelSmall)
                        Text("3. Generate new token → check: repo + workflow", style = MaterialTheme.typography.labelSmall)
                        Text("4. Copy and paste below — it's only shown ONCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = uiState.githubToken,
                    onValueChange = { viewModel.updateGithubToken(it) },
                    label = { Text("GitHub Personal Access Token") },
                    placeholder = { Text("github_pat_...") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showGithubToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showGithubToken = !showGithubToken }) {
                            Icon(if (showGithubToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.githubOwner,
                    onValueChange = { viewModel.updateGithubOwner(it) },
                    label = { Text("GitHub Username") },
                    placeholder = { Text("tonygrischke-art") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, null) }
                )

                OutlinedTextField(
                    value = uiState.githubRepo,
                    onValueChange = { viewModel.updateGithubRepo(it) },
                    label = { Text("Repository Name") },
                    placeholder = { Text("codemaster-ai-studio") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.FolderOpen, null) }
                )

                OutlinedTextField(
                    value = uiState.githubBranch,
                    onValueChange = { viewModel.updateGithubBranch(it) },
                    label = { Text("Branch") },
                    placeholder = { Text("main") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.AccountTree, null) }
                )
            }

            // ── Appearance ─────────────────────────────────────────────
            SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (uiState.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        null, tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (uiState.isDarkTheme) "Dark Theme" else "Light Theme", fontWeight = FontWeight.Medium)
                        Text("Toggle app theme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = uiState.isDarkTheme, onCheckedChange = { viewModel.toggleTheme() })
                }
            }

            // ── About ──────────────────────────────────────────────────
            SettingsSection(title = "About", icon = Icons.Default.Info) {
                AboutRow("Version", "2.0.0 Phase 4")
                AboutRow("AI",      "Groq API")
                AboutRow("Build",   "GitHub Actions")
                AboutRow("Package", "com.codemaster.aistudio")
            }

            // ── Save Button ────────────────────────────────────────────
            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save All Settings", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(32.dp))
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

# ─────────────────────────────────────────────────────────────────────────────
# 8. NAV GRAPH — wire onGoToSettings in BuildScreen
# ─────────────────────────────────────────────────────────────────────────────
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
import com.codemaster.aistudio.ui.screens.terminal.TerminalScreen

sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object Settings : Screen("settings")
    object Chat : Screen("chat/{projectId}") {
        fun createRoute(projectId: Long = -1L) = "chat/$projectId"
    }
    object Editor : Screen("editor/{projectId}/{fileId}") {
        fun createRoute(projectId: Long, fileId: Long = -1L) = "editor/$projectId/$fileId"
    }
    object Build : Screen("build/{projectId}") {
        fun createRoute(projectId: Long = -1L) = "build/$projectId"
    }
    object Terminal : Screen("terminal/{projectId}") {
        fun createRoute(projectId: Long = -1L) = "terminal/$projectId"
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onOpenChat     = { navController.navigate(Screen.Chat.createRoute(it)) },
                onOpenEditor   = { navController.navigate(Screen.Editor.createRoute(it)) },
                onOpenBuild    = { navController.navigate(Screen.Build.createRoute(it)) },
                onOpenTerminal = { navController.navigate(Screen.Terminal.createRoute(it)) },
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
            BuildScreen(
                projectId     = back.arguments?.getLong("projectId") ?: -1L,
                onBack        = { navController.popBackStack() },
                onGoToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(
            Screen.Terminal.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            TerminalScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                onBack    = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
'''

# ─────────────────────────────────────────────────────────────────────────────
# WRITE ALL FILES
# ─────────────────────────────────────────────────────────────────────────────
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

print(f"\n{'='*52}")
print(f"✅ Written : {written} files")
if errors:
    print(f"❌ Errors  : {len(errors)}")
    for e in errors: print(f"   - {e}")
else:
    print("🚀 Phase 4 complete — ready to commit!")
print(f"{'='*52}")
