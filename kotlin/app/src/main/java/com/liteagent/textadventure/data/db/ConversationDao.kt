package com.liteagent.textadventure.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationEntity): Long

    @Update
    suspend fun updateMessage(message: ConversationEntity)

    @Delete
    suspend fun deleteMessage(message: ConversationEntity)

    @Query("SELECT * FROM conversation_table WHERE active_session_id = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversation_table WHERE active_session_id = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesBySessionSync(sessionId: String): List<ConversationEntity>

    @Query("SELECT * FROM conversation_table WHERE active_session_id IS NULL ORDER BY timestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("DELETE FROM conversation_table WHERE active_session_id = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: String)

    @Query("SELECT * FROM conversation_table WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): ConversationEntity?

    @Query("SELECT COUNT(*) FROM conversation_table")
    fun getMessageCount(): Flow<Int>

    @Query("SELECT * FROM conversation_table WHERE active_session_id = :sessionId AND timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getMessagesSince(sessionId: String, since: Long): List<ConversationEntity>

    @Query("SELECT COUNT(*) FROM conversation_table WHERE active_session_id = :sessionId AND role = 'ai'")
    fun getAiMessageCount(sessionId: String): Flow<Int>

    @Query("SELECT * FROM conversation_table ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessage(): ConversationEntity?
}
