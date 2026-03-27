package com.codemaster.aistudio.data.repository

import com.codemaster.aistudio.data.api.GroqApiService
import com.codemaster.aistudio.data.api.GroqChatRequest
import com.codemaster.aistudio.data.api.GroqMessage
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.tools.ToolCall
import com.codemaster.aistudio.data.tools.ToolCatalog
import com.codemaster.aistudio.data.tools.ToolExecutor
import com.codemaster.aistudio.data.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val groqApiService: GroqApiService,
    private val settingsRepository: SettingsRepository,
    private val toolExecutor: ToolExecutor
) {
    private val maxToolIterations = 3
    
    suspend fun sendMessage(
        history: List<ChatMessage>,
        userMessage: String,
        attachedFileContent: String? = null,
        systemPrompt: String = "You are CodeMaster AI, an expert coding assistant. Be concise, helpful, and provide working code examples."
    ): Result<String> = withContext(Dispatchers.IO) {
        sendMessageWithTools(
            history = history,
            userMessage = userMessage,
            attachedFileContent = attachedFileContent,
            systemPrompt = systemPrompt,
            enableTools = true
        )
    }
    
    suspend fun sendMessageWithTools(
        history: List<ChatMessage>,
        userMessage: String,
        attachedFileContent: String? = null,
        systemPrompt: String = "You are CodeMaster AI, an expert coding assistant with access to powerful tools.",
        enableTools: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = settingsRepository.getApiKey()
            if (apiKey.isBlank()) return@withContext Result.failure(Exception("No API key set. Go to Settings."))

            val model = settingsRepository.getModel()
            val messages = mutableListOf<GroqMessage>()
            
            val enhancedSystem = if (enableTools) {
                systemPrompt + "\n\n" + getToolsSystemPrompt()
            } else {
                systemPrompt
            }
            messages.add(GroqMessage("system", enhancedSystem))

            history.takeLast(20).forEach { msg ->
                messages.add(GroqMessage(msg.role, msg.content))
            }

            val nl = "\n"
            val finalUserMessage = if (attachedFileContent != null) {
                userMessage + nl + nl + "<attached_file>" + nl + attachedFileContent + nl + "</attached_file>"
            } else {
                userMessage
            }
            messages.add(GroqMessage("user", finalUserMessage))
            
            var iterations = 0
            var currentMessages = messages.toMutableList()
            var lastResponse = ""
            
            while (iterations < maxToolIterations) {
                val request = GroqChatRequest(model = model, messages = currentMessages)
                val response = groqApiService.chat("Bearer $apiKey", request)
                val content = response.choices.firstOrNull()?.message?.content
                    ?: return@withContext Result.failure(Exception("Empty response from AI"))

                lastResponse = content
                
                val toolCalls = parseToolCalls(content)
                if (toolCalls.isEmpty()) {
                    return@withContext Result.success(content)
                }
                
                currentMessages.add(GroqMessage("assistant", content))
                
                for (call in toolCalls) {
                    val result = toolExecutor.executeTool(call)
                    val resultMessage = formatToolResult(call, result)
                    currentMessages.add(GroqMessage("user", resultMessage))
                }
                
                iterations++
            }
            
            Result.success(lastResponse + "\n\n(Reached max tool iterations)")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun getToolsSystemPrompt(): String {
        return """
You have access to the following tools:

${ToolCatalog.getToolDescriptions()}

**IMPORTANT INSTRUCTIONS:**

1. When you need to read a file, use `read_file`
2. When you need to write code, use `write_file`  
3. When you need to run commands, use `run_command`
4. When you need to search for errors or documentation, use `search_web`
5. When you need git operations, use git_status, git_commit, git_push, git_pull
6. When you need to know the project structure, use `get_project_info`

**Tool Call Format:**
Respond with tool calls in this JSON format:
```
TOOL_CALL: {"tool": "tool_name", "args": {"arg1": "value1", "arg2": "value2"}}
```

Example:
- Read a file: `TOOL_CALL: {"tool": "read_file", "args": {"path": "src/main.kt"}}`
- Search web: `TOOL_CALL: {"tool": "search_web", "args": {"query": "kotlin coroutines tutorial"}}`
- Run command: `TOOL_CALL: {"tool": "run_command", "args": {"command": "ls -la"}}`

**Rules:**
- ALWAYS use tool calls when you need to access files, run commands, or search
- Parse user's intent and automatically use appropriate tools
- After using a tool, analyze the result and continue if needed
- If a tool fails, explain the error and try an alternative approach
- Be autonomous - don't ask the user to do things you can do with tools
""".trimIndent()
    }
    
    private fun parseToolCalls(response: String): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        
        val jsonRegex = Regex("""TOOL_CALL:\s*(\{[^}]+\})""", RegexOption.IGNORE_CASE)
        val matches = jsonRegex.findAll(response)
        
        for (match in matches) {
            try {
                val json = match.groupValues[1]
                val toolName = Regex(""""tool"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1) ?: continue
                val argsStr = Regex(""""args"\s*:\s*(\{[^}]+\})""").find(json)?.groupValues?.get(1) ?: continue
                
                val args = mutableMapOf<String, String>()
                val argRegex = Regex(""""(\w+)"\s*:\s*"(.*?)"""")
                argRegex.findAll(argsStr).forEach { argMatch ->
                    args[argMatch.groupValues[1]] = argMatch.groupValues[2]
                }
                
                if (toolName.isNotBlank()) {
                    calls.add(ToolCall(toolName, args))
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        return calls
    }
    
    private fun formatToolResult(call: ToolCall, result: ToolResult): String {
        return """
TOOL_RESULT: ${call.toolName}
SUCCESS: ${result.success}
OUTPUT:
${result.output}
${if (result.error != null) "ERROR: ${result.error}" else ""}
""".trimIndent()
    }
    
    fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)
}