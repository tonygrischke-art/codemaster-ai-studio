package com.codemaster.aistudio.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val language: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val path: String = ""
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long = -1L,
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0,
    val attachedFileName: String? = null
)

@Entity(tableName = "code_files")
data class CodeFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val path: String,
    val content: String = "",
    val language: String = "kotlin",
    val lastModified: Long = System.currentTimeMillis()
)

data class BuildStatus(
    val runId: String = "",
    val status: String = "idle", // idle, queued, in_progress, completed
    val conclusion: String? = null, // success, failure, cancelled
    val message: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

class StringListConverter {
    @TypeConverter
    fun fromList(list: List<String>): String = Gson().toJson(list)
    @TypeConverter
    fun toList(json: String): List<String> =
        Gson().fromJson(json, object : TypeToken<List<String>>() {}.type)
}
