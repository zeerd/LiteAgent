"""
单元测试：SkillManager 白名单功能测试
测试 allowed_skill_list 参数的过滤逻辑
"""

import pytest
from text_adventure.skill import SkillManager, logger


class TestSkillManagerWhitelist:
    """SkillManager 白名单功能测试类"""

    @pytest.fixture
    def skill_dir(self):
        """测试技能目录路径"""
        return "skills"

    def test_no_whitelist_load_all(self, skill_dir):
        """测试 1: 无白名单时应加载所有技能"""
        manager = SkillManager(skill_dir, allowed_skill_list=None)
        skills = manager.get_all_skills()
        
        # 断言：加载的技能数量应为 2（echo-text 和 text-adventure）
        assert len(skills) == 2, f"Expected 2 skills, got {len(skills)}"
        
        # 断言：技能名称正确
        skill_names = {s.name for s in skills}
        assert "echo-text" in skill_names, "'echo-text' 技能未加载"
        assert "text-adventure" in skill_names, "'text-adventure' 技能未加载"

    def test_whitelist_single_skill(self, skill_dir):
        """测试 2: 白名单指定单个技能时只加载该技能"""
        whitelist = ["text-adventure"]
        manager = SkillManager(skill_dir, allowed_skill_list=whitelist)
        skills = manager.get_all_skills()
        
        # 断言：只加载了 1 个技能
        assert len(skills) == 1, f"Expected 1 skill, got {len(skills)}"
        
        # 断言：加载的是正确的技能
        assert skills[0].name == "text-adventure"
        assert skills[0].description == "Act as a dungeon master for any text-based adventure scenario."

    def test_whitelist_nonexistent_skill(self, skill_dir):
        """测试 3: 白名单指定不存在的技能时应加载 0 个技能"""
        manager = SkillManager(skill_dir, allowed_skill_list=["nonexistent"])
        skills = manager.get_all_skills()
        
        # 断言：没有加载任何技能
        assert len(skills) == 0, f"Expected 0 skills, got {len(skills)}"
        assert manager.get_skills_names() == []

    def test_whitelist_multiple_skills(self, skill_dir):
        """测试 4: 白名单指定多个技能时加载所有指定技能"""
        whitelist = ["text-adventure", "echo-text"]
        manager = SkillManager(skill_dir, allowed_skill_list=whitelist)
        skills = manager.get_all_skills()
        
        # 断言：加载了 2 个技能
        assert len(skills) == 2, f"Expected 2 skills, got {len(skills)}"
        
        # 断言：技能名称正确
        skill_names = {s.name for s in skills}
        assert skill_names == {"text-adventure", "echo-text"}

    def test_whitelist_empty_list_load_all(self, skill_dir):
        """测试 5: 白名单为空列表时应加载所有技能（等价于 None）"""
        manager_empty = SkillManager(skill_dir, allowed_skill_list=[])
        manager_none = SkillManager(skill_dir, allowed_skill_list=None)
        
        skills_empty = manager_empty.get_all_skills()
        skills_none = manager_none.get_all_skills()
        
        # 断言：空列表和 None 效果相同
        assert len(skills_empty) == len(skills_none), \
            f"Empty list and None should have same effect, got {len(skills_empty)} vs {len(skills_none)}"

    def test_whitelist_partial_match(self, skill_dir):
        """测试 6: 白名单中部分技能存在时只加载存在的技能"""
        whitelist = ["text-adventure", "nonexistent-skill"]
        manager = SkillManager(skill_dir, allowed_skill_list=whitelist)
        skills = manager.get_all_skills()
        
        # 断言：只加载了存在的技能
        assert len(skills) == 1, f"Expected 1 skill, got {len(skills)}"
        assert skills[0].name == "text-adventure"

    def test_skill_retrieval_filtered(self, skill_dir):
        """测试 7: 白名单过滤后获取技能应遵守限制"""
        whitelist = ["text-adventure"]
        manager = SkillManager(skill_dir, allowed_skill_list=whitelist)
        
        # 断言：可以获取白名单内的技能
        skill = manager.get_skill("text-adventure")
        assert skill is not None
        assert skill.name == "text-adventure"
        
        # 断言：无法获取不在白名单内的技能
        other_skill = manager.get_skill("echo-text")
        assert other_skill is None

    def test_no_skill_manager_when_no_dir(self):
        """测试 8: 不提供 skill_dir 时技能管理器为空"""
        manager = SkillManager(skill_dir="", allowed_skill_list=None)
        
        # 断言：没有技能
        skills = manager.get_all_skills()
        assert len(skills) == 0
