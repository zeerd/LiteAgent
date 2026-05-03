package com.liteagent.textadventure.service

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRtLmService @Inject constructor(
    private val context: Context
) {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "LiteRtLmService"
        private const val MODEL_FOLDER_NAME = "text_adventure_models"
        private const val DEFAULT_MODEL_NAME = "lite-rtlm-model"
        private const val DEFAULT_MAX_TOKENS = 4096
        private const val DEFAULT_COMPRESSION_THRESHOLD = 0.75f
        private const val DEFAULT_TEMPERATURE = 0.7f
    }

    // Core engine components
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var currentConfig: EngineConfig? = null

    // Compression module
    private var compressor: ContextCompressor? = null

    // State flows
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

    // History tracking for compression
    private val _historyMessages = mutableListOf<ContextCompressor.Message>()
    val historyMessages: List<ContextCompressor.Message> = _historyMessages.toList()

    // Configuration
    private var _systemPrompt = "You are a text adventure game master."
    private var _maxTokens: Int = DEFAULT_MAX_TOKENS
    private var _compressionThreshold: Float = DEFAULT_COMPRESSION_THRESHOLD
    private var _temperature: Float = DEFAULT_TEMPERATURE
    private var _skillDir: String = ""
    private var _allowedSkills: List<String>? = null

    private val initMutex = Mutex()

    // Properties
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
            System.loadLibrary("litertlm_jni")
            Log.d(TAG, "Native library litertlm_jni loaded successfully in service")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load litertlm_jni in service: ${e.message}")
        }
    }

    /**
     * Provide a scope for background tasks that outlive UI components
     */
    fun getScope(): CoroutineScope = serviceScope

    /**
     * 初始化 Engine、技能和压缩器 - 完全对应 Python 版本
     */
    suspend fun initialize(
        engineConfig: EngineConfig,
        systemPrompt: String = "",
        skillDir: String = "",
        allowedSkills: List<String>? = null,
        compressionThreshold: Float = DEFAULT_COMPRESSION_THRESHOLD,
        temperature: Float = DEFAULT_TEMPERATURE
    ): Boolean = initMutex.withLock {
        Log.d(TAG, ">>> initialize() called with ${engineConfig.modelPath}")
        return try {
            performInitialize(
                engineConfig,
                systemPrompt,
                skillDir,
                allowedSkills,
                compressionThreshold,
                temperature
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in initialize: ${e.message}", e)
            _error.value = e.message ?: "Fatal initialization error"
            false
        }
    }

    private suspend fun performInitialize(
        engineConfig: EngineConfig,
        systemPrompt: String,
        skillDir: String,
        allowedSkills: List<String>?,
        compressionThreshold: Float,
        temperature: Float
    ): Boolean {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "===========================================")
            Log.d(TAG, "Performing LiteRtLmService initialization...")
            Log.d(TAG, "  - Model path: ${engineConfig.modelPath}")
            Log.d(TAG, "  - Skill dir: $skillDir")
            Log.d(TAG, "  - Backend: ${engineConfig.backend}")
            Log.d(TAG, "  - Temperature: $temperature")
            Log.d(TAG, "  - Compression threshold: $compressionThreshold")
            Log.d(TAG, "  - Allowed skills: ${allowedSkills?.joinToString(", ") ?: "all"}")

            try {
                // Verify model file exists
                val modelFile = File(engineConfig.modelPath)
                if (!modelFile.exists()) {
                    Log.e(TAG, "❌ Model file not found: ${engineConfig.modelPath}")
                    _error.value = "Model file not found. Please check your settings."
                    return@withContext false
                }

                Log.d(TAG, "Step 1: Closing existing components...")
                // 关闭现有引擎
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
                _systemPrompt = systemPrompt.ifEmpty {
                    "You are a text adventure game master."
                }
                _compressionThreshold = compressionThreshold
                _temperature = temperature
                _skillDir = skillDir
                _allowedSkills = allowedSkills

                Log.d(TAG, "Step 2: Creating and initializing Engine...")
                // 创建 Engine
                val newEngine = Engine(engineConfig)
                newEngine.initialize()

                if (!newEngine.isInitialized()) {
                    Log.e(TAG, "❌ Engine initialization failed: isInitialized() is false")
                    _error.value = "Engine failed to initialize. Check if model file is valid."
                    return@withContext false
                }

                engine = newEngine
                currentConfig = engineConfig
                Log.d(TAG, "   - Engine.isInitialized(): ${engine?.isInitialized()}")

                Log.d(TAG, "Step 3: Loading skills from $skillDir...")
                // 加载技能管理器 - 与 Python 版本完全对应
                val manager = try {
                    SkillManager(skillDir, allowedSkills)
                } catch (e: Exception) {
                    Log.w(TAG, "Skill loading warning: ${e.message}")
                    SkillManager("", emptyList())
                }
                _skillManager.value = manager
                val skillCount = manager.getAllSkills().size
                Log.d(TAG, "✅ Loaded $skillCount skills")

                Log.d(TAG, "Step 4: Initializing ContextCompressor...")
                // 初始化压缩器 - 与 Python 版本完全对应
                compressor = ContextCompressor(
                    modelPath = engineConfig.modelPath,
                    backend = engineConfig.backend,
                    temperature = _temperature,
                    maxNumTokens = (_maxTokens * 2).coerceAtMost(32768)
                )

                Log.d(TAG, "Step 5: Building system prompt...")
                // 构建系统提示词 - 与 Python 版本对应
                val promptInjector = PromptInjector()
                val skills = manager.getAllSkills()
                val instrumentedPrompt = if (skills.isNotEmpty()) {
                    promptInjector.buildInstrumentedPrompt(skills)
                } else {
                    "You are a helpful AI assistant."
                }
                _systemPrompt = "$instrumentedPrompt\n使用中文与用户交流。"

                Log.d(TAG, "Step 6: Creating conversation...")
                // 创建对话配置 - 使用 Python 版本对应的 API
                val config = ConversationConfig(
                    systemInstruction = Contents.of(_systemPrompt)
                )

                conversation = engine?.createConversation(config)

                _historyMessages.clear()

                if (engine != null && conversation != null) {
                    _isInitialized.value = true
                    Log.d(TAG, "Engine initialized successfully")
                    Log.d(TAG, "✅ LiteRtLmService initialization complete")
                    Log.d(TAG, "===========================================")
                    return@withContext true
                } else {
                    Log.e(TAG, "Failed to create engine or conversation")
                    _error.value = "Failed to initialize engine"
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing engine: ${e.message}", e)
                _error.value = e.message ?: "Unknown error"
                return@withContext false
            }
        }
    }

    /**
     * 获取可用技能列表
     */
    fun getAvailableSkills(): List<String> {
        return _skillManager.value?.getSkillsNames() ?: emptyList()
    }

    /**
     * 获取所有技能
     */
    fun getAllSkills(): List<Skill> {
        return _skillManager.value?.getAllSkills() ?: emptyList()
    }

    /**
     * 获取技能信息
     */
    fun getSkillInfo(skillName: String): Skill? {
        return _skillManager.value?.getSkill(skillName)
    }

    /**
     * 加载特定技能 - 对应 Python 的 _handle_load_skill
     */
    fun loadSkill(skillName: String): String {
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

        Log.d(TAG, "✅ load_skill processed successfully: $skillName")
        Log.d(TAG, "   - Skill name: ${skill.name}")
        Log.d(TAG, "   - Instruction length: ${skill.instructions.length} chars")

        return result
    }

    /**
     * 检查是否需要压缩上下文
     * 完全对应 Python 的 _check_compression_trigger
     */
    private fun shouldCompress(lastResponse: String = ""): Pair<Boolean, Int> {
        if (_historyMessages.isEmpty() || _maxTokens <= 0) {
            return false to 0
        }

        val totalContentLength = _historyMessages.sumOf { it.content.length }
        val threshold = (_maxTokens * _compressionThreshold).toInt()
        val actualLength = totalContentLength / 2

        // Condition 1: Context/2 exceeds 75% of threshold
        val lengthTrigger = actualLength > threshold

        // Condition 2: LLM response is shorter than 10 characters
        val shortResponseTrigger = lastResponse.isNotEmpty() && lastResponse.length < 10

        val shouldCompress = lengthTrigger || shortResponseTrigger

        if (shouldCompress) {
            Log.d(TAG, "✅ Trigger compression: lengthTrigger=$lengthTrigger (actual=$actualLength, thresh=$threshold), shortTrigger=$shortResponseTrigger (len=${lastResponse.length})")
            Log.d(TAG, "🔧 Starting context compression...")
        } else {
            Log.d(TAG, "📊 No compression: actual=$actualLength <= thresh=$threshold AND response len=${lastResponse.length} >= 10")
        }

        return shouldCompress to totalContentLength
    }

    /**
     * 构建压缩后的消息列表
     * 完全对应 Python 的 _compress_and_retry
     */
    private fun buildCompressedMessages(
        compressedHistory: String
    ): List<Pair<String, String>> {
        Log.d(TAG, "📜 Compressed history length: ${compressedHistory.length} chars")

        val messages = mutableListOf<Pair<String, String>>()

        // System prompt
        messages.add("system" to _systemPrompt)

        // Compressed history as user message
        messages.add("user" to compressedHistory)

        // Assistant response
        messages.add("assistant" to "Acknowledged.")

        return messages
    }

    /**
     * 发送消息并与 Agent 对话
     * 完全对应 Python 版本的 chat() 方法
     */
    suspend fun chat(userMessage: String): String {
        Log.d(TAG, "===========================================")
        Log.d(TAG, "LLM CHAT INVOKED")
        Log.d(TAG, "SYSTEM PROMPT: $_systemPrompt")
        Log.d(TAG, "USER MESSAGE: $userMessage")
        Log.d(TAG, "===========================================")

        _isProcessing.value = true
        _currentMessage.value = ""

        // Add user message to history
        _historyMessages.add(ContextCompressor.Message("user", userMessage))

        // First check: did the previous context already exceed the limit?
        val (initialNeedCompress, _) = shouldCompress()

        return try {
            val responseBuilder = StringBuilder()

            if (initialNeedCompress) {
                performCompressionAndChat(userMessage, responseBuilder)
            } else {
                Log.d(TAG, "Sending message to conversation...")
                // Normal chat flow without compression
                conversation?.sendMessageAsync(userMessage)?.collect { partialResponse ->
                    _currentMessage.value += partialResponse.toString()
                    responseBuilder.append(partialResponse.toString())
                }
            }

            var responseText = responseBuilder.toString()
            Log.d(TAG, "LLM Response completed: ${responseText.take(50)}...")

            // Add assistant response to history
            _historyMessages.add(ContextCompressor.Message("assistant", responseText))

            // Post-chat check: was the response too short? Or did this message push context over?
            val (postNeedCompress, _) = shouldCompress(responseText)

            if (postNeedCompress && !initialNeedCompress) {
                Log.d(TAG, "🔄 Response triggered late compression. Retrying chat...")
                responseBuilder.clear()
                performCompressionAndChat(userMessage, responseBuilder)
                responseText = responseBuilder.toString()

                // Update the last message in history with the new full response
                if (_historyMessages.isNotEmpty() && _historyMessages.last().role == "assistant") {
                    _historyMessages.removeAt(_historyMessages.size - 1)
                }
                _historyMessages.add(ContextCompressor.Message("assistant", responseText))
            }

            responseText

        } catch (e: Exception) {
            Log.e(TAG, "Error in chat", e)
            _error.value = e.message ?: "Unknown error"
            ""
        } finally {
            _isProcessing.value = false
        }
    }

    private suspend fun performCompressionAndChat(userMessage: String, responseBuilder: StringBuilder) {
        val totalLength = _historyMessages.sumOf { it.content.length }
        Log.d(TAG, "Executing compression (total history length: $totalLength)")

        val compressedHistory = compressor?.compressHistory(_historyMessages)

        if (compressedHistory != null && compressedHistory.isNotEmpty()) {
            Log.d(TAG, "✅ Compression successful: ${compressedHistory.length} chars")

            // Reset history
            _historyMessages.clear()

            // Rebuild conversation
            conversation?.close()
            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(_systemPrompt),
                initialMessages = listOf(
                    Message.user(compressedHistory),
                    Message.model("Acknowledged"),
                ),
                samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8),
            )
            conversation = engine?.createConversation(conversationConfig)

            // Collect final response
            conversation?.sendMessageAsync(userMessage)?.collect { partialResponse ->
                _currentMessage.value += partialResponse.toString()
                responseBuilder.append(partialResponse.toString())
            }
        } else {
            Log.w(TAG, "⚠️ Compression failed, continuing with original conversation")
            conversation?.sendMessageAsync(userMessage)?.collect { partialResponse ->
                _currentMessage.value += partialResponse.toString()
                responseBuilder.append(partialResponse.toString())
            }
        }
    }

    /**
     * Restore conversation history from a list of messages
     */
    fun restoreHistory(messages: List<ContextCompressor.Message>) {
        try {
            _historyMessages.clear()
            _historyMessages.addAll(messages)

            // Re-initialize conversation with full context if possible
            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(_systemPrompt),
                initialMessages = messages.map { msg ->
                    if (msg.role == "user") Message.user(msg.content)
                    else Message.model(msg.content)
                }
            )

            conversation?.close()
            conversation = engine?.createConversation(conversationConfig)

            Log.d(TAG, "Restored ${messages.size} messages to LLM context")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring history: ${e.message}")
        }
    }

    /**
     * 重置会话
     */
    fun resetConversation() {
        try {
            _historyMessages.clear()

            val newConfig = ConversationConfig(
                systemInstruction = Contents.of(_systemPrompt)
            )

            conversation?.close()
            conversation = engine?.createConversation(newConfig)

            _currentMessage.value = ""
            Log.d(TAG, "Conversation reset")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting conversation", e)
        }
    }



    /**
     * 释放资源
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
            Log.d(TAG, "Engine disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing engine", e)
        }
    }

    /**
     * 获取模型文件夹
     */
    fun getOrCreateModelFolder(): File {
        val storageDir = File(context.getExternalFilesDir(null), MODEL_FOLDER_NAME)
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return storageDir
    }

    /**
     * 获取模型路径
     */
    fun getOrCreateModelPath(): String {
        val modelFolder = getOrCreateModelFolder()
        val modelPath = File(modelFolder, DEFAULT_MODEL_NAME).absolutePath
        return modelPath
    }

    /**
     * 获取 Engine 配置
     */
    fun getEngineConfig(): EngineConfig {
        return EngineConfig(
            modelPath = getOrCreateModelPath(),
            backend = Backend.CPU(),
            maxNumTokens = 32768 // Default to 32k for text adventure
        )
    }

    /**
     * 获取当前配置信息
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
