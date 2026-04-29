"""pytest 配置 - LiteAgent 测试"""
import os
import pytest


def pytest_addoption(parser):
    """添加 pytest 命令行选项"""
    parser.addoption(
        "--model",
        action="store",
        default=None,
        help="LiteAgent 模型路径"
    )
    parser.addoption(
        "--skill-dir",
        action="store",
        default=None,
        help="技能目录路径"
    )


@pytest.fixture
def mock_conversation():
    """创建 Mock Conversation 对象，用于单元测试绕过真实 litert-lm"""
    class MockConversation:
        def __init__(self):
            self.messages = []
            self.extra_context = None
    
    return MockConversation()
