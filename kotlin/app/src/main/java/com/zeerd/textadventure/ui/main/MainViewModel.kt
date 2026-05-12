package com.zeerd.textadventure.ui.main

import android.content.Context
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
import com.zeerd.textadventure.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

/**
 * 主界面的视图模型，处理游戏的核心聊天逻辑、模型初始化和状态管理。
 *
 * 【统一的会话保存和显示机制】
 * - sendMessage(): 统一入口，根据角色类型决定保存逻辑
 * - saveToDatabase(): 将消息存入数据库
 * - observeSessionMessages(): 监听数据库变化，自动同步到 UI
 * - handleUserMessage(): 处理用户消息，触发 AI 生成
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val storyHistoryRepository: StoryHistoryRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val liteRtLmService: LiteRtLmService,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "TextAdventure-MainViewModel"
    }

    // UI 状态流
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // 当前活动的故事会话 ID
    private val _currentSessionId = MutableStateFlow<String?>(null)
    private var initializedSessionId: String? = null

    // 快捷动作列表
    private val _quickActions = MutableStateFlow<List<QuickAction>>(emptyList())
    val quickActions: StateFlow<List<QuickAction>> = _quickActions.asStateFlow()

    init {
        Log.d(TAG, "Initializing MainViewModel")
        observeSessionMessages()
        observeLatestStory()

        // 观察 LiteRT-LM 服务的初始化状态
        viewModelScope.launch {
            liteRtLmService.isInitialized.collect { isInitialized ->
                _uiState.update { it.copy(isModelInitialized = isInitialized) }
            }
        }

        // 观察 AI 当前正在生成的消息（流式更新）
        viewModelScope.launch {
            liteRtLmService.currentMessage.collect { message ->
                _uiState.update { it.copy(currentMessage = cleanAiResponse(message)) }
            }
        }

        // 观察 AI 是否正在处理中
        viewModelScope.launch {
            liteRtLmService.isProcessing.collect { isProcessing ->
                _uiState.update { it.copy(isProcessing = isProcessing) }
            }
        }

        // 监听来自 NewStoryViewModel 的显式“新故事”启动信号
        viewModelScope.launch {
            liteRtLmService.newStorySignal.collect { (sessionId, settingContent) ->
                Log.i(TAG, "Received new story signal for session: $sessionId. Initializing engine for intro generation.")
                liteRtLmService.getScope().launch {
                    if (ensureEngineInitialized(sessionId)) {
                        generateIntroForNewStory(sessionId, settingContent)
                    }
                }
            }
        }
    }

    /**
     * 观察数据库中故事的变化，以便在需要时加载最新会话。
     */
    private fun observeLatestStory() {
        Log.v(TAG, "Observing latest story from database...")
        viewModelScope.launch {
            storyHistoryRepository.getAllStories().collect { stories ->
                val latestStory = stories.maxByOrNull { it.lastActive }
                if (latestStory != null) {
                    // 如果会话 ID 发生变化，更新当前会话并恢复 AI 上下文
                    if (_currentSessionId.value != latestStory.id) {
                        _currentSessionId.value = latestStory.id
                        restoreSessionContext(latestStory.id)
                    }
                } else {
                    // 如果没有历史记录，创建一个新的空会话 ID（但不保存到数据库，直到有消息）
                    if (_currentSessionId.value == null) {
                        _currentSessionId.value = UUID.randomUUID().toString()
                    }
                }
            }
        }
    }

    /**
     * 确保 AI 引擎已针对指定会话初始化。
     * 如果引擎未初始化，则进行完整初始化；如果已针对其他会话初始化，则仅恢复历史记录。
     */
    private suspend fun ensureEngineInitialized(sessionId: String): Boolean {
        if (liteRtLmService.isInitialized.value && initializedSessionId == sessionId) {
            return true
        }

        return try {
            val appSettings = appSettingsRepository.getSettings()
            val modelPath = appSettings.selectedModelPath
            if (modelPath == null || !File(modelPath).exists()) return false

            if (!liteRtLmService.isInitialized.value) {
                Log.d(TAG, "Performing full engine initialization for session: $sessionId")
                val skillDir = extractSkillsFromAssets()
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
                if (!isInitSuccess) return false
            }

            Log.d(TAG, "Restoring history context for session: $sessionId")
            val messages = withContext(Dispatchers.IO) {
                conversationRepository.getMessagesBySessionSync(sessionId)
            }
            val serviceMessages = messages.map { entity ->
                com.zeerd.textadventure.service.ChatMessage(
                    role = entity.role, content = entity.text
                )
            }
            liteRtLmService.restoreHistory(serviceMessages)
            initializedSessionId = sessionId
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error in ensureEngineInitialized: ${e.message}", e)
            false
        }
    }

    /**
     * 恢复指定会话的 LLM 上下文。
     * 遵循延迟初始化原则：仅加载 UI 消息，不主动初始化引擎（除非是明确的新故事启动信号）。
     */
    private fun restoreSessionContext(sessionId: String) {
        Log.v(TAG, "Restoring session context for session: $sessionId. Engine initialization deferred.")
        // 此处不再主动初始化引擎或生成开场白，改为在第一次发送消息时或通过 newStorySignal 触发。
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
     * 观察当前会话的消息列表并更新 UI（数据库 -> UI 自动同步）。
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
                            "assistant" -> ChatMessage.Role.ASSISTANT
                            "system" -> ChatMessage.Role.SYSTEM
                            else -> ChatMessage.Role.ASSISTANT
                        },
                        timestamp = entity.timestamp  // ✅ 映射时间戳
                    )
                }

                if (chatMessages.isNotEmpty()) {
                    _uiState.update {
                        it.copy(messages = chatMessages, showWelcome = false)
                    }
                }
            }
        }
    }

    /**
     * 清空当前聊天界面。
     */
    fun clearChat() {
        Log.v(TAG, "Clearing chat for session: ${_currentSessionId.value}")
        _uiState.update {
            it.copy(messages = emptyList(), currentMessage = null, showWelcome = true, selectedQuickAction = null)
        }
    }

    /**
     * 处理用户输入内容的变化。
     */
    fun onInputChange(text: String) {
        _uiState.update { it.copy(userInput = text, showWelcome = false != text.isEmpty(), selectedQuickAction = null) }
    }

    /**
     * 【统一发送消息入口】
     * 根据角色类型决定保存逻辑：
     * - USER → 保存到数据库 + 触发 AI 响应
     * - ASSISTANT/SYSTEM → 直接显示到 UI（数据库会自动同步）
     */
    fun sendMessage(text: String, role: ChatMessage.Role, onAiResponse: ((String) -> Unit)? = null) {
        val sessionId = _currentSessionId.value ?: return
        val currentTime = System.currentTimeMillis()
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            role = role,
            timestamp = currentTime
        )

        when (role) {
            ChatMessage.Role.USER -> {
                Log.v(TAG, "Sending user message: $text")
                viewModelScope.launch {
                    saveToDatabase(sessionId, message.id, text, "user", currentTime)
                    onAiResponse?.invoke(message.id)
                }
            }
            else -> {
                Log.v(TAG, "Adding $role message to UI: $text")
                _uiState.update {
                    it.copy(userInput = "", showWelcome = false, messages = it.messages + message)
                }
            }
        }
    }

    /**
     * 将消息存入数据库（IO 操作）。
     */
    private suspend fun saveToDatabase(sessionId: String, messageId: String, text: String, role: String, timestamp: Long) {
        withContext(Dispatchers.IO) {
            conversationRepository.addMessage(
                ConversationEntity(
                    messageId = messageId, text = text, role = role,
                    sessionId = sessionId, activeSessionId = sessionId, timestamp = timestamp
                )
            )
        }
    }

    /**
     * 处理用户消息：触发 AI 响应生成流程。
     */
    private fun handleUserMessage(sessionId: String, prompt: String) {
        viewModelScope.launch {
            Log.v(TAG, "Generating AI response for: $prompt")
            _uiState.update { it.copy(isProcessing = true) }

            try {
                // 确保引擎已初始化并加载了正确的会话历史
                if (!ensureEngineInitialized(sessionId)) {
                    _uiState.update { it.copy(isProcessing = false, error = context.getString(R.string.error_engine_init_failed)) }
                    return@launch
                }

                val response = liteRtLmService.chat(prompt)
                if (response.isNotEmpty()) {
                    val cleanedResponse = cleanAiResponse(response)
                    Log.v(TAG, "AI response generated: $cleanedResponse")

                    saveToDatabase(sessionId, UUID.randomUUID().toString(), cleanedResponse, "assistant", System.currentTimeMillis())
                    _uiState.update { it.copy(isProcessing = false, currentMessage = null) }
                    updateStoryLastActive(sessionId)
                } else {
                    _uiState.update { it.copy(isProcessing = false, error = context.getString(R.string.error_ai_response_failed)) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating AI response", e)
                _uiState.update { it.copy(isProcessing = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    fun sendChatMessage() {
        val text = _uiState.value.userInput ?: return
        if (text.isEmpty()) return

        // 立即清空输入框，提升用户体验
        _uiState.update { it.copy(userInput = "") }

        sendMessage(text, ChatMessage.Role.USER) { _ ->
            val sessionId = _currentSessionId.value ?: return@sendMessage
            handleUserMessage(sessionId, text)
        }
    }

    /**
     * 调用 AI 服务生成开场白（新故事）。
     */
    private suspend fun generateIntroForNewStory(sessionId: String, settingContent: String) {
        try {
            Log.v(TAG, "Generating intro for new story: $sessionId")
            _uiState.update { it.copy(isProcessing = true) }

            // 确保引擎已就绪
            if (!ensureEngineInitialized(sessionId)) {
                _uiState.update { it.copy(isProcessing = false, error = context.getString(R.string.error_engine_init_failed)) }
                return
            }

            val storyInstruction = context.getString(R.string.story_instruction_prompt)
            val prompt = "$storyInstruction\n```markdown\n$settingContent\n```"
            // 1. 先保存带前缀的指令到数据库（作为第一条消息，角色为 system 以便在 UI 中区分或隐藏）
            saveToDatabase(sessionId, UUID.randomUUID().toString(), prompt, "system", System.currentTimeMillis())

            // 2. 调用 AI 接口生成开场白
            val response = liteRtLmService.chat(prompt)
            val intro = cleanAiResponse(response)

            // 3. 保存 AI 生成的开场白
            saveToDatabase(sessionId, UUID.randomUUID().toString(), intro, "assistant", System.currentTimeMillis())

            viewModelScope.launch {
                val story = storyHistoryRepository.getStoryById(sessionId)
                if (story != null) {
                    storyHistoryRepository.updateStory(story.copy(storyBeginning = intro, messageCount = 2, lastActive = System.currentTimeMillis()))
                }
            }

            _uiState.update { it.copy(isProcessing = false, currentMessage = null, showWelcome = false) }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating intro", e)
            _uiState.update { it.copy(isProcessing = false, error = e.message ?: "Unknown error") }
        }
    }

    /**
     * 更新故事的最后活动时间。
     */
    private fun updateStoryLastActive(sessionId: String) {
        viewModelScope.launch {
            try {
                val story = storyHistoryRepository.getStoryById(sessionId)
                story?.let { storyHistoryRepository.updateStory(it.copy(lastActive = System.currentTimeMillis())) }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating last active", e)
            }
        }
    }

    /**
     * 清理 AI 响应，提取实际的游戏内容。
     */
    private fun cleanAiResponse(text: String): String {
        if (text.isEmpty()) return text

        val codeBlockRegex = Regex("```(?:[\\w-]*)\n?(.*?)```", RegexOption.DOT_MATCHES_ALL)
        codeBlockRegex.find(text)?.let {
            val content = it.groupValues[1].trim()
            if (content.isNotEmpty()) return content
        }

        if (text.contains("```")) {
            val lastIndex = text.lastIndexOf("```")
            val contentAfterLast = text.substring(lastIndex + 3)
            val firstNewLine = contentAfterLast.indexOf('\n')
            if (firstNewLine != -1) return contentAfterLast.substring(firstNewLine + 1).trim()
            return ""
        }

        return text.trim()
    }

    /**
     * 将 assets 中的技能定义提取到内部存储。
     */
    private suspend fun extractSkillsFromAssets(): String = withContext(Dispatchers.IO) {
        val skillsDir = File(context.filesDir, "skills")
        if (!skillsDir.exists()) skillsDir.mkdirs()

        try {
            val textAdventureDir = File(skillsDir, "text-adventure")
            if (!textAdventureDir.exists()) textAdventureDir.mkdirs()

            val skillFile = File(textAdventureDir, "SKILL.md")
            if (!skillFile.exists()) {
                context.assets.open("skills/text-adventure/SKILL.md").use { input ->
                    FileOutputStream(skillFile).use { output -> input.copyTo(output) }
                }
            }
            skillsDir.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting skills", e)
            ""
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
        _uiState.value.messages.lastOrNull { it.role == ChatMessage.Role.USER }?.let { lastUserMsg ->
            viewModelScope.launch { handleUserMessage(_currentSessionId.value ?: return@launch, lastUserMsg.text) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        liteRtLmService.dispose()
    }
}

/**
 * 主界面 (MainScreen) 的 UI 状态数据类。
 * 该状态通过 _uiState 流驱动 MainScreen 的组合和渲染。
 * 任何字段的变化都会触发 UI 重组，反映最新的交互状态。
 */
data class MainUiState(
    /**
     * 对话历史消息列表，传递给 ChatMessageItem 组件列表进行渲染。
     * - 为空时显示欢迎界面 (showWelcome=true)
     * - 非空时显示聊天消息历史
     */
    val messages: List<ChatMessage> = emptyList(),

    /**
     * 当前用户在输入框中输入的文本内容，绑定到 ChatInputBar 组件。
     * - 非空时，发送按钮被启用
     * - 清空表示用户已完成输入，等待 AI 响应
     */
    val userInput: String? = null,

    /**
     * 控制是否显示空聊天状态的欢迎界面。
     * - true: 显示中央欢迎信息和图标 (showWelcome=true)
     * - false: 显示聊天历史记录和输入栏
     */
    val showWelcome: Boolean = true,

    /**
     * 指示 AI 模型是否已完成初始化，用于决定是否启用聊天功能。
     * - false: 显示"模型加载中"状态，阻止用户发送消息
     * - true: 模型已就绪，用户可以开始对话
     */
    val isModelInitialized: Boolean = false,

    /**
     * 指示 AI 引擎是否正在流式生成响应。
     * - true: 显示加载指示器，禁用输入栏以防止用户发送新消息
     * - false: AI 就绪，可以接受用户输入
     */
    val isProcessing: Boolean = false,

    /**
     * 显示应用级别的状态消息（如"正在连接服务器"等），通常在屏幕顶部的 Snackbar 或 Text 组件中显示。
     * - 提供非阻塞式的状态反馈，不干扰用户交互
     */
    val statusMessage: String? = null,

    /**
     * 显示错误信息（如 AI 响应失败、模型加载错误等）。
     * - 非空时，通常通过 Snackbar、Toast 或错误提示卡片显示给用户
     * - 用于告知用户发生了什么错误以及可能的解决方法
     */
    val error: String? = null,

    /**
     * AI 正在流式生成的当前消息内容。
     * - 非 null 时，显示 AI 正在输入的状态，文本逐字追加显示
     * - 用于提供流畅的 AI 响应体验，而不是等待完整响应后再显示
     */
    val currentMessage: String? = null,

    /**
     * 当前选中的快捷动作，用于显示用户点击的快速回复按钮。
     * - 非 null 时，表示用户已选择某个快捷操作（如"重新开始"、"继续对话"等）
     * - UI 会高亮显示该按钮并准备执行相应操作
     */
    val selectedQuickAction: QuickAction? = null
)
