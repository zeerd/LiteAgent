package com.zeerd.textadventure.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 对话数据的数据库访问对象接口。
 */
@Dao
interface ConversationDao {
    /**
     * 插入一条新消息，如果 ID 冲突则替换。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationEntity): Long

    /**
     * 更新一条现有消息。
     */
    @Update
    suspend fun updateMessage(message: ConversationEntity)

    /**
     * 删除一条特定消息。
     */
    @Delete
    suspend fun deleteMessage(message: ConversationEntity)

    /**
     * 获取指定会话的所有消息流，按时间戳升序排列。
     */
    @Query("SELECT * FROM conversation_table WHERE active_session_id = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<ConversationEntity>>

    /**
     * 同步获取指定会话的所有消息，按时间戳升序排列。
     */
    @Query("SELECT * FROM conversation_table WHERE active_session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesBySessionSync(sessionId: String): List<ConversationEntity>

    /**
     * 获取所有未关联具体会话的消息（如果有）。
     */
    @Query("SELECT * FROM conversation_table WHERE active_session_id IS NULL ORDER BY timestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    /**
     * 删除指定会话的所有消息。
     */
    @Query("DELETE FROM conversation_table WHERE active_session_id = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: String)

    /**
     * 根据主键 ID 查询单条消息。
     */
    @Query("SELECT * FROM conversation_table WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): ConversationEntity?

    /**
     * 获取数据库中消息的总数。
     */
    @Query("SELECT COUNT(*) FROM conversation_table")
    fun getMessageCount(): Flow<Int>

    /**
     * 获取指定会话在特定时间之后的最近消息。
     */
    @Query("SELECT * FROM conversation_table WHERE active_session_id = :sessionId AND timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getMessagesSince(sessionId: String, since: Long): List<ConversationEntity>

    /**
     * 统计指定会话中 AI 生成的消息数量。
     */
    @Query("SELECT COUNT(*) FROM conversation_table WHERE active_session_id = :sessionId AND role = 'assistant'")
    fun getAiMessageCount(sessionId: String): Flow<Int>

    /**
     * 获取整个数据库中最新的一条消息。
     */
    @Query("SELECT * FROM conversation_table ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessage(): ConversationEntity?
}
