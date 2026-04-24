"""pytest 配置和固定数据"""
import os
import pytest
from pathlib import Path


def pytest_addoption(parser):
    """添加 pytest 命令行选项"""
    parser.addoption(
        "--model",
        action="store",
        default=None,
        help="LiteAgent 模型路径（覆盖默认值）"
    )
    parser.addoption(
        "--skill-dir",
        action="store",
        default=None,
        help="技能目录路径"
    )
    parser.addoption(
        "--max-tokens",
        action="store",
        default=None,
        type=int,
        help="最大 token 数"
    )
    parser.addoption(
        "--compression-threshold",
        action="store",
        default=None,
        type=float,
        help="上下文压缩阈值 (0-1)"
    )


# =============
# 默认值定义
# =============
def _get_default_model_path():
    """获取默认模型路径"""
    # 尝试相对路径（从 tests 目录）
    tests_dir = Path(__file__).parent
    default_model = str(tests_dir.parent / "LiteAgent" / "gemma-4-E2B-it.litertlm")
    return default_model


@pytest.fixture(scope="session", autouse=True)
def setup_environment(pytestconfig):
    """为所有测试设置环境变量"""
    # 通过命令行参数或环境变量设置
    model_path = pytestconfig.getoption("model")
    if not model_path:
        model_path = os.environ.get("LITEAGENT_MODEL_PATH")
    if not model_path:
        model_path = os.environ.get("MODEL_PATH", _get_default_model_path())

    os.environ['LITEAGENT_MODEL_PATH'] = model_path

    if pytestconfig.getoption("skill_dir"):
        os.environ['LITEAGENT_SKILL_DIR'] = pytestconfig.getoption("skill_dir")
    elif 'LITEAGENT_SKILL_DIR' not in os.environ:
        os.environ['LITEAGENT_SKILL_DIR'] = ''

    if pytestconfig.getoption("max_tokens"):
        os.environ['LITEAGENT_MAX_TOKENS'] = str(pytestconfig.getoption("max_tokens"))
    elif 'LITEAGENT_MAX_TOKENS' not in os.environ:
        os.environ['LITEAGENT_MAX_TOKENS'] = '10000'

    if pytestconfig.getoption("compression_threshold"):
        os.environ['LITEAGENT_COMPRESSION_THRESHOLD'] = str(pytestconfig.getoption("compression_threshold"))
    elif 'LITEAGENT_COMPRESSION_THRESHOLD' not in os.environ:
        os.environ['LITEAGENT_COMPRESSION_THRESHOLD'] = '0.75'

    # 打印当前配置 (可选)
    print(f"\n\n=== 测试环境配置 ===")
    print(f"模型路径：{os.environ['LITEAGENT_MODEL_PATH']}")
    print(f"技能目录：{os.environ.get('LITEAGENT_SKILL_DIR', '(未设置)')}")
    print(f"最大 tokens: {os.environ['LITEAGENT_MAX_TOKENS']}")
    print(f"压缩阈值：{os.environ.get('LITEAGENT_COMPRESSION_THRESHOLD', '0.75')}")
    print(f"=============")
