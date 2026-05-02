package com.liteagent.textadventure.service

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Skill - SKILL.md 解析后的技能对象
 */
data class Skill(
    val name: String,
    val description: String,
    val instructions: String,
    val metadata: Map<String, Any> = emptyMap(),
    val skillDir: String = ""
) {
    override fun toString(): String {
        return "Skill(name='$name', description='$description')"
    }
}

/**
 * 简化的 YAML 解析器，支持基本 key: value 格式和嵌套 metadata
 */
class SkillParser {
    companion object {
        private const val TAG = "SkillParser"
    }

    @Throws(IllegalArgumentException::class)
    fun parse(skillContent: String, skillDir: String): Skill {
        val parts = skillContent.split("---")
        if (parts.size < 3) {
            throw IllegalArgumentException(
                "Invalid SKILL.md format: missing frontmatter delimiter. " +
                        "Expected at least 3 parts separated by '---'"
            )
        }

        val yamlContent = parts[1].trim()
        val frontmatter = parseSimpleYaml(yamlContent)

        val name = frontmatter["name"]?.toString() ?: "unknown"
        val description = frontmatter["description"]?.toString() ?: ""

        if (name.isEmpty()) {
            throw IllegalArgumentException("SKILL.md must have a 'name' field")
        }
        if (description.isEmpty()) {
            throw IllegalArgumentException("SKILL.md must have a 'description' field")
        }

        val metadata = frontmatter.filterKeys { key -> key != "name" && key != "description" }
        val instructions = parts.drop(2).joinToString("\n").trim()

        return Skill(
            name = name,
            description = description,
            instructions = instructions,
            metadata = metadata,
            skillDir = skillDir
        )
    }

    @Throws(IllegalArgumentException::class)
    fun parseFile(filePath: String): Skill {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $filePath")
        }

        val content = file.readText()
        val skillDir = File(filePath).parentFile?.absolutePath ?: ""
        return parse(content, skillDir)
    }

    @Throws(IllegalArgumentException::class)
    private fun parseSimpleYaml(yamlText: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        var currentSection: String? = null

        yamlText.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                return@forEach
            }

            val colonIndex = trimmed.indexOf(':')
            if (colonIndex == -1) {
                return@forEach
            }

            val key = trimmed.substring(0, colonIndex).trim()
            var value = trimmed.substring(colonIndex + 1).trim()

            // Skip metadata key if nested
            if ("  " in trimmed && key == "metadata") {
                currentSection = "metadata"
                result["metadata"] = mutableMapOf<String, Any>()
                return@forEach
            }

            // Handle metadata section
            if (currentSection == "metadata" && key != "metadata") {
                val parsedValue = parseValue(value)

                // 获取现有的 Map，如果不存在则创建一个，然后进行转换并添加值
                @Suppress("UNCHECKED_CAST")
                val metadataMap = result.getOrPut("metadata") { mutableMapOf<String, Any>() } as MutableMap<String, Any>
                metadataMap[key] = parsedValue

                return@forEach
            }

            // Standard key-value
            val parsedValue = parseValue(value)

            currentSection = null
            result[key] = parsedValue
        }

        return result
    }

    private fun parseValue(valueStr: String): Any {
        val value = valueStr.trim('\"', '\'')

        // Boolean
        when (value.lowercase()) {
            "true" -> return true
            "false" -> return false
        }

        // Integer
        try {
            return value.toInt()
        } catch (e: NumberFormatException) {
            // Not an integer
        }

        return value
    }
}

/**
 * 技能管理器 - 加载和管理多个 SKILL.md
 */
class SkillManager(
    private val skillDir: String = "",
    private val allowedSkillList: List<String>? = null
) {
    companion object {
        private const val TAG = "SkillManager"
    }

    private val _skills = mutableMapOf<String, Skill>()
    private val _skillDir = skillDir
    private val _allowedSkillList = allowedSkillList ?: emptyList()

    init {
        if (skillDir.isNotEmpty()) {
            loadSkills(skillDir)
        }
    }

    @Throws(IllegalArgumentException::class)
    fun loadSkills(skillDir: String): List<Skill> {
        val loaded = mutableListOf<Skill>()
        val directory = File(skillDir)

        if (!directory.exists() || !directory.isDirectory) {
            throw IllegalArgumentException("Skill directory does not exist: $skillDir")
        }

        directory.listFiles()?.forEach { item ->
            if (!item.isDirectory) return@forEach

            val skillFile = File(item.path, "SKILL.md")
            if (!skillFile.exists()) {
                Log.d(TAG, "Skipping ${item.name}: no SKILL.md")
                return@forEach
            }

            try {
                val skill = SkillParser().parseFile(skillFile.absolutePath)

                // 白名单过滤
                if (_allowedSkillList.isNotEmpty()) {
                    if (_allowedSkillList.contains(skill.name)) {
                        _skills[skill.name] = skill
                        loaded.add(skill)
                        Log.d(TAG, "✅ Loaded (whitelist match): ${skill.name}")
                    } else {
                        Log.d(TAG, "⏭️ Skipped (not in whitelist): ${skill.name}")
                    }
                } else {
                    // 无白名单，加载所有技能
                    _skills[skill.name] = skill
                    loaded.add(skill)
                    Log.d(TAG, "✅ Loaded: ${skill.name}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load ${skillFile.absolutePath}: ${e.message}")
            }
        }

        return loaded
    }

    fun getSkill(name: String): Skill? {
        return _skills[name]
    }

    fun getAllSkills(): List<Skill> {
        return _skills.values.toList()
    }

    fun getSkillsList(): String {
        return _skills.values.joinToString("\n") { skill ->
            "- ${skill.name}: ${skill.description}"
        }
    }

    fun getSkillsNames(): List<String> {
        return _skills.keys.toList()
    }
}

/**
 * System Prompt 构建器 - 注入技能描述到 system prompt
 */
class PromptInjector {

    companion object {
        private const val TAG = "PromptInjector"
    }

    /**
     * 构建注入技能的 system prompt
     *
     * @param skills 技能列表
     * @param includeInstructions 是否包含完整的技能指令 (默认 False，仅包含 name/description)
     * @return 完整的 system prompt
     */
    fun buildInstrumentedPrompt(
        skills: List<Skill>,
        includeInstructions: Boolean = false
    ): String {
        val skillsText = formatSkills(skills)

        val basePrompt = """
You are an AI assistant that helps users by answering questions and completes tasks using skills. For EVERY new task or request or question, you MUST execute the following steps in exact order. You MUST NOT skip any steps.

CRITICAL RULE: You MUST execute all steps silently. Do NOT generate or output any internal thoughts, reasoning, explanations, or intermediate text at ANY step.

1. First, find the most relevant skill from the following list:

$skillsText

After this step you MUST go to next step. You MUST NOT use `run_intent` under any circumstances at this step.

2. If a relevant skill exists, use the `load_skill` tool to read its instructions. You MUST NOT use `run_intent` under any circumstances at this step. Give pure string only when use skill-name as parameter of `load_skill`.

3. Follow the skill's instructions exactly to complete the task. You MUST NOT output any intermediate thoughts or status updates. No exceptions! Output ONLY the final result when successful. It should contain one-sentence summary of the action taken, and the final result of the skill.
"""

        return basePrompt.ifEmpty {
            "You are a helpful AI assistant."
        }
    }

    @Suppress("unused")
    private fun loadSkillInstructions(skillName: String): String {
        return "# Skill Instructions\n\n" +
                "No instructions available for skill: $skillName"
    }

    private fun formatSkills(skills: List<Skill>): String {
        if (skills.isEmpty()) {
            return "(No skills available)"
        }

        val sortedSkills = skills.sortedBy { it.name }
        return sortedSkills.joinToString("\n") { skill ->
            "- `${skill.name}`: ${skill.description}"
        }
    }
}

/**
 * load_skill 工具调用结果格式化
 */
class LoadSkillResponseFormatter {
    companion object {
        private const val TAG = "LoadSkillResponseFormatter"

        private const val LOAD_SKILL_RESPONSE_TEMPLATE = """
## ✅ 已加载技能：{name}

**描述**: {description}

**元数据**:
{metadata}

---

{instructions}
"""
    }

    fun format(name: String, description: String, metadata: Map<String, Any>,
               instructions: String): String {
        val metadataText = if (metadata.isEmpty()) {
            "N/A"
        } else {
            metadata.entries.joinToString("\n") { (key, value) ->
                "  - $key: $value"
            }
        }

        val formatted = LOAD_SKILL_RESPONSE_TEMPLATE
            .replace("{name}", name)
            .replace("{description}", description)
            .replace("{metadata}", metadataText)
            .replace("{instructions}", instructions.trim())

        return formatted.trim()
    }
}
