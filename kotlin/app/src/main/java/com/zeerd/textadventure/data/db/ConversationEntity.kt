package com.zeerd.textadventure.data.db

import androidx.room.*
import java.util.UUID

/**
 * 聊天消息的数据库实体类。
 */
@Entity(tableName = "conversation_table")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0, // 自增主键

    @ColumnInfo(name = "message_id")
    val messageId: String = UUID.randomUUID().toString(), // 消息的唯一标识符

    val text: String, // 消息内容

    val role: String, // 发送者角色: "user" (玩家) 或 "ai" (游戏大师) 或 "system" (系统/背景)

    @ColumnInfo(name = "session_id")
    val sessionId: String?, // 会话 ID

    @ColumnInfo(name = "active_session_id")
    val activeSessionId: String?, // 当前活动的会话 ID

    val timestamp: Long = System.currentTimeMillis() // 消息发送的时间戳
) {
    /**
     * 将时间戳转换为可读的本地时间对象。
     */
    val timestampLocal: java.sql.Timestamp
        get() = java.sql.Timestamp(timestamp)
}

/**
 * Room 数据库类型转换器，用于 Long 和 Timestamp 之间的转换。
 */
class LongConverter {
    @TypeConverter
    fun fromLong(value: Long): java.sql.Timestamp {
        return java.sql.Timestamp(value)
    }

    @TypeConverter
    fun toTimestamp(date: java.sql.Timestamp): Long {
        return date.time
    }
}
