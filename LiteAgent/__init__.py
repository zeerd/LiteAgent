"""
LiteAgent - 基于 LiteRT-LM 的 Python Agent 框架

支持 SKILL.md 技能系统，自动解析与注入技能描述到 system prompt.
"""

from .agent import LiteAgent
from .compression import ContextCompressor, SimpleContextManager

__all__ = ["LiteAgent", "ContextCompressor", "SimpleContextManager"]

__version__ = "0.1.0"
