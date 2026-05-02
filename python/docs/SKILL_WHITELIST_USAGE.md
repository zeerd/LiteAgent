# Skill Whitelist Usage 使用文档

## 概述

`allowed_skill_list` 参数允许你限制只加载指定的技能，而不是加载技能目录中的所有技能。这对于：
- **安全性**：只允许使用经过审核的技能
- **性能**：减少不需要的技能加载
- **控制**：限制 AI 只能使用特定功能

## API 签名

```python
from text_adventure import text_adventure

agent = text_adventure(
    model_path="./path/to/model.litertlm",
    skill_dir="./path/to/skills",
    allowed_skill_list=["skill_name_1", "skill_name_2"],  # 可选
)
```

## 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `model_path` | `str` | 必填 | LiteRT-LM 模型文件路径 |
| `skill_dir` | `str` | `""` | SKILL.md 目录路径 |
| `allowed_skill_list` | `list` | `None` | 允许加载的技能名称列表 |

## 使用示例

### 示例 1: 只使用文字冒险技能

```python
from text_adventure import text_adventure

# 只加载 text-adventure 技能
agent = text_adventure(
    model_path="./models/liteRT-LM.litertlm",
    skill_dir="./skills",
    allowed_skill_list=["text-adventure"]
)

# 现在 AI 只能使用文字冒险技能
response = agent.chat("我们来玩文字冒险游戏！")
```

### 示例 2: 多个技能的白名单

```python
# 只允许 echo 和 text-adventure 两个技能
agent = text_adventure(
    model_path="./models/liteRT-LM.litertlm",
    skill_dir="./skills",
    allowed_skill_list=["echo-text", "text-adventure"]
)
```

### 示例 3: 空列表（不加载任何技能）

```python
# 空名单表示不加载任何技能
agent = text_adventure(
    model_path="./models/liteRT-LM.litertlm",
    skill_dir="./skills",
    allowed_skill_list=[]
)

# 等价于：
# agent = text_adventure(
#     model_path="./models/liteRT-LM.litertlm",
#     skill_dir="./skills",
#     allowed_skill_list=None  # 或者不传这个参数
# )
```

### 示例 4: 不存在的技能

如果指定的技能不存在，日志会显示跳过：

```
⏭️  跳过 (不在白名单): existing_skill
❌ load_skill 工具调用失败：技能 'nonexistent' 不存在
```

## 日志输出

启用日志后，你会看到：

```
✅ 已加载 (白名单匹配): text-adventure      # 加载成功
⏭️  跳过 (不在白名单): other-skill          # 跳过不在白名单的技能
✅ 已加载：text-adventure                     # 无白名单时全部加载
```

## 使用场景

### 1. 安全限制

在生产环境中，你可能只想允许某些特定的技能：

```python
ALLOWED_SKILLS = ["safe-skill-1", "safe-skill-2"]
agent = text_adventure(..., allowed_skill_list=ALLOWED_SKILLS)
```

### 2. 功能模块

为不同功能加载不同的技能子集：

```python
# 文字冒险模式
adventure_agent = text_adventure(
    ...,
    allowed_skill_list=["text-adventure"]
)

# Echo 模式
echo_agent = text_adventure(
    ...,
    allowed_skill_list=["echo-text"]
)
```

### 3. 开发调试

临时限制可用技能以测试特定功能：

```python
# 只测试 text-adventure 技能
agent = text_adventure(
    ...,
    allowed_skill_list=["text-adventure"]
)
```

## 注意事项

1. **技能名称必须完全匹配**：白名单中的技能名称必须与 `SKILL.md` 文件中的 `name:` 字段完全一致（区分大小写）

2. **空列表 vs None**：
   - `allowed_skill_list=[]` 等价于 `allowed_skill_list=None`，都意味着加载所有技能
   - 真正要限制技能时，必须传入非空列表

3. **不存在的技能**：如果白名单中的技能名不存在，只会生成警告日志，不会抛出异常

4. **日志配置**：确保你的日志配置能够显示 INFO 级别的消息，以便看到哪些技能被加载/跳过

## 相关文档

- [text_adventure/README.md](../README.md) - 主项目文档
- [text_adventure/skill.py](../text_adventure/skill.py) - 技能管理源码
- [text_adventure/agent.py](../text_adventure/agent.py) - Agent 实现
