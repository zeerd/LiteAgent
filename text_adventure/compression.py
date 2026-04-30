"""
text_adventure - 上下文压缩模块

使用 LiteRT-LM 完成历史聊天记录的压缩工作。
核心功能：接收历史聊天记录，返回压缩文本。
"""

import os
from typing import Optional, List, Dict

from litert_lm import (
    Backend,
    Engine,
)


class ContextCompressor:
    """
    上下文压缩器

    负责将对话历史压缩为紧凑的世界状态快照。
    使用 LiteRT-LM 的 LLM 完成压缩任务。
    """

    def __init__(
        self,
        model_path: str,
        backend=Backend.CPU,
        temperature=0.7,
        max_num_tokens: int = 4096,
        compression_prompt_path: str = "compression.md"
    ):
        """
        初始化 ContextCompressor

        Args:
            model_path: LiteRT-LM 模型文件路径 (.litertlm)
            backend: 推理后端 (CPU/GPU)
            temperature: 采样温度
            max_num_tokens: 最大 token 数，默认由 Engine 使用模型的默认值
            compression_prompt_path: 压缩提示词文件路径
                                    默认为 text_adventure/prompts/compression.md
        """
        self._engine = Engine(model_path, backend=backend,
                              max_num_tokens=max_num_tokens or 4096)
        self._compression_prompt = self._load_compression_prompt(
            compression_prompt_path
        )

    def _load_compression_prompt(self, path: str) -> str:
        """
        加载压缩提示词文件

        Args:
            path: 压缩提示词文件路径

        Returns:
            提示词内容字符串
        """
        # 确定提示词文件路径
        if not os.path.isabs(path):
            # 相对路径 - 在脚本所在目录的 prompts 子目录中查找
            script_dir = os.path.dirname(os.path.abspath(__file__))
            path = os.path.join(script_dir, "prompts", path)

        with open(path, 'r', encoding='utf-8') as f:
            return f.read()

    def _build_compress_prompt(self, history_text: str) -> str:
        """
        构建完整的压缩任务提示词

        Args:
            history_text: 需要压缩的原始对话历史

        Returns:
            用于传递给 LLM 的完整提示词
        """
        return f"""你是一名文本压缩任务执行器，专门负责将对话历史压缩为紧凑的世界状态快照。

{self._compression_prompt}

=== 需要压缩的原始对话历史 ===

{history_text}

=== 压缩任务 ===

请将上述对话历史压缩为"世界状态快照（World State Snapshot）"格式。

压缩时请严格遵守：
1. 保留所有关键信息：位置、物品、规则、威胁、NPC、状态
2. 必须具体，禁止模糊词汇（如"一些东西"）
3. 直接输出压缩后的快照，不要解释，不要添加额外内容

现在输出世界状态快照：
"""

    def _extract_ai_response(self, full_response: str) -> str:
        """
        从 LLM 响应中提取纯文本内容

        Args:
            full_response: LLM 的完整响应

        Returns:
            提取后的纯文本响应
        """
        if isinstance(full_response, dict):
            content = full_response.get("content", "")
            if isinstance(content, list):
                return "\n".join([
                    item.get("text", "")
                    if isinstance(item, dict) else str(item)
                    for item in content
                ])
            return str(content)
        return str(full_response)

    def compress_history(
            self, history_messages: List[Dict[str, str]]
    ) -> Optional[str]:
        """
        压缩历史聊天记录

        Args:
            history_messages: 对话历史消息列表，每条消息包含 "role" 和 "content" 字段

        Returns:
            压缩后的文本，压缩成功返回字符串，失败返回 None
        """
        if not history_messages or len(history_messages) < 2:
            return None

        # 格式化历史对话
        history_text = ""
        for msg in history_messages:
            role = msg.get("role", "user")
            content = msg.get("content", "")
            role_label = (
                "User" if role == "user" else "AI"
                if role == "assistant" else role
            )
            history_text += f"{role_label}: {content}\n"
            history_text += "\n"

        # 构建完整的压缩提示词
        full_prompt = self._build_compress_prompt(history_text)

        # 使用 LiteRT-LM 生成压缩结果
        try:
            conversation = self._engine.create_conversation(
                messages=[{"role": "system", "content": full_prompt}]
            )
            response = conversation.send_message("")
            return self._extract_ai_response(response)

        except Exception as e:
            print(f"压缩历史消息失败：{e}")
            return None
