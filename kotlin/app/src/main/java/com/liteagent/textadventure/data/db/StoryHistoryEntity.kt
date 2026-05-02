package com.liteagent.textadventure.data.db

import androidx.room.*
import java.util.UUID

@Entity(tableName = "story_history_table")
data class StoryHistoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "setting_id")
    val settingId: String,
    @ColumnInfo(name = "setting_title")
    val settingTitle: String,
    @ColumnInfo(name = "story_beginning")
    val storyBeginning: String,
    @ColumnInfo(name = "last_active")
    val lastActive: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "message_count")
    val messageCount: Int = 0,
    @ColumnInfo(name = "current_context")
    val currentContext: String? = null
)
