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

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.MAIN_ROUTE,
        modifier = modifier
    ) {
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
        
        composable(Destinations.SETTINGS_ROUTE) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Destinations.NEW_STORY_ROUTE) {
            NewStoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
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
