package com.liteagent.textadventure.data.local

import com.liteagent.textadventure.model.StorySetting
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 预定义故事设置的数据源。
 * 提供可选的游戏背景场景。
 */
@Singleton
class StorySettingsDataSource @Inject constructor() {
    // 所有的故事背景设置列表
    private val _storySettings = MutableStateFlow<List<StorySetting>>(emptyList())
    val storySettings: StateFlow<List<StorySetting>> = _storySettings.asStateFlow()

    // 当前用户选择的故事设置
    private val _selectedSetting = MutableStateFlow<StorySetting?>(null)
    val selectedSetting: StateFlow<StorySetting?> = _selectedSetting.asStateFlow()

    init {
        // 初始化时加载预设的数据
        initializeStorySettings()
    }

    /**
     * 初始化一些硬编码的故事背景。
     */
    private fun initializeStorySettings() {
        val settings = listOf(
            StorySetting(
                id = "fantasy",
                title = "奇幻冒险",
                description = "在一个充满魔法生物、古老预言和强大神器的魔幻国度展开一场史诗般的探索。",
                genre = StorySetting.Genre.FANTASY
            ),
            StorySetting(
                id = "scifi",
                title = "科幻悬疑",
                description = "在深空站调查宇宙异常现象，揭秘公司阴谋和外星遗迹。",
                genre = StorySetting.Genre.SCIFI
            ),
            StorySetting(
                id = "horror",
                title = "恐怖惊悚",
                description = "在废弃庄园里度过一夜，那里往事阴魂不散，阴影也有了自己的生命。",
                genre = StorySetting.Genre.HORROR
            ),
            StorySetting(
                id = "romance",
                title = "浪漫剧情",
                description = "在一段关于意外连接、艰难抉择和原谅力量的故事中体验爱与心碎。",
                genre = StorySetting.Genre.ROMANCE
            ),
            StorySetting(
                id = "mystery",
                title = "侦探推理",
                description = "在黑色电影风格的世界里破解复杂的案件，那里充满了秘密、谎言和道德模糊点。",
                genre = StorySetting.Genre.MYSTERY
            )
        )

        _storySettings.value = settings
    }

    /**
     * 选择一个故事背景。
     */
    fun selectSetting(setting: StorySetting) {
        _selectedSetting.value = setting
    }

    /**
     * 清除当前选择。
     */
    fun clearSelection() {
        _selectedSetting.value = null
    }

    /**
     * 根据 ID 异步获取背景设置。
     */
    suspend fun getSettingById(settingId: String): StorySetting? {
        return _storySettings.value.find { it.id == settingId }
    }
}
