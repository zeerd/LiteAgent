"""
LiteAgent - 上下文自动压缩模块

提供上下文长度管理和自动压缩功能，包括：
- Token 计数估算
- 压缩触发检查
- 对话历史压缩
- 会话重置
"""

from typing import Optional, List, Dict, Any
from litert_lm.interfaces import AbstractConversation


class ContextCompressor:
    """
    上下文压缩器

    负责管理对话历史的 token 使用率，当超过阈值时自动压缩。
    支持独立的 compression 操作，不依赖具体的 Agent 实现。
    """

    def __init__(
        self,
        max_tokens: Optional[int] = None,
        compression_threshold: float = 0.75,
        engine=None  # 用于创建新会话的 Engine 实例
    ):
        """
        初始化 ContextCompressor

        Args:
            max_tokens: 最大 token 数，用于计算使用率
            compression_threshold: 压缩触发阈值 (0-1)
            engine: Engine 实例，用于压缩后创建新会话
        """
        self.max_tokens = max_tokens
        self.compression_threshold = compression_threshold
        self._engine = engine

        # 压缩状态跟踪
        self._compression_history: List[str] = []
        self._conversation_start_turns: int = 0

    def get_current_usage(self, conversation=None):
        """获取当前 token 使用率 (0.0 - 1.0)"""
        if not self.max_tokens:
            return 0.0

        # 如果有 conversation 参数，使用传入的 conversation
        if conversation is not None:
            conv = conversation
        elif hasattr(self, '_conversation') and self._conversation is not None:
            conv = self._conversation
        else:
            return 0.0

        if hasattr(conv, 'messages') and conv.messages:
            # 估算 token 总数
            total_text = ""
            for msg in conv.messages:
                content = msg.get("content", "")
                if isinstance(content, list):
                    for item in content:
                        if (
                            isinstance(item, dict) and
                            item.get("type") == "text"
                        ):
                            total_text += item.get("text", "")
                else:
                    total_text += str(content) if content else ""

            total_tokens = self.estimate_tokens(total_text)
            return total_tokens / self.max_tokens

        return 0.0

    def should_compress(self) -> bool:
        """检查是否需要压缩上下文"""
        usage = self.get_current_usage()
        return usage >= self.compression_threshold

    def estimate_tokens(self, text: str) -> int:
        """估算文本的 token 数量（粗略估计，每 4 字符约 1 token）"""
        return len(text) // 4

    def compress_context(self, conversation) -> Optional[str]:
        """
        压缩给定对话的上下文

        Args:
            conversation: AbstractConversation 对象

        Returns:
            压缩后的历史字符串，如果无法压缩返回 None
        """
        if not conversation.messages or len(conversation.messages) < 2:
            return None

        try:
            # 提取历史对话（跳过 system prompt）
            history_text = ""
            for msg in conversation.messages[1:]:
                content = msg.get("content", "")
                role = msg.get("role", "")
                if isinstance(content, list):
                    for item in content:
                        if (
                            isinstance(item, dict) and
                            item.get("type") == "text"
                        ):
                            history_text += f"[{role}] {item['text']}\n"
                else:
                    history_text += f"[{role}] {content}\n"

            # 创建压缩概要
            summary = (
                f"对话历史概要 (共{len(conversation.messages)-1}轮):\n"
                f"{history_text[:2000]}"
            )
            self._compression_history.append(summary)
            return summary

        except Exception as e:
            print(f"压缩上下文失败：{e}")
            return None

    def reset_with_compressed_history(
        self,
        conversation: AbstractConversation,
        compressed_summary: str,
        system_prompt: str
    ) -> AbstractConversation:
        """
        使用压缩后的历史重置会话

        Args:
            conversation: 当前会话对象（将被关闭）
            compressed_summary: 压缩概要
            system_prompt: 系统提示

        Returns:
            新的会话对象
        """
        # 构建包含压缩历史的新 system prompt
        new_system_prompt = f"""{system_prompt}

你正在继续一个之前的对话。之前的对话已被压缩总结，以下是压缩后的历史：

--- 压缩历史 ---
{compressed_summary}
--- 压缩历史结束 ---

请基于以上压缩后的历史，继续对话。"""

        # 关闭旧会话
        if conversation:
            del conversation

        # 创建新会话
        messages = [{"role": "system", "content": new_system_prompt}]

        if self._engine:
            new_conversation = self._engine.create_conversation(
                messages=messages
            )
        else:
            raise RuntimeError("Engine 不可用，无法创建新会话")

        print(f"✅ 上下文已重置，开启新会话 (历史：{len(self._compression_history)}次压缩)")
        return new_conversation

    def get_stats(self, conversation=None) -> Dict[str, Any]:
        """获取压缩器统计信息"""
        return {
            "max_tokens": self.max_tokens,
            "compression_threshold": self.compression_threshold,
            "compression_count": len(self._compression_history),
            "compression_history": self._compression_history[-3:],  # 最近 3 次
            "conversation_turns": self._conversation_start_turns,
            "current_usage": self.get_current_usage(conversation)
        }

    def get_compression_summary(self) -> str:
        """获取所有压缩历史作为字符串"""
        if not self._compression_history:
            return "暂无压缩历史"
        return "\n\n".join(self._compression_history)


class SimpleContextManager:
    """
    简单的上下文管理器（仅用于测试）

    不依赖 Engine，只提供基础的压缩功能。
    """

    def __init__(
        self,
        max_tokens: Optional[int] = None,
        compression_threshold: float = 0.75
    ):
        self.max_tokens = max_tokens
        self.compression_threshold = compression_threshold
        self._compression_history: List[str] = []
        self._conversation_start_turns: int = 0

    def get_current_usage(self, conversation) -> float:
        """计算当前使用率"""
        if (
            not self.max_tokens or not conversation or
            not conversation.messages
        ):
            return 0.0

        # 估算 token 总数
        total_text = ""
        for msg in conversation.messages:
            content = msg.get("content", "")
            if isinstance(content, list):
                for item in content:
                    if isinstance(item, dict) and item.get("type") == "text":
                        total_text += item.get("text", "")
            total_text += str(content)

        total_tokens = self.estimate_tokens(total_text)
        return total_tokens / self.max_tokens

    def estimate_tokens(self, text: str) -> int:
        """估算 token 数量"""
        return len(text) // 4

    def should_compress(self, conversation) -> bool:
        """检查是否应该压缩"""
        usage = self.get_current_usage(conversation)
        return usage >= self.compression_threshold

    def compress_context(self, conversation) -> Optional[str]:
        """压缩上下文"""
        return ContextCompressor().compress_context(conversation)

    def get_stats(self, conversation) -> Dict[str, Any]:
        """获取统计信息"""
        return {
            "max_tokens": self.max_tokens,
            "compression_threshold": self.compression_threshold,
            "current_usage": self.get_current_usage(conversation),
            "compression_count": len(self._compression_history),
            "conversation_turns": self._conversation_start_turns
        }
