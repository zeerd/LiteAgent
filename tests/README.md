# LiteAgent 测试脚本

这个目录包含 LiteAgent 的各种测试脚本。

## 测试脚本列表

### 核心测试

- **`test_agent.py`** - LiteAgent 基础功能测试
  - 测试 SKILL.md 解析
  - 测试技能注入 system prompt
  - 验证基本对话功能

- **`test_multiturn.py`** - 多轮对话测试
  - 测试修复后的多轮对话上下文累积
  - 验证 Conversation API 的正确使用
  - 测试实际对话轮数能力

### 容量测试

- **`test_context_max_tokens.py`** - Context 长度上限测试（推荐）
  -  configurable max_tokens 参数
  - 可以测试不同配置下的实际极限
  - 命令行参数：
    ```bash
    python test_context_max_tokens.py --max-tokens 32768 --turns 30
    ```

- **`test_context_length_limit.py`** - Context 长度极限测试
  - 测试实际能承载的 tokens 数量
  - 使用长文本逐步累积，找到真实的上限
  - 命令行参数：
    ```bash
    python test_context_length_limit.py --target 32000
    ```

## 运行示例

```bash
# 进入项目目录
cd /home/node/.openclaw/workspace/LiteAgent_Planner

# 使用虚拟环境
source .venv/bin/activate

# 运行基础测试
python tests/test_agent.py

# 运行多轮对话测试
python tests/test_multiturn.py

# 运行容量测试（指定 max_tokens）
python tests/test_context_max_tokens.py --max-tokens 32768 --turns 20
```

## 测试结果总结

根据测试，LiteAgent 的 Context Length 限制：
- **不是固定的 4096**
- **由 `max_tokens` 参数控制**
- **实际约等于配置的 max_tokens 值**

示例：
- `max_tokens=8192` → 约 9 轮对话后崩溃
- `max_tokens=32768` → 约 36 轮对话后崩溃
