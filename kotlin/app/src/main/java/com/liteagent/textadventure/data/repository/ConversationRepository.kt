package com.liteagent.textadventure.data.repository

import com.liteagent.textadventure.data.db.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao
) {
    fun getMessagesBySession(sessionId: String): Flow<List<ConversationEntity>> {
        return conversationDao.getMessagesBySession(sessionId)
    }

    suspend fun addMessage(message: ConversationEntity): Long {
        return conversationDao.insertMessage(message)
    }

    suspend fun updateMessage(message: ConversationEntity) {
        conversationDao.updateMessage(message)
    }

    suspend fun deleteMessage(messageId: Long) {
        val message = conversationDao.getMessageById(messageId)
        message?.let { conversationDao.deleteMessage(it) }
    }

    suspend fun deleteMessagesBySession(sessionId: String) {
        conversationDao.deleteMessagesBySession(sessionId)
    }

    fun getAllConversations(): Flow<List<ConversationEntity>> {
        return conversationDao.getAllConversations()
    }

    suspend fun getMessagesSince(sessionId: String, since: Long): List<ConversationEntity> {
        return conversationDao.getMessagesSince(sessionId, since)
    }

    fun getMessageCount(): Flow<Int> {
        return conversationDao.getMessageCount()
    }

    suspend fun getLatestMessage(): ConversationEntity? {
        return conversationDao.getLatestMessage()
    }
}
