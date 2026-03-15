#!/usr/bin/env python3
import os

BASE = os.path.expanduser("~/codemaster-ai-studio/app/src/main/java/com/codemaster/aistudio")

def write(rel, content):
    path = os.path.join(BASE, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("OK " + rel)

# The only problem in AiRepository is line 36 which has:
#   "$userMessage\n\n<attached_file>\n$attachedFileContent\n</attached_file>"
# We replace the \n inside the string with a variable
write("data/repository/AiRepository.kt", r"""package com.codemaster.aistudio.data.repository

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

            val nl = "\n"
            val finalUserMessage = if (attachedFileContent != null) {
                userMessage + nl + nl + "<attached_file>" + nl + attachedFileContent + nl + "</attached_file>"
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
""")

# GitHubActionsRepository has \n in two Result.success() strings
write("data/repository/GitHubActionsRepository.kt", r"""package com.codemaster.aistudio.data.repository

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

            if (token.isBlank()) return@withContext Result.failure(Exception("GitHub token not set. Go to Settings."))
            if (owner.isBlank()) return@withContext Result.failure(Exception("GitHub username not set. Go to Settings."))
            if (repo.isBlank())  return@withContext Result.failure(Exception("Repository name not set. Go to Settings."))

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
            ?: return@withContext Result.failure(Exception("No workflows found in repo."))

            gitHubApiService.triggerWorkflow(
                token      = "Bearer $token",
                owner      = owner,
                repo       = repo,
                workflowId = workflow.id.toString(),
                body       = WorkflowDispatchRequest(ref = branch)
            )

            val msg = "Build triggered! Workflow: " + workflow.name + "\nCheck GitHub Actions for progress."
            Result.success(msg)
        } catch (e: Exception) {
            Result.failure(Exception("Trigger failed: " + e.message))
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
                latest.conclusion == "success"   -> "SUCCESS"
                latest.conclusion == "failure"   -> "FAILED"
                latest.conclusion == "cancelled" -> "CANCELLED"
                latest.status == "in_progress"   -> "IN PROGRESS"
                latest.status == "queued"        -> "QUEUED"
                else -> latest.status.uppercase()
            }

            val nl = "\n"
            val msg = status + nl + latest.name + nl + "Commit: " + latest.headSha.take(7) + nl + "Started: " + latest.createdAt
            Result.success(msg)
        } catch (e: Exception) {
            Result.failure(Exception("Status check failed: " + e.message))
        }
    }
}
""")

print("\n" + "="*50)
print("Done. Now run:")
print("  git add -A")
print("  git commit -m 'fix: final escape fix AiRepository and GitHubActionsRepository'")
print("  git push origin HEAD")
print("="*50)
