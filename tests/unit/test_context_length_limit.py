#!/usr/bin/env python3
"""
LiteAgent 上下文长度上限测试

测试 LiteAgent 在最大 token 限制下，能承载多少长度的上下文对话历史。

核心目标：找到实际的最大 token 数，而不是能运行多少轮。
"""

import os
import sys
import argparse

workspace_path = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if workspace_path not in sys.path:
    sys.path.insert(0, workspace_path)

from LiteAgent import LiteAgent


def estimate_tokens(text: str) -> int:
    """估算 token 数 (每 4 字符 ≈ 1 token)"""
    return len(text) // 4


def test_context_length_limit(model_path: str, max_tokens: int, test_target_tokens: int = 32000) -> dict:
    """
    测试不同 max_tokens 设置下的实际上下文容量

    Args:
        model_path: 模型文件路径
        max_tokens: 最大 token 数限制 (None 使用模型默认值)
        test_target_tokens: 目标测试长度，我们会尝试接近这个长度

    Returns:
        测试结果字典
    """
    print("=" * 70)
    print("LiteAgent 上下文长度上限测试")
    print("=" * 70)
    print(f"目标测试 token 数：{test_target_tokens}")
    print(f"配置 max_tokens: {max_tokens if max_tokens else 'None (使用模型默认)'}")
    print()

    agent = LiteAgent(
        model_path=model_path,
        temperature=0.7,
        max_tokens=max_tokens
    )

    system_tokens = estimate_tokens("你是一个乐于助人的 AI 助手。")
    print(f"[初始化]")
    print(f"  - System prompt: {system_tokens} tokens")
    if max_tokens:
        print(f"  - max_tokens: {max_tokens}")
        print(f"  - 剩余 tokens: {max_tokens - system_tokens}")
    else:
        print(f"  - max_tokens: 无限制")
    print()

    # 构造长文本输入
    # 每段中文大约 3-4 字符 ≈ 1 token
    test_text = "这是一个测试文本。" * 500  # 约 1500 字符 ≈ 375 tokens

    current_messages = []
    accumulated_tokens = 0

    print(f"[开始累积上下文]")
    print(f"目标：累积约 {test_target_tokens} tokens\n")
    print("-" * 70)

    for turn in range(1, 1000):  # 最多 1000 轮
        user_query = f"第{turn}轮" + (test_text if turn > 1 else "")
        user_tokens = estimate_tokens(user_query)

        # 尝试发送消息
        try:
            response = agent.chat(user_query)
            response_tokens = estimate_tokens(response)

            accumulated_tokens += user_tokens + response_tokens

            # 检查是否接近目标
            use_rate = (accumulated_tokens / test_target_tokens * 100) if test_target_tokens else None

            if turn % 50 == 1 or use_rate and (use_rate > 50 and use_rate % 20 < 5) or turn > 900:
                print(f"Turn {turn:4d}: 累计 tokens = {accumulated_tokens:6d} "
                      f"(使用率：{use_rate:.1f}%)" if use_rate else f"Turn {turn:4d}: 累计 tokens = {accumulated_tokens}")
                sys.stdout.flush()

            # 超过目标 10% 就停止
            if use_rate and use_rate > 110:
                print(f"\n✅ 超过目标 110%，停止测试")
                break

        except Exception as e:
            print(f"\n❌ Turn {turn} 失败：{e}")
            break

    print()
    print("=" * 70)
    print("测试结果")
    print("=" * 70)
    print(f"成功完成轮数：{turn}")
    print(f"最终 tokens: {accumulated_tokens}")
    if test_target_tokens:
        print(f"目标 tokens: {test_target_tokens}")
        print(f"超出目标：{(accumulated_tokens / test_target_tokens - 1) * 100:.1f}%")

    # 估算实际容量
    if test_target_tokens:
        success_threshold = 100  # 100% 完成
        if accumulated_tokens >= test_target_tokens:
            print(f"\n✅ 成功达到目标：{test_target_tokens} tokens")
            # 假设每轮平均增加约 400 tokens (200 用户+200 响应)
            avg_tokens_per_turn = accumulated_tokens / turn
            estimated_max_turns = test_target_tokens / avg_tokens_per_turn
            print(f"平均每轮消耗：{avg_tokens_per_turn:.1f} tokens")
            print(f"理论最大轮数：~{estimated_max_turns:.0f} 轮")
        else:
            print(f"\n⚠️  未完全达到目标")

    print("\n" + "=" * 70)
    print("测试完成")
    print("=" * 70)

    return {
        'total_turns': turn,
        'final_tokens': accumulated_tokens,
        'target_tokens': test_target_tokens,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="测试 LiteAgent 上下文长度上限",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter
    )
    parser.add_argument(
        "--model",
        type=str,
        default="/home/node/.openclaw/workspace/LiteAgent/gemma-4-E2B-it.litertlm",
        help="模型文件路径"
    )
    parser.add_argument(
        "--max-tokens",
        type=int,
        default=None,
        help="最大 token 数 (None 使用模型默认值)"
    )
    parser.add_argument(
        "--target",
        type=int,
        default=32000,
        help="目标测试 token 数"
    )

    args = parser.parse_args()

    result = test_context_length_limit(
        model_path=args.model,
        max_tokens=args.max_tokens,
        test_target_tokens=args.target
    )
