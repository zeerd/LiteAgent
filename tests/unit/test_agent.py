#!/usr/bin/env python3
"""
LiteAgent 功能测试脚本

测试目标:
1. 验证 SKILL.md 解析功能
2. 验证技能注入 system prompt
3. 验证与 LiteRT-LM 的对话功能
"""

import os
import sys
import argparse

_liteagent_path = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _liteagent_path)

from LiteAgent import LiteAgent
from LiteAgent.skill import SkillManager


def parse_args():
    """解析命令行参数"""
    parser = argparse.ArgumentParser(
        description="测试 LiteAgent 与 LiteRT-LM 的集成"
    )
    parser.add_argument(
        "--model",
        type=str,
        required=True,
        help="LiteRT-LM 模型文件路径 (.litertlm)"
    )
    parser.add_argument(
        "--skill-dir",
        type=str,
        default="",
        help="技能目录路径"
    )
    parser.add_argument(
        "--temperature",
        type=float,
        default=0.7,
        help="采样温度 (0.0-1.0)"
    )
    parser.add_argument(
        "--prompt",
        type=str,
        default="Hello, test!",
        help="测试消息"
    )
    parser.add_argument(
        "--list-skills",
        action="store_true",
        help="列出所有技能"
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="详细输出"
    )
    return parser.parse_args()


def test_skill_parsing(skill_dir, verbose=False):
    """测试 SKILL.md 解析"""
    print("\n" + "="*60)
    print("测试 1: SKILL.md 解析验证")
    print("="*60)

    try:
        manager = SkillManager(skill_dir)
        skills = manager.get_all_skills()

        if not skills:
            print("提示：未找到任何 SKILL.md 文件")
            return True

        print(f"✓ 成功加载 {len(skills)} 个技能:")
        for skill in skills:
            print(f"\n  名称：{skill.name}")
            print(f"  描述：{skill.description}")
            print(f"  指令长度：{len(skill.instructions)} 字符")

        return True

    except Exception as e:
        print(f"\n✗ SKILL.md 解析失败：{e}")
        return False


def test_agent_init(args):
    """测试 Agent 初始化"""
    print("\n" + "="*60)
    print("测试 2: LiteAgent 初始化")
    print("="*60)

    try:
        agent = LiteAgent(
            model_path=args.model,
            skill_dir=args.skill_dir,
            temperature=args.temperature
        )
        print(f"✓ LiteAgent 初始化成功")
        print(f"  模型：{agent.model_path}")
        if args.skill_dir:
            print(f"  可用技能：{agent.available_skills}")
        return agent

    except Exception as e:
        print(f"\n✗ 初始化失败：{e}")
        sys.exit(1)


def test_conversation(agent, prompt):
    """测试对话功能"""
    print("\n" + "="*60)
    print("测试 3: 对话功能验证")
    print("="*60)

    print(f"\n发送测试消息：'{prompt}'")
    print("-"*40)

    try:
        response = agent.chat(prompt)
        print(f"\n✓ 模型响应:")
        print(f"  {response}")
        return response

    except Exception as e:
        print(f"\n✗ 对话失败：{e}")
        return None


def main():
    """主测试流程"""
    args = parse_args()

    if not os.path.isfile(args.model):
        print(f"✗ 错误：模型文件不存在：{args.model}")
        sys.exit(1)

    # 可选：测试技能解析
    if args.skill_dir:
        test_skill_parsing(args.skill_dir, args.verbose)

    # 必须：初始化 Agent
    agent = test_agent_init(args)

    # 列表技能
    if args.list_skills and args.skill_dir:
        agent.list_skills()

    # 测试对话
    test_conversation(agent, args.prompt)

    print("\n" + "="*60)
    print("✅ 所有测试完成!")
    print("="*60)


if __name__ == "__main__":
    main()
