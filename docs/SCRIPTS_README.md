# text_adventure 脚本使用指南

## 可用脚本

### 1. `interactive.py` - 交互式 Shell
适合进行多轮对话测试。

**用法**:
```bash
python interactive.py \
    --model /path/to/model.litertlm \
    --skill-dir /path/to/skills
```

**环境变量**:
```bash
export LITEAGENT_MODEL_PATH=/path/to/model.litertlm
export LITEAGENT_SKILL_DIR=/path/to/skills
python interactive.py  # 使用环境变量值
```

**交互命令**:
- /skill <name> - 查看技能信息
- /list - 列出所有技能
- history - 显示对话历史
- clear - 清除历史记录
- quit/exit - 退出

---

### 2. `question.py` - 单次询问
适合快速测试单次问答。

**用法**:
```bash
python question.py "什么是人工智能？"
```

**指定模型**:
```bash
python question.py \
    "什么是人工智能？" \
    --model /path/to/model.litertlm
```

**环境变量**:
```bash
export LITEAGENT_MODEL_PATH=/path/to/model.litertlm
python question.py "什么是 AI？"  # 使用环境变量值
```

**输出格式**:
```bash
python question.py "你好" --output text
python question.py "你好" --output json
```

**启用压缩**:
```bash
python question.py "你好" --compression
```

---

### 3. `run_test.py` - 统一测试脚本
整合了两个脚本的功能。

**用法**:
```bash
# 单次询问
python run_test.py "你好" --model /path/to/model.litertlm

# 交互式模式
python run_test.py --interactive --model /path/to/model.litertlm

# 从环境变量获取模型
python run_test.py "你好"  # 使用 LITEAGENT_MODEL_PATH
```

---

## 环境变量

所有脚本都支持以下环境变量:

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `LITEAGENT_MODEL_PATH` | 模型路径 | 无（必需） |
| `LITEAGENT_SKILL_DIR` | 技能目录 | 无 |
| `LITEAGENT_MAX_TOKENS` | 最大 token 数 | 10000 |
| `LITEAGENT_COMPRESSION_THRESHOLD` | 压缩阈值 | 0.75 |

### 设置环境变量

```bash
# Bash/Zsh
export LITEAGENT_MODEL_PATH=/path/to/model.litertlm
export LITEAGENT_SKILL_DIR=/path/to/skills
python question.py "你好"

# 临时设置
LITEAGENT_MODEL_PATH=/path/to/model python question.py "你好"

# Windows PowerShell
$env:LITEAGENT_MODEL_PATH="/path/to/model"
python question.py "你好"
```

---

## 完整示例

### 示例 1: 单次询问
```bash
python question.py "你是谁？" \
    --model /home/node/.openclaw/workspace/text_adventure/gemma-4-E2B-it.litertlm
```

### 示例 2: 多轮对话
```bash
python interactive.py \
    --model /home/node/.openclaw/workspace/text_adventure/gemma-4-E2B-it.litertlm \
    --skill-dir skills/
```

### 示例 3: 使用环境变量
```bash
export LITEAGENT_MODEL_PATH=/home/node/.openclaw/workspace/text_adventure/gemma-4-E2B-it.litertlm
export LITEAGENT_MAX_TOKENS=5000
python question.py "请解释一下机器学习"
```

### 示例 4: 使用压缩功能
```bash
python question.py \
    "你好，这是一段很长的问题..." \
    --compression \
    --model /path/to/model.litertlm
```

---

## 注意事项

1. **模型路径必须存在** - 脚本不会自动创建或下载模型
2. **虚拟环境依赖** - 确保使用正确的 Python 虚拟环境
3. **技能目录可为空** - 如果不使用技能功能，`--skill-dir` 可以不设置
4. **温度设置** - 较低的值更确定，较高的值更随机
