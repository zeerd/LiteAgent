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
                                  include_instructions: bool = True) -> str:
        """
        构建注入技能的 system prompt

        Args:
            skills: 技能列表
            include_instructions: 是否包含完整的技能指令 (默认 True)

        Returns:
            完整的 system prompt
        """
        if include_instructions:
            # 注入完整技能描述和指令给用户
            skills_text = PromptInjector._format_skills_with_instructions(
                skills
            )
        else:
            # 仅注入技能名称和描述 (精简版)
            skills_text = PromptInjector._format_skills(skills)

        base_prompt = """
You are a helpful AI assistant powered by LiteRT-LM.
You have access to the following skills:

{skills}

When a user's request matches one of these skills, you can leverage the skill
capabilities to provide better responses.
Use the skill's instructions to guide your behavior and provide accurate
responses.

Respond to user queries in a helpful, accurate, and safe manner."""

        return base_prompt.format(skills=skills_text)

    @staticmethod
    def _format_skills_with_instructions(skills: List[Skill]) -> str:
        """格式化技能列表为文本，包含完整指令"""
        output = []
        for skill in sorted(skills, key=lambda s: s.name):
            output.append(f"- **{skill.name}**: {skill.description}")
            if skill.instructions:
                output.append(
                    f"  - Instructions: {skill.instructions[:200]}"
                    f"{'...' if len(skill.instructions) > 200 else ''} ...")
        return "\n".join(output)

    @staticmethod
    def _format_skills(skills: List[Skill]) -> str:
        """格式化技能列表为文本 (精简版)"""
        sorted_skills = sorted(skills, key=lambda s: s.name)
        return "\n".join([
            f"- `{skill.name}`: {skill.description}"
            for skill in sorted_skills
        ])

    @classmethod
    def inject_skill_instructions(cls, skill: Skill,
                                  instructions: bool = True) -> str:
        """
        将特定技能的 instructions 注入到响应中

        Args:
            skill: 技能对象
            instructions: 是否包含 instructions 内容

        Returns:
            包含技能指导的提示文本
        """
        text = f"** Skill: {skill.name}**\n"
        text += f"** Description: {skill.description}**\n"
        if skill.metadata:
            text += f"** Metadata: {skill.metadata}**\n"
        if instructions and skill.instructions:
            text += f"\n** Instructions:\n{skill.instructions}\n**\n"
        return text
