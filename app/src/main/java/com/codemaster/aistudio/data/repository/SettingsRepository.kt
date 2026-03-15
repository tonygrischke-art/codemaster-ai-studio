package com.codemaster.aistudio.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val API_KEY = stringPreferencesKey("groq_api_key")
        val MODEL = stringPreferencesKey("groq_model")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    suspend fun getApiKey(): String =
        dataStore.data.first()[API_KEY] ?: ""

    suspend fun saveApiKey(key: String) {
        dataStore.edit { it[API_KEY] = key }
    }

    suspend fun getModel(): String =
        dataStore.data.first()[MODEL] ?: "llama-3.3-70b-versatile"

    suspend fun saveModel(model: String) {
        dataStore.edit { it[MODEL] = model }
    }

    suspend fun getDarkTheme(): Boolean =
        dataStore.data.first()[DARK_THEME] ?: true

    suspend fun saveDarkTheme(dark: Boolean) {
        dataStore.edit { it[DARK_THEME] = dark }
    }
}
