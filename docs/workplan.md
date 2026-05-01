# text_adventure Python 实现规划

## 1. 背景理解

### Google AI Edge Gallery 的 SKILL 机制
Gallery 通过 `SKILL.md` 文件定义技能扩展，其核心机制为:
1. **目录结构**: 每个技能是一个文件夹，必须包含 `SKILL.md` 文件
2. **YAML Frontmatter**: 定义技能元信息 (name, description, metadata)
3. **Instructions**: 指令内容在 frontmatter 后，指导 LLM 如何使用技能
4. **Prompt 注入**: 所有技能的 name/description 被拼接到 system prompt 中
5. **执行方式**: 
   - 文本技能 - 仅通过 prompt 触发行为
   - JS 技能 - 通过 HTML 文件在 webview 中执行 JavaScript
   - 原生技能 - 调用原生应用功能

### LiteRT-LM Python 接口
LiteRT-LM 提供了 Python 接口:
- `AbstractEngine`: 抽象引擎类
- `AbstractConversation`: 对话管理
- `AbstractSession`: 会话管理
- `SamplerConfig`: 采样配置

## 2. 设计目标

实现一个 Python 的 `text_adventure` 框架，支持:
1. **SKILL.md 加载**: 从目录读取并解析 SKILL.md 文件
2. **技能注册**: 将技能与 LiteRT-LM 整合
3. **Prompt 注入**: 将技能描述自动注入系统 prompt
4. **测试脚本**: 能够使用模型 + SKILL + 用户提示词进行测试
5. **上下文压缩**: 自动检测上下文长度，超过阈值时自动压缩历史消息

## 3. 目录设计

```
text_adventure_Planner/
├── workplan.md                     # 本规划文档
├── design_doc.md                   # 详细设计文档
├── README.md                       # 项目概览文档
├── text_adventure/                 # 核心框架实现
│   ├── __init__.py
│   ├── agent.py                    # text_adventure 主类与对话逻辑
│   ├── skill.py                    # SKILL.md 解析与技能管理
│   └── compression.py              # 上下文压缩模块
├── prompts/                        # 提示词模板
│   └── compression.md              # 压缩提示词
├── skills/                         # 测试技能示例
│   └── echo-text/
│       └── SKILL.md
├── question.py                     # 单次询问工具
└── interactive.py                  # 交互式 Shell
```

## 4. 核心组件设计

### 4.1 SKILL 解析器 (skill.py)

```python
class Skill:
    name: str                           # 技能名称
    description: str                    # 技能描述
    instructions: str                   # 指令内容
    metadata: dict                      # 元数据
    skill_dir: str                      # 技能目录路径

class SkillManager:
    def load_skills(skill_dir: str) -> List[Skill]
    def get_skill_names() -> List[str]  # 返回技能名称列表
    def get_skill_descriptions() -> str # 返回可注入的格式
    def get_skill(name: str) -> Skill | None

class PromptInjector:
    @staticmethod
    def build_instrumented_prompt(skills: List[Skill],
                                  include_instructions: bool = False) -> str
```

### 4.2 text_adventure (agent.py)

```python
class text_adventure:
    def __init__(
        self,
        model_path: str,
        skill_dir: str = "",
        backend=Backend.CPU,
        temperature=0.7,
        max_tokens: int = None,
        compression_threshold: float = 0.75
    )
    
    def chat(self, user_message: str) -> str
    """发送消息并与 Agent 对话，自动处理上下文压缩"""
    
    def get_available_skills(self) -> List[str]
    """获取所有可用技能名称"""
    
    def get_skill_info(skill_name: str) -> Dict | None
    """获取特定技能详情"""
    
    @property
    def history_messages(self) -> List[Dict]
    """获取对话历史"""
```

### 4.3 ContextCompressor (compression.py)

```python
class ContextCompressor:
    def __init__(
        model_path: str,
        backend=Backend.CPU,
        temperature=0.7,
        max_num_tokens: int = 4096,
        compression_prompt_path: str = "compression.md"
    )
    
    def should_compress() -> bool
    """检查是否触发压缩"""
    
    def compress_context(conversation) -> str
    """压缩对话历史"""
    
    def reset_with_compressed_history(...)
    """重置会话，使用压缩后的历史"""
```

## 5. 测试技能设计

最简单的 echo 技能：
```yaml
---
name: echo-text
description: Echo the input text back to the user. When Use: `echo` in the prompt.
---

# Echo Test Skill

## Instructions

When prompted, simply repeat back the user's input exactly as they provided it.

**Behavior Rules**:

1. **Repeat Exactly**: Echo the user's input without modification
2. **No Paraphrasing**: Do not rephrase or summarize
3. **Preserve Format**: Keep the original text formatting intact
4. **No Commentary**: Do not add explanations or commentary
```

## 6. 测试脚本设计

### 6.1 `question.py` - 单次询问

用于快速测试单次问答。
- 支持命令行参数：`--model`, `--skill-dir`, `--temperature`, `--max-tokens`, `--compression`
- 自动从环境变量读取：`LITEAGENT_MODEL_PATH`, `LITEAGENT_SKILL_DIR`

### 6.2 `interactive.py` - 交互式 Shell

用于多轮对话测试。
- 支持命令：`/skill <name>`, `/list`, `history`, `clear`, `quit/exit`
- 自动从环境变量读取：`LITEAGENT_MODEL_PATH`, `LITEAGENT_SKILL_DIR`

## 7. 上下文压缩机制

### 7.1 触发条件

当 `history_messages` 中的 content 总长度 / 2 **超过** `max_tokens * compression_threshold` 时触发。
- 默认 `compression_threshold=0.75` (75% 使用率时触发)
- 使用字符数/2 作为 token 数的近似值

### 7.2 压缩流程

1. **检测触发**: `chat()` 方法中检查上下文使用率
2. **压缩历史**: 调用 `ContextCompressor.compress_history()` 压缩历史消息
3. **重置会话**: 使用压缩后的历史重新创建对话
4. **继续对话**: 继续处理用户的当前消息

### 7.3 压缩提示词

`text_adventure/prompts/compression.md` 定义了压缩任务的 LLM 提示词，要求：
- 保留所有关键信息
- 必须具体，禁止模糊词汇
- 直接输出压缩后的快照

## 8. 实现细节

### 8.1 SKILL.md 解析实现

```python
def _parse_skill_md(content: str) -> Skill:
    """解析 SKILL.md 文件内容"""
    # 分割 frontmatter 和 instructions
    parts = content.split("---")
    if len(parts) < 3:
        raise ValueError("Invalid SKILL.md format")
    
    # 解析 YAML
    yaml_content = parts[1]
    frontmatter = SkillParser._parse_simple_yaml(yaml_content)
    
    # 提取字段
    name = frontmatter.pop("name", "unknown")
    description = frontmatter.pop("description", "")
    metadata = frontmatter
    instructions = "\n".join(parts[2:]).strip()
    
    return Skill(name=name, description=description, 
                 instructions=instructions, metadata=metadata)
```

### 8.2 目录扫描

```python
def _scan_skill_directory(root_dir: str) -> Tuple[str, List[Skill]]:
    """扫描目录，找出所有 SKILL.md 文件"""
    skills = []
    for item in os.scandir(root_dir):
        if not item.is_dir():
            continue
        
        skill_file = os.path.join(item.path, "SKILL.md")
        if not os.path.exists(skill_file):
            continue
        
        try:
            skill = SkillParser.parse_file(skill_file)
            skills.append(skill)
        except Exception as e:
            print(f"Warning: Failed to load {skill_file}: {e}")
    return root_dir, skills
```

### 8.3 错误处理

- **无效 SKILL.md**: 记录警告并跳过
- **模型加载失败**: 抛出明确的错误信息
- **对话错误**: 捕获并友好输出

## 9. 扩展性考虑

未来可以扩展:
1. **支持 JS 技能解析**: 解析 HTML 脚本
2. **API key 管理**: 处理 require-secret
3. **缓存机制**: 避免重复解析
4. **热重载**: 动态加载新技能
5. **更多测试脚本**: 针对不同场景的专用脚本

## 10. 断点恢复

如果工作中断，可从任意点继续:
- 检查已完成文件的 `ls text_adventure/`
- 未完成的模块可在同一文件继续编写
- 测试脚本支持增量开发 (先写基础功能再完善)
