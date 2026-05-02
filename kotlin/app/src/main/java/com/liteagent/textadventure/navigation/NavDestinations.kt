package com.liteagent.textadventure.navigation

sealed class Destinations {
    data object MainDestination : Destinations()
    data object SettingsDestination : Destinations()
    data object NewStoryDestination : Destinations()
    data object HistoryDestination : Destinations()
    
    companion object {
        const val MAIN_ROUTE = "main"
        const val SETTINGS_ROUTE = "settings"
        const val NEW_STORY_ROUTE = "new_story"
        const val HISTORY_ROUTE = "history"
        
        const val ARG_STORY_ID = "story_id"
    }
}
