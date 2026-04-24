#!/bin/bash
cd /home/node/.openclaw/workspace/LiteAgent_Planner
python3 test_agent.py \
    --model /home/node/.openclaw/workspace/LiteAgent/gemma-4-E2B-it.litertlm \
    --skill-dir skills \
    --prompt "Hello, LiteAgent!" \
    --verbose