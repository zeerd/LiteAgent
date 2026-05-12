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
import kotlinx.coroutines.flow.update
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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val TAG = "TextAdventure-LiteRtLmService"
        private const val MODEL_FOLDER_NAME = "text_adventure_models"
        private const val DEFAULT_MODEL_NAME = "lite-rtlm-model"
        private const val DEFAULT_MAX_TOKENS = 4096
        private const val DEFAULT_COMPRESSION_THRESHOLD = 0.75f
        private const val DEFAULT_TEMPERATURE = 0.7f
        private const val EOS_MARKER = "[EOS]"
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
    private val _historyMessages = mutableListOf<ChatMessage>()
    val historyMessages: List<ChatMessage> = _historyMessages.toList()

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
            serviceScope.launch {
                resetConversation()
            }
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

                // Reduce maxNumTokens by 2k for the engine to avoid overflow
                val adjustedMaxTokens = (engineConfig.maxNumTokens ?: DEFAULT_MAX_TOKENS) - 2048
                val adjustedConfig = engineConfig.copy(maxNumTokens = adjustedMaxTokens)

                _maxTokens = adjustedMaxTokens
                _compressionThreshold = compressionThreshold
                _temperature = temperature
                _skillDir = skillDir
                _allowedSkills = allowedSkills

                Log.d(TAG, "Step 2: Creating and initializing Engine...")
                // 创建并初始化引擎
                val newEngine = Engine(adjustedConfig)
                newEngine.initialize()

                if (!newEngine.isInitialized()) {
                    Log.e(TAG, "❌ Engine initialization failed")
                    _error.value = "Engine failed to initialize."
                    return@withContext false
                }

                engine = newEngine
                currentConfig = adjustedConfig

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
                _systemPrompt = promptInjector.buildInstrumentedPrompt(skills)

                // 添加 [EOF] 标记指令，用于判断正常完成还是被截断
                _systemPrompt += "\nPlease append the marker $EOS_MARKER at the end of your response if you have finished."

                // Append language instruction
                val languageInstruction = context.getString(R.string.language_instruction_prompt)
                _systemPrompt += "\n$languageInstruction"

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
    internal fun shouldCompress(lastResponse: String = ""): Pair<Boolean, Int> {
        Log.v(TAG, ">>> shouldCompress() IN (lastResponse length: ${lastResponse.length})")
        if (_historyMessages.isEmpty() || _maxTokens <= 0) {
            Log.v(TAG, "<<< shouldCompress() OUT - History empty or maxTokens <= 0")
            return false to 0
        }

        val totalContentLength = _historyMessages.sumOf { it.content.length }
        val threshold = (_maxTokens * _compressionThreshold).toInt()

        // 改进的 Token 估算：对中文更保守。
        // 假设 1 个字符平均对应 0.8 到 1.2 个 token，这里取 1.0 作为保守估算。
        val actualLength = totalContentLength

        // 条件 1: 上下文长度超过限制
        val lengthTrigger = actualLength > threshold

        // 条件 2: LLM 返回的响应过短（可能意味着上下文已满）
        val shortResponseTrigger = lastResponse.isNotEmpty() && lastResponse.length < 10

        val shouldCompress = lengthTrigger || shortResponseTrigger

        Log.d(TAG, "Compression check: actualLength=$actualLength, threshold=$threshold, lengthTrigger=$lengthTrigger, shortResponseTrigger=$shortResponseTrigger")
        Log.v(TAG, "<<< shouldCompress() OUT - result=$shouldCompress")
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
    suspend fun chat(userMessage: String): String = withContext(Dispatchers.Default) {
        _isProcessing.value = true
        _currentMessage.value = ""

        // 记录本轮对话前的历史快照，确保压缩时不包含当前这一轮的消息
        val historyBeforeThisTurn = _historyMessages.toList()

        // 将 user message 存入历史记录
        _historyMessages.add(ChatMessage("user", userMessage))

        // 发送前检查是否需要压缩
        val (initialNeedCompress, _) = shouldCompress()

        try {
            Log.v(TAG, ">>> chat() called with userMessage:\n$userMessage")
            val responseBuilder = StringBuilder()

            if (initialNeedCompress) {
                performCompressionAndChat(historyBeforeThisTurn, userMessage, responseBuilder)
            } else {
                // 普通对话流
                conversation?.sendMessageAsync(userMessage)?.collect { partialResponse ->
                    // Log.v(TAG, "Received partial response: $partialResponse")
                    val partial = partialResponse.toString()
                    _currentMessage.update { it + partial }
                    responseBuilder.append(partial)
                }
            }

            var responseText = responseBuilder.toString()

            // 检查 [EOF] 标记以判断是否正常完成
            // 正常完成：响应以 [EOF] 结尾，删除标记并正常存储
            // 异常完成：响应过短且无 [EOF] 标记，可能表明上下文被截断
            var completedNormally = responseText.endsWith(EOS_MARKER)
            if (completedNormally) {
                Log.d(TAG, "Normal completion detected $EOS_MARKER.")
                responseText = responseText.removeSuffix(EOS_MARKER)
            } else if (responseText.isEmpty()) {
                Log.w(TAG, "Empty response after removing EOF, possible context cutoff.")
                completedNormally = false
            } else if (responseText.length < 10) {
                Log.w(TAG, "Short response (${responseText.length} chars) without $EOS_MARKER, suggesting cut context.")
                completedNormally = false
            } else {
                Log.w(TAG, "No $EOS_MARKER marker (${responseText.length} chars), response may be incomplete.")
            }

            // 根据完成情况决定是否触发压缩
            var shouldTriggerCompression = false
            if (!completedNormally) {
                Log.d(TAG, "Compression triggered by abnormal response.")
                shouldTriggerCompression = true
            }

            // 将 AI 响应存入历史记录
            _historyMessages.add(ChatMessage("assistant", responseText))

            // 发送后检查是否因为该次响应而触发压缩
            val (postNeedCompress, _) = shouldCompress(responseText)

            if (shouldTriggerCompression || (postNeedCompress && !initialNeedCompress)) {
                Log.d(TAG, "Compression triggered (abnormal response or threshold exceeded). Retrying chat...")

                // 移除这次失败或触发阈值的 assistant 响应，准备重新生成
                if (_historyMessages.isNotEmpty() && _historyMessages.last().role == "assistant") {
                    _historyMessages.removeAt(_historyMessages.size - 1)
                }

                responseBuilder.clear()
                // 使用本轮之前的历史快照进行压缩，确保不包含当前 user 和刚移除的 assistant
                performCompressionAndChat(historyBeforeThisTurn, userMessage, responseBuilder)
                responseText = responseBuilder.toString()

                // 更新历史记录中的最后一条 AI 消息
                if (_historyMessages.isNotEmpty() && _historyMessages.last().role == "assistant") {
                    _historyMessages.removeAt(_historyMessages.size - 1)
                }
                _historyMessages.add(ChatMessage("assistant", responseText))
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
    private suspend fun performCompressionAndChat(
        historyToProcess: List<ChatMessage>,
        userMessage: String,
        responseBuilder: StringBuilder
    ) = withContext(Dispatchers.Default) {
        Log.v(TAG, ">>> performCompressionAndChat() IN, processing ${historyToProcess.size} messages")
        Log.i(TAG, "Initiating context compression...")

        // 清空当前消息并显示“上下文压缩中”
        _currentMessage.value = context.getString(R.string.ai_compressing)

        // 直接使用传入的历史，不再从全局 _historyMessages 中获取（避免包含当前轮次的干扰）
        val (prefix, toCompress, recent) = compressor?.partitionMessages(historyToProcess)
            ?: Triple(emptyList(), historyToProcess, emptyList())

        Log.d(TAG, "Partitioning history: prefix=${prefix.size}, toCompress=${toCompress.size}, recent=${recent.size}")

        // 仅压缩中间部分的对话
        val compressedSnapshot = compressor?.compressHistory(toCompress)

        if (compressedSnapshot != null && compressedSnapshot.isNotEmpty()) {
            Log.i(TAG, "✅ Context compressed successfully. Rebuilding history and conversation.")

            // 1. 构建新的历史基准 (不含当前 userMessage)
            val compressedHistory = mutableListOf<ChatMessage>()
            compressedHistory.addAll(prefix)
            compressedHistory.add(ChatMessage("user", "[World State Snapshot]\n$compressedSnapshot"))
            compressedHistory.addAll(recent)

            // 2. 调用 restoreHistory 恢复对话状态并打印日志
            restoreHistory(
                compressedHistory,
                samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8)
            )

            // 3. 将当前 userMessage 补回到全局历史记录中
            _historyMessages.add(ChatMessage("user", userMessage))

            // 4. 发送前清空压缩提示
            _currentMessage.value = ""

            Log.d(TAG, "Re-sending user message: $userMessage")
            // 5. 重新发送当前消息
            conversation?.sendMessageAsync(userMessage)?.collect { partialResponse ->
                val partial = partialResponse.toString()
                _currentMessage.update { it + partial }
                responseBuilder.append(partial)
            }
        } else {
            Log.e(TAG, "❌ Context compression failed or returned empty. Falling back to original conversation.")
            // 压缩失败，尝试继续原始对话
            conversation?.sendMessageAsync(userMessage)?.collect { partialResponse ->
                val partial = partialResponse.toString()
                _currentMessage.update { it + partial }
                responseBuilder.append(partial)
            }
        }
        Log.v(TAG, "<<< performCompressionAndChat() OUT")
    }

    /**
     * 从消息列表恢复对话历史。
     */
    suspend fun restoreHistory(
        messages: List<ChatMessage>,
        samplerConfig: SamplerConfig? = null
    ) = withContext(Dispatchers.Default) {
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
                },
                samplerConfig = samplerConfig
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
    suspend fun resetConversation() = withContext(Dispatchers.Default) {
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
        serviceScope.launch {
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
            maxNumTokens = 30720 // 32768 - 2048
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
