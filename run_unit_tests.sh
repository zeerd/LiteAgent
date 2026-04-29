
# 设置脚本所在目录
export PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

if [ -d ~/.venv ] || [ -L ~/.venv ] ; then
    echo "虚拟环境“~/.venv”已存在，正在激活..."
    source ~/.venv/bin/activate
else
    if [ -d ".venv" ]; then
        echo "虚拟环境“.venv”已存在，正在激活..."
        source .venv/bin/activate
    else
        echo "虚拟环境不存在，正在创建“.venv”..."
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
export

pytest $PROJECT_ROOT/tests/unit/

