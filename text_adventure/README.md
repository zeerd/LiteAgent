# text_adventure 模块结构

**注意：本目录内严禁出现任何测试代码！**

## 文件组织

```
text_adventure/
├── agent.py           # text_adventure 核心类 - AI Agent 主逻辑
├── compression.py     # 上下文压缩模块 - 自动压缩和会话管理
├── skill/             # 技能系统模块
│   ├── __init__.py
│   ├── skill.py
│   ├── skill_manager.py
│   └── prompt_injector.py
└── __init__.py        # 公共接口导出
```

## 模块职责

### 1. **agent.py** - text_adventure 核心类

**职责**: AI Agent 的主逻辑实现，负责任务执行和人机交互。

**核心功能**:
- 创建和管理 Engine 会话
- 发送和接收消息
- 技能管理和注入
- 上下文压缩协调器（调用 compression 模块）

**类**:
- `text_adventure`: 主要 API 类

### 2. **compression.py** - 上下文自动压缩

**职责**: 独立于 Agent 的上下文长度管理和压缩逻辑。

**核心功能**:
- Token 估算和计数
- 压缩触发判断
- 对话历史压缩（总结）
- 会话重置和重建

**类**:
- `ContextCompressor`: 主要压缩类
  - `compress_context()`: 压缩对话历史
  - `reset_with_compressed_history()`: 重置会话
  - `get_stats()`: 获取统计信息

### 3. **skill/** - 技能系统

**职责**: 提供 Agent 的技能系统，包含各种可插拔的 AI 能力。

**类**:
- `Skill`: 技能定义
- `SkillManager`: 技能管理
- `PromptInjector`: Prompt 注入器

## API 使用

### 基础用法

```python
from text_adventure import text_adventure

# 初始化
agent = text_adventure(
    model_path="/path/to/model.litertlm",
    skill_dir="/path/to/skills",  # 可选
    max_tokens=10000,
    compression_threshold=0.75    # 0-1 之间，75% 触发压缩
)

# 对话
response = agent.chat("你好！")
print(response)

# 获取统计信息
stats = agent.get_context_stats()
print(f"当前使用率：{stats['current_usage']*100:.1f}%")
print(f"压缩次数：{stats['compression_count']}")

# 关闭
agent.close()
```

### 独立使用 ContextCompressor

```python
from text_adventure import ContextCompressor

# 创建压缩器
compressor = ContextCompressor(
    max_tokens=10000,
    compression_threshold=0.75
)

# 检查是否需要压缩
# compressor.should_compress()

# 压缩上下文
# compressed = compressor.compress_context(conversation)

# 重置会话
# new_conversation = compressor.reset_with_compressed_history(
#     old_conversation,
#     compressed,
#     system_prompt
# )

# 获取统计
stats = compressor.get_stats()
```

## 模块关系

```
text_adventure (agent.py)
    ├── SkillManager (skill/skill_manager.py)
    ├── PromptInjector (skill/prompt_injector.py)
    └── ContextCompressor (compression.py)
             ├── compress_context()
             └── reset_with_compressed_history()
```

- `text_adventure` 是主入口，包含所有功能
- `SkillManager` 和 `PromptInjector` 处理技能系统
- `ContextCompressor` 提供独立的重用压缩功能

## 重构好处

1. **职责分离**: 压缩逻辑独立，易于测试
2. **可重用性**: `ContextCompressor` 可用于其他项目
3. **更易维护**: 各模块职责单一，修改影响小
4. **可测试性**: 提供测试专用类，便于单元和集成测试

## 版本

- 当前版本：0.2.0
- 最后更新：2026-04-30
