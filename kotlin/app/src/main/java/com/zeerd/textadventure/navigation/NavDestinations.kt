package com.zeerd.textadventure.navigation

/**
 * 导航目的地定义。
 */
sealed class Destinations {
    data object MainDestination : Destinations()
    data object SettingsDestination : Destinations()
    data object NewStoryDestination : Destinations()
    data object HistoryDestination : Destinations()

    companion object {
        const val MAIN_ROUTE = "main" // 主聊天界面路由
        const val SETTINGS_ROUTE = "settings" // 设置界面路由
        const val NEW_STORY_ROUTE = "new_story" // 新故事创建界面路由
        const val HISTORY_ROUTE = "history" // 历史记录界面路由 (通常作为新故事界面的子状态)

        const val ARG_STORY_ID = "story_id" // 导航参数：故事 ID
    }
}
