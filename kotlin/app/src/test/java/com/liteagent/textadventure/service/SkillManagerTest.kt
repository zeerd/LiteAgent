package com.liteagent.textadventure.service

import com.google.common.truth.Truth.assertThat
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
tags: [test]
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
    fun testGetSkillsList() {
        val skillsList = skillManager.getSkillsList()

        assertThat(skillsList).contains("test-skill")
        assertThat(skillsList).contains("测试技能")
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

    @Test
    fun testBuildInstrumentedPromptWithSkills() {
        val skills = listOf(
            Skill(name = "test-skill", description = "测试技能"),
            Skill(name = "another-skill", description = "另一个技能")
        )

        val injector = PromptInjector()
        val prompt = injector.buildInstrumentedPrompt(skills)

        assertThat(prompt).contains("test-skill")
        assertThat(prompt).contains("another-skill")
        assertThat(prompt).contains("You are an AI assistant")
    }

    @Test
    fun testBuildInstrumentedPromptWithNoSkills() {
        val skills = emptyList<Skill>()

        val injector = PromptInjector()
        val prompt = injector.buildInstrumentedPrompt(skills)

        assertThat(prompt).isEqualTo("You are a helpful AI assistant.")
    }
}
