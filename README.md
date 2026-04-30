# text_adventure - 基于 LiteRT-LM 的轻量级 AI Agent 框架

## 🚀 快速开始

### 运行测试

```bash
python question.py --model models/gemma-4-E2B-it.litertlm --skill-dir skills/ "Who are you?"
```

### 交互式 Shell

```bash
cd benchmarks/
python interactive.py --model models/gemma-4-E2B-it.litertlm --skill-dir skills/
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
