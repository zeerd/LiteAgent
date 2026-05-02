"""text_adventure - 基于 LiteRT-LM 的 Python Agent 框架

支持 SKILL.md 技能系统，自动解析与注入技能描述到 system prompt.
"""

from .agent import text_adventure
from .compression import ContextCompressor

__all__ = ["text_adventure", "ContextCompressor"]

__version__ = "0.2.0"
