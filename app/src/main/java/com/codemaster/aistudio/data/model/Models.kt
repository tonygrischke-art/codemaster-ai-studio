package com.codemaster.aistudio.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

// ─── AI Provider ───────────────────────────────────────────────
enum class AiProvider(val displayName: String, val shortName: String) {
    GEMINI("Google Gemini", "Gemini"),
    KIMI("Moonshot Kimi", "Kimi")
}

// ─── Chat Message ──────────────────────────────────────────────
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String = "",
    val role: MessageRole,
    val content: String,
    val provider: AiProvider = AiProvider.GEMINI,
    val timestamp: Long = System.currentTimeMillis(),
    val hasCodeBlock: Boolean = false,
    val isError: Boolean = false
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }

// ─── Project ───────────────────────────────────────────────────
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val language: ProjectLanguage = ProjectLanguage.KOTLIN,
    val path: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

enum class ProjectLanguage(val displayName: String, val extension: String, val emoji: String) {
    KOTLIN("Kotlin", "kt", "🟣"),
    JAVA("Java", "java", "☕"),
    PYTHON("Python", "py", "🐍"),
    JAVASCRIPT("JavaScript", "js", "🟨"),
    DART("Flutter/Dart", "dart", "💙"),
    CPP("C++", "cpp", "⚙️"),
    SHELL("Shell Script", "sh", "💻"),
    OTHER("Other", "txt", "📄")
}

// ─── Settings ──────────────────────────────────────────────────
data class AppSettings(
    val geminiApiKey: String = "",
    val kimiApiKey: String = "",
    val defaultProvider: AiProvider = AiProvider.GEMINI,
    val darkTheme: Boolean = true,
    val fontSize: Int = 14,
    val sandboxMode: Boolean = true,
    val autoSave: Boolean = true
)
