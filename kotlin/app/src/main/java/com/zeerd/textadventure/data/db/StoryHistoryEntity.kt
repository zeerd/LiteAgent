package com.zeerd.textadventure.data.db

import androidx.room.*
import java.util.UUID

/**
 * 故事历史记录的数据库实体类。
 * 存储每个冒险会话的元数据。
 */
@Entity(tableName = "story_history_table")
data class StoryHistoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(), // 故事会话的唯一 ID

    @ColumnInfo(name = "setting_id")
    val settingId: String, // 故事背景设置 ID

    @ColumnInfo(name = "setting_title")
    val settingTitle: String, // 故事标题/背景名称

    @ColumnInfo(name = "story_beginning")
    val storyBeginning: String, // 故事的开头描述

    @ColumnInfo(name = "last_active")
    val lastActive: Long = System.currentTimeMillis(), // 最后一次活动的时间戳

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(), // 创建故事的时间戳

    @ColumnInfo(name = "message_count")
    val messageCount: Int = 0, // 该故事中的消息总数

    @ColumnInfo(name = "current_context")
    val currentContext: String? = null // AI 模型的当前上下文快照（可选）
)
