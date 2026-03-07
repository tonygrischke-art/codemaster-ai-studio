package com.codemaster.aistudio.data

import androidx.room.*
import com.codemaster.aistudio.data.model.*
import kotlinx.coroutines.flow.Flow

// ─── DAOs ──────────────────────────────────────────────────────

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE projectId = :projectId ORDER BY timestamp ASC")
    fun getMessagesForProject(projectId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT 50")
    fun getRecentMessages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Delete
    suspend fun deleteMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE projectId = :projectId")
    suspend fun clearProjectHistory(projectId: String)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastModified DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: String): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project)

    @Update
    suspend fun updateProject(project: Project)

    @Delete
    suspend fun deleteProject(project: Project)
}

// ─── Converters ────────────────────────────────────────────────

class Converters {
    @TypeConverter
    fun fromMessageRole(role: MessageRole): String = role.name
    @TypeConverter
    fun toMessageRole(name: String): MessageRole = MessageRole.valueOf(name)

    @TypeConverter
    fun fromAiProvider(provider: AiProvider): String = provider.name
    @TypeConverter
    fun toAiProvider(name: String): AiProvider = AiProvider.valueOf(name)

    @TypeConverter
    fun fromProjectLanguage(lang: ProjectLanguage): String = lang.name
    @TypeConverter
    fun toProjectLanguage(name: String): ProjectLanguage = ProjectLanguage.valueOf(name)
}

// ─── Database ──────────────────────────────────────────────────

@Database(
    entities = [ChatMessage::class, Project::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CodeMasterDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun projectDao(): ProjectDao

    companion object {
        const val DATABASE_NAME = "codemaster_db"
    }
}
