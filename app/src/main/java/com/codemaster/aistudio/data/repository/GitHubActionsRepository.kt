package com.codemaster.aistudio.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class BuildStatus(
    val id: Long,
    val status: String,       // "queued", "in_progress", "completed"
    val conclusion: String?,  // "success", "failure", "cancelled"
    val name: String,
    val createdAt: String,
    val updatedAt: String,
    val htmlUrl: String,
    val artifactsUrl: String? = null
)

data class BuildArtifact(
    val id: Long,
    val name: String,
    val sizeInBytes: Long,
    val downloadUrl: String,
    val expiresAt: String
)

@Singleton
class GitHubActionsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    // ─── Trigger a new build ───────────────────────────────────
    suspend fun triggerBuild(): FileResult<String> = withContext(Dispatchers.IO) {
        try {
            val token = settingsRepository.getGitHubToken()
            val repo = settingsRepository.getGitHubRepo() // "owner/repo"

            if (token.isBlank() || repo.isBlank()) {
                return@withContext FileResult.Error(
                    "GitHub token or repo not configured. Go to Settings."
                )
            }

            val url = URL("https://api.github.com/repos/$repo/actions/workflows/build.yml/dispatches")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("ref", "main")
            }.toString()

            conn.outputStream.write(body.toByteArray())
            conn.outputStream.flush()

            val responseCode = conn.responseCode
            conn.disconnect()

            if (responseCode == 204) {
                FileResult.Success("Build triggered successfully!")
            } else {
                FileResult.Error("Failed to trigger build (HTTP $responseCode)")
            }
        } catch (e: Exception) {
            FileResult.Error("Network error: ${e.message}", e)
        }
    }

    // ─── List recent workflow runs ─────────────────────────────
    suspend fun getRecentBuilds(): FileResult<List<BuildStatus>> = withContext(Dispatchers.IO) {
        try {
            val token = settingsRepository.getGitHubToken()
            val repo = settingsRepository.getGitHubRepo()

            if (token.isBlank() || repo.isBlank()) {
                return@withContext FileResult.Error("GitHub not configured in Settings.")
            }

            val url = URL("https://api.github.com/repos/$repo/actions/runs?per_page=10")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                conn.disconnect()
                return@withContext FileResult.Error("Failed to fetch builds (HTTP $responseCode)")
            }

            val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            conn.disconnect()

            val json = JSONObject(response)
            val runs = json.getJSONArray("workflow_runs")
            val builds = mutableListOf<BuildStatus>()

            for (i in 0 until runs.length()) {
                val run = runs.getJSONObject(i)
                builds.add(
                    BuildStatus(
                        id = run.getLong("id"),
                        status = run.getString("status"),
                        conclusion = if (run.isNull("conclusion")) null else run.getString("conclusion"),
                        name = run.getString("display_title"),
                        createdAt = run.getString("created_at"),
                        updatedAt = run.getString("updated_at"),
                        htmlUrl = run.getString("html_url"),
                        artifactsUrl = run.optString("artifacts_url")
                    )
                )
            }

            FileResult.Success(builds)
        } catch (e: Exception) {
            FileResult.Error("Network error: ${e.message}", e)
        }
    }

    // ─── Get artifacts for a run ───────────────────────────────
    suspend fun getArtifacts(runId: Long): FileResult<List<BuildArtifact>> =
        withContext(Dispatchers.IO) {
            try {
                val token = settingsRepository.getGitHubToken()
                val repo = settingsRepository.getGitHubRepo()

                val url = URL("https://api.github.com/repos/$repo/actions/runs/$runId/artifacts")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    conn.disconnect()
                    return@withContext FileResult.Error("Failed to fetch artifacts (HTTP $responseCode)")
                }

                val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                conn.disconnect()

                val json = JSONObject(response)
                val items = json.getJSONArray("artifacts")
                val artifacts = mutableListOf<BuildArtifact>()

                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    artifacts.add(
                        BuildArtifact(
                            id = item.getLong("id"),
                            name = item.getString("name"),
                            sizeInBytes = item.getLong("size_in_bytes"),
                            downloadUrl = item.getString("archive_download_url"),
                            expiresAt = item.getString("expires_at")
                        )
                    )
                }

                FileResult.Success(artifacts)
            } catch (e: Exception) {
                FileResult.Error("Network error: ${e.message}", e)
            }
        }
}
