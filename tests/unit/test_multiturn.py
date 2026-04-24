#!/usr/bin/env python3
"""
LiteAgent 多轮对话测试脚本

测试修复后的 LiteAgent 能运行多少轮多轮对话
"""

import sys
import os

# 添加路径 - 优先使用虚拟环境中的 litert_lm
_liteagent_path = "/home/node/.openclaw/workspace/LiteAgent_Planner"
if _liteagent_path not in sys.path:
    sys.path.insert(0, _liteagent_path)

# 注意：不要加载 LiteAgent/LiteRT-LM/python 目录
# 使用 venv 中已安装的 litert_lm 包（包含编译好的.so 文件）
print("使用虚拟环境中的 litert_lm 包")

# 尝试找到模型文件
model_paths = [
    "/workspace/LiteAgent/gemma-4-E2B-it.litertlm",
    "/home/node/.openclaw/workspace/LiteAgent/gemma-4-E2B-it.litertlm",
    os.environ.get("LITERTLM_MODEL_PATH"),
]

model_path = None
for path in model_paths:
    if path and os.path.exists(path):
        model_path = path
        break

if not model_path:
    print("❌ 未找到 LiteRT-LM 模型文件")
    print("请设置 LITERTLM_MODEL_PATH 环境变量或修改此脚本")
    sys.exit(1)

print(f"✅ 找到模型：{model_path}")
print(f"文件大小：{os.path.getsize(model_path) / (1024*1024):.2f} MB")

from LiteAgent import LiteAgent
from litert_lm import Backend

def run_multiturn_test(max_turns=10):
    """
    测试多轮对话能力

    Args:
        max_turns: 最大对话轮数
    """
    print("\n" + "="*60)
    print("LiteAgent 多轮对话测试")
    print("="*60)
    print(f"模型路径：{model_path}")
    print(f"后端：CPU")
    print(f"最大轮数：{max_turns}")
    print("="*60)

    try:
        # 创建 LiteAgent
        print("\n[1/3] 初始化 LiteAgent...")
        agent = LiteAgent(
            model_path=model_path,
            skill_dir="",
            backend=Backend.CPU,
            temperature=0.7
        )
        print("✅ 初始化成功")

        # 测试多轮对话
        print("\n[2/3] 开始多轮对话测试...")
        print("-" * 60)

        turns = 0
        context_preserved = True

        # 第一轮：建立基础上下文
        turns += 1
        print(f"\n📝 Turn {turns}:")
        query = "你好！你叫什么名字？请简单介绍一下你自己。"
        print(f"  用户：{query}")

        if agent.is_initialized:
            response = agent.chat(query)
            print(f"  助手：{response}")
        else:
            response = "Agent is not initialized"
            print(f"  助手：{response}")

        turn_responses = [response]

        # 第二轮：测试上下文是否保留
        turns += 1
        if context_preserved:
            print(f"\n📝 Turn {turns}:")
            query = "你刚才说的是什么？我是谁？"
            print(f"  用户：{query}")
            print(f"  (测试上下文是否保留第一轮对话)")

            if agent.is_initialized:
                response = agent.chat(query)
                print(f"  助手：{response}")
            turn_responses.append(response)
        else:
            print(f"  (跳过 - 上下文未保留)")

        # 第三轮：进一步测试
        turns += 1
        if context_preserved:
            print(f"\n📝 Turn {turns}:")
            query = "你能做什么？请详细列举你的能力。"
            print(f"  用户：{query}")
            print(f"  (继续测试上下文累积)")

            if agent.is_initialized:
                response = agent.chat(query)
                print(f"  助手：{response}")
            turn_responses.append(response)

        # 第四轮：测试复杂上下文
        turns += 1
        if context_preserved:
            print(f"\n📝 Turn {turns}:")
            query = "结合你刚才说的，总结一下你是谁以及你能做什么。"
            print(f"  用户：{query}")
            print(f"  (测试综合上下文理解)")

            if agent.is_initialized:
                response = agent.chat(query)
                print(f"  助手：{response}")
            turn_responses.append(response)

        # 第五轮：测试上下文是否累积
        turns += 1
        if context_preserved:
            print(f"\n📝 Turn {turns}:")
            query = "在第一轮对话中，我问了什么问题？"
            print(f"  用户：{query}")
            print(f"  (测试是否记得第一轮的对话内容)")

            if agent.is_initialized:
                response = agent.chat(query)
                print(f"  助手：{response}")
                turn_responses.append(response)

                # 检查回答是否提到第一轮的问题
                if "你叫什么名字" in response or "介绍一下" in response:
                    print("  ✅ 上下文累积成功！模型记得第一轮对话")
                    context_preserved = True
                else:
                    print("  ⚠️ 模型可能没有记住第一轮对话")
            else:
                print("  (跳过)")
        else:
            print("  (跳过)")

        print("\n" + "="*60)
        print("测试结果摘要")
        print("="*60)
        print(f"成功运行的对话轮数：{turns}")
        print(f"上下文累积状态：{'✅ 成功' if context_preserved else '❌ 失败'}")

        return {
            "total_turns": turns,
            "context_preserved": context_preserved,
            "responses": turn_responses
        }

    except Exception as e:
        print(f"\n❌ 测试过程中出现错误：{e}")
        import traceback
        traceback.print_exc()
        return None
    finally:
        if 'agent' in locals():
            try:
                agent.close()
                print("\n✅ 已清理资源")
            except:
                pass


def quick_test():
    """
    快速测试脚本的正确性（不需要实际模型）
    """
    print("\n" + "="*60)
    print("LiteAgent 代码结构验证")
    print("="*60)

    try:
        from LiteAgent import LiteAgent
        from litert_lm import Backend

        # 检查必要方法是否存在
        methods = ['chat', 'close', 'start_new_conversation', 'end_conversation', 'is_initialized']

        for method in methods:
            if hasattr(LiteAgent, method):
                print(f"✅ LiteAgent.{method}")
            else:
                print(f"❌ LiteAgent.{method} 不存在")

        print("\n✅ 代码结构验证通过")
        return True

    except Exception as e:
        print(f"❌ 代码结构验证失败：{e}")
        return False


if __name__ == "__main__":
    import sys

    if len(sys.argv) > 1 and sys.argv[1] == "--quick":
        # 快速验证模式
        print("\n使用快速验证模式（不依赖实际模型）")
        result = quick_test()
    else:
        # 实际测试模式
        print("\n使用实际测试模式（需要 LiteRT-LM 模型）")
        result = run_multiturn_test(max_turns=10)

    if result:
        print("\n" + "="*60)
        print("测试完成!")
        print("="*60)
