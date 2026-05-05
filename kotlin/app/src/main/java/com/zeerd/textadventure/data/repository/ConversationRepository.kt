package com.zeerd.textadventure.data.repository

import android.util.Log
import com.zeerd.textadventure.data.db.*
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
    companion object {
        private const val TAG = "TextAdventure-ConversationRepository"
    }
    /**
     * 根据会话 ID 获取消息列表的流。
     */
    fun getMessagesBySession(sessionId: String): Flow<List<ConversationEntity>> {
        Log.v(TAG, ">>> getMessagesBySession() IN - sessionId=$sessionId")
        val result = conversationDao.getMessagesBySession(sessionId)
        Log.v(TAG, "<<< getMessagesBySession() OUT - Flow returned")
        return result
    }

    /**
     * 向数据库添加一条新消息。
     */
    suspend fun addMessage(message: ConversationEntity): Long {
        Log.v(TAG, ">>> addMessage() IN - messageId=${message.messageId}")
        val result = conversationDao.insertMessage(message)
        Log.v(TAG, "<<< addMessage() OUT - insertedId=$result")
        return result
    }

    /**
     * 更新数据库中的现有消息。
     */
    suspend fun updateMessage(message: ConversationEntity) {
        Log.v(TAG, ">>> updateMessage() IN - messageId=${message.messageId}")
        conversationDao.updateMessage(message)
        Log.v(TAG, "<<< updateMessage() OUT")
    }

    /**
     * 根据 ID 删除单条消息。
     */
    suspend fun deleteMessage(messageId: Long) {
        Log.v(TAG, ">>> deleteMessage() IN - messageId=$messageId")
        val message = conversationDao.getMessageById(messageId)
        message?.let { conversationDao.deleteMessage(it) }
        Log.v(TAG, "<<< deleteMessage() OUT")
    }

    /**
     * 删除指定会话的所有消息。
     */
    suspend fun deleteMessagesBySession(sessionId: String) {
        Log.v(TAG, ">>> deleteMessagesBySession() IN - sessionId=$sessionId")
        conversationDao.deleteMessagesBySession(sessionId)
        Log.v(TAG, "<<< deleteMessagesBySession() OUT")
    }

    /**
     * 同步获取指定会话的所有消息。
     */
    suspend fun getMessagesBySessionSync(sessionId: String): List<ConversationEntity> {
        Log.v(TAG, ">>> getMessagesBySessionSync() IN - sessionId=$sessionId")
        val result = conversationDao.getMessagesBySessionSync(sessionId)
        Log.v(TAG, "<<< getMessagesBySessionSync() OUT - count=${result.size}")
        return result
    }

    /**
     * 获取所有会话的所有消息。
     */
    fun getAllConversations(): Flow<List<ConversationEntity>> {
        Log.v(TAG, ">>> getAllConversations() IN")
        val result = conversationDao.getAllConversations()
        Log.v(TAG, "<<< getAllConversations() OUT - Flow returned")
        return result
    }

    /**
     * 获取指定时间点之后的所有消息。
     */
    suspend fun getMessagesSince(sessionId: String, since: Long): List<ConversationEntity> {
        Log.v(TAG, ">>> getMessagesSince() IN - sessionId=$sessionId, since=$since")
        val result = conversationDao.getMessagesSince(sessionId, since)
        Log.v(TAG, "<<< getMessagesSince() OUT - count=${result.size}")
        return result
    }

    /**
     * 获取消息总数的流。
     */
    fun getMessageCount(): Flow<Int> {
        Log.v(TAG, ">>> getMessageCount() IN")
        val result = conversationDao.getMessageCount()
        Log.v(TAG, "<<< getMessageCount() OUT - Flow returned")
        return result
    }

    /**
     * 获取最后一条消息。
     */
    suspend fun getLatestMessage(): ConversationEntity? {
        Log.v(TAG, ">>> getLatestMessage() IN")
        val result = conversationDao.getLatestMessage()
        Log.v(TAG, "<<< getLatestMessage() OUT - ${if (result != null) "found" else "null"}")
        return result
    }
}
