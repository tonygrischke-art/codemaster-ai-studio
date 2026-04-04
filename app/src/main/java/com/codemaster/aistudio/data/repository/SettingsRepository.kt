package com.codemaster.aistudio.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
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
    suspend fun getModel(): String               = dataStore.data.first()[MODEL] ?: "llama-3.1-8b-instant"
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
    suspend fun getProjectPath(): String         = dataStore.data.first()[PROJECT_PATH] 
        ?: context.getExternalFilesDir(null)?.absolutePath 
        ?: context.filesDir.absolutePath
    suspend fun saveProjectPath(p: String)       { dataStore.edit { it[PROJECT_PATH] = p } }
    suspend fun getOnboardingComplete(): Boolean = dataStore.data.first()[ONBOARDING_COMPLETE] ?: false
    suspend fun saveOnboardingComplete(v: Boolean) { dataStore.edit { it[ONBOARDING_COMPLETE] = v } }
    suspend fun getTotalTokensUsed(): Long       = dataStore.data.first()[TOTAL_TOKENS_USED]?.toLongOrNull() ?: 0L
    suspend fun saveTotalTokensUsed(t: Long)     { dataStore.edit { it[TOTAL_TOKENS_USED] = t.toString() } }
}
