package com.liteagent.textadventure.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liteagent.textadventure.data.db.ConversationEntity
import com.liteagent.textadventure.data.repository.ConversationRepository
import com.liteagent.textadventure.model.ChatMessage
import com.liteagent.textadventure.model.QuickAction
import com.liteagent.textadventure.service.LiteRtLmService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val liteRtLmService: LiteRtLmService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    private val _quickActions = MutableStateFlow<List<QuickAction>>(emptyList())
    val quickActions: StateFlow<List<QuickAction>> = _quickActions.asStateFlow()

    // Initialize current session
    init {
        setupQuickActions()
        observeSessionMessages()
        loadLatestSession()

        // Observe LiteRT-LM service
        viewModelScope.launch {
            liteRtLmService.isInitialized.collect { isInitialized ->
                _uiState.update { it.copy(isModelInitialized = isInitialized) }
            }
        }

        viewModelScope.launch {
            liteRtLmService.currentMessage.collect { message ->
                _uiState.update { it.copy(currentMessage = message) }
            }
        }

        viewModelScope.launch {
            liteRtLmService.isProcessing.collect { isProcessing ->
                _uiState.update { it.copy(isProcessing = isProcessing) }
            }
        }
    }

    fun loadLatestSession() {
        viewModelScope.launch {
            val latestMessage = withContext(Dispatchers.IO) {
                conversationRepository.getLatestMessage()
            }
            if (latestMessage != null) {
                _currentSessionId.value = latestMessage.activeSessionId
            } else {
                initCurrentSession()
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeSessionMessages() {
        viewModelScope.launch {
            _currentSessionId.filterNotNull().flatMapLatest { sessionId ->
                conversationRepository.getMessagesBySession(sessionId)
            }.collect { entities ->
                val chatMessages = entities.map { entity ->
                    ChatMessage(
                        id = entity.messageId,
                        text = entity.text,
                        role = if (entity.role == "user") ChatMessage.Role.USER else ChatMessage.Role.ASSISTANT
                    )
                }
                _uiState.update {
                    it.copy(
                        messages = chatMessages,
                        showWelcome = chatMessages.isEmpty()
                    )
                }
            }
        }
    }

    private fun initCurrentSession() {
        if (_currentSessionId.value == null) {
            _currentSessionId.value = UUID.randomUUID().toString()
            clearChat()
        }
    }

    private fun setupQuickActions() {
        val defaultActions = listOf(
            QuickAction(
                id = "continue",
                label = "Continue",
                text = "Continue the story"
            ),
            QuickAction(
                id = "examine",
                label = "Examine",
                text = "Examine current situation"
            ),
            QuickAction(
                id = "help",
                label = "Help",
                text = "What are my options?"
            )
        )
        _quickActions.value = defaultActions
    }

    fun clearChat() {
        _uiState.update {
            it.copy(messages = emptyList(), currentMessage = null, showWelcome = true, selectedQuickAction = null)
        }
    }

    fun onInputChange(text: String) {
        if (text.isNotEmpty()) {
            _uiState.update { it.copy(showWelcome = false) }
        }
        _uiState.update { it.copy(userInput = text, selectedQuickAction = null) }
    }

    fun onQuickActionSelected(action: QuickAction) {
        _uiState.update { it.copy(userInput = action.text, selectedQuickAction = action, showWelcome = false) }
    }

    fun sendChatMessage() {
        val text = _uiState.value.userInput ?: return
        if (text.isEmpty()) return

        // Clear input
        _uiState.update { it.copy(userInput = "", selectedQuickAction = null) }

        val sessionId = _currentSessionId.value ?: return

        // Add user message to chat
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            role = ChatMessage.Role.USER
        )

        viewModelScope.launch {
            saveMessage(ConversationEntity(
                messageId = userMessage.id,
                text = userMessage.text,
                role = "user",
                sessionId = sessionId,
                activeSessionId = sessionId
            ))

            // Generate AI response
            generateAiResponse(sessionId, text)
        }
    }

    private suspend fun generateAiResponse(sessionId: String, prompt: String) {
        try {
            val response = liteRtLmService.chat(prompt)

            if (response.isNotEmpty()) {
                val aiMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = response,
                    role = ChatMessage.Role.ASSISTANT
                )

                saveMessage(ConversationEntity(
                    messageId = aiMessage.id,
                    text = aiMessage.text,
                    role = "ai",
                    sessionId = sessionId,
                    activeSessionId = sessionId
                ))

                _uiState.update { it.copy(currentMessage = null) }
            } else {
                _uiState.update { it.copy(error = "AI failed to respond") }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(error = e.message ?: "Unknown error", currentMessage = null)
            }
        }
    }

    private suspend fun saveMessage(message: ConversationEntity) {
        withContext(Dispatchers.IO) {
            conversationRepository.addMessage(message)
        }
    }

    fun showWelcome() {
        _uiState.update { it.copy(showWelcome = true, error = null) }
    }

    fun retryLastMessage() {
        _uiState.value.messages.lastOrNull { it.role == ChatMessage.Role.USER }?.let { lastUserMsg ->
            viewModelScope.launch {
                generateAiResponse(_currentSessionId.value ?: return@launch, lastUserMsg.text)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        liteRtLmService.dispose()
    }
}

// UI State
data class MainUiState(
    val messages: List<ChatMessage> = emptyList(),
    val userInput: String? = null,
    val showWelcome: Boolean = true,
    val isModelInitialized: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val currentMessage: String? = null,
    val selectedQuickAction: QuickAction? = null
)
