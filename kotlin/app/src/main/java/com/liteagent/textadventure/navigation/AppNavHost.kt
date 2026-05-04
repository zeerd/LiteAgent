package com.liteagent.textadventure.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.liteagent.textadventure.ui.main.MainScreen
import com.liteagent.textadventure.ui.settings.SettingsScreen
import com.liteagent.textadventure.ui.newstory.HistoryScreen
import com.liteagent.textadventure.ui.newstory.NewStoryScreen

/**
 * 应用程序的全局导航宿主。
 * 定义了所有屏幕之间的跳转逻辑和路由。
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.MAIN_ROUTE, // 默认进入主聊天界面
        modifier = modifier
    ) {
        // 主聊天界面
        composable(Destinations.MAIN_ROUTE) {
            MainScreen(
                onNavigateToSettings = {
                    navController.navigate(Destinations.SETTINGS_ROUTE)
                },
                onNavigateToNewStory = {
                    navController.navigate(Destinations.NEW_STORY_ROUTE)
                }
            )
        }

        // 设置界面
        composable(Destinations.SETTINGS_ROUTE) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // 创建新故事界面
        composable(Destinations.NEW_STORY_ROUTE) {
            NewStoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // 历史记录详情界面（如果需要独立跳转）
        composable("${Destinations.HISTORY_ROUTE}/{$Destinations.ARG_STORY_ID}") { backStackEntry ->
            val storyId = backStackEntry.arguments?.getString(Destinations.ARG_STORY_ID) ?: ""
            HistoryScreen(
                storyId = storyId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
