package com.zeerd.textadventure.service

import android.util.Log
import com.zeerd.textadventure.service.ContextCompressor
import com.zeerd.textadventure.service.LiteRtLmService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * 上下文压缩触发逻辑的单元测试
 */
class CompressionTriggerTest {

    private lateinit var service: LiteRtLmService
    private val context = mockk<android.content.Context>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.v(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        service = LiteRtLmService(context)
    }

    private fun getHistoryMessages(service: LiteRtLmService): MutableList<ChatMessage> {
        val field: Field = service.javaClass.getDeclaredField("_historyMessages")
        field.isAccessible = true
        return field.get(service) as MutableList<ChatMessage>
    }

    @Test
    fun `test shouldCompress triggers when length exceeds threshold`() {
        // 设置配置：maxTokens = 100, threshold = 0.5.
        // 触发条件：actualLength (totalContentLength / 2) > (100 * 0.5 = 50)
        service.maxTokens = 100
        service.compressionThreshold = 0.5f

        val history = getHistoryMessages(service)

        // 1. 低于阈值：总长度 80 -> actualLength 40 <= 50
        history.add(ChatMessage("user", "a".repeat(40)))
        history.add(ChatMessage("assistant", "b".repeat(40)))

        val (shouldCompress1, _) = service.shouldCompress()
        assertFalse("Should NOT compress when length is under threshold", shouldCompress1)

        // 2. 超过阈值：总长度 120 -> actualLength 60 > 50
        history.add(ChatMessage("user", "c".repeat(40)))

        val (shouldCompress2, _) = service.shouldCompress()
        assertTrue("Should compress when length exceeds threshold", shouldCompress2)
    }

    @Test
    fun `test shouldCompress triggers on short response`() {
        service.maxTokens = 1000
        service.compressionThreshold = 0.8f // 阈值 800

        val history = getHistoryMessages(service)
        history.add(ChatMessage("user", "Hello"))

        // 1. 正常响应长度 (>= 10)
        val (shouldCompress1, _) = service.shouldCompress("This is a long enough response")
        assertFalse("Should NOT compress on normal response", shouldCompress1)

        // 2. 极短响应 (< 10)
        val (shouldCompress2, _) = service.shouldCompress("Short")
        assertTrue("Should compress on very short response", shouldCompress2)
    }

    @Test
    fun `test shouldCompress does not trigger when history is empty`() {
        service.maxTokens = 100
        service.compressionThreshold = 0.5f

        val (shouldCompress, _) = service.shouldCompress("Too short")
        assertFalse("Should NOT compress when history is empty", shouldCompress)
    }
}
