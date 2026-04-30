# text_adventure Python 实现规划

## 1. 背景理解

### Google AI Edge Gallery 的 SKILL 机制
Gallery 通过 `SKILL.md` 文件定义技能扩展，其核心机制为:
1. **目录结构**: 每个技能是一个文件夹，必须包含 `SKILL.md` 文件
2. **YAML Frontmatter**: 定义技能元信息 (name, description, metadata)
3. **Instructions**: 指令内容在 frontmatter 后，指导 LLM 如何使用技能
4. **Prompt 注入**: 所有技能的 name/description 被拼接到 system prompt 中
5. **执行方式**: 
   - 文本技能 - 仅通过 prompt 触发行为
   - JS 技能 - 通过 HTML 文件在 webview 中执行 JavaScript
   - 原生技能 - 调用原生应用功能

### LiteRT-LM Python 接口
LiteRT-LM 提供了 Python 接口:
- `AbstractEngine`: 抽象引擎类
- `AbstractConversation`: 对话管理
- `AbstractSession`: 会话管理
- `SamplerConfig`: 采样配置

## 2. 设计目标

实现一个 Python 的 `text_adventure` 框架，支持:
1. **SKILL.md 加载**: 从目录读取并解析 SKILL.md 文件
2. **技能注册**: 将技能与 LiteRT-LM 整合
3. **Prompt 注入**: 将技能描述自动注入系统 prompt
4. **测试脚本**: 能够使用模型 + SKILL + 用户提示词进行测试

## 3. 目录设计

```
text_adventure_Planner/
├── workplan.md                     # 本规划文档
├── design_doc.md                   # 详细设计文档
├── text_adventure/                       # 核心框架实现
│   ├── __init__.py
│   ├── agent.py                    # text_adventure 主类
│   ├── skill.py                    # SKILL.md 解析与技能管理
│   └── engine.py                   # LiteRT-LM 引擎封装
├── skills/                    # 测试技能示例
│   └── echo-skill/
│       └── SKILL.md
├── test_agent.py                   # 测试脚本
└── run_test.py                     # 运行测试的入口脚本
```

## 4. 核心组件设计

### 4.1 SKILL 解析器 (skill.py)

```python
class Skill:
    name: str                           # 技能名称
    description: str                    # 技能描述
    instructions: str                   # 指令内容
    metadata: dict                      # 元数据
    skill_dir: str                      # 技能目录路径

class SkillManager:
    def load_skills(skill_dir: str) -> List[Skill]
    def get_skill_descriptions() -> str   # 返回可注入的格式
```

### 4.2 text_adventure (agent.py)

```python
class text_adventure:
    def __init__(
        model_path: str,
        skill_dir: str,
        backend=Backend.CPU
    )
    
    def create_conversation(user_prompt: str) -> str
    def load_and_inject_skills():         # 解析 SKILL.md 并注射
```

### 4.3 Engine 封装 (engine.py)

基于 LiteRT-LM 接口封装基础对话功能。

## 5. 测试技能设计

最简单的 echo 技能:
```yaml
---
name: echo-skill
description: Echo the input text
---

# Echo Skill

## Instructions

When the user provides text, simply repeat it back.
```

## 6. 测试流程

1. 实例化 `text_adventure(model_path, skill_dir)`
2. 加载 SKILL.md
3. 注入 system prompt
4. 发送 user prompt
5. 获取 model 响应

## 7. 时间规划

1. **规划与设计** (已完成): 理解 Gallery SKILL 机制，制定设计方案
2. **核心代码实现**: skill.py + agent.py + engine.py
3. **测试技能**: 创建 echo-skill
4. **测试脚本**: 编写 test_agent.py
5. **验证测试**: 运行并确认功能

## 8. 断点恢复

如果工作中断，可从任意点继续:
- 检查已完成文件的 `ls text_adventure_Planner/text_adventure/`
- 未完成的模块可在同一文件继续编写
- 测试脚本支持增量开发 (先写基础功能再完善)
