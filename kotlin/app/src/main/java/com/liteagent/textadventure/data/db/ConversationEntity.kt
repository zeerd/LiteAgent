package com.liteagent.textadventure.data.db

import androidx.room.*
import java.util.UUID

@Entity(tableName = "conversation_table")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "message_id")
    val messageId: String = UUID.randomUUID().toString(),
    val text: String,
    val role: String, // "user" or "ai"
    @ColumnInfo(name = "session_id")
    val sessionId: String?,
    @ColumnInfo(name = "active_session_id")
    val activeSessionId: String?,
    val timestamp: Long = System.currentTimeMillis()
) {
    val timestampLocal: java.sql.Timestamp
        get() = java.sql.Timestamp(timestamp)
}

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
