package com.liteagent.textadventure.model

import androidx.compose.runtime.Immutable

@Immutable
data class ChatMessage(
    val id: String,
    val text: String,
    val role: Role,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Role {
        USER,
        AI,
        ASSISTANT
    }
    
    val isUser: Boolean
        get() = role == Role.USER
}

// Quick action definition
data class QuickAction(
    val id: String,
    val label: String,
    val text: String,
    val icon: String? = null
)

// Story setting definition
data class StorySetting(
    val id: String,
    val title: String,
    val description: String,
    val genre: Genre
) {
    enum class Genre {
        FANTASY,
        SCIFI,
        HORROR,
        ROMANCE,
        MYSTERY
    }
}

// Story history entry
data class StoryHistoryEntry(
    val id: String,
    val settingId: String,
    val settingTitle: String,
    val storyBeginning: String,
    val lastActive: Long = System.currentTimeMillis(),
    val messageCount: Int = 0
)
