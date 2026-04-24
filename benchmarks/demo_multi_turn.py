#!/usr/bin/env python3
"""
演示 LiteAgent 多轮对话

展示两种使用多轮交互的方式：
1. 通过 agent.chat() 方法 - 简洁
2. 通过 agent._conversation.send_message() - 更灵活
"""

from LiteAgent import LiteAgent

# 创建 Agent
agent = LiteAgent(
    model_path='../LiteAgent/gemma-4-E2B-it.litertlm',
    skill_dir='skills/',
    temperature=0.7
)

print("=" * 60)
print("LiteAgent 多轮对话演示")
print("=" * 60)

# 方式 1: 使用 agent.chat() - 最简单
print("\n【方式 1: 简单对话】")
print("-" * 40)

user_inputs = [
    "Hello! What's your name?",
    "How are you today?",
    "Nice to meet you!",
    "Goodbye!"
]

for user_input in user_inputs:
    print(f"\n您：{user_input}")
    response = agent.chat(user_input)
    print(f"助手：{response}")

# 方式 2: 直接访问 conversation 对象 - 更灵活
print("\n【方式 2: 直接访问 conversation 对象】")
print("-" * 40)
print("这种方式可以用于:")
print("- 查看对话历史")
print("- 插入自定义消息")
print("- 取消正在进行的生成")

# 查看对话历史
print(f"\n当前对话历史 (共{len(agent._conversation.messages)} 条消息):")
for i, msg in enumerate(agent._conversation.messages, 1):
    role = '用户' if msg['role'] in ['user', 'system'] else '助手'
    preview = msg['content'][:50] + "..." if len(msg['content']) > 50 else msg['content']
    print(f"  {i}. [{role}] {preview}")

print("\n" + "=" * 60)
print("✅ 演示完成!")
print("=" * 60)
