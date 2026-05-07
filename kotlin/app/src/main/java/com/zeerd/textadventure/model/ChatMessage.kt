package com.zeerd.textadventure.model

import androidx.compose.runtime.Immutable

/**
 * 聊天消息数据模型。
 */
@Immutable
data class ChatMessage(
    val id: String, // 消息 ID
    val text: String, // 消息内容
    val role: Role, // 发送者角色
    val timestamp: Long = System.currentTimeMillis() // 时间戳
) {
    /**
     * 对话角色枚举。
     */
    enum class Role {
        USER,       // 玩家
        ASSISTANT,  // 助手（AI 游戏大师）
        SYSTEM      // 内部上下文或系统提示词
    }

    /**
     * 判断是否为用户发送的消息。
     */
    val isUser: Boolean
        get() = role == Role.USER
}

/**
 * 界面底部的快捷动作模型。
 */
data class QuickAction(
    val id: String,
    val label: String, // 按钮显示的标签
    val text: String, // 触发的实际命令文本
    val icon: String? = null // 可选图标名称
)

/**
 * 故事背景设置模型。
 */
data class StorySetting(
    val id: String,
    val title: String, // 标题
    val description: String, // 详细描述
    val genre: Genre // 题材类型
) {
    /**
     * 故事题材枚举。
     */
    enum class Genre {
        FANTASY, // 奇幻
        SCIFI,   // 科幻
        HORROR,  // 恐怖
        ROMANCE, // 浪漫
        MYSTERY  // 悬疑
    }
}

/**
 * 故事历史简要记录模型。
 */
data class StoryHistoryEntry(
    val id: String,
    val settingId: String,
    val settingTitle: String,
    val storyBeginning: String,
    val lastActive: Long = System.currentTimeMillis(),
    val messageCount: Int = 0
)
