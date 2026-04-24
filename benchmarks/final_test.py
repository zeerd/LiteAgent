#!/usr/bin/env python3
"""LiteAgent 最终测试"""

from litert_lm import Engine, Backend

model_path = '/home/node/.openclaw/workspace/LiteAgent/gemma-4-E2B-it.litertlm'

print("=" * 60)
print("LiteAgent 完整测试")
print("=" * 60)

# 创建 Engine
engine = Engine(model_path, backend=Backend.CPU)
print("\n✓ Engine 创建成功")

# 创建对话（使用关键字参数）
messages = [{'role': 'system', 'content': 'You are a helpful assistant.'}]
conv = engine.create_conversation(messages=messages)
print("✓ 对话创建成功")

# 测试对话
print("\n" + "=" * 60)
print("对话测试")
print("=" * 60)

tests = [
    "What is 1+1?",
    "What language are you speaking?",
    "Goodbye"
]

for user_input in tests:
    print(f"\n用户：{user_input}")
    r = conv.send_message(user_input)
    assistant_response = r['content'][0]['text']
    print(f"助手：{assistant_response}")
    print("-" * 40)

print("=" * 60)
print("✓ 测试完成 - LiteAgent 引擎工作正常!")
print("=" * 60)
