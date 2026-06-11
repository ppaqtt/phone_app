package com.example.notes.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
)

/**
 * F11: 主题色覆盖。按 [ColorTheme] 选不同的 primary, light/dark 各一组。
 * 实现: 复制默认 [lightColorScheme] / [darkColorScheme], 改 primary 系字段即可。
 */
private fun lightColorsWith(primary: Color) = LightColors.copy(
    primary = primary,
    primaryContainer = primary.copy(alpha = 0.18f),
    onPrimaryContainer = primary,
    secondary = primary
)

private fun darkColorsWith(primary: Color) = DarkColors.copy(
    primary = primary,
    primaryContainer = primary.copy(alpha = 0.30f),
    onPrimaryContainer = primary,
    secondary = primary
)

/** F11: 主题色 → 对应 primary Color (浅色) */
private fun colorThemePrimary(c: ColorTheme): Color = when (c) {
    ColorTheme.TEAL -> Primary
    ColorTheme.BLUE -> Color(0xFF1976D2)
    ColorTheme.PURPLE -> Color(0xFF7B1FA2)
    ColorTheme.GREEN -> Color(0xFF388E3C)
    ColorTheme.ORANGE -> Color(0xFFE65100)
}

@Composable
fun NotesAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    // F7/F8/F11: 通过 [ThemePreference] 注入; 默认取 Composable 上下文中订阅的最新值
    pref: ThemePref = rememberThemePref(),
    content: @Composable () -> Unit
) {
    // F7: 主题模式 (SYSTEM/LIGHT/DARK)
    val effectiveDark = when (pref.darkMode) {
        DarkMode.SYSTEM -> darkTheme
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }

    // F11: 是否使用动态色 (跟随系统取色, 优先 F11 自定义主色; 系统不支持动态色时回落到 F11)
    val useColorTheme = !dynamicColor ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        pref.colorTheme != ColorTheme.TEAL  // 选了非默认色, 强制走自定义

    val colorScheme = when {
        useColorTheme -> if (effectiveDark)
            darkColorsWith(colorThemePrimary(pref.colorTheme))
        else
            lightColorsWith(colorThemePrimary(pref.colorTheme))
        effectiveDark -> DarkColors
        else -> LightColors
    }

    // F8: 字号缩放
    val typography: Typography = if (pref.fontScale == FontScale.MEDIUM) {
        AppTypography
    } else {
        AppTypography.scale(pref.fontScale.scale)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !effectiveDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}

/**
 * F8: 把 [Typography] 按 scale 缩放。Material3 没有自带 scale 扩展,
 * 简单实现: 把 fontSize * scale。sp 单位本身会跟系统字体大小相乘,
 * 这里相当于"app 自身再叠一层"缩放。
 */
private fun Typography.scale(s: Float): Typography {
    fun androidx.compose.ui.text.TextStyle.s(): androidx.compose.ui.text.TextStyle =
        copy(fontSize = fontSize * s, lineHeight = lineHeight * s)
    return Typography(
        displayLarge = displayLarge.s(),
        displayMedium = displayMedium.s(),
        displaySmall = displaySmall.s(),
        headlineLarge = headlineLarge.s(),
        headlineMedium = headlineMedium.s(),
        headlineSmall = headlineSmall.s(),
        titleLarge = titleLarge.s(),
        titleMedium = titleMedium.s(),
        titleSmall = titleSmall.s(),
        bodyLarge = bodyLarge.s(),
        bodyMedium = bodyMedium.s(),
        bodySmall = bodySmall.s(),
        labelLarge = labelLarge.s(),
        labelMedium = labelMedium.s(),
        labelSmall = labelSmall.s()
    )
}
