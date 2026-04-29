#!/usr/bin/env python3
"""
LiteAgent 单次询问工具

使用方法:
    python question.py "What is AI?"
    python question.py "你是谁？" \
        --model /path/to/model.litertlm --skill-dir /path/to/skills

环境变量:
    LITEAGENT_MODEL_PATH    默认模型路径
    LITEAGENT_SKILL_DIR     默认技能目录
"""

import sys
import os
import ast
import argparse

from LiteAgent import LiteAgent
import logging

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('Question')

_liteagent_path = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _liteagent_path)


def main():
    parser = argparse.ArgumentParser(
        description="LiteAgent 单次询问工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
    python question.py "What is AI?"
    python question.py "你是谁？" --model /path/to/model.litertlm
    python question.py "Hello" --skill-dir ./skills

环境变量:
    LITEAGENT_MODEL_PATH    默认模型路径
    LITEAGENT_SKILL_DIR     默认技能目录
    LITEAGENT_MAX_TOKENS    最大 token 数
"""
    )

    parser.add_argument(
        "prompt",
        type=str,
        help="询问内容"
    )

    parser.add_argument(
        "--model",
        type=str,
        default=os.environ.get('LITEAGENT_MODEL_PATH', None),
        help="LiteRT-LM 模型文件路径 (默认：LITEAGENT_MODEL_PATH 环境变量)"
    )

    parser.add_argument(
        "--skill-dir",
        type=str,
        default=os.environ.get('LITEAGENT_SKILL_DIR', ''),
        help="SKILL.md 目录路径 (默认：LITEAGENT_SKILL_DIR 环境变量)"
    )

    parser.add_argument(
        "--temperature",
        type=float,
        default=0.7,
        help="采样温度 (0.0 最确定，1.0 最随机，默认：0.7)"
    )

    parser.add_argument(
        "--max-tokens",
        type=int,
        default=int(os.environ.get('LITEAGENT_MAX_TOKENS', 10000)),
        help="最大 token 数 (默认：10000 或 LITEAGENT_MAX_TOKENS 环境变量)"
    )

    parser.add_argument(
        "--compression",
        type=float,
        default=0.75,
        help="上下文压缩阈值 (0-1，默认：0.75) 当 token 使用率超过此值时自动压缩"
    )

    parser.add_argument(
        "--output",
        type=str,
        choices=['json', 'text'],
        default='text',
        help="输出格式 (默认：text)"
    )

    args = parser.parse_args()

    # 验证模型路径
    if not args.model:
        print("❌ 错误：未指定模型路径")
        print("请使用 --model 参数或设置 LITEAGENT_MODEL_PATH 环境变量")
        sys.exit(1)

    # 创建 Agent
    try:
        agent = LiteAgent(
            model_path=args.model,
            skill_dir=args.skill_dir,
            temperature=args.temperature,
            max_tokens=args.max_tokens,
            compression_threshold=args.compression
        )
    except Exception as e:
        print(f"❌ 创建 Agent 失败：{e}")
        sys.exit(1)

    # 获取响应
    try:
        response = agent.chat(args.prompt)
        logger.setLevel(logging.INFO)

        # 输出结果
        if args.output == 'json':
            logger.info('助手：'+response)
        else:
            # 解析 JSON 输出纯文本
            try:
                json_data = ast.literal_eval(response)
                text = json_data[0].get('text', response)
                logger.info('助手：'+text)
            except Exception:
                logger.info('助手：'+response)

    except Exception as e:
        logger.error(f"❌ 推理失败：{e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
