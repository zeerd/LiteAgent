#!/usr/bin/env python3
"""调试测试：查看 Conversation 返回的数据结构"""

from litert_lm import Engine, Backend, Conversation

model_path = '/home/node/.openclaw/workspace/LiteAgent/gemma-4-E2B-it.litertlm'

engine = Engine(model_path, backend=Backend.CPU)
messages = [{'role': 'system', 'content': 'You are a helpful assistant.'}]
conv = engine.create_conversation(messages=messages)

print("\n=== 探索对话 API ===\n")

# 探索 send_message 的返回值
user_input = "Hello"
result = conv.send_message(user_input)
print(f"返回类型：{type(result)}")
print(f"返回内容：{result}")
print(f"返回键：{result.keys() if isinstance(result, dict) else 'Not a dict'}")

# 查看对话状态
print(f"\n当前消息数：{len(conv.messages)}")
print("\n最后一条消息：")
if conv.messages:
    last_msg = conv.messages[-1]
    print(f"  类型：{type(last_msg)}")
    print(f"  内容：{last_msg}")
