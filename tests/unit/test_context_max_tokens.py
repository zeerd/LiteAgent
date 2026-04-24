#!/usr/bin/env python3
"""
LiteAgent 上下文 Max Tokens 测试

测试 LiteAgent 在不同 max_tokens 配置下的多轮对话能力。

用法:
    python3 tests/test_context_max_tokens.py --max-tokens 32768

    可以测试不同的 max_tokens 值来找到真正的上限
"""

import os
import sys
import argparse

workspace_path = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if workspace_path not in sys.path:
    sys.path.insert(0, workspace_path)

from LiteAgent import LiteAgent


def estimate_tokens(text: str) -> int:
    """估算文本的 token 数量（基于每个 token 约 4 个字符）"""
    return len(text) // 4


def test_context_capacity(model_path: str, max_tokens: int, max_turns: int = 30, test_steps: int = 5) -> dict:
    """测试 LiteAgent 在多轮对话中的上下文容量"""
    print("=" * 70)
    print("LiteAgent 上下文 Max Tokens 测试")
    print("=" * 70)

    agent = LiteAgent(
        model_path=model_path,
        skill_dir="",
        temperature=0.7,
        max_tokens=max_tokens
    )

    system_prompt = agent._build_system_prompt()
    initial_tokens = estimate_tokens(system_prompt)

    print(f"\n[初始化]")
    print(f"  - System Prompt: {len(system_prompt)} chars, {initial_tokens} tokens")
    if max_tokens:
        print(f"  - 最大 tokens: {max_tokens}")
    else:
        print(f"  - 最大 tokens: None (使用模型默认值)")
    print(f"  - 可用 tokens: {max_tokens - initial_tokens if max_tokens else '动态'}")

    questions = []
    for i in range(1, max_turns + 1):
        question_map = {
            1: "你好！我们开始聊天。",
            2: "还记得我吗？",
            3: "我们聊了多少轮了？",
            4: "你还能记住什么？",
            5: "我们刚开始聊了什么？",
            6: "现在聊到什么程度了？",
            7: "继续聊天吧。",
            8: "你还能正常思考吗？",
            9: "我们聊到哪儿了？",
            10: "还记得开头聊什么吗？",
            11: "你的理解能力怎么样？",
            12: "之前说过什么？",
            13: "我们聊过哪些话题？",
            14: "能记住所有对话吗？",
            15: "聊了很久吗？",
            16: "记忆还清晰吗？",
            17: "开始聊什么了？",
            18: "聊了什么？",
            19: "还记得我和说过什么？",
            20: "总结一下我们的对话。",
        }
        questions.append(question_map.get(i, f"第{i}轮问题"))

    token_records = []
    error_turn = None

    print("\n[开始测试]")
    print("-" * 70)

    for turn in range(1, max_turns + 1):
        query = questions[turn - 1]
        all_messages = str(agent._conversation.messages)
        tokens_before = estimate_tokens(all_messages)

        try:
            response = agent.chat(query)
            all_messages_after = str(agent._conversation.messages)
            tokens_after = estimate_tokens(all_messages_after)
            tokens_consumed = tokens_after - tokens_before
            total_tokens = tokens_after + initial_tokens

            token_records.append({
                'turn': turn,
                'query': query,
                'response_len': len(response),
                'tokens_consumed': tokens_consumed,
                'tokens_total': total_tokens,
            })

            if turn % test_steps == 0 or turn <= 5:
                use_rate = (total_tokens / max_tokens * 100) if max_tokens else '?'
                print(f"\n第 {turn:2d} 轮：{query[:25]}...")
                print(f"  - 对话历史：{tokens_after} tokens")
                print(f"  - 本轮消耗：{tokens_consumed} tokens")
                print(f"  - 总 tokens: {total_tokens} (使用率：{use_rate}%)")

        except Exception as e:
            print(f"\n❌ 第 {turn} 轮发生异常：{e}")
            error_turn = turn
            break

    print("\n" + "=" * 70)
    print("测试完成 - 上下文统计")
    print("=" * 70)

    if token_records:
        total_turns = len(token_records)
        avg_tokens_per_turn = sum(r['tokens_consumed'] for r in token_records) / total_turns
        max_tokens_reached = max(r['tokens_total'] for r in token_records)

        print(f"\n成功完成：{total_turns} 轮对话")
        print(f"每轮平均消耗：{avg_tokens_per_turn:.1f} tokens")

        if error_turn:
            print(f"在第 {error_turn} 轮时发生错误")

        if avg_tokens_per_turn > 0 and max_tokens:
            estimated_max_turns = (max_tokens - initial_tokens) / avg_tokens_per_turn
            print(f"\n理论最大轮数预估：~{estimated_max_turns:.0f} 轮 (基于当前 max_tokens={max_tokens})")

        token_records.sort(key=lambda x: x['tokens_total'], reverse=True)
        print(f"\n前 10 轮详情 (按消耗排序):")
        for record in token_records[:10]:
            print(f"  Turn {record['turn']:2d}: {record['tokens_consumed']} tokens, total: {record['tokens_total']} tokens")

        return {
            'total_turns': total_turns,
            'avg_consumption': avg_tokens_per_turn,
            'max_tokens_reached': max_tokens_reached,
            'error_turn': error_turn,
        }

    return {
        'total_turns': 0,
        'avg_consumption': 0,
        'max_tokens_reached': 0,
        'error_turn': error_turn or max_turns,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="测试 LiteAgent 上下文 Max Tokens",
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
        "--turns",
        type=int,
        default=30,
        help="最大测试轮数"
    )
    parser.add_argument(
        "--steps",
        type=int,
        default=5,
        help="每多少轮输出一次统计"
    )

    args = parser.parse_args()

    # 运行测试
    result = test_context_capacity(
        model_path=args.model,
        max_tokens=args.max_tokens,
        max_turns=args.turns,
        test_steps=args.steps
    )

    print("\n" + "=" * 70)
    print("测试完成!")
    print("=" * 70)
