package com.qingjian.notes.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 权限引导页标记: 记录是否已向用户展示过一次性权限引导。
 * 首次启动展示，之后不再展示。
 */
object PermissionIntroPrefs {
    private const val PREFS_NAME = "permission_intro"
    private const val KEY_SHOWN = "intro_shown"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isShown(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOWN, false)
    }

    fun markShown(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_SHOWN, true).apply()
    }
}
