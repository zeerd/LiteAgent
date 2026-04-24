#!/usr/bin/env python3
"""最终完整测试"""

from litert_lm import Engine, Backend

model_path = '/home/node/.openclaw/workspace/LiteAgent/gemma-4-E2B-it.litertlm'

print("=" * 60)
print("完整测试 LiteAgent")
print("=" * 60)

# 创建 Engine
engine = Engine(model_path, backend=Backend.CPU)
print("\n✓ Engine 创建成功")

# 创建对话
messages = [{'role': 'system', 'content': 'You are a helpful assistant.'}]
conv = engine.create_conversation(messages=messages)
print("✓ 对话创建成功")

# 多轮对话测试
tests = [
    "Hello, what is 1+1?",
    "What language are you speaking?",
    "Goodbye!"
]

print("\n" + "=" * 60)
print("多轮对话测试")
print("=" * 60)

for i, user_input in enumerate(tests, 1):
    print(f"\n--- 对话 {i} ---")
    print(f"用户：{user_input}")
    result = conv.send_message(user_input)
    assistant_response = result['content'][0]['text']
    print(f"助手：{assistant_response}")

print("\n" + "=" * 60)
print("测试完成 - LiteAgent 引擎工作正常!")
print("=" * 60)
