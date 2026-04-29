"""
SKILL.md 解析与技能管理模块
参考 Google AI Edge Gallery 的 SKILL.md 机制实现
"""

import os
from dataclasses import dataclass, field
from typing import List, Dict, Optional


@dataclass
class Skill:
    """SKILL.md 解析后的技能对象"""
    name: str
    description: str
    instructions: str
    metadata: Dict = field(default_factory=dict)
    skill_dir: str = ""

    def __repr__(self):
        return f"Skill(name='{self.name}', description='{self.description}')"


class SkillParser:
    """SKILL.md 文件解析器 - 简化的 YAML 解析器"""

    @staticmethod
    def _parse_simple_yaml(yaml_text: str) -> Dict:
        """简单的 YAML 解析函数，支持基本 key: value 格式和嵌套 metadata"""
        result = {}
        current_section = None

        for line in yaml_text.strip().split('\n'):
            line = line.strip()
            if not line or line.startswith('#'):
                continue

            # Skip metadata: key if nested
            if '  ' in line and 'metadata' in line:
                continue

            if ':' in line:
                key, value = line.split(':', 1)
                key = key.strip()
                value = value.strip().strip('"').strip("'")

                if key == 'metadata':
                    # Start nested metadata parsing
                    current_section = 'metadata'
                    result['metadata'] = {}
                    continue

                if current_section and key != 'metadata':
                    # Parse nested key in metadata section
                    if value.lower() == 'true':
                        value = True
                    elif value.lower() == 'false':
                        value = False
                    elif value.isdigit():
                        value = int(value)
                    result['metadata'][key] = value
                else:
                    if value.lower() == 'true':
                        value = True
                    elif value.lower() == 'false':
                        value = False
                    elif value.isdigit():
                        value = int(value)
                    else:
                        result[key] = value

        return result

    @staticmethod
    def parse(skill_content: str, skill_dir: str) -> Skill:
        """解析 SKILL.md 文件内容"""
        parts = skill_content.split("---")
        if len(parts) < 3:
            raise ValueError(
                "Invalid SKILL.md format: missing frontmatter delimiter. "
            )

        yaml_content = parts[1].strip()
        frontmatter = SkillParser._parse_simple_yaml(yaml_content)

        name = frontmatter.pop("name", "unknown")
        description = frontmatter.pop("description", "")

        if not name:
            raise ValueError("SKILL.md must have a 'name' field")
        if not description:
            raise ValueError("SKILL.md must have a 'description' field")

        metadata = frontmatter
        instructions = "\n".join(parts[2:]).strip()

        return Skill(
            name=name,
            description=description,
            instructions=instructions,
            metadata=metadata,
            skill_dir=skill_dir
        )

    @classmethod
    def parse_file(cls, file_path: str) -> Skill:
        """解析 SKILL.md 文件"""
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read()
        skill_dir = os.path.dirname(os.path.dirname(file_path))
        return cls.parse(content, skill_dir)


class SkillManager:
    """技能管理器 - 加载和管理多个 SKILL.md"""

    def __init__(self, skill_dir: str = ""):
        """
        初始化技能管理器

        Args:
            skill_dir: SKILL.md 文件的根目录 (可选，用于初始化时扫描)
        """
        self._skills: Dict[str, Skill] = {}
        self._skill_dir = skill_dir
        if skill_dir:
            self.load_skills(skill_dir)

    def load_skills(self, skill_dir: str) -> List[Skill]:
        """
        扫描目录并加载所有 SKILL.md 文件

        Returns:
            加载的技能列表
        """
        loaded = []
        self._skill_dir = skill_dir

        if not os.path.isdir(skill_dir):
            raise ValueError(f"Skill directory does not exist: {skill_dir}")

        for item in os.scandir(skill_dir):
            if not item.is_dir():
                continue

            skill_file = os.path.join(item.path, "SKILL.md")

            if not os.path.exists(skill_file):
                continue

            try:
                skill = SkillParser.parse_file(skill_file)
                self._skills[skill.name] = skill
                loaded.append(skill)
            except Exception as e:
                print(f"Warning: Failed to load {skill_file}: {e}")

        return loaded

    def get_skill(self, name: str) -> Optional[Skill]:
        """获取指定名称的技能"""
        return self._skills.get(name)

    def get_all_skills(self) -> List[Skill]:
        """获取所有已加载的技能"""
        return list(self._skills.values())

    def get_skills_list(self) -> str:
        """
        获取技能列表的文本格式 (用于注入 prompt)

        Returns:
            格式化为 "- name: description" 的字符串
        """
        return "\n".join([
            f"- {skill.name}: {skill.description}"
            for skill in self._skills.values()
        ])

    def get_skills_names(self) -> List[str]:
        """获取所有技能名称列表"""
        return list(self._skills.keys())


class PromptInjector:
    """System Prompt 构建器 - 注入技能描述到 system prompt"""

    @staticmethod
    def build_instrumented_prompt(skills: List[Skill],
                                  include_instructions: bool = False) -> str:
        """
        构建注入技能的 system prompt

        Args:
            skills: 技能列表
            include_instructions: 是否包含完整的技能指令 (默认 False，仅包含 name/description)

        Returns:
            完整的 system prompt
        """
        # 仅注入技能名称和描述到 system prompt
        skills_text = PromptInjector._format_skills(skills)

        base_prompt = """
You are an AI assistant that helps users by answering questions and completes tasks using skills. For EVERY new task or request or question, you MUST execute the following steps in exact order. You MUST NOT skip any steps.

CRITICAL RULE: You MUST execute all steps silently. Do NOT generate or output any internal thoughts, reasoning, explanations, or intermediate text at ANY step.

1. First, find the most relevant skill from the following list:

{skills}

After this step you MUST go to next step. You MUST NOT use `run_intent` under any circumstances at this step.

2. If a relevant skill exists, use the `load_skill` tool to read its instructions. You MUST NOT use `run_intent` under any circumstances at this step. Give pure string only when use skill-name as parameter of `load_skill`.

3. Follow the skill's instructions exactly to complete the task. You MUST NOT output any intermediate thoughts or status updates. No exceptions! Output ONLY the final result when successful. It should contain one-sentence summary of the action taken, and the final result of the skill.
"""

        return base_prompt.format(skills=skills_text if skills_text else "(无可用技能)")
    @staticmethod
    def _format_skills(skills: List[Skill]) -> str:
        """格式化技能列表为文本 (精简版)"""
        sorted_skills = sorted(skills, key=lambda s: s.name)
        return "\n".join([
            f"- `{skill.name}`: {skill.description}"
            for skill in sorted_skills
        ])

# 工具调用结果格式化模板
LOAD_SKILL_TOOL_RESPONSE = """
## ✅ 已加载技能：{name}

**描述**: {description}

**元数据**:
{metadata}

---

{instructions}
"""


def format_load_skill_result(name: str, description: str, metadata: dict,
                             instructions: str) -> str:
    """
    格式化 load_skill 工具调用的结果

    Args:
        name: 技能名称
        description: 技能描述
        metadata: 技能元数据
        instructions: 技能完整指令

    Returns:
        格式化后的技能信息文本
    """
    metadata_text = "\n".join([f"  - {k}: {v}" for k, v in metadata.items()])

    return LOAD_SKILL_TOOL_RESPONSE.strip().format(
        name=name,
        description=description,
        metadata=metadata_text if metadata_text else "无",
        instructions=instructions.strip()
    )
