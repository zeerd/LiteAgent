#!/usr/bin/env python3
"""
LiteAgent 交互式 Shell

使用方法:
    python interactive.py --model /path/to/model.litertlm --skill-dir /path/to/skills
"""

import sys
import os
import json
import argparse

_liteagent_path = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _liteagent_path)

from LiteAgent import LiteAgent


def create_interactive_shell(agent):
    """创建交互式对话 Shell"""
    print("=" * 60)
    print("LiteAgent Interactive Shell")
    print("=" * 60)
    print(f"模型：{agent.model_path}")
    print(f"可用技能：{agent.available_skills}")
    print(f"提示符：输入 'quit' 或 'exit' 退出，'history' 查看历史")
    print("=" * 60)

    history = []  # 存储对话历史

    while True:
        try:
            # 获取用户输入
            user_input = input("\n您：").strip()

            if not user_input:
                continue

            if user_input.lower() in ('quit', 'exit', 'q'):
                print("再见！")
                break

            if user_input.lower() == 'history':
                if not history:
                    print("暂无历史记录")
                else:
                    print("\n--- 对话历史 ---")
                    BLUE = '\033[94m'
                    YELLOW = '\033[93m'
                    END = '\033[0m'
                    for i, (inp, out) in enumerate(history, 1):
                        print(f"\n{i}. {BLUE}您：{inp}{END}")
                        print(type(out))
                        out = json.loads(out) if isinstance(out, str) else out
                        print(f"   {YELLOW}助手：{out.get('text', out)}{END}")
                continue

            if user_input.lower() == 'clear':
                history = []
                print("对话历史已清")
                continue

            # 发送消息
            if user_input.startswith('/skill '):
                # 查询技能信息
                skill_name = user_input[7:].strip()
                info = agent.get_skill_info(skill_name)
                if info:
                    print(f"\n技能：{info['name']}")
                    print(f"描述：{info['description']}")
                    print(f"指令:\n{info['instructions']}")
                else:
                    print(f"未找到技能：{skill_name}")
                continue

            if user_input.startswith('/list'):
                agent.list_skills()
                continue

            # 发送消息获取响应
            response = agent.chat(user_input)

            # 显示响应
            print(f"助手：{response}")

            # 保存到历史
            history.append((user_input, response))

        except EOFError:
            print("\n再见！")
            break
        except KeyboardInterrupt:
            print("\n再见！")
            break
        except Exception as e:
            print(f"错误：{e}")


def main():
    parser = argparse.ArgumentParser(description="LiteAgent 交互式 Shell")
    parser.add_argument(
        "--model",
        type=str,
        default="../LiteAgent/gemma-4-E2B-it.litertlm",
        help="LiteRT-LM 模型文件路径"
    )
    parser.add_argument(
        "--skill-dir",
        type=str,
        default="skills/",
        help="SKILL.md 目录路径"
    )
    parser.add_argument(
        "--temperature",
        type=float,
        default=0.7,
        help="采样温度 (0.0 最确定，1.0 最随机)"
    )

    args = parser.parse_args()

    # 创建 Agent
    agent = LiteAgent(
        model_path=args.model,
        skill_dir=args.skill_dir,
        temperature=args.temperature
    )

    # 启动交互式 Shell
    create_interactive_shell(agent)


if __name__ == "__main__":
    main()
