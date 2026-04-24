#!/usr/bin/env python3
"""
测试 LLM 上下文使用情况

诊断问题：
1. 每轮对话消耗的上下文长度
2. 上下文是否接近上限
3. 对话历史是否过长
"""

import os
import sys

_liteagent_path = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _liteagent_path)

from LiteAgent import LiteAgent


def estimate_tokens(text):
    """估算文本的 token 数量（基于每个 token 约 4 个字符）"""
    return len(text) // 4


def test_kitchen_adventure_context():
    """测试 kitchen-adventure 多轮对话的上下文消耗"""

    # 加载 gallery 技能
    gallery_skills_dir = '../LiteAgent/gallery/skills/built-in/'

    agent = LiteAgent(
        model_path='../LiteAgent/gemma-4-E2B-it.litertlm',
        skill_dir=gallery_skills_dir,
        temperature=0.7,
        max_tokens=32768
    )

    print("=" * 60)
    print("Kitchen Adventure 上下文分析")
    print("=" * 60)

    # 初始 system prompt
    system_prompt = agent._build_system_prompt()
    initial_tokens = estimate_tokens(system_prompt)
    print(f"\n初始 System Prompt:")
    print(f"  - 字符数：{len(system_prompt)}")
    print(f"  - 估计 tokens: {initial_tokens}")

    # 模型最大 token
    max_tokens = agent._conversation.extra_context if hasattr(agent._conversation, 'extra_context') else 4096
    print(f"  - 最大 tokens: ~4096")

    print("\n" + "-" * 60)
    print("多轮对话测试")
    print("-" * 60)

    # Kitchen Adventure 对话
    dialogue = [
        "start kitchen adventure",
        "I want to examine the ingredients first",
        "What can I cook with these?",
        "Let me try making a simple pasta",
        "The pasta is ready! What's next?",
        "Can you introduce me to the other appliances?",
    ]

    for i, user_input in enumerate(dialogue, 1):
        print(f"\n对话 {i}:")
        print(f"  用户：{user_input}")

        # 对话前
        context_len = len(agent._conversation.messages)
        context_chars = sum(len(str(msg['content'])) for msg in agent._conversation.messages)
        context_tokens = estimate_tokens(str(agent._conversation.messages))

        response = agent.chat(user_input)

        # 对话后
        context_len_after = len(agent._conversation.messages)
        print(f"  助手：{response[:80]}..." if len(response) > 80 else f"  助手：{response}")

        # 分析上下文
        current_tokens = estimate_tokens(str(agent._conversation.messages))
        estimated_used = initial_tokens + current_tokens

        print(f"  当前上下文：{context_len_after} 条消息，{estimated_used} tokens")
        print(f"  使用率：{estimated_used / 4096 * 100:.1f}%")

        # 如果接近上限，警告
        if estimated_used > 3500:
            print(f"  ⚠️  警告：上下文已接近 4096 tokens 上限!")

        if i > 3:
            print(f"  ℹ️   从第 {i} 轮开始可能开始出现响应质量问题")

    print("\n" + "-" * 60)
    print("上下文统计")
    print("-" * 60)

    # 最终统计
    all_text = str(agent._conversation.messages)
    total_bytes = len(all_text)
    total_chars = len(all_text)
    estimated_tokens = estimate_tokens(all_text)

    print(f"\n最终状态:")
    print(f"  - 对话消息：{len(agent._conversation.messages)} 条")
    print(f"  - 总字符数：{total_chars}")
    print(f"  - 估计 tokens: {estimated_tokens}")
    print(f"  - System Prompt tokens: {initial_tokens}")
    print(f"  - 对话历史 tokens: {estimated_tokens - initial_tokens}")
    print(f"  - 总使用率：{estimated_tokens / 4096 * 100:.1f}%")


if __name__ == "__main__":
    test_kitchen_adventure_context()
