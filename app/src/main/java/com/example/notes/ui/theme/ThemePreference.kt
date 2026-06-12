package com.example.notes.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.example.notes.NotesApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * F7: 主题设置 (深色 / 浅色 / 跟随系统), 持久化在 SharedPreferences。
 * F8: 字号设置 (小 / 中 / 大 / 超大), 同样持久化。
 * F11: 主题色 (teal / 蓝 / 紫 / 绿 / 橙), 同样持久化。
 *
 * 三个设置放在一个 Pref 里, 都是 [String], 共享一个 [StateFlow] 通知变化。
 */
class ThemePreference(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadAll())
    val state: StateFlow<ThemePref> = _state.asStateFlow()

    fun setDarkMode(value: DarkMode) {
        prefs.edit().putString(KEY_DARK_MODE, value.name).apply()
        _state.value = _state.value.copy(darkMode = value)
    }

    fun setFontScale(value: FontScale) {
        prefs.edit().putString(KEY_FONT_SCALE, value.name).apply()
        _state.value = _state.value.copy(fontScale = value)
    }

    fun setColorTheme(value: ColorTheme) {
        prefs.edit().putString(KEY_COLOR_THEME, value.name).apply()
        _state.value = _state.value.copy(colorTheme = value)
    }

    private fun loadAll(): ThemePref = ThemePref(
        darkMode = DarkMode.fromOrDefault(prefs.getString(KEY_DARK_MODE, null)),
        fontScale = FontScale.fromOrDefault(prefs.getString(KEY_FONT_SCALE, null)),
        colorTheme = ColorTheme.fromOrDefault(prefs.getString(KEY_COLOR_THEME, null))
    )

    companion object {
        private const val PREFS_NAME = "theme_pref"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_COLOR_THEME = "color_theme"
    }
}

data class ThemePref(
    val darkMode: DarkMode,
    val fontScale: FontScale,
    val colorTheme: ColorTheme
)

enum class DarkMode {
    /** 跟随系统 (默认) */
    SYSTEM,
    /** 强制浅色 */
    LIGHT,
    /** 强制深色 */
    DARK;

    companion object {
        fun fromOrDefault(s: String?): DarkMode =
            values().firstOrNull { it.name == s } ?: SYSTEM
    }
}

enum class FontScale(val scale: Float, val displayName: String) {
    SMALL(0.85f, "小"),
    MEDIUM(1.0f, "中"),
    LARGE(1.15f, "大"),
    XLARGE(1.3f, "超大");

    companion object {
        fun fromOrDefault(s: String?): FontScale =
            values().firstOrNull { it.name == s } ?: MEDIUM
    }
}

enum class ColorTheme(val displayName: String) {
    TEAL("青绿 (默认)"),
    BLUE("蓝"),
    PURPLE("紫"),
    GREEN("绿"),
    ORANGE("橙");

    companion object {
        fun fromOrDefault(s: String?): ColorTheme =
            values().firstOrNull { it.name == s } ?: TEAL
    }
}

/**
 * F7: 在 Composable 中获取当前 ThemePreference 实例 (单例, Application 持有)。
 * P115-FIX: 之前用 `remember(context) { ThemePreference(context) }` 每次 new
 * 一个, 状态不共享. 现在改为从 Application 拿单例, 所有 Composable 订阅同一个 StateFlow。
 */
@Composable
fun rememberThemePreference(): ThemePreference {
    val context = LocalContext.current
    val app = context.applicationContext as? NotesApplication
    // 在 Preview 等非 Application 环境下回落到 new 一个, 避免崩溃
    return when (app) {
        null -> ThemePreference(context)
        else -> app.themePreference
    }
}

/** F7: 在 Composable 中订阅 ThemePref 状态 */
@Composable
fun rememberThemePref(): ThemePref {
    val pref = rememberThemePreference()
    return pref.state.collectAsState().value
}
