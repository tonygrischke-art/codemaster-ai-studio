package com.codemaster.aistudio.data.dao

import androidx.room.*
import com.codemaster.aistudio.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE projectId = :projectId ORDER BY timestamp ASC")
    fun getMessagesForProject(projectId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages WHERE projectId = :projectId")
    suspend fun clearMessagesForProject(projectId: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()

    @Query("SELECT SUM(tokenCount) FROM chat_messages WHERE projectId = :projectId")
    suspend fun getTotalTokensForProject(projectId: Long): Int?
}
