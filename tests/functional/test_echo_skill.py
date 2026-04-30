#!/usr/bin/env python3
"""
text_adventure 功能测试脚本

功能:测试 echo-skill 技能的正确调用
预期行为:
- AI 应该识别 echo-text 技能
- 调用 load_skill 工具
- 返回 "Who are you?" 而不是 "I am..."
"""

import subprocess
import sys
import os
import re
import time

# 预期条件
EXPECTED_LOG = "✅"
EXPECTED_LOG_SKILL = "load_skill 处理成功"
EXPECTED_SKILL_NAME = "echo-test"

EXPECTED_OUTPUT_START = "*Who are you?"
EXPECTED_OUTPUT_NOT_START = "I am"


def run_test():
    """运行测试"""
    print("=" * 70)
    print("text_adventure 功能测试 - echo-skill 测试")
    print("=" * 70)

    print("\n✅ 测试目的:")
    print("1. 验证 text_adventure 能正确加载 echo-skill")
    print("2. 验证 load_skill 工具能被调用")
    print("3. 验证 AI 响应格式")
    print()

    # 从环境变量获取模型路径
    model_path = os.environ.get("LITERTLM_MODEL_PATH")
    skills_path = os.environ.get("LITERTLM_SKILL_DIR", '')
    PROJECT_ROOT = os.environ.get("PROJECT_ROOT", '')

    # 构建命令 (使用环境变量)
    cmd = [
        'python3', 'question.py',
        '--model', model_path,
        '--skill-dir', skills_path,
        'echo: Who are you?'
    ]

    cmd_str = ' '.join(cmd)
    print(f"\n执行命令:{cmd_str}\n")
    print("-" * 70)

    # 运行测试
    start_time = time.time()
    result = subprocess.run(
        cmd,
        cwd=PROJECT_ROOT,
        env=os.environ.copy(),
        capture_output=True,
        text=True
    )
    elapsed_time = time.time() - start_time

    # 输出日志
    print("\n完整输出日志:")
    print("-" * 70)
    print(result.stdout)
    if result.stderr:
        print("错误输出:")
        print(result.stderr)
    print("-" * 70)

    all_logs = result.stdout + result.stderr

    print("\n检查结果:")
    print("-" * 70)

    # 检查 1:
    check1 = "load_skill 处理成功:echo-text" in all_logs
    if check1:
        print("✅ load_skill 成功")
    else:
        print("❌ load_skill 失败")

    # 检查 2:
    check2 = re.search(r"助手：.*Who are you", all_logs) is not None
    if check2:
        print("✅ echo-text 技能被调用，AI 正确复述了用户消息")
    else:
        print("❌ echo-text 技能未被调用，AI 没有正确复述用户消息")

    # 检查 3:
    check3 = "助手：I am" in all_logs or "助手：我是" in all_logs
    if check3:
        print("❌ echo-text 技能未被调用，AI 输出了默认响应")
    else:
        print("✅ echo-text 技能被调用，AI 没有输出默认响应")

    print()
    print("=" * 70)
    print(f"测试结果：{elapsed_time:.2f}秒")
    print("=" * 70)

    # 综合判断
    all_passed = check1 and check2 and not check3
    if all_passed:
        print("\n✅ 核心功能测试通过!")
        return 0
    else:
        print("\n❌ 核心功能测试失败")
        return 1


if __name__ == "__main__":
    sys.exit(run_test())
