# Python text_adventure 框架设计文档

## 一、SKILL.md 解析机制

### 1.1 SKILL.md 格式

```markdown
---
name: skill-name
description: Skill description here
metadata:
  require-secret: true
  require-secret-description: API key description
  homepage: https://example.com
---

# Skill Instructions

This is the main instruction content.

## Subsections
- Can have multiple sections
- LLM uses this for behavior
```

### 1.2 解析流程

1. **提取 frontmatter**: 读取 `---` 之间的 YAML 内容
2. **提取 instructions**: `---` 之后的所有 Markdown 内容
3. **结构化数据**: 封装为 `Skill` 对象

### 1.3 Prompt 注入格式

Gallery 使用以下格式注入技能:
```
- skill-name: Skill description
- another-skill: Another description
```

## 二、Python 类设计

### 2.1 Skill 数据类

```python
@dataclass
class Skill:
    name: str
    description: str
    instructions: str
    metadata: dict = field(default_factory=dict)
    skill_dir: str = ""
```

### 2.2 SkillManager 类

职责:
- 扫描目录查找所有 SKILL.md
- 解析每个 SKILL.md 文件
- 生成可用技能列表
- 生成注入 prompts

```python
class SkillManager:
    def __init__(self, skill_dir: str)
    
    def load_skills() -> List[Skill]
    def get_skills_list() -> str  # 用于注入
    def get_skill_by_name(name: str) -> Skill | None
```

### 2.3 text_adventure 类

核心接口设计:
```python
class text_adventure:
    def __init__(
        self,
        model_path: str,
        skill_dir: str = "",
        backend=Backend.CPU,
        temperature: float = 0.7,
        max_tokens: int = None,
        compression_threshold: float = 0.75
    )
    
    def chat(self, user_message: str) -> str
    """发送用户消息，自动注入技能描述，返回模型响应"""
    
    def get_available_skills(self) -> List[str]
    """获取所有可用技能名称"""
    
    @property
    def history_messages(self) -> List[Dict]
    """获取对话历史"""
```

## 三、与 LiteRT-LM 集成

### 3.1 基础对话流程

基于 LiteRT-LM 的 `create_conversation` API:

```python
# 使用上下文管理器创建对话
with engine.create_conversation(
    tools=tools,
    messages=[{"role": "system", "content": system_prompt}]
) as conversation:
    response = conversation.send_message(user_message)
```

**注意**:
- `create_conversation` 需要作为上下文管理器使用 (with 语句)
- 对话对象会在 with 块结束时自动关闭，释放资源

### 3.2 System Prompt 构建

```python
def build_system_prompt(skills: List[Skill]) -> str:
    """构建包含技能描述的 system prompt"""
    skills_text = "\n".join([
        f"- {skill.name}: {skill.description}"
        for skill in skills
    ])
    return f"""You are a helpful AI assistant with access to the following skills:

{skills_text}

Skills are optional capabilities that can enhance your responses. 
When a skill is relevant to the user's request, you can leverage it."""
```

## 四、测试脚本设计

### 4.1 命令行接口

实际项目中提供了两个脚本：

**`question.py` - 单次询问**:
```bash
python question.py "什么是人工智能？" \
    --model /path/to/model.litertlm
```

**`interactive.py` - 交互式 Shell**:
```bash
python interactive.py \
    --model /path/to/model.litertlm \
    --skill-dir /path/to/skills
```

### 4.2 测试流程
1. **初始化 Agent**: 加载模型 + SKILL.md 目录
2. **验证解析**: 确认技能被正确加载
3. **创建对话**: 注入 system prompt
4. **发送消息**: 执行对话推理
5. **输出结果**: 显示完整响应
6. **上下文压缩**: 自动触发，无需手动干预

## 五、实现细节

### 5.1 SKILL.md 解析实现

```python
def _parse_skill_md(content: str) -> Skill:
    """解析 SKILL.md 文件内容"""
    # 分割 frontmatter 和 instructions
    parts = content.split("---")
    if len(parts) < 3:
        raise ValueError("Invalid SKILL.md format")
    
    # 解析 YAML
    yaml_content = parts[1]
    frontmatter = yaml.safe_load(yaml_content)
    
    # 提取字段
    name = frontmatter.pop("name", "unknown")
    description = frontmatter.pop("description", "")
    metadata = frontmatter
    instructions = "\n".join(parts[2:]).strip()
    
    return Skill(name=name, description=description, instructions=instructions, metadata=metadata)
```

### 5.2 目录扫描

```python
def _scan_skill_directory(root_dir: str) -> Tuple[str, List[Skill]]:
    """扫描目录，找出所有 SKILL.md 文件"""
    skills = []
    for item in os.scandir(root_dir):
        skill_file = os.path.join(item.path, "SKILL.md")
        if item.is_dir() and os.path.exists(skill_file):
            try:
                with open(skill_file, 'r', encoding='utf-8') as f:
                    content = f.read()
                    skill = _parse_skill_md(content)
                    skill.skill_dir = item.path
                    skills.append(skill)
            except Exception as e:
                print(f"Warning: Failed to parse {skill_file}: {e}")
    return root_dir, skills
```

### 5.3 错误处理

- **无效 SKILL.md**: 记录警告并跳过
- **模型加载失败**: 抛出明确的错误信息
- **对话错误**: 捕获并友好输出

## 六、扩展性考虑

未来可以扩展:
1. **支持 JS 技能解析**: 解析 HTML 脚本
2. **API key 管理**: 处理 require-secret
3. **缓存机制**: 避免重复解析
4. **热重载**: 动态加载新技能
