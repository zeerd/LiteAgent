# LiteAgent_Planner - SKILL.md 解析和上下文压缩功能实现说明

## 新增功能

### 1. SKILL.md 解析器 (SkillManager.kt)

**位置**: `app/src/main/java/com/liteagent/textadventure/service/SkillManager.kt`

**功能**:
- ✅ 解析 SKILL.md 文件 frontmatter (YAML 格式)
- ✅ 加载和管理多个 SKILL.md 文件
- ✅ 白名单过滤机制
- ✅ 提供技能列表查询接口
- ✅ PromptInjector 系统提示词构建器

**核心类**:
```kotlin
- Skill: 技能数据类
- SkillParser: SKILL.md 文件解析器
- SkillManager: 技能管理器
- PromptInjector: 系统提示词构建器
- LoadSkillResponseFormatter: 技能加载响应格式化
```

**使用方法**:
```kotlin
val manager = SkillManager(skillDir = "/path/to/skills") 
val skills = manager.getAllSkills()
val skill = manager.getSkill("text-adventure")
```

### 2. 上下文压缩模块 (ContextCompressor.kt)

**位置**: `app/src/main/java/com/liteagent/textadventure/service/ContextCompressor.kt`

**功能**:
- ✅ 使用 LiteRT-LM 执行对话历史压缩
- ✅ 压缩提示词加载 (默认/自定义)
- ✅ 压缩后的历史重新构建对话
- ✅ 压缩触发检查机制

**核心类**:
```kotlin
- ContextCompressor: 上下文压缩器
  - compressHistory(): 压缩历史消息
  - compressAndRetry(): 使用压缩历史继续对话
  - isCompressionTriggered(): 检查是否触发压缩
```

**压缩触发逻辑**:
```kotlin
阈值检查：total_content_length / 2 > max_tokens * compression_threshold
默认阈值：0.75 (即当内容长度超过 max_tokens 的 75% 时触发)
```

### 3. LiteRtLmService 增强版

**位置**: `app/src/main/java/com/liteagent/textadventure/service/LiteRtLmService.kt`

**新增 API**:
```kotlin
// 压缩器状态
val isInitialized: StateFlow<Boolean>
val isProcessing: StateFlow<Boolean>

// 技能管理
val skillManager: StateFlow<SkillManager?>
fun getAvailableSkills(): List<String>
fun getAllSkills(): List<Skill>
fun getSkillInfo(skillName: String): Skill?
fun loadSkill(skillName: String): String

// 状态管理
val historyMessages: List<ContextCompressor.Message>  // 当前对话历史
val compressionThreshold: Float  // 压缩触发阈值
val maxTokens: Int  // 最大 token 数

// 压缩功能
suspend fun chat(userMessage: String): Pair<String, Boolean>  // 自动压缩
suspend fun generateResponse(prompt: String): String  // 旧接口兼容
```

### 4. 测试文件

**位置**: `app/src/test/java/com/liteagent/textadventure/service/SkillManagerTest.kt`

**测试覆盖**:
- ✅ `testLoadSkills()` - 验证技能加载
- ✅ `testGetSkill()` - 验证单个技能查询
- ✅ `testGetSkillsNames()` - 验证技能名列表
- ✅ `testGetAllSkills()` - 验证获取所有技能
- ✅ `testGetSkillsList()` - 验证格式化技能列表
- ✅ `testGetNonExistentSkill()` - 验证不存在技能处理
- ✅ `testBuildPromptWithSkills()` - 验证提示词构建

### 5. 示例 SKILL.md 文件

**位置**: `skills/text-adventure/SKILL.md`

**内容**:
完整的文本冒险游戏主持人技能定义，包含：
- 游戏类型选择
- 核心规则
- 交互指南

### 6. 压缩提示词

**位置**: `app/src/main/res/raw/compression.md`

**内容**:
Python 版本一致的世界状态快照格式，包含：
- Current State (当前状态)
- Inventory (物品)
- Rules & Mechanics (规则)
- Threats & Dangers (威胁)
- Important NPCs (NPC)
- Recent Events (最近事件)
- Current Objectives (目标)

## 对比 Python 版本

| 功能 | Python 版本 | Java 版本 |
|------|-----------|---------|
| SKILL.md 解析 | ✅ 已实现 | ✅ 新增实现 |
| 技能加载 | ✅ 通过工具调用 | ✅ 通过 API |
| 上下文压缩 | ✅ 使用 LiteRT-LM | ✅ 使用 LiteRT-LM |
| 自动压缩检测 | ✅ 阈值检查 | ✅ 阈值检查 |
| 压缩后重试 | ✅ 消息重构 | ✅ 消息重构 |

## Gradle 依赖更新

```kotlin
// app/build.gradle.kts
dependencies {
    // Kotlin Serialization (basic JSON support)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
```

## 使用示例

### Java 代码示例

```kotlin
// 初始化服务
val service = LiteRtLmService(context)
val config = service.getEngineConfig()

service.initialize(
    engineConfig = config,
    systemPrompt = "You are a helpful AI.",
    skillDir = "/path/to/skills",
    allowedSkills = listOf("text-adventure")
)

// 加载技能
val skillText = service.loadSkill("text-adventure")
Log.d(TAG, "Loaded: $skillText")

// 开始对话
launch {
    service.chat("开始一场奇幻冒险！").collect { response ->
        Log.d(TAG, "Response: $response")
    }
}
```

## 兼容性说明

- **Android SDK**: minSdk 24, targetSdk 35
- **Kotlin 版本**: 1.9.20+
- **Compose BOM**: 2026.04.01
- **LiteRT-LM**: 0.10.2

## 后续优化建议

1. **添加单元测试** - 已创建，建议补充更多覆盖场景
2. **添加 UI 集成** - 将技能加载展示到界面
3. **添加配置持久化** - 压缩阈值等可配置项持久化
4. **性能优化** - 压缩器初始化可延迟，减少启动时间
