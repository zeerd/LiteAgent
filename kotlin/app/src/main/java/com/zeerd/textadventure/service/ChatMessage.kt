package com.zeerd.textadventure.service

/**
 * 基础消息数据类，解耦了对 LiteRT-LM 库的依赖。
 */
data class ChatMessage(
    val role: String,
    val content: String
)
