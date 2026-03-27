package com.codemaster.aistudio.data.agent

import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.repository.AiRepository
import com.codemaster.aistudio.data.repository.FileSystemRepository
import com.codemaster.aistudio.data.repository.GitRepository
import com.codemaster.aistudio.data.tools.ToolExecutor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

data class AgentTask(
    val id: String,
    val description: String,
    val status: TaskStatus,
    val progress: Float = 0f,
    val result: String? = null,
    val error: String? = null
) {
    enum class TaskStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }
}

sealed class AgentEvent {
    data class TaskStarted(val task: AgentTask) : AgentEvent()
    data class TaskProgress(val taskId: String, val progress: Float, val message: String) : AgentEvent()
    data class TaskCompleted(val task: AgentTask) : AgentEvent()
    data class TaskFailed(val task: AgentTask, val error: String) : AgentEvent()
    data class Suggestion(val message: String, val action: String? = null) : AgentEvent()
}

@Singleton
class AutonomousAgent @Inject constructor(
    private val aiRepository: AiRepository,
    private val fileSystemRepository: FileSystemRepository,
    private val gitRepository: GitRepository,
    private val toolExecutor: ToolExecutor
) {
    private val _tasks = MutableStateFlow<List<AgentTask>>(emptyList())
    val tasks: StateFlow<List<AgentTask>> = _tasks.asStateFlow()

    private val _events = MutableSharedFlow<AgentEvent>()
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private var currentProjectPath: String = ""
    private var agentScope: CoroutineScope? = null

    private val taskHistory = mutableListOf<Pair<String, String>>()
    private val projectMemory = mutableMapOf<String, Any>()

    fun setProjectPath(path: String) {
        currentProjectPath = path
        toolExecutor.setProjectPath(path)
    }

    fun enableAutonomousMode(enabled: Boolean) {
        aiRepository.autonomousMode = enabled
    }

    fun startAutonomousAgent() {
        if (_isRunning.value) return
        _isRunning.value = true
        agentScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        
        agentScope?.launch {
            analyzeProjectAndSuggest()
        }
    }

    fun stopAutonomousAgent() {
        _isRunning.value = false
        agentScope?.cancel()
        agentScope = null
    }

    suspend fun analyzeProjectAndSuggest() {
        if (currentProjectPath.isBlank()) {
            _events.emit(AgentEvent.Suggestion("No project loaded. Open a project to enable AI assistance."))
            return
        }

        val projectInfo = fileSystemRepository.scanDirectory(currentProjectPath, maxDepth = 2)
            .getOrNull() ?: return

        val totalFiles = projectInfo.count { !it.isDirectory }
        val languages = projectInfo.filter { !it.isDirectory }
            .groupBy { it.extension }
            .mapValues { it.value.size }
            .filter { it.value > 0 }
            .keys
            .take(5)

        val suggestions = mutableListOf<String>()

        if (totalFiles < 5) {
            suggestions.add("This project is small. Would you like me to help expand it?")
        }

        val hasBuildFile = projectInfo.any { it.name == "build.gradle" || it.name == "build.gradle.kts" || it.name == "package.json" }
        if (hasBuildFile) {
            suggestions.add("Build configuration found. Want me to check for issues and run a build?")
        }

        val readme = projectInfo.find { it.name.lowercase() == "readme.md" }
        if (readme == null) {
            suggestions.add("No README found. Should I create one?")
        }

        gitRepository.getStatus(currentProjectPath).onSuccess { status ->
            if (status.staged.isNotEmpty() || status.unstaged.isNotEmpty()) {
                suggestions.add("You have uncommitted changes. Want me to analyze them?")
            }
            if (!status.isRepo) {
                suggestions.add("Not a git repository. Initialize one?")
            }
        }

        _suggestions.value = suggestions
        suggestions.firstOrNull()?.let {
            _events.emit(AgentEvent.Suggestion(it))
        }
    }

    suspend fun executeTask(taskDescription: String): AgentTask {
        val task = AgentTask(
            id = System.currentTimeMillis().toString(),
            description = taskDescription,
            status = AgentTask.TaskStatus.PENDING
        )

        _tasks.value = _tasks.value + task
        _events.emit(AgentEvent.TaskStarted(task))

        try {
            val updatedTask = task.copy(status = AgentTask.TaskStatus.RUNNING)
            _tasks.value = _tasks.value.map { if (it.id == task.id) updatedTask else it }

            aiRepository.autonomousMode = true
            val history = taskHistory.map { ChatMessage("user", it.first) }.toMutableList()

            val result = aiRepository.sendMessageWithTools(
                history = history,
                userMessage = taskDescription,
                systemPrompt = getAutonomousSystemPrompt(),
                enableTools = true
            )

            result.fold(
                onSuccess = { response ->
                    taskHistory.add(taskDescription to response)
                    val completedTask = task.copy(
                        status = AgentTask.TaskStatus.COMPLETED,
                        progress = 1f,
                        result = response
                    )
                    _tasks.value = _tasks.value.map { if (it.id == task.id) completedTask else it }
                    _events.emit(AgentEvent.TaskCompleted(completedTask))
                    completedTask
                },
                onFailure = { error ->
                    val failedTask = task.copy(
                        status = AgentTask.TaskStatus.FAILED,
                        error = error.message
                    )
                    _tasks.value = _tasks.value.map { if (it.id == task.id) failedTask else it }
                    _events.emit(AgentEvent.TaskFailed(failedTask, error.message ?: "Unknown error"))
                    failedTask
                }
            ).also {
                aiRepository.autonomousMode = false
            }
        } catch (e: Exception) {
            val failedTask = task.copy(
                status = AgentTask.TaskStatus.FAILED,
                error = e.message
            )
            _tasks.value = _tasks.value.map { if (it.id == task.id) failedTask else it }
            _events.emit(AgentEvent.TaskFailed(failedTask, e.message ?: "Unknown error"))
            aiRepository.autonomousMode = false
            return failedTask
        }

        return task
    }

    suspend fun runAutonomousTask(taskDescription: String) {
        agentScope?.launch {
            executeTask(taskDescription)
        }
    }

    fun cancelTask(taskId: String) {
        _tasks.value = _tasks.value.map {
            if (it.id == taskId && it.status == AgentTask.TaskStatus.RUNNING) {
                it.copy(status = AgentTask.TaskStatus.FAILED, error = "Cancelled by user")
            } else it
        }
    }

    fun clearCompletedTasks() {
        _tasks.value = _tasks.value.filter {
            it.status == AgentTask.TaskStatus.RUNNING || it.status == AgentTask.TaskStatus.PENDING
        }
    }

    private fun getAutonomousSystemPrompt(): String {
        return """
You are an autonomous AI coding assistant. Your goal is to complete tasks without user intervention.

CURRENT PROJECT: $currentProjectPath

YOUR JOB:
1. Analyze the task carefully
2. Plan the steps needed
3. Execute each step using available tools
4. Verify the results
5. Continue until the task is FULLY complete
6. Report back with the final result

AVAILABLE TOOLS:
- read_file, write_file, create_directory, delete_file
- list_directory, search_files
- run_command (for builds, tests, etc.)
- git_status, git_commit, git_push, git_pull
- search_web (for documentation)
- get_project_info
- build_project

AUTONOMY RULES:
- NEVER ask the user for confirmation
- NEVER wait for user input
- If something fails, try an alternative approach
- If you need to make decisions, make the best one
- Keep working until DONE
- After completing the main task, check if there's anything else to improve

RESPONSE FORMAT:
- Use tool calls for all file/command operations
- When task is complete, respond with: "TASK_COMPLETE: [summary]"
- If you cannot complete, explain why and what you tried
""".trimIndent()
    }

    fun remember(key: String, value: Any) {
        projectMemory[key] = value
    }

    fun recall(key: String): Any? = projectMemory[key]

    fun getContext(): Map<String, Any> = projectMemory.toMap()
}
