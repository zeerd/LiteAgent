package com.liteagent.textadventure.data.repository

import com.liteagent.textadventure.data.db.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 对话数据存储库，负责与数据库交互以管理聊天消息。
 */
@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao
) {
    /**
     * 根据会话 ID 获取消息列表的流。
     */
    fun getMessagesBySession(sessionId: String): Flow<List<ConversationEntity>> {
        return conversationDao.getMessagesBySession(sessionId)
    }

    /**
     * 向数据库添加一条新消息。
     */
    suspend fun addMessage(message: ConversationEntity): Long {
        return conversationDao.insertMessage(message)
    }

    /**
     * 更新数据库中的现有消息。
     */
    suspend fun updateMessage(message: ConversationEntity) {
        conversationDao.updateMessage(message)
    }

    /**
     * 根据 ID 删除单条消息。
     */
    suspend fun deleteMessage(messageId: Long) {
        val message = conversationDao.getMessageById(messageId)
        message?.let { conversationDao.deleteMessage(it) }
    }

    /**
     * 删除指定会话的所有消息。
     */
    suspend fun deleteMessagesBySession(sessionId: String) {
        conversationDao.deleteMessagesBySession(sessionId)
    }

    /**
     * 同步获取指定会话的所有消息。
     */
    suspend fun getMessagesBySessionSync(sessionId: String): List<ConversationEntity> {
        return conversationDao.getMessagesBySessionSync(sessionId)
    }

    /**
     * 获取所有会话的所有消息。
     */
    fun getAllConversations(): Flow<List<ConversationEntity>> {
        return conversationDao.getAllConversations()
    }

    /**
     * 获取指定时间点之后的所有消息。
     */
    suspend fun getMessagesSince(sessionId: String, since: Long): List<ConversationEntity> {
        return conversationDao.getMessagesSince(sessionId, since)
    }

    /**
     * 获取消息总数的流。
     */
    fun getMessageCount(): Flow<Int> {
        return conversationDao.getMessageCount()
    }

    /**
     * 获取最后一条消息。
     */
    suspend fun getLatestMessage(): ConversationEntity? {
        return conversationDao.getLatestMessage()
    }
}
