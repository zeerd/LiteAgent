package com.zeerd.textadventure.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeerd.textadventure.data.db.ConversationEntity
import com.zeerd.textadventure.data.local.AppSettingsRepository
import com.zeerd.textadventure.data.repository.ConversationRepository
import com.zeerd.textadventure.data.repository.StoryHistoryRepository
import com.zeerd.textadventure.model.ChatMessage
import com.zeerd.textadventure.model.QuickAction
import com.zeerd.textadventure.service.LiteRtLmService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * 主界面的视图模型，处理游戏的核心聊天逻辑、模型初始化和状态管理。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val storyHistoryRepository: StoryHistoryRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val liteRtLmService: LiteRtLmService
) : ViewModel() {

    companion object {
        private const val TAG = "TextAdventure-MainViewModel"
    }

    // UI 状态流
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // 当前活动的故事会话 ID
    private val _currentSessionId = MutableStateFlow<String?>(null)

    // 快捷动作列表
    private val _quickActions = MutableStateFlow<List<QuickAction>>(emptyList())
    val quickActions: StateFlow<List<QuickAction>> = _quickActions.asStateFlow()

    init {
        Log.d(TAG, "Initializing MainViewModel")
        setupQuickActions()
        observeSessionMessages()
        observeLatestSession()

        // 观察 LiteRT-LM 服务的初始化状态
        viewModelScope.launch {
            liteRtLmService.isInitialized.collect { isInitialized ->
                _uiState.update { it.copy(isModelInitialized = isInitialized) }
            }
        }

        // 观察 AI 当前正在生成的消息（流式更新）
        viewModelScope.launch {
            liteRtLmService.currentMessage.collect { message ->
                _uiState.update { it.copy(currentMessage = message) }
            }
        }

        // 观察 AI 是否正在处理中
        viewModelScope.launch {
            liteRtLmService.isProcessing.collect { isProcessing ->
                _uiState.update { it.copy(isProcessing = isProcessing) }
            }
        }
    }

    /**
     * 观察数据库中消息的变化，以便在需要时加载最新会话。
     */
    private fun observeLatestSession() {
        Log.v(TAG, "Observing latest session from database...")
        viewModelScope.launch {
            conversationRepository.getMessageCount().collect { count ->
                loadLatestSession()
            }
        }
    }

    /**
     * 加载数据库中最后活跃的会话。
     */
    fun loadLatestSession() {
        Log.v(TAG, "Loading latest session from database...")
        viewModelScope.launch {
            val latestMessage = withContext(Dispatchers.IO) {
                conversationRepository.getLatestMessage()
            }
            if (latestMessage != null) {
                // 如果会话 ID 发生变化，更新当前会话并恢复 AI 上下文
                if (_currentSessionId.value != latestMessage.activeSessionId) {
                    val sessionId = latestMessage.activeSessionId!!
                    _currentSessionId.value = sessionId
                    restoreSessionContext(sessionId)
                }
            } else {
                // 如果没有历史记录，创建一个新的空会话
                if (_currentSessionId.value == null) {
                    _currentSessionId.value = UUID.randomUUID().toString()
                }
            }
        }
    }

    /**
     * 恢复指定会话的 LLM 上下文。
     * 这包括重新初始化引擎并加载历史消息。
     */
    private fun restoreSessionContext(sessionId: String) {
        Log.v(TAG, "Restoring session context for session: $sessionId")
        viewModelScope.launch {
            try {
                val messages = withContext(Dispatchers.IO) {
                    conversationRepository.getMessagesBySessionSync(sessionId)
                }

                if (messages.isEmpty()) return@launch

                liteRtLmService.getScope().launch {
                    try {
                        val appSettings = appSettingsRepository.getSettings()
                        val modelPath = appSettings.selectedModelPath

                        if (modelPath == null || !File(modelPath).exists()) return@launch

                        val skillDir = "/data/user/0/com.zeerd.textadventure/files/skills"

                        // 初始化引擎
                        val isInitSuccess = liteRtLmService.initialize(
                            engineConfig = liteRtLmService.getEngineConfig().copy(
                                modelPath = modelPath,
                                backend = if (appSettings.accelerationMode == "GPU")
                                    com.google.ai.edge.litertlm.Backend.GPU()
                                else
                                    com.google.ai.edge.litertlm.Backend.CPU(),
                                maxNumTokens = appSettings.maxTokens
                            ),
                            skillDir = skillDir,
                            allowedSkills = listOf("text-adventure"),
                            compressionThreshold = 0.75f,
                            temperature = appSettings.temperature
                        )

                        if (isInitSuccess) {
                            // 恢复历史消息到引擎内存
                            val serviceMessages = messages.map { entity ->
                                com.zeerd.textadventure.service.ContextCompressor.Message(
                                    role = entity.role,
                                    content = entity.text
                                )
                            }
                            liteRtLmService.restoreHistory(serviceMessages)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore LLM context: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in restoreSessionContext: ${e.message}", e)
            }
        }
    }

    /**
     * 手动加载特定的会话。
     */
    fun loadSession(sessionId: String) {
        Log.v(TAG, "Loading session: $sessionId")
        if (_currentSessionId.value != sessionId) {
            _currentSessionId.value = sessionId
            restoreSessionContext(sessionId)
        }
    }

    /**
     * 观察当前会话的消息列表并更新 UI。
     */
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
                        role = when (entity.role) {
                            "user" -> ChatMessage.Role.USER
                            "ai", "assistant" -> ChatMessage.Role.ASSISTANT
                            "system" -> ChatMessage.Role.SYSTEM
                            else -> ChatMessage.Role.ASSISTANT
                        }
                    )
                }

                if (chatMessages.isEmpty()) {
                    Log.v(TAG, "No messages found for session: ${_currentSessionId.value}")
                } else {
                    _uiState.update {
                        Log.v(TAG, "Updating UI with ${chatMessages.size} messages for session: ${_currentSessionId.value}")
                        it.copy(
                            messages = chatMessages,
                            showWelcome = chatMessages.isEmpty()
                        )
                    }
                }
            }
        }
    }

    private fun setupQuickActions() {
        // 配置快捷动作（如需要可在此添加）
    }

    /**
     * 清空当前聊天界面。
     */
    fun clearChat() {
        Log.v(TAG, "Clearing chat for current session: ${_currentSessionId.value}")
        _uiState.update {
            it.copy(messages = emptyList(), currentMessage = null, showWelcome = true, selectedQuickAction = null)
        }
    }

    /**
     * 处理用户输入内容的变化。
     */
    fun onInputChange(text: String) {
        if (text.isNotEmpty()) {
            _uiState.update { it.copy(showWelcome = false) }
        }
        _uiState.update { it.copy(userInput = text, selectedQuickAction = null) }
    }

    /**
     * 发送聊天消息。
     */
    fun sendChatMessage() {
        val text = _uiState.value.userInput ?: return
        if (text.isEmpty()) return

        Log.v(TAG, "Sending chat message: ${_uiState.value.userInput}")
        _uiState.update { it.copy(userInput = "", selectedQuickAction = null) }

        val sessionId = _currentSessionId.value ?: return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            role = ChatMessage.Role.USER
        )

        viewModelScope.launch {
            // 保存用户消息
            saveMessage(ConversationEntity(
                messageId = userMessage.id,
                text = userMessage.text,
                role = "user",
                sessionId = sessionId,
                activeSessionId = sessionId
            ))

            // 调用 AI 生成响应
            generateAiResponse(sessionId, text)
        }
    }

    /**
     * 调用 AI 服务生成对话内容。
     */
    private suspend fun generateAiResponse(sessionId: String, prompt: String) {
        try {
            Log.v(TAG, "Generating AI response for prompt:\n$prompt")
            val response = liteRtLmService.chat(prompt)

            if (response.isNotEmpty()) {
                Log.v(TAG, "AI response generated:\n$response")
                val aiMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = response,
                    role = ChatMessage.Role.ASSISTANT
                )

                // 保存 AI 消息到数据库
                saveMessage(ConversationEntity(
                    messageId = aiMessage.id,
                    text = aiMessage.text,
                    role = "ai",
                    sessionId = sessionId,
                    activeSessionId = sessionId
                ))

                Log.d(TAG, "UI updated with new AI message, clearing currentMessage stream")
                _uiState.update { it.copy(currentMessage = null) }

                // 更新该故事的最后活动时间
                viewModelScope.launch {
                    try {
                        val story = storyHistoryRepository.getStoryById(sessionId)
                        if (story != null) {
                            storyHistoryRepository.updateStory(story.copy(lastActive = System.currentTimeMillis()))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating story last active time", e)
                    }
                }
            } else {
                _uiState.update { it.copy(error = "AI failed to respond") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating AI response", e)
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
        Log.v(TAG, "Showing welcome screen")
        _uiState.update { it.copy(showWelcome = true, error = null) }
    }

    /**
     * 重新尝试最后一轮对话。
     */
    fun retryLastMessage() {
        Log.v(TAG, "Retrying last message for session: ${_currentSessionId.value}")
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

/**
 * 主界面的 UI 状态数据类。
 */
data class MainUiState(
    val messages: List<ChatMessage> = emptyList(), // 对话历史列表
    val userInput: String? = null, // 当前用户输入
    val showWelcome: Boolean = true, // 是否显示欢迎界面
    val isModelInitialized: Boolean = false, // 模型是否已就绪
    val isProcessing: Boolean = false, // 模型是否正在生成中
    val statusMessage: String? = null, // 状态消息
    val error: String? = null, // 错误信息
    val currentMessage: String? = null, // 正在流式生成的当前消息内容
    val selectedQuickAction: QuickAction? = null // 当前选中的快捷动作
)
