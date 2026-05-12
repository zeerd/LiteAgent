package com.zeerd.textadventure.service

import com.google.common.truth.Truth.assertThat
import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileWriter

/**
 * SkillManager 单元测试
 */
class SkillManagerTest {

    private lateinit var testDir: File
    private lateinit var skillManager: SkillManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        // 创建临时测试目录
        testDir = File.createTempFile("skill_test", "").apply {
            deleteOnExit()
            delete()
            mkdirs()
        }

        // 创建一个测试用的 SKILL.md
        val testSkillDir = File(testDir, "test-skill")
        testSkillDir.mkdirs()

        val skillContent = """
---
name: test-skill
description: 测试技能
version: "1.0"
---

# Test Skill Instructions

This is the instruction text.
""".trimIndent()

        FileWriter(File(testSkillDir, "SKILL.md")).use { it.write(skillContent) }

        // 初始化 SkillManager
        skillManager = SkillManager(testDir.absolutePath)
    }

    @Test
    fun testLoadSkills() {
        val skills = skillManager.getAllSkills()

        assertThat(skills.size).isEqualTo(1)
        assertThat(skills[0].name).isEqualTo("test-skill")
    }

    @Test
    fun testGetSkill() {
        val skill = skillManager.getSkill("test-skill")

        assertThat(skill).isNotNull()
        assertThat(skill?.name).isEqualTo("test-skill")
        assertThat(skill?.description).isEqualTo("测试技能")
    }

    @Test
    fun testGetSkillsNames() {
        val names = skillManager.getSkillsNames()

        assertThat(names).containsExactly("test-skill")
    }

    @Test
    fun testGetAllSkills() {
        val skills = skillManager.getAllSkills()

        assertThat(skills.size).isEqualTo(1)
        assertThat(skills[0].name).isEqualTo("test-skill")
        assertThat(skills[0].instructions).contains("This is the instruction text")
    }

    @Test
    fun testGetNonExistentSkill() {
        val skill = skillManager.getSkill("non-existent")

        assertThat(skill).isNull()
    }
}

/**
 * PromptInjector 单元测试
 */
class PromptInjectorTest {

    private val context = io.mockk.mockk<android.content.Context>(relaxed = true)

    @Test
    fun testBuildInstrumentedPromptWithSkills() {
        io.mockk.every { context.getString(any()) } returns "Respond in English."

        val skills = listOf(
            Skill(name = "test-skill", description = "测试技能", instructions = "Instructions")
        )

        val injector = PromptInjector()
        val prompt = injector.buildInstrumentedPrompt(skills)

        assertThat(prompt).contains("test-skill")
        assertThat(prompt).contains("You are an AI assistant")
    }

    @Test
    fun testBuildInstrumentedPromptWithNoSkills() {
        io.mockk.every { context.getString(any()) } returns "Respond in English."
        val skills = emptyList<Skill>()

        val injector = PromptInjector()
        val prompt = injector.buildInstrumentedPrompt(skills)

        assertThat(prompt).contains("You are an AI assistant")
        assertThat(prompt).contains("(No skills available)")
    }
}
