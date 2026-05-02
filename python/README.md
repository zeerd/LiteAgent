# Python text_adventure Framework

基于 Google AI Edge Gallery 的 SKILL.md 机制实现的 Python Agent 框架，整合 LiteRT-LM 引擎。

## 📚 项目结构

```
text_adventure_Planner/
├── workplan.md                 # 项目规划文档
├── design_doc.md               # 详细设计文档
├── README.md                   # 本文件
├── text_adventure/             # 核心框架
│   ├── __init__.py           # 模块导出
│   ├── skill.py              # SKILL.md 解析与技能管理
│   ├── agent.py              # text_adventure 主类与对话功能
│   └── compression.py        # 上下文压缩模块
├── skills/                   # 测试技能目录
│   └── echo-text/
│       └── SKILL.md          # Echo 测试技能
├── question.py               # 命令行单次询问工具
└── interactive.py            # 交互式 Shell 入口
```

## 🚀 快速开始

### 1. 初始化 text_adventure

```python
from text_adventure import text_adventure
from LiteRT_LM.python.litert_lm.interfaces import Backend

agent = text_adventure(
    model_path="/path/to/model.litertlm",
    skill_dir="./skills",
    backend=Backend.CPU,
    temperature=0.7
)

# 列出可用技能
agent.list_skills()

# 发送消息
response = agent.chat("Hello, world!")
print(response)
```

### 2. 使用 text_adventure 多轮对话

```python
from text_adventure import text_adventure

agent = text_adventure(
    model_path="/path/to/model.litertlm",
    skill_dir="./skills"
)

# 多轮对话
print(agent.chat("What can you do?"))
print(agent.chat("Tell me more"))
print("会话历史:", agent.history_messages)
```

## 🛠️ 命令行测试

```bash
# 基本测试
python question.py \
    --model gemma-4-E2B-it.litertlm \
    --skill-dir skills \
    "Test message"

# 交互式模式
python interactive.py \
    --model model.litertlm \
    --skill-dir skills

# 列出技能
python question.py \
    --model model.litertlm \
    --skill-dir skills \
    "--list-skills"
```

## 📁 SKILL.md 格式

```markdown
---
name: skill-name
description: Skill description here
metadata:
  require-secret: true
  require-secret-description: "API key description"
---

# Skill Name

## Instructions

This is the instruction content that guides the LLM's behavior.

## Behavior Rules

1. Rule 1
2. Rule 2
```

## 🔄 与 Gallery SKILL.md 对比

| 特性 | Gallery (Android) | Python text_adventure |
|------|-------------------|------------------|
| **SKILL.md 解析** | Kotlin 解析器 | Python Yaml 解析器 |
| **Prompt 注入** | 替换 `___SKILLS___` | 构建完整 system prompt |
| **技能加载** | 从 assets 或 URL | 从文件系统目录 |
| **执行方式** | JavaScript/Webview | 纯 Python + LLM推理 |
| **API 调用** | 支持 | 需自行实现工具调用 |

## 🧪 测试技能示例

`echo-text`:

```markdown
---
name: echo-test
description: Echo the input text back to the user.
---

# Echo Test Skill

## Instructions

Simply repeat back the user's input exactly.
```

## 🔧 核心功能

### SkillManager

- `load_skills(skill_dir)`: 加载目录下所有 SKILL.md
- `get_skill(name)`: 获取特定技能
- `get_skills_list()`: 返回注入格式的技能列表
- `get_skills_names()`: 返回技能名称列表

### text_adventure

- `chat(user_message)`: 单轮对话
- `chat_stream(user_message)`: 流式对话
- `list_skills()`: 列出所有技能
- `get_skill_info(skill_name)`: 获取技能详情
- `available_skills`: 属性返回技能名称列表

### PromptInjector

- `build_instrumented_prompt(skills)`: 构建带技能的 system prompt
- `inject_skill_instructions(skill, instructions)`: 注入特定技能指令

## 📝 扩展指南

### 添加新技能

1. 创建技能目录：`my-skill/`
2. 创建 SKILL.md，包含 frontmatter 和 instructions
3. 在 text_adventure 初始化时指定 `skill_dir`

### 自定义工具集成

参考 LiteRT-LM 的 `tools.py` 实现：

```python
from LiteRT_LM.python.litert_lm.interfaces import Tool

class MyTool(Tool):
    def get_tool_description(self) -> dict:
        return {
            "name": "my_tool",
            "description": "Does something useful"
        }

    def execute(self, param) -> Any:
        # 实现工具逻辑
        return result
```

## ⚠️ 注意事项

1. **模型文件**: 必须使用 `.litertlm` 格式
2. **路径分离**: 确保 `text_adventure/` 在 `LiteRT-LM/python/` 同级目录
3. **Python 版本**: Python 3.8+ 推荐使用 `Pillow` 和 `PyYAML`
4. **GPU 后端**: 需要相应硬件支持，默认使用 CPU

## 📄 许可证

本项目基于 Apache License 2.0 (与 LiteRT-LM 一致)。
