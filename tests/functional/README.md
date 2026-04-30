# 功能测试用例设计

- 通用规则参照`.agents/AGENT.md`。
- 在项目跟目录通过`run_functional_tests.sh`脚本配置运行环境并运行测试脚本。

## 技能加载测试

- 测试脚本：`test_echo_skill.py`
- 测试目的：验证LLM可以根据用户描述找到正确的技能，加载技能、使用技能


## 上下文压缩核心算法测试

- 测试脚本：`test_compression_core.py`
- 测试设计：基于`docs/compression_test_design.md`完成测试设计。1. 读取`chat_history.json`生成聊天历史，并直接调用压缩机制来压缩。2. 使用压缩结果和设计好的三个问题调用真实的chat验证压缩效果。
- 测试目的：证实： 1. 使用了正确的压缩提示词`text_adventure/prompts/compression.md`；2. 正确的压缩了测试上下文；3. 基于压缩后的上下文的提问不会遗漏关键信息。


## 上下文压缩流程测试

- 测试脚本：`test_compression_flow.py`
- 测试设计：规划多论对话内容，验证在上下文累积达到预设标准之后，可以正确的触发上下文压缩，并且能够继续对话。
- 测试目的：验证：1. 上下文压缩可以被触发； 2. 压缩后可以继续聊天不会被异常中断。
