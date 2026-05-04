package com.liteagent.textadventure

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.google.android.material.color.DynamicColors
import com.liteagent.textadventure.ui.theme.TextAdventureTheme
import com.liteagent.textadventure.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * 应用程序的主入口 Activity。
 * 使用 @AndroidEntryPoint 启用 Hilt 注入。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启用全屏显示（边缘到边缘布局）
        enableEdgeToEdge()

        // 如果设备支持，应用动态色彩（Material You）
        DynamicColors.applyToActivityIfAvailable(this)

        setContent {
            // 应用自定义的 Compose 主题
            TextAdventureTheme {
                // 主容器界面
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 初始化导航控制器并设置导航宿主
                    val navController = rememberNavController()
                    AppNavHost(navController = navController)
                }
            }
        }
    }
}
