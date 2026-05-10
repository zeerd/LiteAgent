package com.zeerd.textadventure.service

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Field

/**
 * 上下文压缩的集成测试（运行在 Android 设备/模拟器上）
 */
@RunWith(AndroidJUnit4::class)
class CompressionInstrumentedTest {

    private lateinit var service: LiteRtLmService
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val TAG = "TextAdventure-CompressionInstrumentedTest"

    @Before
    fun setUp() {
        service = LiteRtLmService(context)
    }

    private fun getHistoryMessages(service: LiteRtLmService): MutableList<ChatMessage> {
        val field: Field = service.javaClass.getDeclaredField("_historyMessages")
        field.isAccessible = true
        return field.get(service) as MutableList<ChatMessage>
    }

    @Test
    fun testShouldCompressTriggerLogic() {
        // 验证触发逻辑在 Android 环境下是否正常工作
        service.maxTokens = 100
        service.compressionThreshold = 0.5f

        val history = getHistoryMessages(service)

        // 1. 低于阈值 (15 + 15 = 30 chars < 50 threshold)
        history.add(ChatMessage("user", "a".repeat(15)))
        history.add(ChatMessage("assistant", "b".repeat(15)))

        val (shouldCompress1, _) = service.shouldCompress()
        assertFalse("Should NOT compress when length is under threshold", shouldCompress1)

        // 2. 超过阈值 (30 + 30 = 60 chars > 50 threshold)
        history.add(ChatMessage("user", "c".repeat(30)))

        val (shouldCompress2, _) = service.shouldCompress()
        assertTrue("Should compress when length exceeds threshold", shouldCompress2)
    }

    @Test
    fun testComplexSemanticCompression() = runBlocking {
        // 这个测试尝试执行实际的压缩。
        // 注意：这需要设备上有模型文件。
        val modelFile = File(context.getExternalFilesDir(null), "text_adventure_models/lite-rtlm-model")
        if (!modelFile.exists()) {
            // 如果没有模型，跳过执行测试
            return@runBlocking
        }

        val engineConfig = EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = Backend.CPU(),
            maxNumTokens = 4096
        )

        val initialized = service.initialize(engineConfig)
        assertTrue("Service should initialize", initialized)

        val history = getHistoryMessages(service)

        // 模拟高密度干扰信息历史 (完整同步 Python 版本 chat_history.json)
        history.add(ChatMessage("user", "我现在站在\"刻律涅亚\"号的医疗舱里，四周全是绿色的雾气。我该怎么办？"))
        history.add(ChatMessage("assistant", "这里的能见度极低，空气中弥漫着冷却液和某种有机物腐烂的混合气味。你右手边的墙壁上有一个应急物资箱，左手边是三台已经损坏的生命维持仪。你发现舱门边的废墟里有一张染血的便签，上面写着：\"如果主控 AI 询问安全等级，回答'翡翠之梦 -1994'，否则它会立刻抽干氧气。\""))
        history.add(ChatMessage("user", "我拿起了便签，放进口袋。我想检查一下那个物资箱。"))
        history.add(ChatMessage("assistant", "你走向物资箱，脚下的金属地板发出刺耳的摩擦声。当你试图打开箱子时，发现锁头已经因为高温熔化在一起了。你不得不拿起旁边的一根断裂的钛合金杠杆，用力撬动。经过一番努力，咔哒一声，箱子开了。里面没有你期待的武器，只有一支强效镇静剂和一卷发黄的绷带。这时，走廊外传来重物拖行的声音，咚——咚——，每一下都伴随着金属甲板的呻吟。"))
        history.add(ChatMessage("user", "我感觉心跳很快。我把镇静剂和绷带收好，准备离开医疗舱进入走廊。"))
        history.add(ChatMessage("assistant", "你紧握着那根杠杆，慢慢推开舱门。外面的走廊更暗，红色的应急灯光闪烁不定。系统警告音在你耳边响起：\"警告，C 区防御系统逻辑溢出，敌我识别功能受损。\"根据你之前在控制室看到的受损日志，这个区域的探头现在只能识别正向移动的生物特征。这是一条死亡走廊，除非你始终保持倒退行走，否则机枪塔会瞬间把你撕碎。"))
        history.add(ChatMessage("user", "这太荒谬了，但我只能照做。我开始倒退着向 C 区深处走。"))
        history.add(ChatMessage("assistant", "这是一个极度压抑的过程。你看不见身后的路，只能通过眼角的余光观察两侧舱壁的缝隙。你感觉到脊背发凉，仿佛黑暗中有什么东西在注视着你。你的呼吸变得急促，宇航服面罩上结了一层薄薄的水雾。你低头看了一眼手腕上的氧气监测表，数字正在跳动：34%......33.8%......33.5%。氧气存量正在加速消耗，可能系统有微小的泄漏。"))
        history.add(ChatMessage("user", "我看到前面（其实是我身后的方向）拐角处好像躺着一个人。"))
        history.add(ChatMessage("assistant", "那是大副埃德蒙。他靠在冷却管道旁，胸口被某种利器贯穿，宇航服已经彻底报废。看到你走近，他吃力地抬起头，满嘴是血。他颤抖着从怀里掏出一个生锈的指南针塞进你手里。他死死抓着你的手腕，盯着你的眼睛说：\"听着……不要相信任何星图……这片星区的引力异常……这里的指南针，南极点其实是指向最近的引力源的，跟着它走才能找到逃生舱……还有，如果你想停掉这一层的警报，去配电盒那里，记住，剪断蓝色的信号线……不是红色的，是蓝色的……\"话音刚落，他的手失去了力气，重重地摔在甲板上。"))
        history.add(ChatMessage("user", "埃德蒙！该死……我收起指南针。我得继续走。"))
        history.add(ChatMessage("assistant", "埃德蒙的死让走廊显得更加死寂。你手中那个生锈的指南针指针正在疯狂旋转，最后指向了你左侧的一道加固闸门。你感觉到脚下的甲板在轻微震动，那是空间站动力炉不稳定的征兆。你必须在氧气耗尽前找到主控室，或者那个该死的逃生舱。你继续倒退着行走，每一步都踏在血迹和冷却液的混合物上，发出的粘稠声响在幽长的走廊里回荡。"))

        // 获取压缩器
        val field = service.javaClass.getDeclaredField("compressor")
        field.isAccessible = true
        val compressor = field.get(service) as? ContextCompressor
        assertTrue(compressor != null)

        if (compressor != null) {
            val snapshot = compressor.compressHistory(history)

            println("=== COMPRESSED SNAPSHOT ===\n$snapshot\n==========================")
            Log.d(TAG, "=== COMPRESSED SNAPSHOT ===\n$snapshot\n==========================")

            // 验证关键信息（这些是绝对不能被“压缩掉”的生存要素）
            assertTrue("Snapshot should not be null", snapshot != null)

            // 1. 验证规则保留
            assertTrue("应保留倒退行走规则", snapshot!!.contains("倒退") || snapshot.contains("backwards"))

            // 2. 验证关键道具
            assertTrue("应保留镇静剂", snapshot.contains("镇静剂") || snapshot.contains("sedative"))
            assertTrue("应保留指南针", snapshot.contains("指南针") || snapshot.contains("compass"))

            // 3. 验证核心代码与操作
            assertTrue("应保留安全代码 1994", snapshot.contains("1994"))
            assertTrue("应保留剪断蓝线的指令", snapshot.contains("蓝色") || snapshot.contains("blue"))

            // 4. 验证数值状态
            assertTrue("应保留氧气数值", snapshot.contains("33.5"))
        }
    }
}
