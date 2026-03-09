package com.codemaster.aistudio.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.codemaster.aistudio.data.model.AiProvider
import com.codemaster.aistudio.data.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "codemaster_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val DEFAULT_PROVIDER = stringPreferencesKey("default_provider")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val FONT_SIZE = intPreferencesKey("font_size")
        val SANDBOX_MODE = booleanPreferencesKey("sandbox_mode")
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: "",
            groqApiKey = prefs[Keys.GROQ_API_KEY] ?: "",
            defaultProvider = AiProvider.valueOf(prefs[Keys.DEFAULT_PROVIDER] ?: AiProvider.GEMINI.name),
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            fontSize = prefs[Keys.FONT_SIZE] ?: 14,
            sandboxMode = prefs[Keys.SANDBOX_MODE] ?: true,
            autoSave = prefs[Keys.AUTO_SAVE] ?: true
        )
    }

    suspend fun getSettings(): AppSettings = settingsFlow.first()

    suspend fun updateGeminiKey(key: String) {
        context.dataStore.edit { it[Keys.GEMINI_API_KEY] = key }
    }

    suspend fun updateGroqKey(key: String) {
        context.dataStore.edit { it[Keys.GROQ_API_KEY] = key }
    }

    suspend fun updateDefaultProvider(provider: AiProvider) {
        context.dataStore.edit { it[Keys.DEFAULT_PROVIDER] = provider.name }
    }

    suspend fun updateDarkTheme(dark: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun updateFontSize(size: Int) {
        context.dataStore.edit { it[Keys.FONT_SIZE] = size }
    }

    suspend fun updateSandboxMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SANDBOX_MODE] = enabled }
    }

    suspend fun getGitHubToken(): String {
        return context.dataStore.data.first()[androidx.datastore.preferences.core.stringPreferencesKey("github_token")] ?: ""
    }

    suspend fun getGitHubRepo(): String {
        return context.dataStore.data.first()[androidx.datastore.preferences.core.stringPreferencesKey("github_repo")] ?: ""
    }

    suspend fun saveGitHubToken(token: String) {
        context.dataStore.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("github_token")] = token }
    }

    suspend fun saveGitHubRepo(repo: String) {
        context.dataStore.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("github_repo")] = repo }
    }

    suspend fun updateAutoSave(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SAVE] = enabled }
    }
}
