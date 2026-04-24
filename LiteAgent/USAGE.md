# LiteAgent 使用指南

## 📋 快速开始

### 基础使用

```python
from LiteAgent import LiteAgent
from litert_lm import Backend

# 创建 LiteAgent 实例
agent = LiteAgent(
    model_path="/path/to/model.litertlm",
    skill_dir="/path/to/skills",  # 可选
    backend=Backend.CPU,
    temperature=0.7
)

# 对话（多轮对话会自动累积上下文）
response1 = agent.chat("你好！你叫什么名字？")
print(response1)

response2 = agent.chat("告诉我关于你的信息")
print(response2)

response3 = agent.chat("你擅长什么？")
print(response3)

# 清理资源
agent.close()
```

## 🔍 修复说明

### 问题：LVM 不返回有效信息

**原因**：之前可能直接修改了 `conversation.messages` 列表，导致 Engine 内部的 KV cache 没有同步更新。

**修复**：现在通过 `send_message()` 让 Engine 自动管理上下文状态。

### ❌ 错误的用法（修复前）

```python
# 错误：直接修改 conversation.messages
response = conversation.send_message("Hello!")
conversation.messages = [
    {"role": "user", "content": "Hello!"},
    {"role": "assistant", "content": response}
]
response = conversation.send_message("What's next?")  # ❌ 这里可能失败，上下文不累积
```

### ✅ 正确的用法（修复后）

```python
# 正确：直接调用 send_message()，上下文会自动累积
response = conversation.send_message("Hello!")
response = conversation.send_message("What's next?")  # ✅ 上下文自动累积
response = conversation.send_message("Goodbye!")       # ✅ 继续累积
```

## 📚 详细 API

### LiteAgent.__init__

```python
def __init__(
    self,
    model_path: str,      # 必需：LiteRT-LM 模型文件路径 (.litertlm)
    skill_dir: str = "",  # 可选：SKILL.md 目录路径
    backend=Backend.CPU, # 可选：CPU 或 GPU
    temperature=0.7,      # 可选：采样温度
)
```

### LiteAgent.chat

```python
def chat(self, user_message: str) -> str:
    """
    发送消息并与 Agent 对话
    
    Args:
        user_message: 用户消息
        
    Returns:
        模型生成的文本响应
        
    Example:
        >>> agent = LiteAgent('/path/to/model.litertlm')
        >>> response1 = agent.chat("你好！你叫什么名字？")
        >>> response2 = agent.chat("告诉我关于你的信息")
        >>> response3 = agent.chat("你擅长什么？")
    """
```

### LiteAgent.close

```python
def close(self):
    """清理引擎和对话资源"""
```

### LiteAgent.start_new_conversation

```python
def start_new_conversation(self) -> AbstractConversation:
    """
    开始一个新的对话（会关闭当前对话）
    
    Returns:
        新的对话对象
    """
```

### LiteAgent.end_conversation

```python
def end_conversation(self):
    """结束当前对话"""
```

## 🎯 常见问题

### Q: 为什么多轮对话不累积？

A: 确保不要直接使用 `self._conversation.messages = [...]` 修改历史消息。使用 `agent.chat()` 方法会自动处理上下文累积。

### Q: 如何获取当前对话历史？

A: 可以读取 `self._conversation.messages` 只查看历史，但不要修改它。

### Q: 如何重新开始对话？

A: 调用 `agent.start_new_conversation()` 会关闭当前对话并开始新的对话。

## 🔧 LVM（LiteRT-LM Virtual Machine）说明

- **上下文状态保存位置**：在 `Engine` 对象内部（KV cache）
- **错误方式**：直接修改 `conversation.messages` 列表
- **正确方式**：通过 `conversation.send_message()` 让 Engine 自动管理
- **对话初始化参数**：`messages` 参数仅用于初始化，真正的状态在 Engine 内部

## 📖 相关文件

- 核心实现：`LiteAgent/agent.py`
- 技能管理：`LiteAgent/skill.py`
- API 文档：`LiteAgent/interfaces.py`, `LiteRT-LM/python/litert_lm/interfaces.py`
