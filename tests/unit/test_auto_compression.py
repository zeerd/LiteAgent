#!/usr/bin/env python3
"""
LiteAgent 上下文自动压缩测试

测试压缩功能：
1. 创建 Agent，设置压缩阈值
2. 发送多条消息，触发压缩
3. 验证压缩后上下文正常继续
"""

import sys
import os

_liteagent_path = "/home/node/.openclaw/workspace/LiteAgent_Planner"
if _liteagent_path not in sys.path:
    sys.path.insert(0, _liteagent_path)

from LiteAgent import LiteAgent


def test_auto_compression():
    """测试自动上下文压缩功能"""

    print("=" * 70)
    print("LiteAgent 上下文自动压缩测试")
    print("=" * 70)

    # 1. 测试 A：压缩阈值设为 50%，快速触发压缩
    print("\n['测试 A'] 压缩阈值=50%，测试自动压缩触发")
    print("-" * 70)

    test_text = "这是一个测试文本。" * 500  # 约 375 tokens

    try:
        agent = LiteAgent(
            model_path="/home/node/.openclaw/workspace/LiteAgent/gemma-4-E2B-it.litertlm",
            temperature=0.7,
            max_tokens=1000,  # 设置较小限制
            compression_threshold=0.5  # 50% 触发压缩
        )

        for i in range(1, 20):
            response = agent.chat(f"第{i}轮" + test_text)

            # 打印统计
            stats = agent.get_context_stats()
            if i % 5 == 0 or stats["compression_count"] > 0:
                print(f"轮次 {i}: 使用率 {stats['current_usage']:.2%}, "
                      f"压缩次数 {stats['compression_count']}")

        print(f"\n✅ 测试完成 - 最终压缩次数：{agent.get_context_stats()['compression_count']}")

    except Exception as e:
        print(f"\n❌ 测试 A 失败：{e}")
        import traceback
        traceback.print_exc()
        return False

    # 2. 测试 B：验证压缩后上下文是否正常工作
    print("\n['测试 B'] 验证压缩后对话连续性")
    print("-" * 70)

    try:
        agent2 = LiteAgent(
            model_path="/home/node/.openclaw/workspace/LiteAgent/gemma-4-E2B-it.litertlm",
            temperature=0.7,
            max_tokens=2000,
            compression_threshold=0.7
        )

        # 发送一些普通问题
        for i in range(1, 10):
            if i <= 5:
                agent2.chat(f"第{i}个问题：你是谁？")
            else:
                agent2.chat(f"第{i}个问题：你能记住之前的对话吗？")

        # 检查是否触发了压缩
        stats = agent2.get_context_stats()
        print(f"对话轮次：~{stats['conversation_turns']}")
        print(f"压缩次数：{stats['compression_count']}")

        # 询问模型是否记得压缩前的对话
        last_response = agent2.chat("你记得我们之前聊了什么吗？请总结一下。")
        print(f"\n总结询问：{last_response[:300]}...")

        print("\n✅ 测试 B 完成")

    except Exception as e:
        print(f"\n❌ 测试 B 失败：{e}")
        import traceback
        traceback.print_exc()
        return False

    print("\n" + "=" * 70)
    print("所有测试完成!")
    print("=" * 70)
    return True


if __name__ == "__main__":
    success = test_auto_compression()
    sys.exit(0 if success else 1)
