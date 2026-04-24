#!/usr/bin/env python3
"""
LiteAgent 基准测试脚本

用于快速测试各种 LiteAgent 功能。
"""

import sys
import os
import argparse

_liteagent_path = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _liteagent_path)

from LiteAgent import LiteAgent


def main():
    parser = argparse.ArgumentParser(description="LiteAgent 基准测试工具")
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
        help="采样温度"
    )

    args = parser.parse_args()

    print("=" * 60)
    print("LiteAgent 基准测试")
    print("=" * 60)

    # 测试 1: 加载技能
    print("\n[1/3] 加载技能...")
    try:
        agent = LiteAgent(
            model_path=args.model,
            skill_dir=args.skill_dir,
            temperature=args.temperature
        )
        print(f"✓ 成功加载技能：{agent.available_skills}")
    except Exception as e:
        print(f"✗ 加载失败：{e}")
        return

    # 测试 2: 简单对话
    print("\n[2/3] 简单对话...")
    try:
        response = agent.chat("Hello, world!")
        print(f"✓ 响应：{response}")
    except Exception as e:
        print(f"✗ 对话失败：{e}")

    # 测试 3: 技能调用
    print("\n[3/3] 测试技能调用...")
    try:
        response = agent.chat("重复：测试完成")
        print(f"✓ 技能响应：{response}")
    except Exception as e:
        print(f"✗ 技能调用失败：{e}")

    print("\n" + "=" * 60)
    print("✅ 基准测试完成!")
    print("=" * 60)


if __name__ == "__main__":
    main()
