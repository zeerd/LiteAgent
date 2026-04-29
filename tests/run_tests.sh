#!/bin/bash
# LiteAgent 测试运行脚本
# 支持通过命令行参数指定模型路径

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(dirname "$SCRIPT_DIR")"

cd "$WORKSPACE_DIR"

# =====================
# 参数解析
# =====================
MODEL_PATH=""
SKILL_DIR=""
MAX_TOKENS=""
COMPRESSION_THRESHOLD=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --model)
            MODEL_PATH="$2"
            shift 2
            ;;
        --model=*)
            MODEL_PATH="${1#*=}"
            shift
            ;;
        --skill-dir)
            SKILL_DIR="$2"
            shift 2
            ;;
        --skill-dir=*)
            SKILL_DIR="${1#*=}"
            shift
            ;;
        --max-tokens)
            MAX_TOKENS="$2"
            shift 2
            ;;
        --max-tokens=*)
            MAX_TOKENS="${1#*=}"
            shift
            ;;
        --compression-threshold)
            COMPRESSION_THRESHOLD="$2"
            shift 2
            ;;
        --compression-threshold=*)
            COMPRESSION_THRESHOLD="${1#*=}"
            shift
            ;;
        *)
            echo "未知参数：$1"
            echo "用法：$0 [--model PATH] [--skill-dir PATH] [--max-tokens NUMBER] [--compression-threshold FLOAT]"
            exit 1
            ;;
    esac
done

# =====================
# 环境变量设置
# =====================
export VENV_PYTHON="$(dirname "$WORKSPACE_DIR")/.venv/bin/python3"
export WORKSPACE_DIR

# 通过环境变量传递参数
if [ -n "$MODEL_PATH" ]; then
    export LITEAGENT_MODEL_PATH="$MODEL_PATH"
fi
if [ -n "$SKILL_DIR" ]; then
    export LITEAGENT_SKILL_DIR="$SKILL_DIR"
fi
if [ -n "$MAX_TOKENS" ]; then
    export LITEAGENT_MAX_TOKENS="$MAX_TOKENS"
fi
if [ -n "$COMPRESSION_THRESHOLD" ]; then
    export LITEAGENT_COMPRESSION_THRESHOLD="$COMPRESSION_THRESHOLD"
fi

# =====================
# 环境检查
# =====================
echo "========================================"
echo "LiteAgent 测试运行脚本"
echo "========================================"
echo "工作目录：$WORKSPACE_DIR"
echo "Python: $VENV_PYTHON"
echo "模型路径：${LITEAGENT_MODEL_PATH:-使用默认值}"
if [ -n "$SKILL_DIR" ]; then
    echo "技能目录：$SKILL_DIR"
fi
if [ -n "$MAX_TOKENS" ]; then
    echo "最大 tokens: $MAX_TOKENS"
fi
if [ -n "$COMPRESSION_THRESHOLD" ]; then
    echo "压缩阈值：$COMPRESSION_THRESHOLD"
fi
echo ""

# 检查是否存在虚拟环境
if [ ! -f "$VENV_PYTHON" ]; then
    echo "❗️ 警告：未找到虚拟环境，尝试使用系统 Python"
    VENV_PYTHON="python3"
fi

# 检查 pytest 是否安装
if ! $VENV_PYTHON -c "import pytest" 2>/dev/null; then
    echo "❗️ 警告：未找到 pytest，尝试安装..."
    $VENV_PYTHON -m pip install pytest -q
fi

echo "执行 pytest..."
echo "========================================"
echo ""

# 运行测试 - 将参数传递给 pytest
pytest_args=(-v --tb=short)
if [ -n "$MODEL_PATH" ]; then
    pytest_args+=(--model="$MODEL_PATH")
fi
if [ -n "$SKILL_DIR" ]; then
    pytest_args+=(--skill-dir="$SKILL_DIR")
fi
if [ -n "$MAX_TOKENS" ]; then
    pytest_args+=(--max-tokens="$MAX_TOKENS")
fi
if [ -n "$COMPRESSION_THRESHOLD" ]; then
    pytest_args+=(--compression-threshold="$COMPRESSION_THRESHOLD")
fi

$VENV_PYTHON -m pytest "${pytest_args[@]}"

echo ""
echo "========================================"
echo "测试完成!"
echo "========================================"
