package com.codemaster.aistudio.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KIMI_API_KEY = stringPreferencesKey("kimi_api_key")
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val FONT_SIZE = intPreferencesKey("font_size")
        val AI_PROVIDER = stringPreferencesKey("ai_provider") // "gemini" or "kimi"
        val EDITOR_WORD_WRAP = booleanPreferencesKey("editor_word_wrap")
    }

    // ─── Getters ───────────────────────────────────────────────

    suspend fun getGeminiApiKey(): String =
        dataStore.data.first()[GEMINI_API_KEY] ?: ""

    suspend fun getKimiApiKey(): String =
        dataStore.data.first()[KIMI_API_KEY] ?: ""

    suspend fun getGitHubToken(): String =
        dataStore.data.first()[GITHUB_TOKEN] ?: ""

    suspend fun getGitHubRepo(): String =
        dataStore.data.first()[GITHUB_REPO] ?: ""

    suspend fun isDarkTheme(): Boolean =
        dataStore.data.first()[DARK_THEME] ?: true

    suspend fun getFontSize(): Int =
        dataStore.data.first()[FONT_SIZE] ?: 14

    suspend fun getAiProvider(): String =
        dataStore.data.first()[AI_PROVIDER] ?: "gemini"

    // ─── Flows (for reactive UI) ───────────────────────────────

    val darkThemeFlow: Flow<Boolean> = dataStore.data.map { it[DARK_THEME] ?: true }
    val fontSizeFlow: Flow<Int> = dataStore.data.map { it[FONT_SIZE] ?: 14 }
    val aiProviderFlow: Flow<String> = dataStore.data.map { it[AI_PROVIDER] ?: "gemini" }

    // ─── Setters ───────────────────────────────────────────────

    suspend fun saveGeminiApiKey(key: String) {
        dataStore.edit { it[GEMINI_API_KEY] = key }
    }

    suspend fun saveKimiApiKey(key: String) {
        dataStore.edit { it[KIMI_API_KEY] = key }
    }

    suspend fun saveGitHubToken(token: String) {
        dataStore.edit { it[GITHUB_TOKEN] = token }
    }

    suspend fun saveGitHubRepo(repo: String) {
        dataStore.edit { it[GITHUB_REPO] = repo }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { it[DARK_THEME] = enabled }
    }

    suspend fun setFontSize(size: Int) {
        dataStore.edit { it[FONT_SIZE] = size }
    }

    suspend fun setAiProvider(provider: String) {
        dataStore.edit { it[AI_PROVIDER] = provider }
    }
}
