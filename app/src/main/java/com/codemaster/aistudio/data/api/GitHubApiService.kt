package com.codemaster.aistudio.data.api

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
