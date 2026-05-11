package com.zeerd.textadventure.service

import android.util.Log
import com.zeerd.textadventure.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 技能数据类，代表从 SKILL.md 解析出的一个功能模块。
 */
data class Skill(
    val name: String, // 技能名称
    val description: String, // 技能描述
    val instructions: String, // 详细的操作指令
    val metadata: Map<String, Any> = emptyMap(), // 其他元数据
    val skillDir: String = "" // 技能文件所在目录
) {
    override fun toString(): String {
        return "Skill(name='$name', description='$description')"
    }
}

/**
 * 技能解析器，用于解析 Markdown 格式的技能文件。
 */
class SkillParser {
    companion object {
        private const val TAG = "TextAdventure-SkillParser"
    }

    /**
     * 解析字符串形式的技能内容。
     */
    @Throws(IllegalArgumentException::class)
    fun parse(skillContent: String, skillDir: String): Skill {
        Log.v(TAG, ">>> parse() IN - skillDir=$skillDir")
        val parts = skillContent.split("---")
        if (parts.size < 3) {
            Log.e(TAG, "Invalid SKILL.md format: missing frontmatter delimiter")
            throw IllegalArgumentException(
                "Invalid SKILL.md format: missing frontmatter delimiter."
            )
        }

        // 解析头部 YAML 内容
        val yamlContent = parts[1].trim()
        val frontmatter = parseSimpleYaml(yamlContent)

        val name = frontmatter["name"]?.toString() ?: "unknown"
        val description = frontmatter["description"]?.toString() ?: ""

        if (name.isEmpty()) {
            Log.e(TAG, "SKILL.md must have a 'name' field")
            throw IllegalArgumentException("SKILL.md must have a 'name' field")
        }
        if (description.isEmpty()) {
            Log.e(TAG, "SKILL.md must have a 'description' field")
            throw IllegalArgumentException("SKILL.md must have a 'description' field")
        }

        val metadata = frontmatter.filterKeys { key -> key != "name" && key != "description" }
        val instructions = parts.drop(2).joinToString("\n").trim()

        val result = Skill(
            name = name,
            description = description,
            instructions = instructions,
            metadata = metadata,
            skillDir = skillDir
        )
        Log.v(TAG, "<<< parse() OUT - name=$name")
        return result
    }

    /**
     * 解析指定路径的文件。
     */
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

    /**
     * 简单的 YAML 解析器。
     */
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

            // 处理 metadata 嵌套节
            if ("  " in trimmed && key == "metadata") {
                currentSection = "metadata"
                result["metadata"] = mutableMapOf<String, Any>()
                return@forEach
            }

            if (currentSection == "metadata" && key != "metadata") {
                val parsedValue = parseValue(value)
                @Suppress("UNCHECKED_CAST")
                val metadataMap = result.getOrPut("metadata") { mutableMapOf<String, Any>() } as MutableMap<String, Any>
                metadataMap[key] = parsedValue
                return@forEach
            }

            val parsedValue = parseValue(value)
            currentSection = null
            result[key] = parsedValue
        }

        return result
    }

    /**
     * 将字符串值转换为对应的基本类型（Boolean, Int 等）。
     */
    private fun parseValue(valueStr: String): Any {
        val value = valueStr.trim('\"', '\'')
        when (value.lowercase()) {
            "true" -> return true
            "false" -> return false
        }
        try {
            return value.toInt()
        } catch (e: NumberFormatException) {}
        return value
    }
}

/**
 * 技能管理器，负责从指定目录加载技能并进行管理。
 */
class SkillManager(
    private val skillDir: String = "",
    private val allowedSkillList: List<String>? = null
) {
    companion object {
        private const val TAG = "TextAdventure-SkillManager"
    }

    private val _skills = mutableMapOf<String, Skill>()
    private val _allowedSkillList = allowedSkillList ?: emptyList()

    init {
        Log.v(TAG, ">>> SkillManager init IN - skillDir=$skillDir")
        if (skillDir.isNotEmpty()) {
            loadSkills(skillDir)
        }
        Log.v(TAG, "<<< SkillManager init OUT - ${_skills.size} skills loaded")
    }

    /**
     * 递归加载目录下所有的技能。
     */
    @Throws(IllegalArgumentException::class)
    fun loadSkills(skillDir: String): List<Skill> {
        Log.v(TAG, ">>> loadSkills() IN - skillDir=$skillDir")
        val loaded = mutableListOf<Skill>()
        val directory = File(skillDir)

        if (!directory.exists() || !directory.isDirectory) {
            Log.e(TAG, "Skill directory does not exist: $skillDir")
            throw IllegalArgumentException("Skill directory does not exist: $skillDir")
        }

        directory.listFiles()?.forEach { item ->
            if (!item.isDirectory) return@forEach

            val skillFile = File(item.path, "SKILL.md")
            if (!skillFile.exists()) return@forEach

            try {
                val skill = SkillParser().parseFile(skillFile.absolutePath)
                // 如果定义了白名单，只加载名单内的技能
                if (_allowedSkillList.isNotEmpty()) {
                    if (_allowedSkillList.contains(skill.name)) {
                        _skills[skill.name] = skill
                        loaded.add(skill)
                    } else {
                        Log.v(TAG, "Skipping skill ${skill.name} - not in allowed list")
                    }
                } else {
                    _skills[skill.name] = skill
                    loaded.add(skill)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load ${skillFile.absolutePath}: ${e.message}")
            }
        }
        Log.v(TAG, "<<< loadSkills() OUT - loaded ${loaded.size} skills")
        return loaded
    }

    fun getSkill(name: String): Skill? = _skills[name]

    fun getAllSkills(): List<Skill> = _skills.values.toList()

    fun getSkillsNames(): List<String> = _skills.keys.toList()
}

/**
 * 系统提示词注入器，将技能信息织入模型的基础提示词中。
 */
class PromptInjector {
    companion object {
        private const val TAG = "TextAdventure-PromptInjector"
    }

    /**
     * 构建包含技能描述的系统提示词。
     */
    fun buildInstrumentedPrompt(
        skills: List<Skill>,
        includeInstructions: Boolean = false
    ): String {
        Log.v(TAG, ">>> buildInstrumentedPrompt() IN - skills count=${skills.size}")
        val skillsText = formatSkills(skills)
        val result = """
You are an AI assistant that helps users using specific skills.

CRITICAL OPERATIONAL RULES:
1. You MUST execute the following 3-step workflow for EVERY request, but you MUST do so INVISIBLY.
2. STRIKINGLY FORBIDDEN: Do NOT output "Step 1", "Step 2", "Step 3", "First, find...", "If a relevant skill exists...", or "load_skill".
3. Any text representing your internal thoughts or the instructions themselves will be considered a system failure.
4. Your response MUST start directly with the final result.

WORKFLOW (Internal Only):
1. Identify the most relevant skill from: $skillsText
2. If found, call `load_skill(skill_name)`. (Do not textually output this call).
3. Execute the skill instructions.

OUTPUT FORMAT:
[Final result of the skill]
"""
        Log.v(TAG, "<<< buildInstrumentedPrompt() OUT - output length=${result.length}")
        return result
    }

    private fun formatSkills(skills: List<Skill>): String {
        if (skills.isEmpty()) return "(No skills available)"
        return skills.sortedBy { it.name }.joinToString("\n") { skill ->
            "- `${skill.name}`: ${skill.description}"
        }
    }
}

/**
 * 技能加载响应的格式化器。
 */
class LoadSkillResponseFormatter {
    companion object {
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
        val metadataText = if (metadata.isEmpty()) "N/A" else {
            metadata.entries.joinToString("\n") { (key, value) -> "  - $key: $value" }
        }

        return LOAD_SKILL_RESPONSE_TEMPLATE
            .replace("{name}", name)
            .replace("{description}", description)
            .replace("{metadata}", metadataText)
            .replace("{instructions}", instructions.trim())
            .trim()
    }
}
