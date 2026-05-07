package com.zeerd.textadventure.service

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
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
        compressionPrompt = DEFAULT_COMPRESSION_PROMPT

        val promptFile = findCompressionPromptFile()
        if (promptFile != null && promptFile.exists()) {
            try {
                compressionPrompt = promptFile.readText()
                Log.d(TAG, "✅ Loaded compression prompt from file")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load compression prompt file: ${e.message}")
            }
        }
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
        fullPrompt: String
    ): String? {
        return try {
            val config = EngineConfig(
                modelPath = modelPath,
                backend = backend
            )

            engine?.close()
            engine = Engine(config)
            engine?.initialize()

            val convConfig = ConversationConfig(
                systemInstruction = Contents.of(fullPrompt)
            )

            var conversation: Conversation? = null
            try {
                conversation = engine?.createConversation(convConfig)
                val response = conversation?.sendMessage("")

                extractTextResponse(response)
            } finally {
                conversation?.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed: ${e.message}", e)
            null
        }
    }

    /**
     * 构建最终发送给模型的压缩任务提示词。
     */
    private fun buildCompressPrompt(historyText: String): String {
        return """
You are a text compression task executor, specialized in compressing conversation history into a compact world state snapshot.

${compressionPrompt}

=== Raw Conversation History ===

$historyText

=== Compression Task ===

Please compress the above conversation history into a "World State Snapshot" format.

When compressing, strictly follow:
1. Keep all key information: location, items, rules, threats, NPCs, status
2. Be specific, avoid vague terms like "some items"
3. Direct output of the compressed snapshot only, no explanation, no additional content

Now output the world state snapshot:
"""
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
        historyMessages: List<Message>
    ): String? {
        if (historyMessages.isEmpty() || historyMessages.size < 2) {
            Log.w(TAG, "History too short for compression")
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

        val fullPrompt = buildCompressPrompt(historyText)
        return executeCompression(fullPrompt)
    }

    /**
     * 辅助方法：基于压缩后的快照构建新的消息序列。
     */
    fun compressAndRetry(
        compressedHistory: String,
        systemPrompt: String,
        recentMessages: List<Message> = emptyList()
    ): List<Pair<String, String>> {
        val messages = mutableListOf<Pair<String, String>>()

        try {
            // [系统提示词]
            messages.add("system" to systemPrompt)

            // [压缩后的世界状态快照] (作为第一个用户输入)
            messages.add("user" to compressedHistory)

            // [模型确认已理解状态]
            messages.add("assistant" to "Acknowledged.")

            // [（可选）添加最近的几轮对话以保持语义连贯]
            val recent = if (recentMessages.size >= 4) {
                recentMessages.takeLast(4)
            } else {
                recentMessages
            }

            recent.forEach { msg ->
                messages.add(msg.role to msg.content)
            }

            return messages
        } catch (e: Exception) {
            Log.e(TAG, "Error building compressed messages: ${e.message}")
            return emptyList()
        }
    }

    /**
     * 释放压缩器所占用的引擎资源。
     */
    fun close() {
        engine?.close()
        engine = null
    }

    /**
     * 内部消息数据类。
     */
    data class Message(
        val role: String,
        val content: String
    )
}
