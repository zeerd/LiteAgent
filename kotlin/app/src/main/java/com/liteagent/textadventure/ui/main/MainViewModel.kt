package com.liteagent.textadventure.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liteagent.textadventure.data.db.ConversationEntity
import com.liteagent.textadventure.data.local.AppSettingsRepository
import com.liteagent.textadventure.data.repository.ConversationRepository
import com.liteagent.textadventure.data.repository.StoryHistoryRepository
import com.liteagent.textadventure.model.ChatMessage
import com.liteagent.textadventure.model.QuickAction
import com.liteagent.textadventure.service.LiteRtLmService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val storyHistoryRepository: StoryHistoryRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val liteRtLmService: LiteRtLmService
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    private val _quickActions = MutableStateFlow<List<QuickAction>>(emptyList())
    val quickActions: StateFlow<List<QuickAction>> = _quickActions.asStateFlow()

    // Initialize current session
    init {
        Log.d(TAG, "Initializing MainViewModel")
        setupQuickActions()
        observeSessionMessages()
        observeLatestSession()

        // Observe LiteRT-LM service
        viewModelScope.launch {
            liteRtLmService.isInitialized.collect { isInitialized ->
                Log.d(TAG, "LiteRT-LM initialized state changed: $isInitialized")
                _uiState.update { it.copy(isModelInitialized = isInitialized) }
            }
        }

        viewModelScope.launch {
            liteRtLmService.currentMessage.collect { message ->
                // if (message.isNotEmpty()) {
                //     Log.v(TAG, "Streaming message update: ${message.take(20)}...")
                // }
                _uiState.update { it.copy(currentMessage = message) }
            }
        }

        viewModelScope.launch {
            liteRtLmService.isProcessing.collect { isProcessing ->
                Log.d(TAG, "LiteRT-LM isProcessing state changed: $isProcessing")
                _uiState.update { it.copy(isProcessing = isProcessing) }
            }
        }
    }

    private fun observeLatestSession() {
        viewModelScope.launch {
            conversationRepository.getMessageCount().collect { count ->
                Log.d(TAG, "Message count changed: $count. Reloading latest session.")
                loadLatestSession()
            }
        }
    }

    fun loadLatestSession() {
        viewModelScope.launch {
            val latestMessage = withContext(Dispatchers.IO) {
                conversationRepository.getLatestMessage()
            }
            if (latestMessage != null) {
                Log.d(TAG, "Latest message found, session ID: ${latestMessage.activeSessionId}")
                // If the session changed, we update it to trigger observeSessionMessages
                if (_currentSessionId.value != latestMessage.activeSessionId) {
                    Log.d(TAG, "Updating currentSessionId from ${_currentSessionId.value} to ${latestMessage.activeSessionId}")
                    val sessionId = latestMessage.activeSessionId!!
                    _currentSessionId.value = sessionId
                    restoreSessionContext(sessionId)
                }
            } else {
                Log.d(TAG, "No latest message found.")
                // Only init if no session exists
                if (_currentSessionId.value == null) {
                    val newSessionId = UUID.randomUUID().toString()
                    Log.d(TAG, "Initializing new session ID: $newSessionId")
                    _currentSessionId.value = newSessionId
                }
            }
        }
    }

    private fun restoreSessionContext(sessionId: String) {
        Log.d(TAG, "Restoring LLM context for session: $sessionId")
        viewModelScope.launch {
            try {
                val messages = withContext(Dispatchers.IO) {
                    conversationRepository.getMessagesBySessionSync(sessionId)
                }

                if (messages.isEmpty()) {
                    Log.d(TAG, "No messages to restore for session $sessionId")
                    return@launch
                }

                liteRtLmService.getScope().launch {
                    try {
                        val appSettings = appSettingsRepository.getSettings()
                        val modelPath = appSettings.selectedModelPath

                        if (modelPath == null || !File(modelPath).exists()) {
                            Log.e(TAG, "Cannot restore context: Model path invalid")
                            return@launch
                        }

                        // Use a reliable skill directory path
                        val skillDir = "/data/user/0/com.liteagent.textadventure/files/skills"

                        Log.d(TAG, "Initializing engine for context restoration...")
                        val isInitSuccess = liteRtLmService.initialize(
                            engineConfig = liteRtLmService.getEngineConfig().copy(
                                modelPath = modelPath,
                                backend = if (appSettings.accelerationMode == "GPU")
                                    com.google.ai.edge.litertlm.Backend.GPU()
                                else
                                    com.google.ai.edge.litertlm.Backend.CPU(),
                                maxNumTokens = appSettings.maxTokens
                            ),
                            systemPrompt = "You are a text adventure game master using the text-adventure skill.",
                            skillDir = skillDir,
                            allowedSkills = listOf("text-adventure"),
                            compressionThreshold = 0.75f,
                            temperature = appSettings.temperature
                        )

                        if (isInitSuccess) {
                            Log.d(TAG, "Engine initialized, restoring ${messages.size} messages")
                            val serviceMessages = messages.map { entity ->
                                com.liteagent.textadventure.service.ContextCompressor.Message(
                                    role = entity.role,
                                    content = entity.text
                                )
                            }
                            liteRtLmService.restoreHistory(serviceMessages)
                            Log.d(TAG, "LLM Context restored successfully")
                        } else {
                            Log.e(TAG, "Engine failed to initialize during context restoration")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore LLM context: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in restoreSessionContext: ${e.message}")
            }
        }
    }

    fun loadSession(sessionId: String) {
        Log.d(TAG, "Manually loading session: $sessionId")
        if (_currentSessionId.value != sessionId) {
            _currentSessionId.value = sessionId
            restoreSessionContext(sessionId)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeSessionMessages() {
        viewModelScope.launch {
            _currentSessionId.filterNotNull().flatMapLatest { sessionId ->
                Log.d(TAG, "Observing messages for session: $sessionId")
                conversationRepository.getMessagesBySession(sessionId)
            }.collect { entities ->
                Log.d(TAG, "Received ${entities.size} message entities for session")
                val chatMessages = entities.map { entity ->
                    ChatMessage(
                        id = entity.messageId,
                        text = entity.text,
                        role = if (entity.role == "user") ChatMessage.Role.USER else ChatMessage.Role.ASSISTANT
                    )
                }

                _uiState.update {
                    Log.d(TAG, "Updating UI with ${chatMessages.size} messages.")
                    it.copy(
                        messages = chatMessages,
                        showWelcome = chatMessages.isEmpty()
                    )
                }
            }
        }
    }

    private fun setupQuickActions() {
        // Function preserved but body can be empty or removed if no longer needed at all.
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

        Log.d(TAG, "Sending user message: ${text.take(50)}...")

        // Clear input
        _uiState.update { it.copy(userInput = "", selectedQuickAction = null) }

        val sessionId = _currentSessionId.value ?: run {
            Log.e(TAG, "Cannot send message: sessionId is null")
            return
        }

        // Add user message to chat
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            role = ChatMessage.Role.USER
        )

        viewModelScope.launch {
            Log.d(TAG, "Saving user message to repository")
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
        Log.d(TAG, "Generating AI response for prompt: ${prompt.take(50)}...")
        try {
            val response = liteRtLmService.chat(prompt)

            if (response.isNotEmpty()) {
                Log.d(TAG, "AI responded with ${response.length} characters")
                Log.v(TAG, "AI response: {response}")
                val aiMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = response,
                    role = ChatMessage.Role.ASSISTANT
                )

                Log.d(TAG, "Saving AI response to repository")
                saveMessage(ConversationEntity(
                    messageId = aiMessage.id,
                    text = aiMessage.text,
                    role = "ai",
                    sessionId = sessionId,
                    activeSessionId = sessionId
                ))

                _uiState.update { it.copy(currentMessage = null) }

                // Update last active time in story history
                viewModelScope.launch {
                    try {
                        val story = storyHistoryRepository.getStoryById(sessionId)
                        if (story != null) {
                            storyHistoryRepository.updateStory(story.copy(lastActive = System.currentTimeMillis()))
                            Log.d(TAG, "Updated lastActive for story: $sessionId")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update story lastActive: ${e.message}")
                    }
                }
            } else {
                Log.e(TAG, "AI failed to respond (empty response)")
                _uiState.update { it.copy(error = "AI failed to respond") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating AI response: ${e.message}", e)
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
    val statusMessage: String? = null,
    val error: String? = null,
    val currentMessage: String? = null,
    val selectedQuickAction: QuickAction? = null
)
