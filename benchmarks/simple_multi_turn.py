#!/usr/bin/env python3
"""简单多轮对话示例"""

from LiteAgent import LiteAgent

agent = LiteAgent(
    model_path='../LiteAgent/gemma-4-E2B-it.litertlm',
    skill_dir='skills/',
    temperature=0.7
)

print("=== 简单多轮对话 ===")

# 第 1 轮
print("\n第 1 轮:")
print("您：重复我说的话：今天天气很好！")
response = agent.chat("重复我说的话：今天天气很好！")
print(f"助手：{response}")

# 第 2 轮
print("\n第 2 轮:")
print("您：你还有什么技能？")
response = agent.chat("你还有什么技能？")
print(f"助手：{response}")

# 第 3 轮
print("\n第 3 轮:")
print("您：1+1 等于多少？")
response = agent.chat("1+1 等于多少？")
print(f"助手：{response}")

# 第 4 轮
print("\n第 4 轮:")
print("您：再见！")
response = agent.chat("再见！")
print(f"助手：{response}")

print("\n=== 对话完成 ===")
print(f"对话历史共有 {len(agent._conversation.messages)} 条消息")
