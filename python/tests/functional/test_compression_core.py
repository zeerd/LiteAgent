#!/usr/bin/env python3
"""
text_adventure 功能测试脚本 - 上下文压缩核心功能测试

测试目的：
1. 将对话历史通过 AI 压缩为"世界状态快照"
2. 验证压缩结果包含关键信息
3. 输出压缩前后的统计对比
"""

import sys
import os
import json
from text_adventure import compression, text_adventure

MODEL_PATH = os.environ.get("LITERTLM_MODEL_PATH")
SKILL_PATH = os.environ.get("LITERTLM_SKILL_DIR", '')
PROJECT_ROOT = os.environ.get("PROJECT_ROOT", '')

CHAT_HISTORY_PATH = os.path.join(PROJECT_ROOT, 'tests', 'functional',
                                 'chat_history.json')

agent = text_adventure(
    model_path=MODEL_PATH,
    max_tokens=4096,
)


def load_chat_history():
    """加载聊天历史"""
    with open(CHAT_HISTORY_PATH, 'r', encoding='utf-8') as f:
        data = json.load(f)
    return data.get('chat_history', [])


def load_verification_queries():
    """加载聊天历史"""
    with open(CHAT_HISTORY_PATH, 'r', encoding='utf-8') as f:
        data = json.load(f)
    return data.get('verification_queries', [])


def format_history(chat_history):
    """格式化对话历史文本"""
    text = ""
    for msg in chat_history:
        text += f"User: {msg.get('content')}\n"
        text += f"AI: {msg.get('content')}\n\n"
    return text


def compress_with_ai(history_messages):
    """使用 AI 压缩文本"""
    zipper = compression.ContextCompressor(MODEL_PATH)
    result = zipper.compress_history(history_messages)
    print(f'压缩之后的文本：\n{result}')
    return result


def main():
    """主测试函数"""
    print("\n" + "=" * 70)
    print("text_adventure 功能测试 - 上下文压缩核心功能测试")
    print("=" * 70)
    print()

    # ========== 1. 加载数据 ==========
    print("[1/5] 加载测试数据")
    chat_history = load_chat_history()
    num_turns = len(chat_history)

    # 格式化原始对话
    original_text = format_history(chat_history)
    original_length = len(original_text)
    original_tokens = original_length // 4

    print("✅ 原始对话:")
    print(f"   - 轮数：{num_turns}")
    print(f"   - 字符数：{original_length}")
    print(f"   - Token 约：~{original_tokens}")
    print()

    # ========== 2. 执行压缩 ==========
    print("\n[2/5] 执行 AI 压缩")

    compressed = compress_with_ai(chat_history)
    compressed_length = len(compressed)
    compressed_tokens = compressed_length // 4

    if original_length > 0:
        ratio = (1 - compressed_length / original_length) * 100
    else:
        ratio = 0

    print("✅ 压缩结果:")
    print(f"   - 字符数：{compressed_length}/{original_length}")
    print(f"   - Token 约：~{compressed_tokens}")
    print(f"   - 压缩比：{ratio:.1f}%")
    print()
    print("✅ 压缩后的世界状态快照 (完整输出):")
    print("-" * 65)
    print(compressed)
    print("-" * 65)

    # ========== 4. 验证关键信息 ==========

    # 从 chat_history.json 读取验证查询
    queries = load_verification_queries()

    all_found = True
    for i, q in enumerate(queries):
        print(f"\n[{3+i}/5] 验证关键信息")
        name = q['name']
        keywords_pass = q.get('expected_pass', [])
        keywords_fail = q.get('expected_fail', [])
        found_pass = []
        found_fail = []

        messages = [
            {"role": "user", "content": compressed},
            {"role": "assistant", "content": 'Acknowledged.'}
        ]
        answer = agent.chat(
            q.get('question', '') + '\n返回纯文字，不要任何格式。',
            history_messages=messages
        )

        for kw in keywords_pass:
            if kw in answer:
                found_pass.append(kw)
        for kw in keywords_fail:
            if kw in answer:
                found_fail.append(kw)

        if found_pass or not found_fail:
            print(f"  ✅ {name}: 找到 → {found_pass} 没有找到 → {found_fail}")
        else:
            print(
                f"  ❌ {name}: 未找到关键信息 → {keywords_pass}，"
                f"却找到了错误信息 → {found_fail}"
            )
            all_found = False

    # ========== 总结 ==========
    print("\n" + "=" * 70)
    print("测试总结")
    print("=" * 70)
    print(f"压缩前：{original_length} 字符 ≈ {original_tokens} tokens")
    print(f"压缩后：{compressed_length} 字符 ≈ {compressed_tokens} tokens")
    print(f"压缩比：{ratio:.1f}%")
    print(f"关键信息保留：{'✅ 通过' if all_found else '❌ 失败'}")

    print("\n详细压缩结果请查看上面的世界状态快照输出。")

    return 0


if __name__ == "__main__":
    sys.exit(main())
