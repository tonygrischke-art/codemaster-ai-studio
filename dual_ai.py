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

print("\n DUAL AI ENGINE - Claude + Kimi Collaboration\n")

# ═══════════════════════════════════════════════════════════
# 1. Kimi API Service (moonshot-v1)
# ═══════════════════════════════════════════════════════════
write("data/api/KimiApiService.kt", r'''package com.codemaster.aistudio.data.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class KimiMessage(val role: String, val content: String)
data class KimiChatRequest(
    val model: String = "moonshot-v1-128k",
    val messages: List<KimiMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 8000
)
data class KimiChoice(val message: KimiMessage)
data class KimiChatResponse(val choices: List<KimiChoice>)

interface KimiApiService {
    @POST("v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Body request: KimiChatRequest
    ): KimiChatResponse
}
''')

# ═══════════════════════════════════════════════════════════
# 2. Claude API Service (Anthropic)
# ═══════════════════════════════════════════════════════════
write("data/api/ClaudeApiService.kt", r'''package com.codemaster.aistudio.data.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class ClaudeMessage(val role: String, val content: String)
data class ClaudeChatRequest(
    val model: String = "claude-opus-4-5",
    val max_tokens: Int = 8000,
    val system: String? = null,
    val messages: List<ClaudeMessage>
)
data class ClaudeContent(val type: String, val text: String)
data class ClaudeChatResponse(val content: List<ClaudeContent>)

interface ClaudeApiService {
    @POST("v1/messages")
    suspend fun chat(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Header("content-type") contentType: String = "application/json",
        @Body request: ClaudeChatRequest
    ): ClaudeChatResponse
}
''')

# ═══════════════════════════════════════════════════════════
# 3. SettingsRepository - add Claude + Kimi keys
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
        val API_KEY             = stringPreferencesKey("groq_api_key")
        val CLAUDE_API_KEY      = stringPreferencesKey("claude_api_key")
        val KIMI_API_KEY        = stringPreferencesKey("kimi_api_key")
        val MODEL               = stringPreferencesKey("groq_model")
        val DARK_THEME          = booleanPreferencesKey("dark_theme")
        val GITHUB_TOKEN        = stringPreferencesKey("github_token")
        val GITHUB_OWNER        = stringPreferencesKey("github_owner")
        val GITHUB_REPO         = stringPreferencesKey("github_repo")
        val GITHUB_BRANCH       = stringPreferencesKey("github_branch")
        val AI_PERSONA          = stringPreferencesKey("ai_persona")
        val PROJECT_PATH        = stringPreferencesKey("project_path")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val TOTAL_TOKENS_USED   = stringPreferencesKey("total_tokens_used")
    }

    suspend fun getApiKey(): String              = dataStore.data.first()[API_KEY] ?: ""
    suspend fun saveApiKey(k: String)            { dataStore.edit { it[API_KEY] = k } }
    suspend fun getClaudeApiKey(): String        = dataStore.data.first()[CLAUDE_API_KEY] ?: ""
    suspend fun saveClaudeApiKey(k: String)      { dataStore.edit { it[CLAUDE_API_KEY] = k } }
    suspend fun getKimiApiKey(): String          = dataStore.data.first()[KIMI_API_KEY] ?: ""
    suspend fun saveKimiApiKey(k: String)        { dataStore.edit { it[KIMI_API_KEY] = k } }
    suspend fun getModel(): String               = dataStore.data.first()[MODEL] ?: "llama-3.3-70b-versatile"
    suspend fun saveModel(m: String)             { dataStore.edit { it[MODEL] = m } }
    suspend fun getDarkTheme(): Boolean          = dataStore.data.first()[DARK_THEME] ?: true
    suspend fun saveDarkTheme(d: Boolean)        { dataStore.edit { it[DARK_THEME] = d } }
    suspend fun getGitHubToken(): String         = dataStore.data.first()[GITHUB_TOKEN] ?: ""
    suspend fun saveGitHubToken(t: String)       { dataStore.edit { it[GITHUB_TOKEN] = t } }
    suspend fun getGitHubOwner(): String         = dataStore.data.first()[GITHUB_OWNER] ?: ""
    suspend fun saveGitHubOwner(o: String)       { dataStore.edit { it[GITHUB_OWNER] = o } }
    suspend fun getGitHubRepo(): String          = dataStore.data.first()[GITHUB_REPO] ?: ""
    suspend fun saveGitHubRepo(r: String)        { dataStore.edit { it[GITHUB_REPO] = r } }
    suspend fun getGitHubBranch(): String        = dataStore.data.first()[GITHUB_BRANCH] ?: "main"
    suspend fun saveGitHubBranch(b: String)      { dataStore.edit { it[GITHUB_BRANCH] = b } }
    suspend fun getPersonaId(): String           = dataStore.data.first()[AI_PERSONA] ?: "codemaster"
    suspend fun savePersonaId(id: String)        { dataStore.edit { it[AI_PERSONA] = id } }
    suspend fun getProjectPath(): String         = dataStore.data.first()[PROJECT_PATH] ?: "/sdcard/codemaster-ai-studio"
    suspend fun saveProjectPath(p: String)       { dataStore.edit { it[PROJECT_PATH] = p } }
    suspend fun getOnboardingComplete(): Boolean = dataStore.data.first()[ONBOARDING_COMPLETE] ?: false
    suspend fun saveOnboardingComplete(v: Boolean) { dataStore.edit { it[ONBOARDING_COMPLETE] = v } }
    suspend fun getTotalTokensUsed(): Long       = dataStore.data.first()[TOTAL_TOKENS_USED]?.toLongOrNull() ?: 0L
    suspend fun saveTotalTokensUsed(t: Long)     { dataStore.edit { it[TOTAL_TOKENS_USED] = t.toString() } }
}
''')

# ═══════════════════════════════════════════════════════════
# 4. DualAiRepository - the collaboration engine
# ═══════════════════════════════════════════════════════════
write("data/repository/DualAiRepository.kt", r'''package com.codemaster.aistudio.data.repository

import com.codemaster.aistudio.data.api.ClaudeApiService
import com.codemaster.aistudio.data.api.ClaudeChatRequest
import com.codemaster.aistudio.data.api.ClaudeMessage
import com.codemaster.aistudio.data.api.KimiApiService
import com.codemaster.aistudio.data.api.KimiChatRequest
import com.codemaster.aistudio.data.api.KimiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DualAiProgress(
    val stage: Stage,
    val message: String,
    val claudeVision: String? = null,
    val kimiVision: String? = null,
    val mergedSpec: String? = null,
    val generatedFiles: Map<String, String> = emptyMap(),
    val totalTokens: Int = 0,
    val estimatedCost: String = "$0.00",
    val error: String? = null
) {
    enum class Stage {
        STARTING,
        CLAUDE_THINKING,
        KIMI_THINKING,
        COLLABORATING,
        MERGING,
        GENERATING,
        WRITING_FILES,
        COMPLETE,
        ERROR
    }
}

@Singleton
class DualAiRepository @Inject constructor(
    private val claudeApiService: ClaudeApiService,
    private val kimiApiService: KimiApiService,
    private val settingsRepository: SettingsRepository
) {
    fun buildApp(
        userDescription: String,
        language: String,
        outputPath: String
    ): Flow<DualAiProgress> = flow {

        val claudeKey = settingsRepository.getClaudeApiKey()
        val kimiKey = settingsRepository.getKimiApiKey()
        val groqKey = settingsRepository.getApiKey()

        val hasClaudeKey = claudeKey.isNotBlank()
        val hasKimiKey = kimiKey.isNotBlank()
        val hasGroqKey = groqKey.isNotBlank()

        if (!hasClaudeKey && !hasKimiKey && !hasGroqKey) {
            emit(DualAiProgress(DualAiProgress.Stage.ERROR, "No AI keys configured. Go to Settings.", error = "No API keys"))
            return@flow
        }

        emit(DualAiProgress(DualAiProgress.Stage.STARTING, "Initializing Dual AI collaboration engine..."))

        val visionPrompt = buildString {
            appendLine("You are an elite software architect. A user wants to build the following app:")
            appendLine()
            appendLine("\"$userDescription\"")
            appendLine()
            appendLine("Provide your complete vision for this app covering:")
            appendLine("1. FEATURES: Complete feature list with priorities")
            appendLine("2. TECH STACK: Languages, frameworks, libraries, databases")
            appendLine("3. ARCHITECTURE: System design, patterns, data flow")
            appendLine("4. UI/UX: Screen layouts, user journeys, design principles")
            appendLine("5. SECURITY: Auth, encryption, data protection")
            appendLine("6. SCALABILITY: How it grows from 100 to 1M users")
            appendLine("7. UNIQUE ADVANTAGES: What makes this app special")
            appendLine()
            append("Be specific, creative, and think beyond what was asked. This is your chance to design something exceptional.")
        }

        var claudeVision = ""
        var kimiVision = ""
        var totalTokens = 0

        // Phase 1: Independent visions (parallel if both keys available)
        if (hasClaudeKey && hasKimiKey) {
            emit(DualAiProgress(DualAiProgress.Stage.CLAUDE_THINKING, "Claude and Kimi are independently designing your app..."))
            coroutineScope {
                val claudeJob = async(Dispatchers.IO) {
                    try {
                        val response = claudeApiService.chat(
                            apiKey = claudeKey,
                            version = "2023-06-01",
                            contentType = "application/json",
                            request = ClaudeChatRequest(
                                model = "claude-opus-4-5",
                                max_tokens = 8000,
                                system = "You are Claude, an expert software architect and senior developer.",
                                messages = listOf(ClaudeMessage("user", visionPrompt))
                            )
                        )
                        response.content.firstOrNull()?.text ?: ""
                    } catch (e: Exception) { "Claude vision unavailable: ${e.message}" }
                }
                val kimiJob = async(Dispatchers.IO) {
                    try {
                        val response = kimiApiService.chat(
                            auth = "Bearer $kimiKey",
                            request = KimiChatRequest(
                                model = "moonshot-v1-128k",
                                messages = listOf(
                                    KimiMessage("system", "You are Kimi, an expert software architect and senior developer."),
                                    KimiMessage("user", visionPrompt)
                                )
                            )
                        )
                        response.choices.firstOrNull()?.message?.content ?: ""
                    } catch (e: Exception) { "Kimi vision unavailable: ${e.message}" }
                }
                claudeVision = claudeJob.await()
                kimiVision = kimiJob.await()
                totalTokens += estimateTokens(claudeVision) + estimateTokens(kimiVision)
            }
        } else if (hasClaudeKey) {
            emit(DualAiProgress(DualAiProgress.Stage.CLAUDE_THINKING, "Claude is designing your app..."))
            claudeVision = withContext(Dispatchers.IO) {
                try {
                    val response = claudeApiService.chat(
                        apiKey = claudeKey,
                        version = "2023-06-01",
                        contentType = "application/json",
                        request = ClaudeChatRequest(
                            model = "claude-opus-4-5",
                            max_tokens = 8000,
                            system = "You are Claude, an expert software architect.",
                            messages = listOf(ClaudeMessage("user", visionPrompt))
                        )
                    )
                    response.content.firstOrNull()?.text ?: ""
                } catch (e: Exception) { "Error: ${e.message}" }
            }
            kimiVision = claudeVision // Use same vision for both
            totalTokens += estimateTokens(claudeVision)
        } else if (hasKimiKey) {
            emit(DualAiProgress(DualAiProgress.Stage.KIMI_THINKING, "Kimi is designing your app..."))
            kimiVision = withContext(Dispatchers.IO) {
                try {
                    val response = kimiApiService.chat(
                        auth = "Bearer $kimiKey",
                        request = KimiChatRequest(
                            model = "moonshot-v1-128k",
                            messages = listOf(
                                KimiMessage("system", "You are Kimi, an expert software architect."),
                                KimiMessage("user", visionPrompt)
                            )
                        )
                    )
                    response.choices.firstOrNull()?.message?.content ?: ""
                } catch (e: Exception) { "Error: ${e.message}" }
            }
            claudeVision = kimiVision
            totalTokens += estimateTokens(kimiVision)
        }

        emit(DualAiProgress(
            stage = DualAiProgress.Stage.COLLABORATING,
            message = "AIs are comparing and merging their visions...",
            claudeVision = claudeVision,
            kimiVision = kimiVision,
            totalTokens = totalTokens
        ))

        // Phase 2: Collaboration - merge the two visions
        val collaborationPrompt = buildString {
            appendLine("Two expert AI architects have independently designed the same app.")
            appendLine("Your job is to merge their visions into one superior specification.")
            appendLine()
            appendLine("USER REQUEST: \"$userDescription\"")
            appendLine()
            appendLine("=== CLAUDE'S VISION ===")
            appendLine(claudeVision.take(3000))
            appendLine()
            appendLine("=== KIMI'S VISION ===")
            appendLine(kimiVision.take(3000))
            appendLine()
            appendLine("Create the ULTIMATE merged specification:")
            appendLine("1. Explicitly identify the strongest elements from each vision")
            appendLine("2. Resolve any conflicts by choosing the superior approach")
            appendLine("3. Combine into one definitive spec that's better than either alone")
            appendLine("4. List the final tech stack, architecture, and feature set")
            appendLine()
            append("Format as a clear, detailed specification that will guide code generation.")
        }

        var mergedSpec = ""
        withContext(Dispatchers.IO) {
            try {
                if (hasClaudeKey) {
                    val response = claudeApiService.chat(
                        apiKey = claudeKey,
                        version = "2023-06-01",
                        contentType = "application/json",
                        request = ClaudeChatRequest(
                            model = "claude-opus-4-5",
                            max_tokens = 8000,
                            system = "You are a master architect synthesizing the best ideas from multiple experts.",
                            messages = listOf(ClaudeMessage("user", collaborationPrompt))
                        )
                    )
                    mergedSpec = response.content.firstOrNull()?.text ?: ""
                } else if (hasKimiKey) {
                    val response = kimiApiService.chat(
                        auth = "Bearer $kimiKey",
                        request = KimiChatRequest(
                            messages = listOf(
                                KimiMessage("system", "You are a master architect synthesizing the best ideas from multiple experts."),
                                KimiMessage("user", collaborationPrompt)
                            )
                        )
                    )
                    mergedSpec = response.choices.firstOrNull()?.message?.content ?: ""
                }
                totalTokens += estimateTokens(mergedSpec)
            } catch (e: Exception) {
                mergedSpec = "Merged spec generation failed: ${e.message}\n\nUsing individual visions."
            }
        }

        emit(DualAiProgress(
            stage = DualAiProgress.Stage.GENERATING,
            message = "Generating complete production-ready code...",
            claudeVision = claudeVision,
            kimiVision = kimiVision,
            mergedSpec = mergedSpec,
            totalTokens = totalTokens
        ))

        // Phase 3: Code generation from merged spec
        val codePrompt = buildString {
            appendLine("Based on this specification, generate a COMPLETE, PRODUCTION-READY $language application.")
            appendLine()
            appendLine("SPECIFICATION:")
            appendLine(mergedSpec.take(4000))
            appendLine()
            appendLine("REQUIREMENTS:")
            appendLine("- Generate ALL necessary files with complete, working code")
            appendLine("- Include comments explaining key sections")
            appendLine("- Follow $language best practices and conventions")
            appendLine("- Include a README.md with setup and run instructions")
            appendLine("- Include basic tests where appropriate")
            appendLine("- Code must be immediately runnable")
            appendLine()
            appendLine("Use EXACTLY this format for each file (no other text):")
            appendLine("FILE: path/to/filename.ext")
            appendLine("<<<")
            appendLine("complete file content")
            appendLine(">>>")
            appendLine()
            append("Generate at least 5-10 complete files. Make it exceptional.")
        }

        var generatedCode = ""
        withContext(Dispatchers.IO) {
            try {
                if (hasClaudeKey) {
                    val response = claudeApiService.chat(
                        apiKey = claudeKey,
                        version = "2023-06-01",
                        contentType = "application/json",
                        request = ClaudeChatRequest(
                            model = "claude-opus-4-5",
                            max_tokens = 8000,
                            system = "You are an elite software engineer. Generate only code in the exact format specified. No explanations outside of file blocks.",
                            messages = listOf(ClaudeMessage("user", codePrompt))
                        )
                    )
                    generatedCode = response.content.firstOrNull()?.text ?: ""
                } else if (hasKimiKey) {
                    val response = kimiApiService.chat(
                        auth = "Bearer $kimiKey",
                        request = KimiChatRequest(
                            messages = listOf(
                                KimiMessage("system", "You are an elite software engineer. Generate only code in the exact format specified."),
                                KimiMessage("user", codePrompt)
                            )
                        )
                    )
                    generatedCode = response.choices.firstOrNull()?.message?.content ?: ""
                } else if (hasGroqKey) {
                    // Fallback to Groq
                    generatedCode = "FILE: README.md\n<<<\n# $userDescription\n\nGenerated by CodeMaster AI\nAdd Claude or Kimi API key for full code generation.\n>>>"
                }
                totalTokens += estimateTokens(generatedCode)
            } catch (e: Exception) {
                generatedCode = "Code generation failed: ${e.message}"
            }
        }

        emit(DualAiProgress(
            stage = DualAiProgress.Stage.WRITING_FILES,
            message = "Writing files to disk...",
            claudeVision = claudeVision,
            kimiVision = kimiVision,
            mergedSpec = mergedSpec,
            totalTokens = totalTokens
        ))

        // Phase 4: Parse and write files
        val generatedFiles = mutableMapOf<String, String>()
        val lines = generatedCode.lines()
        var currentFile = ""
        var collecting = false
        val currentContent = StringBuilder()

        for (line in lines) {
            when {
                line.startsWith("FILE:") -> {
                    if (currentFile.isNotBlank() && currentContent.isNotBlank()) {
                        generatedFiles[currentFile] = currentContent.toString().trimEnd()
                        currentContent.clear()
                    }
                    currentFile = line.removePrefix("FILE:").trim()
                    collecting = false
                }
                line.trim() == "<<<" -> collecting = true
                line.trim() == ">>>" -> collecting = false
                collecting -> currentContent.appendLine(line)
            }
        }
        // Last file
        if (currentFile.isNotBlank() && currentContent.isNotBlank()) {
            generatedFiles[currentFile] = currentContent.toString().trimEnd()
        }

        // Write to disk
        File(outputPath).mkdirs()
        generatedFiles.forEach { (fileName, content) ->
            try {
                val file = File(outputPath, fileName)
                file.parentFile?.mkdirs()
                file.writeText(content)
            } catch (_: Exception) {}
        }

        // Always write the spec as a file
        File(outputPath, "AI_COLLABORATION_SPEC.md").writeText(buildString {
            appendLine("# AI Collaboration Spec")
            appendLine("Generated by CodeMaster AI Dual Engine")
            appendLine()
            appendLine("## User Request")
            appendLine(userDescription)
            appendLine()
            appendLine("## Claude's Vision")
            appendLine(claudeVision)
            appendLine()
            appendLine("## Kimi's Vision")
            appendLine(kimiVision)
            appendLine()
            appendLine("## Merged Specification")
            appendLine(mergedSpec)
        })

        // Calculate cost estimate
        val claudeCost = (totalTokens / 1000.0) * 0.015 // Claude Opus pricing
        val estimatedCost = "~${"%.4f".format(claudeCost)}"

        // Save token usage
        val previousTokens = settingsRepository.getTotalTokensUsed()
        settingsRepository.saveTotalTokensUsed(previousTokens + totalTokens)

        emit(DualAiProgress(
            stage = DualAiProgress.Stage.COMPLETE,
            message = "Your app is ready! ${generatedFiles.size} files generated.",
            claudeVision = claudeVision,
            kimiVision = kimiVision,
            mergedSpec = mergedSpec,
            generatedFiles = generatedFiles,
            totalTokens = totalTokens,
            estimatedCost = estimatedCost
        ))
    }

    private fun estimateTokens(text: String) = (text.length / 4).coerceAtLeast(1)
}
''')

# ═══════════════════════════════════════════════════════════
# 5. DualAiBuilderScreen.kt
# ═══════════════════════════════════════════════════════════
write("ui/screens/dualai/DualAiBuilderScreen.kt", r'''package com.codemaster.aistudio.ui.screens.dualai

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualAiBuilderScreen(
    onBack: () -> Unit,
    onProjectCreated: (Long) -> Unit,
    viewModel: DualAiBuilderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dual AI Builder", fontWeight = FontWeight.Bold)
                        Text("Claude + Kimi Collaboration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // AI Status indicators
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiStatusChip(
                    label = "Claude",
                    isActive = uiState.hasClaudeKey,
                    activeColor = Color(0xFF7C4DFF),
                    modifier = Modifier.weight(1f)
                )
                AiStatusChip(
                    label = "Kimi",
                    isActive = uiState.hasKimiKey,
                    activeColor = Color(0xFF00BCD4),
                    modifier = Modifier.weight(1f)
                )
                AiStatusChip(
                    label = "Groq",
                    isActive = uiState.hasGroqKey,
                    activeColor = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
            }

            if (!uiState.hasClaudeKey && !uiState.hasKimiKey) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Claude or Kimi API key in Settings for full dual-AI generation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // App description input
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Describe Your App", fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = uiState.userDescription,
                        onValueChange = { viewModel.updateDescription(it) },
                        placeholder = { Text("e.g. A social fitness tracker with AI coaching, leaderboards, and health sync") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        enabled = !uiState.isBuilding
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Kotlin", "Python", "JavaScript", "Flutter").forEach { lang ->
                            FilterChip(
                                selected = uiState.language == lang,
                                onClick = { viewModel.updateLanguage(lang) },
                                label = { Text(lang, fontSize = 12.sp) },
                                enabled = !uiState.isBuilding
                            )
                        }
                    }
                }
            }

            // Build button
            Button(
                onClick = { viewModel.startBuild(onProjectCreated) },
                enabled = uiState.userDescription.isNotBlank() && !uiState.isBuilding,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C4DFF)
                )
            ) {
                if (uiState.isBuilding) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Building with Dual AI...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Build with Claude + Kimi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // Progress stages
            if (uiState.isBuilding || uiState.progress != null) {
                uiState.progress?.let { progress ->
                    ProgressCard(progress)
                }
            }

            // Results
            if (uiState.isComplete) {
                uiState.progress?.let { progress ->
                    ResultsCard(progress, onBack = onBack, onViewProject = { onProjectCreated(uiState.projectId) })
                }
            }

            // Token usage
            if (uiState.totalTokensUsed > 0) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, modifier2 = Modifier.fillMaxWidth()) {
                        Text("Session tokens", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${uiState.totalTokensUsed}", fontWeight = FontWeight.Bold)
                        Text(uiState.estimatedCost, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun AiStatusChip(label: String, isActive: Boolean, activeColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (isActive) activeColor else Color.Gray)
            )
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProgressCard(progress: com.codemaster.aistudio.data.repository.DualAiProgress) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(progress.message, fontWeight = FontWeight.Medium)
            }

            val stages = listOf(
                "Starting" to (progress.stage.ordinal >= 0),
                "Claude Vision" to (progress.claudeVision?.isNotBlank() == true),
                "Kimi Vision" to (progress.kimiVision?.isNotBlank() == true),
                "Collaborating" to (progress.mergedSpec?.isNotBlank() == true),
                "Generating Code" to (progress.stage.ordinal >= 5),
                "Writing Files" to (progress.stage.ordinal >= 6)
            )
            stages.forEach { (label, done) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = if (done) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun ResultsCard(
    progress: com.codemaster.aistudio.data.repository.DualAiProgress,
    onBack: () -> Unit,
    onViewProject: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("App Generated!", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Text("${progress.generatedFiles.size} files created", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (progress.generatedFiles.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        progress.generatedFiles.keys.take(10).forEach { fileName ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Icon(Icons.Default.InsertDriveFile, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(fileName, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (progress.generatedFiles.size > 10) {
                            Text("+ ${progress.generatedFiles.size - 10} more files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Home")
                }
                Button(onClick = onViewProject, modifier = Modifier.weight(2f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))) {
                    Icon(Icons.Default.Code, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open in Editor", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
''')

# ═══════════════════════════════════════════════════════════
# 6. DualAiBuilderViewModel
# ═══════════════════════════════════════════════════════════
write("ui/screens/dualai/DualAiBuilderViewModel.kt", r'''package com.codemaster.aistudio.ui.screens.dualai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.DualAiProgress
import com.codemaster.aistudio.data.repository.DualAiRepository
import com.codemaster.aistudio.data.repository.ProjectRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class DualAiBuilderUiState(
    val userDescription: String = "",
    val language: String = "Kotlin",
    val hasClaudeKey: Boolean = false,
    val hasKimiKey: Boolean = false,
    val hasGroqKey: Boolean = false,
    val isBuilding: Boolean = false,
    val isComplete: Boolean = false,
    val progress: DualAiProgress? = null,
    val projectId: Long = -1L,
    val totalTokensUsed: Int = 0,
    val estimatedCost: String = "$0.00",
    val error: String? = null
)

@HiltViewModel
class DualAiBuilderViewModel @Inject constructor(
    private val dualAiRepository: DualAiRepository,
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DualAiBuilderUiState())
    val uiState: StateFlow<DualAiBuilderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                hasClaudeKey = settingsRepository.getClaudeApiKey().isNotBlank(),
                hasKimiKey = settingsRepository.getKimiApiKey().isNotBlank(),
                hasGroqKey = settingsRepository.getApiKey().isNotBlank()
            )
        }
    }

    fun updateDescription(d: String) { _uiState.value = _uiState.value.copy(userDescription = d) }
    fun updateLanguage(l: String) { _uiState.value = _uiState.value.copy(language = l) }

    fun startBuild(onProjectCreated: (Long) -> Unit) {
        val state = _uiState.value
        if (state.userDescription.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBuilding = true, isComplete = false, error = null)

            // Determine output path
            val projectName = state.userDescription.take(30)
                .replace(Regex("[^a-zA-Z0-9 ]"), "")
                .trim().replace(" ", "-")
                .ifBlank { "AI-App" }
            val outputPath = android.os.Environment.getExternalStorageDirectory().absolutePath + "/$projectName"

            dualAiRepository.buildApp(
                userDescription = state.userDescription,
                language = state.language,
                outputPath = outputPath
            ).collect { progress ->
                _uiState.value = _uiState.value.copy(
                    progress = progress,
                    totalTokensUsed = progress.totalTokens,
                    estimatedCost = progress.estimatedCost
                )

                when (progress.stage) {
                    DualAiProgress.Stage.COMPLETE -> {
                        // Register project
                        val id = projectRepository.createProject(
                            projectName, state.language, "Loaded from: $outputPath"
                        )
                        projectRepository.getProjectById(id)?.let {
                            projectRepository.updateProject(it.copy(path = outputPath))
                        }
                        _uiState.value = _uiState.value.copy(
                            isBuilding = false, isComplete = true, projectId = id
                        )
                        onProjectCreated(id)
                    }
                    DualAiProgress.Stage.ERROR -> {
                        _uiState.value = _uiState.value.copy(
                            isBuilding = false, error = progress.error
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}
''')

# ═══════════════════════════════════════════════════════════
# 7. AppModule - register Kimi + Claude retrofit clients
# ═══════════════════════════════════════════════════════════
write("di/AppModule.kt", r'''package com.codemaster.aistudio.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.codemaster.aistudio.data.CodeMasterDatabase
import com.codemaster.aistudio.data.api.ClaudeApiService
import com.codemaster.aistudio.data.api.GitHubApiService
import com.codemaster.aistudio.data.api.GroqApiService
import com.codemaster.aistudio.data.api.KimiApiService
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
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton @Named("groq")
    fun provideGroqRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.groq.com/openai/").client(client)
        .addConverterFactory(GsonConverterFactory.create()).build()

    @Provides @Singleton @Named("github")
    fun provideGitHubRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/").client(client)
        .addConverterFactory(GsonConverterFactory.create()).build()

    @Provides @Singleton @Named("claude")
    fun provideClaudeRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.anthropic.com/").client(client)
        .addConverterFactory(GsonConverterFactory.create()).build()

    @Provides @Singleton @Named("kimi")
    fun provideKimiRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.moonshot.cn/").client(client)
        .addConverterFactory(GsonConverterFactory.create()).build()

    @Provides @Singleton
    fun provideGroqApiService(@Named("groq") retrofit: Retrofit): GroqApiService =
        retrofit.create(GroqApiService::class.java)

    @Provides @Singleton
    fun provideGitHubApiService(@Named("github") retrofit: Retrofit): GitHubApiService =
        retrofit.create(GitHubApiService::class.java)

    @Provides @Singleton
    fun provideClaudeApiService(@Named("claude") retrofit: Retrofit): ClaudeApiService =
        retrofit.create(ClaudeApiService::class.java)

    @Provides @Singleton
    fun provideKimiApiService(@Named("kimi") retrofit: Retrofit): KimiApiService =
        retrofit.create(KimiApiService::class.java)
}
''')

# ═══════════════════════════════════════════════════════════
# 8. SettingsScreen - add Claude + Kimi key fields
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
    val claudeApiKey: String = "",
    val kimiApiKey: String = "",
    val model: String = "llama-3.3-70b-versatile",
    val isDarkTheme: Boolean = true,
    val githubToken: String = "",
    val githubOwner: String = "",
    val githubRepo: String = "",
    val githubBranch: String = "main",
    val projectPath: String = "/sdcard/codemaster-ai-studio",
    val onboardingComplete: Boolean = false,
    val totalTokensUsed: Long = 0L,
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
                apiKey             = settingsRepository.getApiKey(),
                claudeApiKey       = settingsRepository.getClaudeApiKey(),
                kimiApiKey         = settingsRepository.getKimiApiKey(),
                model              = settingsRepository.getModel(),
                isDarkTheme        = settingsRepository.getDarkTheme(),
                githubToken        = settingsRepository.getGitHubToken(),
                githubOwner        = settingsRepository.getGitHubOwner(),
                githubRepo         = settingsRepository.getGitHubRepo(),
                githubBranch       = settingsRepository.getGitHubBranch().ifBlank { "main" },
                projectPath        = settingsRepository.getProjectPath().ifBlank { "/sdcard/codemaster-ai-studio" },
                onboardingComplete = settingsRepository.getOnboardingComplete(),
                totalTokensUsed    = settingsRepository.getTotalTokensUsed()
            )
        }
    }

    fun updateApiKey(k: String)        { _uiState.value = _uiState.value.copy(apiKey = k, isSaved = false) }
    fun updateClaudeApiKey(k: String)  { _uiState.value = _uiState.value.copy(claudeApiKey = k, isSaved = false) }
    fun updateKimiApiKey(k: String)    { _uiState.value = _uiState.value.copy(kimiApiKey = k, isSaved = false) }
    fun updateModel(m: String)         { _uiState.value = _uiState.value.copy(model = m, isSaved = false) }
    fun toggleTheme()                  { _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme, isSaved = false) }
    fun updateGithubToken(t: String)   { _uiState.value = _uiState.value.copy(githubToken = t, isSaved = false) }
    fun updateGithubOwner(o: String)   { _uiState.value = _uiState.value.copy(githubOwner = o, isSaved = false) }
    fun updateGithubRepo(r: String)    { _uiState.value = _uiState.value.copy(githubRepo = r, isSaved = false) }
    fun updateGithubBranch(b: String)  { _uiState.value = _uiState.value.copy(githubBranch = b, isSaved = false) }
    fun updateProjectPath(p: String)   { _uiState.value = _uiState.value.copy(projectPath = p, isSaved = false) }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.saveOnboardingComplete(true)
            _uiState.value = _uiState.value.copy(onboardingComplete = true)
            saveSettings()
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            val s = _uiState.value
            settingsRepository.saveApiKey(s.apiKey)
            settingsRepository.saveClaudeApiKey(s.claudeApiKey)
            settingsRepository.saveKimiApiKey(s.kimiApiKey)
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
DUAL AI ENGINE COMPLETE:

  KimiApiService - Moonshot v1 128k API
  ClaudeApiService - Claude Opus 4.5 API
  SettingsRepository - Claude + Kimi key storage
  DualAiRepository - Full collaboration engine:
    Phase 1: Independent visions (parallel)
    Phase 2: AI collaboration + merge
    Phase 3: Code generation from merged spec
    Phase 4: File parsing + disk write
  DualAiBuilderScreen - Full UI with progress
  DualAiBuilderViewModel - State management
  AppModule - Kimi + Claude Retrofit clients
  SettingsViewModel - Claude + Kimi key fields

USER GETS FREE GROQ KEY:
  console.groq.com - free tier

FOR DUAL AI POWER:
  Claude: console.anthropic.com
  Kimi: platform.moonshot.cn

Run:
  git add -A
  git commit -m "feat: Dual AI Builder - Claude + Kimi collaboration engine"
  git push origin HEAD
""")
