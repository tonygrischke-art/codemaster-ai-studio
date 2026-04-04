package com.codemaster.aistudio.ui.agent

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

// ─── CONSTANTS ────────────────────────────────────────────────────────────────

private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
private const val GROQ_MODEL = "llama-3.3-70b-versatile"

private const val CLAUDE_URL = "https://api.anthropic.com/v1/messages"
private const val CLAUDE_MODEL = "claude-opus-4-6"
private const val CLAUDE_VERSION = "2023-06-01"

private const val KIMI_URL = "https://api.moonshot.cn/v1/chat/completions"
private const val KIMI_MODEL = "moonshot-v1-8k"

private const val MAX_STEPS = 20  // Safety cap on autonomous steps

// ─── ENUMS & MODELS ──────────────────────────────────────────────────────────

enum class AgentProvider(val label: String) {
    GROQ("Groq"),
    CLAUDE("Claude"),
    KIMI("Kimi")
}

enum class EntryType {
    USER_GOAL,       // What user asked for
    AGENT_PLAN,      // Agent's plan text
    AGENT_THOUGHT,   // Agent reasoning step
    COMMAND,         // Shell command being run
    OUTPUT,          // Command output
    SUCCESS,         // Step succeeded
    ERROR,           // Step failed
    AGENT_DONE       // Task complete
}

data class AgentEntry(
    val id: Long = System.currentTimeMillis(),
    val type: EntryType,
    val text: String,
    val provider: AgentProvider? = null
)

data class AgentUiState(
    val entries: List<AgentEntry> = emptyList(),
    val isRunning: Boolean = false,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val activeProvider: AgentProvider = AgentProvider.GROQ,
    val input: String = "",
    val error: String? = null,
    // API Keys — populated from SettingsRepository
    val groqKey: String = "",
    val claudeKey: String = "",
    val kimiKey: String = ""
)

// ─── SYSTEM PROMPT ────────────────────────────────────────────────────────────

private fun buildSystemPrompt(): String = """
You are an autonomous agent running inside an Android terminal (Termux/shell environment).
Your job is to complete the user's goal by executing shell commands step by step.

RESPONSE FORMAT — always respond with valid JSON only, no markdown, no backticks:
{
  "thought": "your reasoning about what to do next",
  "command": "the exact shell command to run, or null if done",
  "done": false,
  "summary": "brief summary of what you just did (shown to user)"
}

When the task is complete, set "done": true and "command": null.

RULES:
- Keep commands simple and safe. One command per step.
- Always check output before next step. If a command fails, diagnose and try differently.
- Use absolute paths when dealing with files.
- For git operations: always use 'git push origin HEAD'
- Do NOT use interactive commands (vim, nano, etc). Use echo/cat/sed for file edits.
- Max $MAX_STEPS steps. Be efficient.
- Never hardcode API keys into files.
- If unsure, use 'ls' or 'cat' to inspect before acting.
""".trimIndent()

// ─── VIEWMODEL ───────────────────────────────────────────────────────────────

@HiltViewModel
class AgentTerminalViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    // Call this from your SettingsRepository injection point
    fun setApiKeys(groq: String, claude: String, kimi: String) {
        val active = when {
            claude.isNotBlank() -> AgentProvider.CLAUDE
            kimi.isNotBlank() -> AgentProvider.KIMI
            else -> AgentProvider.GROQ
        }
        _uiState.update { it.copy(groqKey = groq, claudeKey = claude, kimiKey = kimi, activeProvider = active) }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    fun setProvider(provider: AgentProvider) {
        _uiState.update { it.copy(activeProvider = provider) }
    }

    fun startAgent(goal: String) {
        if (goal.isBlank() || _uiState.value.isRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = true, entries = emptyList(), currentStep = 0, error = null, input = "") }

            addEntry(AgentEntry(type = EntryType.USER_GOAL, text = "Goal: $goal"))

            val conversationHistory = mutableListOf<JSONObject>()
            var step = 0
            var done = false

            // Seed the conversation with the goal
            conversationHistory.add(userMessage(goal))

            while (!done && step < MAX_STEPS) {
                step++
                _uiState.update { it.copy(currentStep = step) }

                // Ask the AI what to do next
                val aiResponse = callAI(conversationHistory)
                if (aiResponse == null) {
                    addEntry(AgentEntry(type = EntryType.ERROR, text = "AI call failed at step $step"))
                    done = true
                } else {

                // Parse response
                val thought = aiResponse.optString("thought", "")
                val command = aiResponse.optString("command").takeIf { it.isNotBlank() && it != "null" }
                done = aiResponse.optBoolean("done", false)
                val summary = aiResponse.optString("summary", "")

                if (thought.isNotBlank()) {
                    addEntry(AgentEntry(type = EntryType.AGENT_THOUGHT, text = thought, provider = _uiState.value.activeProvider))
                }

                if (command != null) {
                    addEntry(AgentEntry(type = EntryType.COMMAND, text = "$ $command"))

                    // Execute the command
                    val output = runCommand(command)
                    addEntry(AgentEntry(type = EntryType.OUTPUT, text = output.ifBlank { "(no output)" }))

                    // Feed result back to AI
                    conversationHistory.add(assistantMessage(aiResponse.toString()))
                    conversationHistory.add(userMessage("Command output:\n$output\n\nContinue with the next step."))
                } else if (done) {
                    addEntry(AgentEntry(type = EntryType.AGENT_DONE, text = summary.ifBlank { "Task complete ✓" }))
                } else {
                    // No command and not done — something went wrong
                    addEntry(AgentEntry(type = EntryType.ERROR, text = "Agent returned no command and task not marked done."))
                    done = true
                }

                delay(300) // Small pause between steps for readability
                }
            }

            if (step >= MAX_STEPS && !done) {
                addEntry(AgentEntry(type = EntryType.ERROR, text = "Reached max step limit ($MAX_STEPS). Stopping."))
            }

            _uiState.update { it.copy(isRunning = false) }
        }
    }

    fun stopAgent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunning = false) }
            addEntry(AgentEntry(type = EntryType.ERROR, text = "⛔ Agent stopped by user"))
        }
    }

    fun clearLog() {
        _uiState.update { it.copy(entries = emptyList(), currentStep = 0, error = null) }
    }

    // ─── AI CALL ─────────────────────────────────────────────────────────────

    private suspend fun callAI(history: List<JSONObject>): JSONObject? = withContext(Dispatchers.IO) {
        val state = _uiState.value
        return@withContext try {
            val raw = when (state.activeProvider) {
                AgentProvider.CLAUDE -> callClaude(history, state.claudeKey)
                AgentProvider.KIMI -> callOpenAICompat(history, state.kimiKey, KIMI_URL, KIMI_MODEL)
                AgentProvider.GROQ -> callOpenAICompat(history, state.groqKey, GROQ_URL, GROQ_MODEL)
            }
            // Strip markdown fences if AI misbehaves
            val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            JSONObject(cleaned)
        } catch (e: Exception) {
            null
        }
    }

    /** OpenAI-compatible endpoint (Groq + Kimi both use this format) */
    private fun callOpenAICompat(
        history: List<JSONObject>,
        apiKey: String,
        url: String,
        model: String
    ): String {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", buildSystemPrompt())
            })
            history.forEach { put(it) }
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 1024)
            put("temperature", 0.3)
            put("response_format", JSONObject().put("type", "json_object"))
        }

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 30000
            readTimeout = 30000
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        return if (conn.responseCode == 200) {
            val response = JSONObject(conn.inputStream.bufferedReader().readText())
            response.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("HTTP ${conn.responseCode}: $err")
        }
    }

    /** Claude uses a different API format */
    private fun callClaude(history: List<JSONObject>, apiKey: String): String {
        val messages = JSONArray().apply {
            history.forEach { put(it) }
        }

        val body = JSONObject().apply {
            put("model", CLAUDE_MODEL)
            put("max_tokens", 1024)
            put("system", buildSystemPrompt())
            put("messages", messages)
        }

        val conn = (URL(CLAUDE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", CLAUDE_VERSION)
            doOutput = true
            connectTimeout = 30000
            readTimeout = 30000
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        return if (conn.responseCode == 200) {
            val response = JSONObject(conn.inputStream.bufferedReader().readText())
            response.getJSONArray("content")
                .getJSONObject(0)
                .getString("text")
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("HTTP ${conn.responseCode}: $err")
        }
    }

    // ─── COMMAND EXECUTION ───────────────────────────────────────────────────

    private suspend fun runCommand(command: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true) // merge stderr into stdout
                .start()

            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
                // Stream output to UI in real time
                val currentOutput = output.toString()
                withContext(Dispatchers.Main) {
                    // Update last OUTPUT entry in real time if it exists
                    _uiState.update { state ->
                        val entries = state.entries.toMutableList()
                        val lastOutputIndex = entries.indexOfLast { it.type == EntryType.OUTPUT }
                        if (lastOutputIndex >= 0) {
                            entries[lastOutputIndex] = entries[lastOutputIndex].copy(text = currentOutput)
                            state.copy(entries = entries)
                        } else state
                    }
                }
            }

            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            "Error running command: ${e.message}"
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private fun addEntry(entry: AgentEntry) {
        _uiState.update { it.copy(entries = it.entries + entry) }
    }

    private fun userMessage(content: String) = JSONObject().apply {
        put("role", "user")
        put("content", content)
    }

    private fun assistantMessage(content: String) = JSONObject().apply {
        put("role", "assistant")
        put("content", content)
    }
}

// ─── UI ──────────────────────────────────────────────────────────────────────

@Composable
fun AgentTerminalScreen(
    viewModel: AgentTerminalViewModel = hiltViewModel(),
    groqKey: String = "",
    claudeKey: String = "",
    kimiKey: String = ""
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Set API keys from parent
    LaunchedEffect(groqKey, claudeKey, kimiKey) {
        viewModel.setApiKeys(groqKey, claudeKey, kimiKey)
    }

    // Auto-scroll to bottom as new entries arrive
    LaunchedEffect(state.entries.size) {
        if (state.entries.isNotEmpty()) {
            listState.animateScrollToItem(state.entries.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D)) // Dark terminal background
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        AgentHeader(
            state = state,
            onProviderSelect = viewModel::setProvider,
            onClear = viewModel::clearLog
        )

        // ── Log ─────────────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (state.entries.isEmpty() && !state.isRunning) {
                item { EmptyState() }
            }
            items(state.entries, key = { it.id }) { entry ->
                AgentEntryRow(entry)
            }
            if (state.isRunning) {
                item { ThinkingIndicator(state.activeProvider) }
            }
        }

        // ── Input ────────────────────────────────────────────────────────────
        AgentInput(
            input = state.input,
            isRunning = state.isRunning,
            onInputChange = viewModel::onInputChange,
            onSubmit = { viewModel.startAgent(state.input) },
            onStop = viewModel::stopAgent
        )
    }
}

// ─── HEADER ──────────────────────────────────────────────────────────────────

@Composable
private fun AgentHeader(
    state: AgentUiState,
    onProviderSelect: (AgentProvider) -> Unit,
    onClear: () -> Unit
) {
    Surface(color = Color(0xFF1A1A2E), tonalElevation = 4.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF7C3AED),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Agent Terminal",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.weight(1f))

                // Step counter when running
                if (state.isRunning) {
                    Text(
                        "Step ${state.currentStep}/$MAX_STEPS",
                        color = Color(0xFF7C3AED),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(12.dp))
                }

                // Clear button
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
                }
            }

            // Provider selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val providers = buildList {
                    add(AgentProvider.GROQ) // Always available
                    if (state.claudeKey.isNotBlank()) add(AgentProvider.CLAUDE)
                    if (state.kimiKey.isNotBlank()) add(AgentProvider.KIMI)
                }

                providers.forEach { provider ->
                    val selected = state.activeProvider == provider
                    val (bgColor, textColor) = when (provider) {
                        AgentProvider.GROQ -> Pair(Color(0xFF1E3A5F), Color(0xFF60A5FA))
                        AgentProvider.CLAUDE -> Pair(Color(0xFF3D1F00), Color(0xFFFF8C42))
                        AgentProvider.KIMI -> Pair(Color(0xFF1F2D1F), Color(0xFF4ADE80))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) bgColor else Color(0xFF1A1A1A))
                            .border(
                                width = if (selected) 1.dp else 0.dp,
                                color = if (selected) textColor else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { if (!state.isRunning) onProviderSelect(provider) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            provider.label,
                            color = if (selected) textColor else Color(0xFF666666),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ─── LOG ENTRIES ─────────────────────────────────────────────────────────────

@Composable
private fun AgentEntryRow(entry: AgentEntry) {
    when (entry.type) {
        EntryType.USER_GOAL -> GoalEntry(entry.text)
        EntryType.AGENT_PLAN -> PlanEntry(entry.text)
        EntryType.AGENT_THOUGHT -> ThoughtEntry(entry.text, entry.provider)
        EntryType.COMMAND -> CommandEntry(entry.text)
        EntryType.OUTPUT -> OutputEntry(entry.text)
        EntryType.SUCCESS -> StatusEntry(entry.text, Color(0xFF4ADE80))
        EntryType.ERROR -> StatusEntry(entry.text, Color(0xFFEF4444))
        EntryType.AGENT_DONE -> DoneEntry(entry.text)
    }
}

@Composable
private fun GoalEntry(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("🎯", fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color(0xFF93C5FD), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ThoughtEntry(text: String, provider: AgentProvider?) {
    val color = when (provider) {
        AgentProvider.CLAUDE -> Color(0xFFFF8C42)
        AgentProvider.KIMI -> Color(0xFF4ADE80)
        else -> Color(0xFF7C3AED)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("💭", fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(text, color = color.copy(alpha = 0.85f), fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    }
}

@Composable
private fun PlanEntry(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A2E))
            .padding(12.dp)
    ) {
        Text("📋 Plan", color = Color(0xFF7C3AED), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(text, color = Color(0xFFCCCCCC), fontSize = 13.sp)
    }
}

@Composable
private fun CommandEntry(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0F1923))
            .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            color = Color(0xFF60A5FA),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OutputEntry(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF111111))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            color = Color(0xFFAAAAAA),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun StatusEntry(text: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, fontSize = 13.sp)
    }
}

@Composable
private fun DoneEntry(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF052E16))
            .border(1.dp, Color(0xFF166534), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("✅", fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color(0xFF4ADE80), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ThinkingIndicator(provider: AgentProvider) {
    val color = when (provider) {
        AgentProvider.CLAUDE -> Color(0xFFFF8C42)
        AgentProvider.KIMI -> Color(0xFF4ADE80)
        AgentProvider.GROQ -> Color(0xFF7C3AED)
    }
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = color
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${provider.label} is thinking…",
            color = color.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🤖", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Tell me what to do.",
                color = Color(0xFF555555),
                fontSize = 16.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "I'll plan and execute it step by step.",
                color = Color(0xFF444444),
                fontSize = 13.sp
            )
        }
    }
}

// ─── INPUT BAR ───────────────────────────────────────────────────────────────

@Composable
private fun AgentInput(
    input: String,
    isRunning: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit
) {
    Surface(color = Color(0xFF111111), tonalElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        "What should I do?",
                        color = Color(0xFF444444),
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF7C3AED),
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor = Color(0xFF7C3AED),
                    focusedContainerColor = Color(0xFF1A1A1A),
                    unfocusedContainerColor = Color(0xFF1A1A1A)
                ),
                maxLines = 4,
                enabled = !isRunning,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!isRunning) onSubmit() })
            )

            Spacer(Modifier.width(8.dp))

            // Run / Stop button
            if (isRunning) {
                FloatingActionButton(
                    onClick = onStop,
                    modifier = Modifier.size(48.dp),
                    containerColor = Color(0xFFEF4444)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                }
            } else {
                FloatingActionButton(
                    onClick = onSubmit,
                    modifier = Modifier.size(48.dp),
                    containerColor = if (input.isNotBlank()) Color(0xFF7C3AED) else Color(0xFF333333)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = Color.White)
                }
            }
        }
    }
}
