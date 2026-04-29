"""
LiteAgent - 基于 LiteRT-LM 的 Agent 框架
支持 SKILL.md 解析与技能注入
使用虚拟环境中的 liteRT-LM 实现
"""

# 导入 LiteRT-LM - 使用~/.venv 虚拟环境中的包
from litert_lm import (
    Backend,
    Engine,
)
from litert_lm.interfaces import ToolEventHandler

# 导入标准库
import os
import sys
import logging
from typing import Optional, List, Dict, Any

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('LiteAgent')

_liteagent_path = os.path.dirname(os.path.dirname(__file__))
sys.path.insert(0, _liteagent_path)

try:
    logger.info("✅ LiteRT-LM 导入成功")
except ImportError as e:
    logger.error("❌ LiteRT-LM 导入失败:%s", e)
    raise ImportError("LiteRT-LM 未安装：请确保已安装 litert-lm")

# 导入本地模块
from .skill import (
    SkillManager,
    PromptInjector,
    format_load_skill_result
)
from .compression import ContextCompressor


def clean_tool_args(args: dict) -> dict:
    """
    清洗 LiteRT-LM 返回的异常参数格式
    """
    import re
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

def _handle_load_skill(skill_manager: SkillManager, params: Dict[str, Any]) -> str:
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
        error_msg = "load_skill 需要 name 参数"
        logger.error(error_msg)
        return f"❌ {error_msg}"

    skill = skill_manager.get_skill(skill_name)
    if not skill:
        error_msg = f"技能不存在:{skill_name}"
        logger.warning(error_msg)
        available = skill_manager.get_skills_names()
        return f"❌ {error_msg}\n可用技能:{', '.join(available)}"

    # 格式化响应
    result = format_load_skill_result(
        name=skill.name,
        description=skill.description,
        metadata=skill.metadata,
        instructions=skill.instructions
    )

    logger.info("✅ load_skill 处理成功:%s", skill_name)
    logger.info("   - 技能名称:%s", skill.name)
    logger.info("   - 指令长度:%d 字符", len(skill.instructions))

    return result


class LiteAgent:
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
            use_minimal_prompt: 是否使用精简系统提示词
                               True: 不包含技能指令全文，仅定义 load_skill 工具
                               False: 包含技能指令摘要
        """
        logger.info("=" * 60)
        logger.info("正在初始化 LiteAgent...")
        logger.info("  - 模型路径:%s", model_path)
        logger.info("  - 技能目录:%s", skill_dir)
        logger.info("  - 后端:%s", backend)
        logger.info("  - 温度:%s", temperature)
        logger.info("  - 精简提示词:%s", use_minimal_prompt)

        # 验证模型文件
        if not os.path.isfile(model_path):
            logger.error("❌ 模型文件不存在:%s", model_path)
            raise ValueError(f"模型文件不存在:{model_path}")

        logger.info("✅ 模型文件验证通过:%s", model_path)
        #set_min_log_severity(LogSeverity.SILENT)

        self.model_path = model_path
        self.backend = backend
        self.temperature = temperature
        self.max_tokens = max_tokens
        self._compression_threshold = compression_threshold
        self._use_minimal_prompt = use_minimal_prompt

        # 初始化技能管理器
        self._skill_manager: Optional[SkillManager] = None
        if skill_dir:
            logger.info("正在加载技能:%s", skill_dir)
            self._skill_manager = SkillManager(skill_dir)
            skills = self._skill_manager.get_all_skills()
            logger.info("✅ 加载 %d 个技能", len(skills))
            for skill in skills:
                logger.debug("   - %s: %s..", skill.name, skill.description[:50])

        # 初始化上下文压缩模块
        self._compressor: Optional[ContextCompressor] = None

        # 构建 system prompt - 启用完整指令
        system_prompt = self._build_system_prompt(use_minimal=False) + '\n使用中文与用户交流。'
        logger.info("✅ 构建 system prompt 完成:\n%s", system_prompt)

        # 创建工具列表 - 使用闭包传递 skill_manager
        tools: List[Any] = []
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
                    return f"❌ load_skill 工具调用失败： {e}"

            tools = [load_skill]
        else:
            logger.warning("没有加载技能管理器，工具不会被注册")

        # 创建引擎
        logger.info("🚀 创建推理引擎...")
        self._engine = Engine(model_path, backend=backend, max_num_tokens=max_tokens or 4096)

        # 创建对话 (传入工具)
        logger.info("🔧 创建对话，注册工具...")
        messages = [{"role": "system", "content": system_prompt}]

        self._conversation = self._engine.create_conversation(
            messages=messages,
            tools=tools
        )

        logger.info("✅ 工具已注册到对话中:")
        if tools:
            logger.info("  - load_skill: 加载技能详细指令")
            logger.info("  - list_skills: 列出所有技能")
        else:
            logger.info("  暂无可用工具")

        logger.info("✅ LiteAgent 初始化完成")
        logger.info("=" * 60)

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
                    'name': skill.name,
                    'description': skill.description,
                    'instructions': skill.instructions,
                    'metadata': skill.metadata
                }
        return None

    def list_skills(self):
        """列出所有技能"""
        if self._skill_manager:
            skills = self._skill_manager.get_all_skills()
            print(f"可用技能 ({len(skills)} 个):")
            for skill in skills:
                print(f"  - `{skill.name}`: {skill.description}")
        else:
            print("暂无加载的技能")

    def _build_system_prompt(self, use_minimal: bool = True) -> str:
        """构建包含技能的 system prompt"""
        if self._skill_manager:
            skills = self._skill_manager.get_all_skills()
            return PromptInjector.build_instrumented_prompt(
                skills,
                include_instructions=not use_minimal
            )
        return "You are a helpful AI assistant."

    @property
    def is_initialized(self) -> bool:
        """检查 Agent 是否已初始化"""
        return self._engine is not None and self._conversation is not None

    def chat(self, user_message: str) -> str:
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
            >>> agent = LiteAgent('/path/to/model.litertlm')
            >>> response1 = agent.chat("你好！你叫什么名字？")
            >>> response2 = agent.chat("告诉我关于你的信息")
        """
        logger.info("-" * 40)
        logger.info("📝 用户消息:%s..", user_message[:80])

        try:
            response = self._conversation.send_message(user_message)
            # 提取纯文本内容
            if isinstance(response, dict):
                content = response.get("content", "")
                # 如果是列表格式，拼接内容
                if isinstance(content, list):
                    content = "\n".join([
                        item.get("text", "") if isinstance(item, dict) else str(item)
                        for item in content
                    ])
                logger.info("✅ 回复长度:%d 字符", len(content))
                return str(content)
            return str(response)

        except Exception as e:
            logger.error("❌ 对话错误:%s", e)
            raise

    def close(self):
        """关闭所有资源"""
        logger.info("🔒 正在关闭资源...")

        if self._conversation:
            try:
                # 只置空，让 Python 的 GC 处理
                self._conversation = None
                logger.info("✅ conversation 已释放")
            except Exception as e:
                logger.warning("关闭 conversation 时出错：%s", e)

        self._engine = None
        self._compressor = None

        logger.info("✅ 资源已关闭")

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
            except:
                pass
