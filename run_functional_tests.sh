#!/bin/bash
# LiteAgent 功能测试运行脚本

# 设置脚本所在目录
export PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

if [ -d ~/.venv ] || [ -L ~/.venv ] ; then
    echo "虚拟环境"~/.venv"已存在，正在激活..."
    source ~/.venv/bin/activate
else
    if [ -d ".venv" ]; then
        echo "虚拟环境".venv"已存在，正在激活..."
        source .venv/bin/activate
    else
        echo "虚拟环境不存在，正在创建".venv"..."
        python3 -m venv .venv
        source .venv/bin/activate
        echo "正在安装依赖..."
        pip install -r requirements.txt
    fi
fi
install -d .tmp

export PYTHONPATH=$PROJECT_ROOT
export LITERTLM_MODEL_PATH=$PROJECT_ROOT/models/gemma-4-E2B-it.litertlm
export LITERTLM_SKILL_DIR=$PROJECT_ROOT/skills

# 运行所有功能测试
echo "========================================"
echo "LiteAgent 功能测试运行器"
echo "========================================"
echo ""

# 遍历 tests/functional/目录中的所有测试
for test_file in tests/functional/test_*.py; do
    if [ -f "$test_file" ]; then
        echo "========================================"
        echo "运行测试：$test_file"
        echo "========================================"
        python3 "$test_file"
        result=$?

        if [ $result -eq 0 ]; then
            echo "✅ 测试通过：$test_file"
        else
            echo "❌ 测试失败：$test_file"
        fi
        echo ""

        # 如果有任何测试失败，返回非零状态码
        if [ $result -ne 0 ]; then
            exit 1
        fi
    fi
done

echo "========================================"
echo "所有功能测试完成"
echo "========================================"
