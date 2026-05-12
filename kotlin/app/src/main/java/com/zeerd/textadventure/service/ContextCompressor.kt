package com.zeerd.textadventure.service

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 上下文压缩器。
 * 负责将冗长的对话历史压缩为紧凑的“世界状态快照”，以节省模型的上下文窗口空间。
 */
class ContextCompressor(
    private val modelPath: String,
    private val backend: Backend = Backend.CPU(),
    private val temperature: Float = 0.7f,
    private val maxNumTokens: Int = 4096,
    private val compressionPromptPath: String = "compression.md"
) {
    companion object {
        private const val TAG = "TextAdventure-ContextCompressor"

        // 压缩后的快照格式模板
        private const val COMPRESSION_SNAPSHOT_FORMAT = """
Location: [Current Location/State]
Inventory: [Detailed list of items, quantities, and condition]
Rules: [Active game mechanics or constraints]
Threats: [Current dangers or enemies]
NPCs: [Key characters and their state]
Goals: [Current objectives]
"""

        // 默认的压缩指令
        private const val DEFAULT_COMPRESSION_PROMPT = """
You are a text compression task executor, specialized in compressing conversation history into a compact world state snapshot.

**Guidelines:**

1. **Identify Key Information:**
   - **Current Location/State**: Where is the player? What's the situation?
   - **Inventory/Possessions**: What items does the player have?
   - **Rules/Mechanics**: What are the game rules or constraints?
   - **Threats/Dangers**: What dangers or enemies are present?
   - **NPCs/Aliens**: Are there any important characters or creatures?
   - **Goals/Objectives**: What is the player trying to achieve?

2. **Be Specific:**
   - Avoid vague terms like "some items" - specify what these items are.
   - Use concrete details: "rusty key (iron, slightly bent)" instead of "key".
   - Track quantities: "3 apples" not "some food".

3. **Maintain Temporal Order:**
   - Record recent events and their outcomes.
   - Note when significant events occurred, if relevant.

4. **Format:**
   - Use the "World State Snapshot" format provided below.
   - Be concise but complete.
   - Focus on information that will affect future decisions.

**World State Snapshot Format:**
$COMPRESSION_SNAPSHOT_FORMAT
"""
    }

    private var engine: Engine? = null
    private var compressionPrompt: String = DEFAULT_COMPRESSION_PROMPT

    init {
        loadCompressionPrompt()
    }

    /**
     * 加载压缩提示词。优先从本地文件加载，否则使用硬编码默认值。
     */
    private fun loadCompressionPrompt() {
        Log.v(TAG, ">>> loadCompressionPrompt() IN")
        compressionPrompt = DEFAULT_COMPRESSION_PROMPT

        val promptFile = findCompressionPromptFile()
        if (promptFile != null && promptFile.exists()) {
            try {
                Log.d(TAG, "Found compression prompt file at: ${promptFile.absolutePath}")
                compressionPrompt = promptFile.readText()
                Log.d(TAG, "✅ Loaded compression prompt from file (Length: ${compressionPrompt.length})")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load compression prompt file: ${e.message}")
            }
        } else {
            Log.d(TAG, "Using default hardcoded compression prompt")
        }
        Log.v(TAG, "Compression prompt loaded:\n$compressionPrompt")
        Log.v(TAG, "<<< loadCompressionPrompt() OUT")
    }

    /**
     * 在多个可能的位置查找压缩提示词文件。
     */
    private fun findCompressionPromptFile(): File? {
        val possiblePaths = listOf(
            File(modelPath).parent?.let { File(it, "prompts/compression.md") },
            File(modelPath).parent?.let { File(it, "compression.md") },
            File(System.getProperty("user.dir"), "prompts/compression.md")
        )

        return possiblePaths.find { it != null && it.exists() }
    }

    /**
     * 运行 LLM 引擎执行压缩任务。
     */
    private suspend fun executeCompression(
        systemPrompt: String,
        userPrompt: String
    ): String? = withContext(Dispatchers.Default) {
        Log.v(TAG, ">>> executeCompression() IN")
        Log.d(TAG, "Starting compression execution with model: $modelPath")
        try {
            val adjustedMaxTokens = if (maxNumTokens * 2 <= 32768) maxNumTokens * 2 else 32768
            val config = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                maxNumTokens = adjustedMaxTokens,
            )
            Log.d(TAG, "Engine configuration: backend=$backend, maxNumTokens=$adjustedMaxTokens")

            Log.d(TAG, "Step 1: Re-initializing engine for compression...")
            engine?.close()
            engine = Engine(config)
            engine?.initialize()

            if (engine?.isInitialized() != true) {
                Log.e(TAG, "❌ Compression Engine failed to initialize")
                return@withContext null
            }

            val convConfig = ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                samplerConfig = SamplerConfig(
                    temperature = 0.3, // 压缩任务使用较低温度以保证稳定性
                    topK = 40,
                    topP = 0.95
                )
            )
            Log.d(TAG, "Step 2: Creating conversation for compression task")

            var conversation: Conversation? = null
            try {
                conversation = engine?.createConversation(convConfig)

                Log.i(TAG, ">>> SENDING COMPRESSION REQUEST TO LLM <<<")
                Log.d(TAG, "SYSTEM_INSTRUCTION (Compression):\n$systemPrompt")
                Log.d(TAG, "USER_PROMPT (History to compress):\n$userPrompt")

                val response = conversation?.sendMessage(userPrompt)

                val result = extractTextResponse(response)
                Log.i(TAG, ">>> RECEIVED COMPRESSION RESPONSE FROM LLM <<<")
                Log.v(TAG, "Raw Response: $result")
                result
            } finally {
                conversation?.close()
                Log.d(TAG, "Compression conversation closed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Compression failed with error: ${e.message}", e)
            null
        } finally {
            Log.v(TAG, "<<< executeCompression() OUT")
        }
    }

    /**
     * 构建压缩任务的系统提示词。
     */
    private fun buildSystemPrompt(): String {
        return """
You are a text compression task executor, specialized in compressing conversation history into a compact world state snapshot.

${compressionPrompt}
""".trimIndent()
    }

    /**
     * 构建压缩任务的用户输入（包含待压缩的历史）。
     */
    private fun buildUserPrompt(historyText: String): String {
        return """
=== Raw Conversation History ===

$historyText

=== Compression Task ===

Please compress the above conversation history into a "World State Snapshot" format.

When compressing, strictly follow:
1. Keep all key information: location, items, rules, threats, NPCs, status
2. Be specific, avoid vague terms like "some items"
3. Direct output of the compressed snapshot only, no explanation, no additional content

Now output the world state snapshot:
""".trimIndent()
    }

    private fun extractTextResponse(response: Any?): String {
        return when (response) {
            is String -> response
            else -> response?.toString() ?: ""
        }
    }

    /**
     * 核心压缩方法：将对话历史转换为快照字符串。
     */
    suspend fun compressHistory(
        historyMessages: List<ChatMessage>
    ): String? {
        Log.v(TAG, ">>> compressHistory() IN")
        Log.i(TAG, "Starting compression of history with ${historyMessages.size} messages")
        if (historyMessages.isEmpty() || historyMessages.size < 2) {
            Log.w(TAG, "⚠️ History too short for compression (count: ${historyMessages.size})")
            Log.v(TAG, "<<< compressHistory() OUT (null)")
            return null
        }

        // 格式化对话历史以便模型阅读
        val historyText = historyMessages.joinToString("\n") { msg ->
            val roleLabel = when (msg.role) {
                "user" -> "User"
                "assistant" -> "Assistant"
                else -> msg.role
            }
            "$roleLabel: ${msg.content}"
        }
        Log.d(TAG, "Formatted history text length: ${historyText.length} characters")

        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(historyText)

        Log.d(TAG, "Executing compression engine...")
        val result = executeCompression(systemPrompt, userPrompt)

        if (result != null && result.isNotEmpty()) {
            Log.i(TAG, "✅ Compression successful. Compressed size: ${result.length} vs Original size: ${historyText.length}")
        } else {
            Log.e(TAG, "❌ Compression failed or returned empty result")
        }

        Log.v(TAG, "<<< compressHistory() OUT")
        return result
    }

    /**
     * 辅助方法：基于压缩后的快照构建新的消息序列。
     */
    fun buildCompressedSequence(
        systemPrompt: String,
        compressedHistory: String,
        prefixMessages: List<ChatMessage> = emptyList(),
        recentMessages: List<ChatMessage> = emptyList()
    ): List<Pair<String, String>> {
        Log.v(TAG, ">>> buildCompressedSequence() IN")
        Log.i(TAG, "Building compressed messages sequence. Prefix: ${prefixMessages.size}, Recent: ${recentMessages.size}")
        val messages = mutableListOf<Pair<String, String>>()

        try {
            // 1. [系统提示词]
            messages.add("system" to systemPrompt)
            Log.d(TAG, "Added system prompt")

            // 2. [保留的前缀/背景消息]
            prefixMessages.forEach { msg ->
                messages.add(msg.role to msg.content)
            }
            if (prefixMessages.isNotEmpty()) {
                Log.d(TAG, "Added ${prefixMessages.size} prefix messages")
            }

            // 3. [压缩后的世界状态快照] (作为用户输入)
            messages.add("user" to "Context Snapshot of previous events:\n\n$compressedHistory")
            Log.d(TAG, "Added compressed world state snapshot")

            // 4. [模型确认已理解状态]
            messages.add("assistant" to "Acknowledged.")
            Log.d(TAG, "Added assistant acknowledgement")

            // 5. [保留的最近对话以保持语义连贯]
            recentMessages.forEach { msg ->
                messages.add(msg.role to msg.content)
            }
            if (recentMessages.isNotEmpty()) {
                Log.d(TAG, "Added ${recentMessages.size} recent messages")
            }

            Log.d(TAG, "Final message sequence count: ${messages.size}")
            return messages
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error building compressed messages: ${e.message}", e)
            return emptyList()
        } finally {
            Log.v(TAG, "<<< buildCompressedSequence() OUT")
        }
    }

    /**
     * 将消息列表分为三部分：前缀（保留）、待压缩、最近（保留）。
     * 默认保留前 2 条作为前缀（背景），最后 2 条作为最近上下文。
     */
    fun partitionMessages(
        messages: List<ChatMessage>,
        prefixCount: Int = 2,
        recentCount: Int = 2
    ): Triple<List<ChatMessage>, List<ChatMessage>, List<ChatMessage>> {
        if (messages.size <= prefixCount + recentCount) {
            return Triple(messages, emptyList(), emptyList())
        }
        val prefix = messages.take(prefixCount)
        val remaining = messages.drop(prefixCount)
        val recent = remaining.takeLast(recentCount)
        val toCompress = remaining.dropLast(recentCount)
        return Triple(prefix, toCompress, recent)
    }

    /**
     * 释放压缩器所占用的引擎资源。
     */
    fun close() {
        engine?.close()
        engine = null
    }
}
