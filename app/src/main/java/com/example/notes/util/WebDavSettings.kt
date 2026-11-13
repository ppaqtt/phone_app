package com.example.notes.util

import android.content.Context
import android.content.SharedPreferences

/**
 * WebDAV 配置 (坚果云 / 自建 NAS 等)。
 *
 * 默认指向坚果云: 服务器 `https://dav.jianguoyun.com/dav/`,
 * 用户名 = 坚果云账号, 密码 = 在「安全选项 → 第三方应用管理」里生成的应用密码
 * (不是登录密码)。应用密码可随时吊销, 比存登录密码安全。
 */
class WebDavSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, DEFAULT_SERVER)?.trim().orEmpty().ifBlank { DEFAULT_SERVER }
        set(value) {
            prefs.edit().putString(KEY_SERVER, value.trim()).apply()
        }

    var username: String
        get() = prefs.getString(KEY_USERNAME, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_USERNAME, value.trim()).apply()
        }

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_PASSWORD, value).apply()
        }

    /** 远端目录 (相对服务器根路径), 默认 qingjian */
    var remoteDir: String
        get() = prefs.getString(KEY_REMOTE_DIR, DEFAULT_DIR)?.trim().orEmpty().ifBlank { DEFAULT_DIR }
        set(value) {
            prefs.edit().putString(KEY_REMOTE_DIR, value.trim()).apply()
        }

    fun isConfigured(): Boolean =
        serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    private fun normalizedServer(): String {
        val raw = serverUrl.trim()
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    /** 服务器根 URL, 末尾带 `/` */
    fun serverRootUrl(): String = normalizedServer()

    /** 远端目录 URL, 末尾不带 `/` */
    fun directoryUrl(): String {
        val dir = remoteDir.trim().trim('/')
        return normalizedServer() + dir
    }

    /** 同步文件 URL (notes.json) */
    fun fileUrl(): String = directoryUrl() + "/" + FILE_NAME

    companion object {
        const val DEFAULT_SERVER = "https://dav.jianguoyun.com/dav/"
        const val DEFAULT_DIR = "qingjian"
        const val FILE_NAME = "notes.json"

        private const val PREFS_NAME = "webdav_pref"
        private const val KEY_SERVER = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REMOTE_DIR = "remote_dir"
    }
}
