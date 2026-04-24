# LiteAgent - 基于 LiteRT-LM 的轻量级 AI Agent 框架

## 📁 项目结构

```
LiteAgent_Planner/
├── README.md                   # 本文件
├── usage_examples.py           # 使用示例脚本
├── run_test.py                 # 统一测试入口
├── docs/                       # 文档目录
│   ├── README.md               # 完整项目文档
│   ├── design_doc.md           # 设计文档
│   └── workplan.md             # 开发计划
├── tests/                      # 测试脚本目录
│   ├── __init__.py
│   └── test_agent.py           # Agent 功能测试
├── benchmarks/                 # 基准测试和演示
│   ├── bench_test.py           # 基准测试工具
│   ├── demo_multi_turn.py      # 多轮对话演示
│   ├── simple_multi_turn.py    # 简单对话示例
│   ├── final_test.py           # 最终测试
│   ├── full_test.py            # 全功能测试
│   ├── verify.py               # 功能验证
│   └── interactive.py          # 交互式 Shell
├── LiteAgent/                  # 核心模块
│   ├── __init__.py
│   ├── agent.py
│   └── skill.py
└── skills/                # 测试技能
    └── echo-skill/
        └── SKILL.md
```

## 🚀 快速开始

### 运行测试

```bash
python question.py --model gemma-4-E2B-it.litertlm --skill-dir skills/ "Who are you?"
```

### 交互式 Shell

```bash
cd benchmarks/
python interactive.py --model gemma-4-E2B-it.litertlm --skill-dir skills/
```

## 📚 技能文件

创建新的 SKILL.md：

```markdown
---
name: skill-name
description: Skill description
metadata:
  version: "1.0"
---

# Skill Name

## Instructions

Instruction text for the skill.
```

## 详细文档

查看 [`docs/README.md`](docs/README.md) 了解更多细节。
