"""
text_adventure - 基于 LiteRT-LM 的 Agent 框架
支持 SKILL.md 解析与技能注入
使用虚拟环境中的 liteRT-LM 实现
"""

import collections.abc
# 导入 LiteRT-LM - 使用~/.venv 虚拟环境中的包
from litert_lm import (
    Backend,
    Engine,
    set_min_log_severity,
    LogSeverity
)

# 导入标准库
import os
import logging
import re
from typing import Optional, List, Dict, Any

# 导入本地模块
from .skill import (
    SkillManager,
    PromptInjector,
    format_load_skill_result
)
from .compression import ContextCompressor


# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('text_adventure')


def clean_tool_args(args: dict) -> dict:
    """
    清洗 LiteRT-LM 返回的异常参数格式
    """
    cleaned_args = {}
    # 匹配模式：移除类似 <|"|> 或 <|any_string|> 的包装
    pattern = r'<\|["\']?|["\']?\|>'

    for key, value in args.items():
        if isinstance(value, str):
            # 移除特殊标识符并 strip 空格
            new_value = re.sub(pattern, '', value).strip()
            cleaned_args[key] = new_value
        else:
            cleaned_args[key] = value
    return cleaned_args


def _handle_load_skill(skill_manager: SkillManager,
                       params: Dict[str, Any]) -> str:
    """
    load_skill 工具的底层处理函数

    Args:
        skill_manager: 技能管理器实例
        params: 参数字典
    """
    logger.info("🔧 处理工具调用:load_skill")
    params = clean_tool_args(params)
    logger.info("   参数:%s", params)

    skill_name = params.get("name")
    if not skill_name:
        available = skill_manager.get_skills_names()
        error_msg = f"❌ 缺少参数：name，可用技能:{', '.join(available)}"
        logger.error(error_msg)
        return error_msg

    skill = skill_manager.get_skill(skill_name)
    if not skill:
        available = skill_manager.get_skills_names()
        error_msg = f"❌ 技能 '{skill_name}' 不存在\n可用技能:{', '.join(available)}"
        logger.error(error_msg)
        return error_msg

    # 格式化响应
    result = format_load_skill_result(
        name=skill.name,
        description=skill.description,
        instructions=skill.instructions,
        metadata=skill.metadata
    )
    logger.info("✅ load_skill 处理成功:%s", skill_name)
    logger.info("   - 技能名称:%s", skill.name)
    logger.info("   - 指令长度:%d 字符", len(skill.instructions))
    return result


class text_adventure:
    """
    轻量级 AI Agent，基于 LiteRT-LM，支持 SKILL.md 技能系统

    【核心功能】
    - 使用 engine.create_conversation() 创建对话对象
    - 通过 conversation.send_message() 发送消息，上下文自动累积
    - 自动上下文压缩 (当超过 compression_threshold 时触发)
    - 支持工具调用 (load_skill, list_skills)

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
        compression_threshold: float = 0.75,
        use_minimal_prompt: bool = True,
        allowed_skill_list: list = None,
    ):
        """
        初始化 text_adventure

        Args:
            model_path: LiteRT-LM 模型文件路径 (.litertlm)
            skill_dir: SKILL.md 目录路径 (可选)
            backend: 推理后端 (CPU/GPU)
            temperature: 采样温度
            max_tokens: 最大 token 数，默认由 Engine 使用模型的默认值
            compression_threshold: 压缩触发阈值 (0-1)
                                   当 token 使用率超过此值时自动压缩
                                   默认 0.75 (75%)
            use_minimal_prompt: 是否使用精简系统提示词
                               True: 不包含技能指令全文，仅定义 load_skill 工具
                               False: 包含技能指令摘要
            allowed_skill_list: 只允许加载的技能名称列表 (可选)
                               如果提供，只加载这些技能，跳过其他技能
        """
        logger.info("=" * 60)
        logger.info("正在初始化 text_adventure...")
        logger.info("  - 模型路径:%s", model_path)
        logger.info("  - 技能目录:%s", skill_dir)
        logger.info("  - 后端:%s", backend)
        logger.info("  - 温度:%s", temperature)
        logger.info("  - 压缩阈值:%s", compression_threshold)
        logger.info("  - 精简提示词:%s", use_minimal_prompt)
        logger.info("  - 允许技能列表:%s", allowed_skill_list)

        # 验证模型文件
        if not os.path.isfile(model_path):
            logger.error("❌ 模型文件不存在:%s", model_path)
            raise ValueError(f"模型文件不存在:{model_path}")

        logger.info("✅ 模型文件验证通过:%s", model_path)
        set_min_log_severity(LogSeverity.SILENT)

        self.model_path = model_path
        self.backend = backend
        self.temperature = temperature
        self.max_tokens = max_tokens
        self._compression_threshold = compression_threshold

        # 初始化技能管理器 (传入白名单)
        self._skill_manager: Optional[SkillManager] = None
        if skill_dir:
            self._skill_manager = SkillManager(skill_dir, allowed_skill_list)
            skills = self._skill_manager.get_all_skills()
            logger.info("✅ 加载 %d 个技能", len(skills))

        # 初始化上下文压缩模块
        self._compressor: Optional[ContextCompressor] = None
        self.history_messages = []

        # 构建 system prompt
        self.system_prompt = self._build_system_prompt(
            use_minimal_prompt
        ) + '\n使用中文与用户交流。\n如果你完成了回答，请在最后加上标记 [EOF]'
        logger.info("✅ 构建 system prompt 完成:\n%s", self.system_prompt)

        # 创建工具列表 - 使用闭包传递 skill_manager
        self.tools: List[Any] = []
        if self._skill_manager:
            def load_skill(name: str) -> str:
                try:
                    """加载特定技能的完整指令内容。
                    Args:
                        name: 技能名称
                    """
                    params = {"name": name}
                    return _handle_load_skill(self._skill_manager, params)
                except Exception as e:
                    logger.error("❌ load_skill 工具调用失败：%s", e)
                    return f"❌ load_skill 工具调用失败：{e}"

            self.tools = [load_skill]
        else:
            logger.warning("没有加载技能管理器，工具不会被注册")

        # 创建引擎
        logger.info("🚀 创建推理引擎...")
        self._engine = Engine(model_path, backend=backend,
                              max_num_tokens=max_tokens or 4096)

        # 创建对话 (传入工具)
        logger.info("🔧 创建对话，注册工具...")

        # 初始化压缩模块
        self._compressor = ContextCompressor(
            model_path=model_path,
            backend=backend,
            temperature=temperature,
            max_num_tokens=max_tokens * 2 if max_tokens * 2 < 32768 else 32768
        )

        logger.info("✅ 工具已注册到对话中:")
        if self.tools:
            logger.info("  - load_skill: 加载技能详细指令")
            logger.info("  - list_skills: 列出所有技能")
        else:
            logger.info("  暂无可用工具")

        logger.info("✅ text_adventure 初始化完成")
        logger.info("=" * 60)

    def close(self) -> None:
        """关闭所有资源（引擎和会话）"""
        logger.info("🔒 正在关闭资源...")
        self._engine = None
        self._compressor = None
        logger.info("✅ 资源已关闭")

    def close_conversation(self) -> None:
        """仅关闭当前会话（保留引擎）"""

    def _create_engine(self):
        """创建推理引擎"""
        # max_num_tokens 不能为 None，使用默认值 4096
        max_tokens_value = (
            self.max_tokens if self.max_tokens is not None else 4096
        )
        return Engine(self.model_path, backend=self.backend,
                      max_num_tokens=max_tokens_value)

    def _build_system_prompt(self, use_minimal: bool = True) -> str:
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

    def get_available_skills(self) -> List[str]:
        """获取所有可用技能名称列表"""
        if self._skill_manager:
            return self._skill_manager.get_skills_names()
        return []

    def get_skill_info(self, skill_name: str) -> Optional[Dict[str, Any]]:
        """获取特定技能的详细信息"""
        if self._skill_manager:
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

    def list_skills(self):
        """列出所有技能"""
        if self._skill_manager:
            skills = self._skill_manager.get_all_skills()
            print(f"可用技能 ({len(skills)} 个):")
            for skill in skills:
                print(f"  - {skill.name}")
                print(f"    {skill.description}")
            print()
        else:
            print("暂无加载的技能")

    def is_initialized(self) -> bool:
        """检查 Agent 是否已初始化"""
        return self._engine is not None

    def _check_compression_trigger(self) -> bool:
        """
        检查是否需要触发上下文压缩

        判断标准：self.history_messages 中的 content 总长度 / 2
        是否超过 self.max_tokens 的 75%

        Returns:
            bool: 是否触发压缩
        """
        if not self.history_messages or not self.max_tokens:
            return False, 0

        # 计算 history_messages 中 content 的总长度
        total_content_length = sum(
            len(msg.get("content", ""))
            for msg in self.history_messages
        )

        # 计算压缩触发阈值：total_content_length / 2 > max_tokens * 0.75
        # LiteRT-LM 没有提供直接获取 token 数的接口，我们使用字符数/2作为近似指标
        threshold = (self.max_tokens * self._compression_threshold)
        if (total_content_length / 2) > threshold:
            logger.info("✅ 触发上下文压缩：总内容长度 %d / 2 = %d > 阈值 %d",
                        total_content_length, total_content_length // 2,
                        threshold)
            return True, total_content_length

        logger.info("📊 未触发压缩：总内容长度 %d / 2 = %d <= 阈值 %d",
                    total_content_length, total_content_length // 2, threshold)
        return False, total_content_length

    def _compress_and_retry(self, compressed_history: str) -> str:
        """
        使用压缩后的历史重新执行 send_message

        Args:
            compressed_history: 压缩后的历史快照

        Returns:
            模型生成的文本响应
        """
        logger.info("📜 压缩历史快照长度:%d 字符", len(compressed_history))
        logger.info("📜 压缩历史快照内容:\n%s", compressed_history)  # 只打印前1000字符

        try:
            # 构建新的 system prompt，包含压缩历史
            # 只保留最近几次交互作为短期记忆（可选）
            # 这里保留最近 2 条消息，帮助 LLM 理解当前对话流
            messages = [
                {"role": "system", "content": self.system_prompt},
                {"role": "user", "content": compressed_history},
                {"role": "assistant", "content": 'Acknowledged.'}
            ]
            recent_messages = (
                self.history_messages[-4:]
                if len(self.history_messages) >= 4 else self.history_messages
            )
            messages.extend(recent_messages)
            return messages

        except Exception as e:
            logger.error("❌ 压缩后重试失败：%s", e)
            raise

    def chat(
        self,
        user_message: str,
        history_messages: (
            collections.abc.Sequence[collections.abc.Mapping[str, Any]] | None
        ) = None
    ) -> str:
        """
        发送消息并与 Agent 对话

        上下文压缩逻辑:
        1. 每次调用 chat() 时，先检查上下文使用率
        2. 如果超过 compression_threshold，自动压缩并重启会话
        3. 压缩后会话重新开始，使用压缩后的历史继续对话

        Args:
            user_message: 用户消息

        Returns:
            模型生成的文本响应

        Example:
            >>> agent = text_adventure('/path/to/model.litertlm')
            >>> response1 = agent.chat("你好！你叫什么名字？")
            >>> response2 = agent.chat("告诉我关于你的信息")
        """
        logger.info("-" * 40)
        if history_messages:
            self.history_messages = history_messages
        logger.info("📝 用户消息:%s..", user_message[:80])

        # 判断是否需要压缩
        need_compression, total_content_length = (
            self._check_compression_trigger()
        )

        try:
            messages = [{"role": "system", "content": self.system_prompt}]
            if isinstance(self.history_messages, list):
                messages.extend(self.history_messages)

            while True:
                with self._engine.create_conversation(
                    tools=self.tools, messages=messages
                ) as conv:
                    response = conv.send_message(user_message)
                    logger.debug("raw-response=%s", response)
                    # 提取纯文本内容
                    if isinstance(response, dict):
                        content = response.get("content", "")
                        # 如果是列表格式，拼接内容
                        if isinstance(content, list):
                            content = "\n".join([
                                item.get("text", "")
                                if isinstance(item, dict) else str(item)
                                for item in content
                            ])
                        if content.endswith('[EOF]'):
                            logger.debug("ends normally.")
                            content = content[:-5]
                        else:
                            logger.info("ends by cutted.")
                            need_compression = True


                        # 检查 response 是否非常短（< 5 字符）
                        is_short_response = len(content) < 5
                        if is_short_response:
                            logger.warning(
                                "⚠️ 检测到极短响应 (%d 字符)，"
                                "输出原始 response", len(content)
                            )
                            logger.info("   原始响应:%s", content)

                        logger.info("✅ 回复长度:%d 字符", len(content))
                        logger.info("✅ 回复内容:%s", content)

                        # 更新历史消息
                        self.history_messages.append(
                            {"role": "user", "content": user_message}
                        )
                        self.history_messages.append(
                            {"role": "assistant", "content": content}
                        )

                        # 如果触发压缩，重新执行
                        if need_compression or is_short_response:
                            logger.info("🔧 开始执行上下文压缩...")
                            compressed = self._compressor.compress_history(
                                self.history_messages
                            )
                            if compressed:
                                logger.info(
                                    "✅ 压缩成功，"
                                    "压缩后长度:%d 字符, 原始长度:%d 字符",
                                    len(compressed), total_content_length
                                )
                                # 重置历史，只保留压缩后的快照
                                self.history_messages = []
                                # 使用压缩历史重新执行
                                messages = self._compress_and_retry(compressed)
                                logger.info("🔄 压缩后重新对话：%s", user_message)
                                continue
                            else:
                                logger.warning("⚠️ 压缩失败，继续使用原始响应")

                        return str(content)
                    return str(response)

        except Exception:
            raise

    def __del__(self):
        """析构器，确保资源释放

        ⚠️ 注意：__del__ 不应该执行实际的清理操作，
        因为这可能导致死锁或卡死。应该使用上下文管理器。
        这里我们只做引用置空，让 Python 的 GC 处理。
        """
        try:
            self.close()
        except Exception as e:
            # __del__ 中不能抛出异常，必须吞掉
            try:
                logger.warning("⚠️ __del__ 调用时出错：%s", e)
            except Exception:
                pass
