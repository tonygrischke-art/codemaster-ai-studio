package com.codemaster.aistudio.data.repository

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

            if (token.isBlank())  return@withContext Result.failure(Exception("GitHub token not set. Go to Settings."))
            if (owner.isBlank())  return@withContext Result.failure(Exception("GitHub username not set. Go to Settings."))
            if (repo.isBlank())   return@withContext Result.failure(Exception("Repository name not set. Go to Settings."))

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

            Result.success("Build triggered! Workflow: ${workflow.name}
Check GitHub Actions for progress.")
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
                latest.conclusion == "success"   -> "SUCCESS"
                latest.conclusion == "failure"   -> "FAILED"
                latest.conclusion == "cancelled" -> "CANCELLED"
                latest.status == "in_progress"   -> "IN PROGRESS"
                latest.status == "queued"        -> "QUEUED"
                else -> latest.status.uppercase()
            }

            Result.success("$status
${latest.name}
Commit: ${latest.headSha.take(7)}
Started: ${latest.createdAt}")
        } catch (e: Exception) {
            Result.failure(Exception("Status check failed: ${e.message}"))
        }
    }
}
