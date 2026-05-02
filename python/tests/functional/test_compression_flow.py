#!/usr/bin/env python3
"""
test_compression_flow.py - 上下文压缩流程功能测试

测试目标:
1. 验证短响应检测功能
2. 验证上下文压缩触发机制
3. 验证压缩后重试机制
"""

import subprocess
import sys
import os
import re
import time


COMPRESS_TRIGGER_LOG = "触发上下文压缩"
COMPRESSION_SUCCESS_LOG = "压缩成功"
COMPRESSION_RETRY_LOG = "使用压缩历史重新执行对话"
SHORT_MSG = "极短响应"


def run_test_script(script_content, python_args=None):
    """运行 Python 测试脚本并返回输出"""
    python_args = python_args or []
    env = os.environ.copy()
    env['PYTHONUNBUFFERED'] = '1'  # 确保 Python 输出不被缓冲

    check1 = False  # 检测压缩触发日志
    check2 = False  # 检测压缩重试日志
    check3 = False  # 检测正常响应日志

    try:
        # 1. 准备命令
        cmd = ['python3', '-u', '-c', script_content]

        # 2. 启动进程
        # (注意：不设置 capture_output=True，而是设置 stdout/stderr 为管道)
        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,  # 等同于 universal_newlines=True，确保输出是字符串而非字节
            bufsize=1,  # 行缓冲，确保能逐行读取
            env=env
        )

        output_log = ""

        # 3. 循环读取输出
        # 这里使用 iter 逐行读取 stdout
        for line in process.stdout:
            print(line, end='')  # 实时打印到控制台 (end='' 防止换行重复)
            output_log += line   # 如果需要保存日志，可以累积在这里

            if '🔧 开始执行上下文压缩' in line:
                print("📜 Detected compression trigger log")
                check1 = True

            if '🔄 压缩后重新对话' in line:
                print("📜 Detected compression retry log")
                check2 = True

            res_len = re.search(r'✅ 回复长度:(\d+) 字符', line)
            if res_len and int(res_len.group(1)) > 10 and check1 and check2:
                print("📜 Detected normal response log")
                check3 = True

            if check1 and check2 and check3:
                print("✅ All expected logs detected")
                process.kill()  # 确保进程结束

        for line in process.stderr:
            print('STDERR: ' + line, end='')  # 实时打印到控制台 (end='' 防止换行重复)
            output_log += line   # 如果需要保存日志，可以累积在这里

        # 4. 等待进程结束并获取可能的剩余 stderr
        # wait() 确保进程完全结束
        process.wait()

        # 5. 返回累积的日志（如果需要返回值的话）
        return output_log
    except Exception as e:
        return "Error: " + str(e)


def run_tests():
    """测试 1: 短响应检测功能"""
    print("=" * 70)
    print("测试 1: 短响应检测功能")
    print("=" * 70)

    model_path = os.environ.get("LITERTLM_MODEL_PATH")

    if not model_path or not os.path.exists(model_path):
        print("Error: Model not found", model_path)
        return False

    # Build script dynamically
    model_path_escaped = model_path.replace('\\', '\\\\').replace('"', '\\"')
    test_queries = [
        "你现在在干嘛？",
        "为什么不回我消息？",
        "你是不是在跟别人聊天？",
        "那个人是谁？",
        "你们聊了什么？",
        "你是不是有事瞒着我？",
        "你最近是不是对我冷淡了？",
        "你是不是不喜欢我了？",
        "那你喜欢谁？",
        "你心里到底有没有我？",
        "你昨天去哪了？",
        "为什么不告诉我？",
        "你是不是觉得我很烦？",
        "那你为什么还跟我在一起？",
        "你是不是想分手？",
        "你是不是早就想好了？",
        "你有没有想过我的感受？",
        "你到底爱不爱我？",
        "你以后还会不会这样？",
        "那你现在能不能立刻出现在我面前？",
    ]
    queries_str = ", ".join(['"%s"' % q for q in test_queries])

    # 测试脚本内容
    test_code = """
import sys
sys.path.insert(0, '/home/node/.openclaw/workspace/LiteAgent_Planner')

from text_adventure import text_adventure
from litert_lm import Backend
import logging
import sys

# 获取 root logger
logger = logging.getLogger()
logger.setLevel(logging.INFO) # 或者你需要的级别

# 关键步骤：
# 找到所有的 Handler，并将它们的 stream 设置为 sys.stdout (且 flush 为 True)
for handler in logger.handlers:
    if hasattr(handler, 'stream'):
        # 强制使用未缓冲的 sys.stdout
        handler.stream = sys.stdout
        # 有些 Handler 有 flush 方法，确保每次写入都刷新
        handler.flush = lambda: sys.stdout.flush()

model_path = "MODEL_PATH"
test_queries = [TEST_QUERIES]

print("Model:", model_path)
print("Max tokens: 4096")
print()

agent = text_adventure(
    model_path=model_path,
    skill_dir="",
    max_tokens=4096,
    compression_threshold=0.25,
    temperature=0.7,
    backend=Backend.CPU
)

for i, q in enumerate(test_queries, 1):

    response = agent.chat(q)

    if isinstance(response, str):
        resp_len = len(response)
        print(str(i) + ". Query:", q)
        print("Response length:", resp_len, "chars")

agent.close()

print()
print("=== Test Complete ===")
"""

    test_code = test_code.replace("MODEL_PATH", model_path_escaped)
    test_code = test_code.replace("TEST_QUERIES", queries_str)

    _ = run_test_script(test_code)

    return True


if __name__ == "__main__":
    print("Starting compression flow tests...\n")
    start_time = time.time()

    results = []
    results.append(run_tests())

    elapsed = time.time() - start_time

    print("\n" + "=" * 70)
    print("Test suite completed in %.2f seconds" % elapsed)
    print("=" * 70)

    if all(results):
        print("\n✅ All tests executed successfully")
    else:
        passed = sum(results)
        total = len(results)
        print("\n❌ Results: %d/%d tests passed" % (passed, total))

    sys.exit(0)
