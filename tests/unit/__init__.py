#!/usr/bin/env python3
"""
LiteAgent 测试套件

包含：
- 功能测试
- 集成测试
"""

import unittest


class TestLiteAgent(unittest.TestCase):
    """基础测试类"""

    def test_import(self):
        """测试导入"""
        from LiteAgent import LiteAgent
        self.assertIsNotNone(LiteAgent)


if __name__ == "__main__":
    unittest.main()
