package com.zeerd.textadventure.ui.historyedit

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeerd.textadventure.data.db.ConversationEntity
import com.zeerd.textadventure.data.repository.ConversationRepository
import com.zeerd.textadventure.data.repository.StoryHistoryRepository
import com.zeerd.textadventure.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HistoryEditViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val storyHistoryRepository: StoryHistoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>(Destinations.ARG_STORY_ID) ?: ""

    private val _messages = MutableStateFlow<List<ConversationEntity>>(emptyList())
    val messages: StateFlow<List<ConversationEntity>> = _messages.asStateFlow()

    init {
        Log.d("HistoryEditViewModel", "Initialized with sessionId: $sessionId")
        if (sessionId.isNotEmpty()) {
            loadMessages()
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            conversationRepository.getMessagesBySession(sessionId).collect {
                _messages.value = it
            }
        }
    }

    fun deleteFromMessage(message: ConversationEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val allMessages = conversationRepository.getMessagesBySessionSync(sessionId)
                val messagesToDelete = allMessages.filter { it.timestamp >= message.timestamp }

                messagesToDelete.forEach {
                    conversationRepository.deleteMessage(it.id)
                }

                // 更新故事历史表中的最后活跃时间和消息预览
                val remainingMessages = allMessages.filter { it.timestamp < message.timestamp }
                val story = storyHistoryRepository.getStoryById(sessionId)
                if (story != null) {
                    storyHistoryRepository.updateStory(
                        story.copy(
                            lastActive = System.currentTimeMillis(),
                            messageCount = remainingMessages.size,
                            storyBeginning = remainingMessages.firstOrNull()?.text?.take(200) ?: story.storyBeginning
                        )
                    )
                }

                Log.d("HistoryEditViewModel", "Deleted ${messagesToDelete.size} messages from session $sessionId")
            }
        }
    }

    fun updateLastActive() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val story = storyHistoryRepository.getStoryById(sessionId)
                story?.let {
                    storyHistoryRepository.updateStory(it.copy(lastActive = System.currentTimeMillis()))
                }
            }
        }
    }
}
