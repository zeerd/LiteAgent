#!/usr/bin/env python3
"""
text_adventure 上下文自动压缩单元测试

使用 Mock 对象绕开真实 litert-lm 接口，纯逻辑测试
"""

import sys
import os
import pytest

_liteagent_path = "/home/node/.openclaw/workspace/text_adventure_Planner"
if _liteagent_path not in sys.path:
    sys.path.insert(0, _liteagent_path)

from text_adventure import ContextCompressor


class MockConversation:
    """Mock Conversation 对象，用于纯单元测试"""
    def __init__(self, messages=None):
        self.messages = messages or []
        self.extra_context = None


class TestAutoCompressionLogic:
    """测试自动压缩的纯逻辑（不用真实模型）"""

    @pytest.fixture
    def compressor(self):
        """创建压缩器实例"""
        return ContextCompressor(max_tokens=10000, compression_threshold=0.5)

    def test_get_current_usage_above_threshold(self, compressor):
        """测试获取使用率 - 超过阈值"""
        conv = MockConversation([
            {"role": "system", "content": "x" * 25000},  # 约 6250 tokens
            {"role": "user", "content": "x" * 25000},
        ])
        usage = compressor.get_current_usage(conv)
        # 6250*2/10000 = 0.625 > 0.5 (50%)
        assert usage >= 0.5, f"使用率 {usage} 应该达到阈值 0.5"

    def test_get_current_usage_below_threshold(self, compressor):
        """测试获取使用率 - 低于阈值"""
        conv = MockConversation([
            {"role": "system", "content": "小文本"},
        ])
        usage = compressor.get_current_usage(conv)
        assert usage < 0.5, f"使用率 {usage} 应该低于阈值 0.5"

    def test_compress_with_small_context(self, compressor):
        """测试小上下文压缩"""
        conv = MockConversation([
            {"role": "system", "content": "system"},
            {"role": "user", "content": "user input"},
            {"role": "assistant", "content": "assistant response"},
        ])
        result = compressor.compress_context(conv)
        assert result is not None
        assert "对话历史概要" in result

