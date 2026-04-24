#!/usr/bin/env python3
"""验证 LiteAgent 核心功能"""

import os
import sys

_liteagent_path = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _liteagent_path)

# 测试 SKILL.md 解析
print("="*60)
print("测试 SKILL.md 解析")
print("="*60)

# 手动实现简单解析器
def parse_skill_md(file_path):
    with open(file_path, "r") as f:
        content = f.read()

    parts = content.split("---")
    if len(parts) < 3:
        raise ValueError("Invalid format")

    yaml_content = parts[1].strip()
    frontmatter = {}
    for line in yaml_content.split('\n'):
        line = line.strip()
        if ':' in line:
            k, v = line.split(':', 1)
            frontmatter[k.strip()] = v.strip().strip('"\'')

    name = frontmatter.pop('name', 'unknown')
    description = frontmatter.pop('description', '')
    instructions = '\n'.join(parts[2:]).strip()

    return {
        'name': name,
        'description': description,
        'metadata': frontmatter,
        'instructions': instructions
    }

# 测试解析 echo-skill
skill_file = os.path.join(_liteagent_path, "skills", "echo-skill", "SKILL.md")
if os.path.exists(skill_file):
    skill = parse_skill_md(skill_file)
    print(f"\n✅ SKILL.md 解析成功:")
    print(f"  名称：{skill['name']}")
    print(f"  描述：{skill['description']}")
    print(f"  元数据：{skill['metadata']}")
    print(f"  指令长度：{len(skill['instructions'])} 字符")
else:
    print(f"❌ 找不到测试技能：{skill_file}")

# 测试 LiteAgent 导入
print("\n" + "="*60)
print("测试 LiteAgent 模块导入")
print("="*60)

try:
    # 简单导入检查
    from litert_lm.interfaces import Backend, SamplerConfig
    print("✅ litert_lm 导入成功")
except ImportError as e:
    print(f"⚠️ litert_lm 导入失败：{e}")

# 检查 LiteAgent 模块
try:
    from LiteAgent.skill import Skill, SkillManager
    print("✅ LiteAgent.skill 导入成功")
except Exception as e:
    print(f"❌ LiteAgent.skill 导入失败：{e}")

# 检查 agent 模块
try:
    from LiteAgent.agent import LiteAgent
    print("✅ LiteAgent.agent 导入成功")
except Exception as e:
    print(f"❌ LiteAgent.agent 导入失败：{e}")

print("\n" + "="*60)
print("验证完成")
print("="*60)
