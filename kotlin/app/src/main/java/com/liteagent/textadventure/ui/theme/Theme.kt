package com.liteagent.textadventure.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 浅色主题颜色方案配置。
 */
private val LightColorScheme = lightColorScheme(
    primary = Purple80,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = PurpleGrey40,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Pink80,
    onTertiary = Color.White,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = Color(0xFF31111D),
    error = Pink80,
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF8FDFF),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFF8FDFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = PurpleGrey80,
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E)
)

/**
 * 深色主题颜色方案配置。
 */
private val DarkColorScheme = darkColorScheme(
    primary = DarkPurple80,
    onPrimary = DarkPurple85,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkPink60,
    onSecondary = Color(0xFF1D192B),
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = DarkCyan50,
    onTertiary = Color(0xFF31111D),
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = DarkPink60,
    onError = DarkPurple85,
    errorContainer = DarkPurple85,
    onErrorContainer = Color(0xFFFFDAD6),
    background = SurfaceDark,
    onBackground = Color(0xFFE6E1E5),
    surface = SurfaceDark,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = DarkPurple85,
    onSurfaceVariant = Color(0xFFD7D0DB),
    outline = Color(0xFF98929A)
)

/**
 * 应用程序主题入口。
 * 支持动态色彩 (Material You) 和系统深色模式。
 */
@Composable
fun TextAdventureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Android 12+ 动态色彩支持
    content: @Composable () -> Unit
) {
    // 决定使用哪种颜色方案
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 设置状态栏和导航栏颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // 应用 MaterialTheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
