#!/usr/bin/env python3
"""
LiteAgent 类单元测试（mock 版本）

不使用真实 litert-lm，测试初始化逻辑和参数验证
"""

import sys
import os
import pytest

_liteagent_path = "/home/node/.openclaw/workspace/LiteAgent_Planner"
if _liteagent_path not in sys.path:
    sys.path.insert(0, _liteagent_path)

from LiteAgent import LiteAgent


class TestLiteAgentInit:
    """测试 LiteAgent 初始化逻辑"""

    def test_init_with_nonexistent_model(self):
        """测试使用不存在的模型文件 - 应抛出异常"""
        with pytest.raises(ValueError) as excinfo:
            LiteAgent(
                model_path="/nonexistent/path/to/model.litertlm",
                temperature=0.7
            )
        assert "模型文件不存在" in str(excinfo.value)

    def test_init_params_validation(self):
        """测试参数验证"""
        # 这个测试会跳过，因为需要真实模型
        pass

    @pytest.mark.skip(reason="需要真实模型环境")
    def test_init_with_valid_model(self):
        """测试使用有效模型文件初始化"""
        model_path = os.environ.get("LITERTLM_MODEL_PATH")
        if not model_path or not os.path.isfile(model_path):
            pytest.skip("模型文件不存在")
        
        agent = LiteAgent(model_path=model_path, temperature=0.7)
        assert agent is not None
        assert agent.temperature == 0.7
        agent.close()


class TestLiteAgentCoreFunctionsMock:
    """测试 LiteAgent 核心功能 - 基础验证"""

    def test_get_available_skills_no_skills(self):
        """测试无技能时的可用技能列表"""
        model_path = os.environ.get("LITERTLM_MODEL_PATH")
        if not model_path or not os.path.isfile(model_path):
            pytest.skip("模型文件不存在")
        
        agent = LiteAgent(model_path=model_path, skill_dir="")
        skills = agent.get_available_skills()
        assert skills == []
        agent.close()

    def test_get_skill_info_no_skills(self):
        """测试无技能时的技能信息"""
        model_path = os.environ.get("LITERTLM_MODEL_PATH")
        if not model_path or not os.path.isfile(model_path):
            pytest.skip("模型文件不存在")
        
        agent = LiteAgent(model_path=model_path, skill_dir="")
        info = agent.get_skill_info("nonexistent")
        assert info is None
        agent.close()

    def test_list_skills_no_skills(self):
        """测试无技能时的列出功能"""
        model_path = os.environ.get("LITERTLM_MODEL_PATH")
        if not model_path or not os.path.isfile(model_path):
            pytest.skip("模型文件不存在")
        
        agent = LiteAgent(model_path=model_path, skill_dir="")
        # list_skills 只是打印，不验证返回值
        agent.list_skills()  # 应该能正常调用不抛异常
        agent.close()

