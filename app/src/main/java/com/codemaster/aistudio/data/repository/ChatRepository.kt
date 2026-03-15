package com.codemaster.aistudio.data.repository

import com.codemaster.aistudio.data.dao.ChatMessageDao
import com.codemaster.aistudio.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatMessageDao: ChatMessageDao
) {
    fun getMessagesForProject(projectId: Long): Flow<List<ChatMessage>> =
        chatMessageDao.getMessagesForProject(projectId)

    fun getAllMessages(): Flow<List<ChatMessage>> = chatMessageDao.getAllMessages()

    suspend fun saveMessage(message: ChatMessage): Long =
        chatMessageDao.insertMessage(message)

    suspend fun clearMessagesForProject(projectId: Long) =
        chatMessageDao.clearMessagesForProject(projectId)

    suspend fun clearAll() = chatMessageDao.clearAll()

    suspend fun getTotalTokens(projectId: Long): Int =
        chatMessageDao.getTotalTokensForProject(projectId) ?: 0
}
