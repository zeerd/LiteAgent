package com.zeerd.textadventure.service

import android.content.Context
import android.util.Log
import com.zeerd.textadventure.R
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LiteRT-LM 服务，负责管理 AI 引擎的生命周期、对话和上下文压缩。
 */
@Singleton
class LiteRtLmService @Inject constructor(
    private val context: Context
) {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "TextAdventure-LiteRtLmService"
        private const val MODEL_FOLDER_NAME = "text_adventure_models"
        private const val DEFAULT_MODEL_NAME = "lite-rtlm-model"
        private const val DEFAULT_MAX_TOKENS = 4096
        private const val DEFAULT_COMPRESSION_THRESHOLD = 0.75f
        private const val DEFAULT_TEMPERATURE = 0.7f
    }

    // 核心引擎组件
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var currentConfig: EngineConfig? = null

    // 压缩模块
    private var compressor: ContextCompressor? = null

    // 状态流，供 UI 观察
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _skillManager = MutableStateFlow<SkillManager?>(null)
    val skillManager: StateFlow<SkillManager?> = _skillManager.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentMessage = MutableStateFlow("")
    val currentMessage: StateFlow<String> = _currentMessage.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 用于通知 MainViewModel 开启新故事并生成开场白
    private val _newStorySignal = MutableSharedFlow<Pair<String, String>>()
    val newStorySignal = _newStorySignal.asSharedFlow()

    /**
     * 发送开启新故事的信号。
     */
    suspend fun triggerNewStory(sessionId: String, settingContent: String) {
        Log.d(TAG, "Triggering new story signal for session: $sessionId")
        _newStorySignal.emit(sessionId to settingContent)
    }

    // 历史消息追踪，用于压缩逻辑
    private val _historyMessages = mutableListOf<ContextCompressor.Message>()
    val historyMessages: List<ContextCompressor.Message> = _historyMessages.toList()

    // 配置参数
    private var _systemPrompt = ""
    private var _maxTokens: Int = DEFAULT_MAX_TOKENS
    private var _compressionThreshold: Float = DEFAULT_COMPRESSION_THRESHOLD
    private var _temperature: Float = DEFAULT_TEMPERATURE
    private var _skillDir: String = ""
    private var _allowedSkills: List<String>? = null

    private val initMutex = Mutex()

    // 属性访问器
    var systemPrompt: String
        get() = _systemPrompt
        set(value) {
            _systemPrompt = value
            resetConversation()
        }

    var maxTokens: Int
        get() = _maxTokens
        set(value) { _maxTokens = value }

    var compressionThreshold: Float
        get() = _compressionThreshold
        set(value) { _compressionThreshold = value }

    var temperature: Float
        get() = _temperature
        set(value) { _temperature = value }

    init {
        Log.d(TAG, "LiteRtLmService initialized")
        try {
            // 加载本地库
            System.loadLibrary("litertlm_jni")
            Log.d(TAG, "Native library litertlm_jni loaded successfully in service")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load litertlm_jni in service: ${e.message}")
        }
    }

    /**
     * 提供一个比 UI 组件存活更久的协程作用域。
     */
    fun getScope(): CoroutineScope {
        Log.v(TAG, ">>> getScope() IN")
        val result = serviceScope
        Log.v(TAG, "<<< getScope() OUT")
        return result
    }

    /**
     * 初始化 Engine、技能和压缩器。
     */
    suspend fun initialize(
        engineConfig: EngineConfig,
        skillDir: String = "",
        allowedSkills: List<String>? = null,
        compressionThreshold: Float = DEFAULT_COMPRESSION_THRESHOLD,
        temperature: Float = DEFAULT_TEMPERATURE
    ): Boolean = initMutex.withLock {
        Log.v(TAG, ">>> initialize() called with ${engineConfig.modelPath}")
        return try {
            performInitialize(
                engineConfig,
                skillDir,
                allowedSkills,
                compressionThreshold,
                temperature
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in initialize: ${e.message}", e)
            _error.value = e.message ?: "Fatal initialization error"
            false
        } finally {
            Log.v(TAG, "<<< initialize() completed with isInitialized=${_isInitialized.value}")
        }
    }

    /**
     * 执行实际的初始化流程。
     */
    private suspend fun performInitialize(
        engineConfig: EngineConfig,
        skillDir: String,
        allowedSkills: List<String>?,
        compressionThreshold: Float,
        temperature: Float
    ): Boolean {
        return withContext(Dispatchers.IO) {
            Log.v(TAG, "===========================================")
            Log.v(TAG, "Performing LiteRtLmService initialization...")

            try {
                // 验证模型文件是否存在
                val modelFile = File(engineConfig.modelPath)
                if (!modelFile.exists()) {
                    Log.e(TAG, "❌ Model file not found: ${engineConfig.modelPath}")
                    _error.value = "Model file not found. Please check your settings."
                    return@withContext false
                }

                Log.d(TAG, "Step 1: Closing existing components...")
                // 关闭旧组件
                try {
                    conversation?.close()
                    engine?.close()
                    compressor?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error while closing existing components: ${e.message}")
                }

                engine = null
                conversation = null
                compressor = null

                _maxTokens = engineConfig.maxNumTokens ?: DEFAULT_MAX_TOKENS
                _compressionThreshold = compressionThreshold
                _temperature = temperature
                _skillDir = skillDir
                _allowedSkills = allowedSkills

                Log.d(TAG, "Step 2: Creating and initializing Engine...")
                // 创建并初始化引擎
                val newEngine = Engine(engineConfig)
                newEngine.initialize()

                if (!newEngine.isInitialized()) {
                    Log.e(TAG, "❌ Engine initialization failed")
                    _error.value = "Engine failed to initialize."
                    return@withContext false
                }

                engine = newEngine
                currentConfig = engineConfig

                Log.d(TAG, "Step 3: Loading skills from $skillDir...")
                // 初始化技能管理器
                val manager = try {
                    SkillManager(skillDir, allowedSkills)
                } catch (e: Exception) {
                    Log.w(TAG, "Skill loading warning: ${e.message}")
                    SkillManager("", emptyList())
                }
                _skillManager.value = manager

                Log.d(TAG, "Step 4: Initializing ContextCompressor...")
                // 初始化上下文压缩器
                compressor = ContextCompressor(
                    modelPath = engineConfig.modelPath,
                    backend = engineConfig.backend,
                    temperature = _temperature,
                    maxNumTokens = (_maxTokens * 2).coerceAtMost(32768)
                )

                Log.d(TAG, "Step 5: Building system prompt...")
                // 注入技能说明到系统提示词
                val promptInjector = PromptInjector()
                val skills = manager.getAllSkills()
                _systemPrompt = promptInjector.buildInstrumentedPrompt(context, skills)
                Log.v(TAG, "Constructed system prompt:\n$_systemPrompt")

                Log.d(TAG, "Step 6: Creating conversation...")
                // 创建对话会话
                val config = ConversationConfig(
                    systemInstruction = Contents.of(_systemPrompt)
                )

                conversation = engine?.createConversation(config)
                _historyMessages.clear()

                if (engine != null && conversation != null) {
                    _isInitialized.value = true
                    Log.d(TAG, "✅ LiteRtLmService initialization complete")
                    return@withContext true
                } else {
                    _error.value = "Failed to initialize engine"
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing engine: ${e.message}", e)
                _error.value = e.message ?: "Unknown error"
                return@withContext false
            } finally {
                Log.v(TAG, "<<< performInitialize() completed with isInitialized=${_isInitialized.value}")
            }
        }
    }

    /**
     * 获取可用技能名称列表。
     */
    fun getAvailableSkills(): List<String> {
        return _skillManager.value?.getSkillsNames() ?: emptyList()
    }

    /**
     * 获取所有技能对象。
     */
    fun getAllSkills(): List<Skill> {
        return _skillManager.value?.getAllSkills() ?: emptyList()
    }

    /**
     * 获取特定技能的信息。
     */
    fun getSkillInfo(skillName: String): Skill? {
        return _skillManager.value?.getSkill(skillName)
    }

    /**
     * 加载并格式化特定技能。
     */
    fun loadSkill(skillName: String): String {
        Log.v(TAG, ">>> loadSkill() called with skillName: $skillName")
        val skill = _skillManager.value?.getSkill(skillName)
        if (skill == null) {
            val available = _skillManager.value?.getSkillsNames() ?: emptyList()
            return "❌ Skill '$skillName' not found. Available: ${available.joinToString(", ")}"
        }

        val formatter = LoadSkillResponseFormatter()
        val result = formatter.format(
            name = skill.name,
            description = skill.description,
            metadata = skill.metadata,
            instructions = skill.instructions
        )

        Log.v(TAG, "<<< loadSkill() completed with result:\n$result")
        return result
    }

    /**
     * 检查是否需要触发上下文压缩。
     */
    private fun shouldCompress(lastResponse: String = ""): Pair<Boolean, Int> {
        if (_historyMessages.isEmpty() || _maxTokens <= 0) {
            return false to 0
        }

        val totalContentLength = _historyMessages.sumOf { it.content.length }
        val threshold = (_maxTokens * _compressionThreshold).toInt()
        val actualLength = totalContentLength / 2

        // 条件 1: 上下文长度超过限制
        val lengthTrigger = actualLength > threshold

        // 条件 2: LLM 返回的响应过短（可能意味着上下文已满）
        val shortResponseTrigger = lastResponse.isNotEmpty() && lastResponse.length < 10

        val shouldCompress = lengthTrigger || shortResponseTrigger

        return shouldCompress to totalContentLength
    }

    /**
     * 构建压缩后的消息序列。
     */
    private fun buildCompressedMessages(
        compressedHistory: String
    ): List<Pair<String, String>> {
        val messages = mutableListOf<Pair<String, String>>()
        messages.add("system" to _systemPrompt)
        messages.add("user" to compressedHistory)
        messages.add("assistant" to "Acknowledged.")
        return messages
    }

    /**
     * 发送用户消息并获取 AI 响应。
     */
    suspend fun chat(userMessage: String): String {
        _isProcessing.value = true
        _currentMessage.value = ""

        // 将用户消息存入历史记录
        _historyMessages.add(ContextCompressor.Message("user", userMessage))

        // 发送前检查是否需要压缩
        val (initialNeedCompress, _) = shouldCompress()

        return try {
            Log.v(TAG, ">>> chat() called with userMessage:\n$userMessage")
            val responseBuilder = StringBuilder()

            if (initialNeedCompress) {
                performCompressionAndChat(userMessage, responseBuilder)
            } else {
                // 普通对话流
                conversation?.sendMessageAsync(userMessage)?.collect { partialResponse ->
                    // Log.v(TAG, "Received partial response: $partialResponse")
                    _currentMessage.value += partialResponse.toString()
                    responseBuilder.append(partialResponse.toString())
                }
            }

            var responseText = responseBuilder.toString()

            // 将 AI 响应存入历史记录
            _historyMessages.add(ContextCompressor.Message("assistant", responseText))

            // 发送后检查是否因为该次响应而触发压缩
            val (postNeedCompress, _) = shouldCompress(responseText)

            if (postNeedCompress && !initialNeedCompress) {
                Log.d(TAG, "🔄 Response triggered late compression. Retrying chat...")
                responseBuilder.clear()
                performCompressionAndChat(userMessage, responseBuilder)
                responseText = responseBuilder.toString()

                // 更新历史记录中的最后一条 AI 消息
                if (_historyMessages.isNotEmpty() && _historyMessages.last().role == "assistant") {
                    _historyMessages.removeAt(_historyMessages.size - 1)
                }
                _historyMessages.add(ContextCompressor.Message("assistant", responseText))
            }

            Log.v(TAG, "<<< chat() completed with response:\n$responseText")
            responseText

        } catch (e: Exception) {
            Log.e(TAG, "Error in chat", e)
            _error.value = e.message ?: "Unknown error"
            ""
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * 执行压缩流程并重新发送对话。
     */
    private suspend fun performCompressionAndChat(userMessage: String, responseBuilder: StringBuilder) {
        Log.v(TAG, ">>> performCompressionAndChat() called with userMessage:\n$userMessage")
        val compressedHistory = compressor?.compressHistory(_historyMessages)

        if (compressedHistory != null && compressedHistory.isNotEmpty()) {
            // 压缩成功，重置历史并重建会话
            _historyMessages.clear()

            conversation?.close()
            Log.i(TAG, ">>> RE-PASSING CONTEXT TO LLM ENGINE (After Compression) <<<")
            Log.d(TAG, "SYSTEM_INSTRUCTION:\n$_systemPrompt")
            Log.d(TAG, "COMPRESSED_HISTORY (User):\n$compressedHistory")
            Log.d(TAG, "ASSISTANT_ACK: Acknowledged")

            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(_systemPrompt),
                initialMessages = listOf(
                    Message.user(compressedHistory),
                    Message.model("Acknowledged"),
                ),
                samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8),
            )
            conversation = engine?.createConversation(conversationConfig)

            // 重新发送当前消息
            conversation?.sendMessageAsync(userMessage)?.collect { partialResponse ->
                _currentMessage.value += partialResponse.toString()
                responseBuilder.append(partialResponse.toString())
            }
        } else {
            // 压缩失败，尝试继续原始对话
            conversation?.sendMessageAsync(userMessage)?.collect { partialResponse ->
                _currentMessage.value += partialResponse.toString()
                responseBuilder.append(partialResponse.toString())
            }
        }
        Log.v(TAG, "<<< performCompressionAndChat() completed with response:\n${responseBuilder.toString()}")
    }

    /**
     * 从消息列表恢复对话历史。
     */
    fun restoreHistory(messages: List<ContextCompressor.Message>) {
        Log.v(TAG, ">>> restoreHistory() called with ${messages.size} messages")
        try {
            _historyMessages.clear()
            _historyMessages.addAll(messages)

            Log.i(TAG, ">>> RE-PASSING CONTEXT TO LLM ENGINE (Restore History) <<<")
            Log.d(TAG, "SYSTEM_INSTRUCTION:\n$_systemPrompt")
            messages.forEachIndexed { index, msg ->
                Log.d(TAG, "HISTORY_MSG #$index [${msg.role}]:\n${msg.content}")
            }

            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(_systemPrompt),
                initialMessages = messages.map { msg ->
                    // 如果是 user 或从数据库恢复的 system(故事背景)，都作为用户输入发送给 LLM
                    if (msg.role == "user" || msg.role == "system") {
                        Message.user(msg.content)
                    } else {
                        Message.model(msg.content)
                    }
                }
            )

            conversation?.close()
            conversation = engine?.createConversation(conversationConfig)

            Log.d(TAG, "Restored ${messages.size} messages to LLM context")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring history: ${e.message}")
        } finally {
            Log.v(TAG, "<<< restoreHistory() completed with ${_historyMessages.size} messages in context")
        }
    }

    /**
     * 重置对话，清空所有历史上下文。
     */
    fun resetConversation() {
        Log.v(TAG, ">>> resetConversation() called")
        try {
            _historyMessages.clear()

            Log.i(TAG, ">>> RESETTING CONVERSATION WITH SYSTEM PROMPT <<<")
            Log.d(TAG, "SYSTEM_INSTRUCTION:\n$_systemPrompt")

            val newConfig = ConversationConfig(
                systemInstruction = Contents.of(_systemPrompt)
            )

            conversation?.close()
            conversation = engine?.createConversation(newConfig)

            _currentMessage.value = ""
            Log.v(TAG, "<<< resetConversation() completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting conversation", e)
        }
    }

    /**
     * 释放所有引擎资源。
     */
    fun dispose() {
        try {
            conversation?.close()
            engine?.close()
            compressor?.close()
            engine = null
            conversation = null
            compressor = null
            _isInitialized.value = false
            Log.v(TAG, "<<< dispose() completed, all resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing engine", e)
        }
    }

    /**
     * 获取或创建模型存储目录。
     */
    fun getOrCreateModelFolder(): File {
        val storageDir = File(context.getExternalFilesDir(null), MODEL_FOLDER_NAME)
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return storageDir
    }

    /**
     * 获取默认模型路径。
     */
    fun getOrCreateModelPath(): String {
        val modelFolder = getOrCreateModelFolder()
        return File(modelFolder, DEFAULT_MODEL_NAME).absolutePath
    }

    /**
     * 获取当前引擎的默认配置。
     */
    fun getEngineConfig(): EngineConfig {
        return EngineConfig(
            modelPath = getOrCreateModelPath(),
            backend = Backend.CPU(),
            maxNumTokens = 32768
        )
    }

    /**
     * 获取当前服务的配置详情映射。
     */
    fun getConfiguration(): Map<String, Any> {
        return mapOf(
            "systemPrompt" to _systemPrompt,
            "maxTokens" to _maxTokens,
            "compressionThreshold" to _compressionThreshold,
            "temperature" to _temperature,
            "hasSkills" to (_skillManager.value?.getAllSkills()?.isNotEmpty() == true),
            "skillCount" to (_skillManager.value?.getAllSkills()?.size ?: 0)
        )
    }
}
