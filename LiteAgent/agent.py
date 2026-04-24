"""
LiteAgent - 基于 LiteRT-LM 的 Agent 框架
支持 SKILL.md 解析与技能注入
使用虚拟环境中的 liteRT-LM 实现
"""

import os
import sys
from typing import Optional, List, Dict, Any

# 注意：不要手动修改 litert_python_path，使用 venv 中已安装的 litert_lm
# if _litert_python_path not in sys.path:
#     sys.path.insert(0, _litert_python_path)

# ✅ 使用 venv 中已安装的 litert_lm（包含编译好的.so 文件）

# 导入 LiteRT-LM
from litert_lm import (
    Backend,
    Engine,
    set_min_log_severity,
    LogSeverity
)
from litert_lm.interfaces import AbstractConversation

from .skill import (
    SkillManager,
    PromptInjector,
)
from .compression import ContextCompressor

_liteagent_path = os.path.dirname(os.path.dirname(__file__))
_litert_python_path = os.path.join(_liteagent_path, "LiteRT-LM", "python")

# 重要：先添加 LiteAgent 路径
if _liteagent_path not in sys.path:
    sys.path.insert(0, _liteagent_path)


class LiteAgent:
    """
    轻量级 AI Agent，基于 LiteRT-LM，支持 SKILL.md 技能系统

    【核心功能】
    - 使用 engine.create_conversation() 创建对话对象
    - 通过 conversation.send_message() 发送消息，上下文自动累积
    - 自动上下文压缩（当超过 compression_threshold 时触发）

    【重要】不要把 conversation.messages 当作状态存储，
    上下文状态保存在 Engine 内部的 KV cache 中，
    应该通过 conversation.send_message() 来管理。
    """

    def __init__(
        self,
        model_path: str,
        skill_dir: str = "",
        backend=Backend.CPU,
        temperature=0.7,
        max_tokens: int = None,
        compression_threshold: float = 0.75
    ):
        """
        初始化 LiteAgent

        Args:
            model_path: LiteRT-LM 模型文件路径 (.litertlm)
            skill_dir: SKILL.md 目录路径 (可选)
            backend: 推理后端 (CPU/GPU)
            temperature: 采样温度
            max_tokens: 最大 token 数，默认由 Engine 使用模型的默认值
            compression_threshold: 压缩触发阈值 (0-1)
                                   当 token 使用率超过此值时自动压缩
                                   默认 0.75 (75%)
        """
        # 验证模型文件
        if not os.path.isfile(model_path):
            raise ValueError(f"模型文件不存在：{model_path}")

        set_min_log_severity(LogSeverity.SILENT)

        self.model_path = model_path
        self.backend = backend
        self.temperature = temperature
        self.max_tokens = max_tokens
        self._compression_threshold = compression_threshold

        # 初始化技能管理器
        self._skill_manager: Optional[SkillManager] = None
        if skill_dir:
            self._skill_manager = SkillManager(skill_dir)

        # 初始化上下文压缩模块
        self._compressor: Optional[ContextCompressor] = None

        # 构建 system prompt
        system_prompt = self._build_system_prompt() + '\n使用中文与用户交流。'

        # 创建引擎和会话
        engine = self._create_engine()
        messages = [{"role": "system", "content": system_prompt}]
        self._conversation = engine.create_conversation(messages=messages)
        self._engine = engine

        # 初始化压缩模块
        self._compressor = ContextCompressor(
            max_tokens=max_tokens,
            compression_threshold=compression_threshold,
            engine=engine
        )

    @property
    def is_initialized(self) -> bool:
        """检查 Agent 是否已初始化"""
        return self._engine is not None and self._conversation is not None

    def start_new_conversation(self) -> AbstractConversation:
        """
        开始一个新的对话

        Returns:
            新的对话对象
        """
        self.close()
        system_prompt = self._build_system_prompt() + '\n使用中文与用户交流。'
        messages = [{"role": "system", "content": system_prompt}]
        self._conversation = self._engine.create_conversation(
            messages=messages
        )
        return self._conversation

    def end_conversation(self) -> None:
        """结束当前对话"""
        self.close_conversation()

    def close(self) -> None:
        """关闭所有资源（引擎和会话）"""
        self._engine = None
        self._compressor = None
        if self._conversation:
            del self._conversation
            self._conversation = None

    def close_conversation(self) -> None:
        """仅关闭当前会话（保留引擎）"""
        if self._conversation:
            del self._conversation
            self._conversation = None

    def _create_engine(self):
        """创建推理引擎"""
        # max_num_tokens 不能为 None，使用默认值 4096
        max_tokens_value = (
            self.max_tokens if self.max_tokens is not None else 4096
        )
        return Engine(self.model_path, backend=self.backend,
                      max_num_tokens=max_tokens_value)

    def _build_system_prompt(self) -> str:
        """构建包含技能的 system prompt"""
        if self._skill_manager:
            skills = self._skill_manager.get_all_skills()
            return PromptInjector.build_instrumented_prompt(skills)
        return "You are a helpful AI assistant."

    @property
    def skill_manager(self) -> Optional[SkillManager]:
        """获取技能管理器"""
        return self._skill_manager

    @property
    def available_skills(self) -> List[str]:
        """获取所有可用技能名称"""
        if self._skill_manager:
            return self._skill_manager.get_skills_names()
        return []

    def chat(self, user_message: str) -> str:
        """
        发送消息并与 Agent 对话

        上下文压缩逻辑：
        1. 每次调用 chat() 时，先检查上下文使用率
        2. 如果超过 compression_threshold，自动压缩并重启会话
        3. 压缩后会话重新开始，使用压缩后的历史继续对话

        注意：
        - 不要手动修改 self._conversation.messages
        - 使用 send_message() 让 Engine 自动管理上下文

        Args:
            user_message: 用户消息

        Returns:
            模型生成的文本响应

        Example:
            >>> agent = LiteAgent('/path/to/model.litertlm')
            >>> response1 = agent.chat("你好！你叫什么名字？")
            >>> response2 = agent.chat("告诉我关于你的信息")
            >>> response3 = agent.chat("你擅长什么？")
        """
        # 检查是否需要压缩
        if self._compressor and self._compressor.should_compress():
            print("⚠️  上下文超过阈值，自动压缩...\n")
            compressed = self._compressor.compress_context(self._conversation)
            if compressed:
                # 重置会话并传入压缩历史
                system_prompt = self._build_system_prompt()
                # 重置会话
                self._conversation = (
                    self._compressor.reset_with_compressed_history(
                        self._conversation,
                        compressed,
                        system_prompt
                    )
                )
                self._compressor._conversation_start_turns = 0

        try:
            response = self._conversation.send_message(user_message)

            if isinstance(response, dict):
                content = response.get("content", response)
                return str(content)
            return str(response)

        except Exception as e:
            print(f"对话错误：{e}")
            raise

    def get_skill_info(self, skill_name: str) -> Optional[Dict[str, Any]]:
        """获取特定技能的详细信息"""
        if not self._skill_manager:
            return None

        skill = self._skill_manager.get_skill(skill_name)
        if skill:
            return {
                "name": skill.name,
                "description": skill.description,
                "instructions": skill.instructions,
                "metadata": skill.metadata,
                "directory": skill.skill_dir
            }
        return None

    def list_skills(self) -> None:
        """打印所有可用技能列表"""
        if not self._skill_manager:
            print("暂无加载的技能")
            return

        skills = self._skill_manager.get_all_skills()
        print(f"可用技能 ({len(skills)} 个):")
        for skill in skills:
            print(f"  - {skill.name}")
            print(f"    {skill.description}")
        print()

    def get_context_stats(self) -> dict:
        """获取上下文统计信息"""
        if self._compressor:
            stats = self._compressor.get_stats()
            # 额外添加当前使用率
            stats["current_usage"] = self._compressor.get_current_usage(
                self._conversation
            )
            return stats
        return {
            "max_tokens": self.max_tokens,
            "compression_threshold": self._compression_threshold,
            "compression_count": 0,
            "conversation_turns": 0,
            "current_usage": 0.0
        }
