package com.zeerd.textadventure.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.zeerd.textadventure.ui.main.MainScreen
import com.zeerd.textadventure.ui.settings.SettingsScreen
import com.zeerd.textadventure.ui.newstory.HistoryScreen
import com.zeerd.textadventure.ui.newstory.NewStoryScreen
import com.zeerd.textadventure.ui.historyedit.HistoryEditScreen

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
                },
                onNavigateToHistoryEdit = { storyId ->
                    navController.navigate("${Destinations.HISTORY_ROUTE}/$storyId")
                }
            )
        }

        // 历史记录详情界面（如果需要独立跳转）
        composable(
            route = "${Destinations.HISTORY_ROUTE}/{${Destinations.ARG_STORY_ID}}",
            arguments = listOf(
                navArgument(Destinations.ARG_STORY_ID) { type = NavType.StringType }
            )
        ) {
            HistoryEditScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onConfirmed = {
                    // 确认后跳转到主界面，MainViewModel 会自动加载最新的会话
                    navController.navigate(Destinations.MAIN_ROUTE) {
                        popUpTo(Destinations.MAIN_ROUTE) { inclusive = true }
                    }
                }
            )
        }
    }
}
