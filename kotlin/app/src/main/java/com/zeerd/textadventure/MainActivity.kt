package com.zeerd.textadventure

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.navigation.compose.rememberNavController
import com.google.android.material.color.DynamicColors
import com.zeerd.textadventure.ui.theme.TextAdventureTheme
import com.zeerd.textadventure.navigation.AppNavHost
import com.zeerd.textadventure.data.local.AppSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 应用程序的主入口 Activity。
 * 使用 @AndroidEntryPoint 启用 Hilt 注入。
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 应用保存的语言设置
        val settings = appSettingsRepository.getSettings()
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(settings.language)
        Log.d("MainActivity", "Current saved lang: ${settings.language}, AppLocales: ${AppCompatDelegate.getApplicationLocales().toLanguageTags()}")
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != appLocale.toLanguageTags()) {
            Log.i("MainActivity", "Applying locale: ${settings.language}")
            AppCompatDelegate.setApplicationLocales(appLocale)
        }

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
