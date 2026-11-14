package com.example.notes.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** WebDAV 同步配置 (密码在落盘前用 KeystoreSecret 加密) */
data class SyncConfig(
    val serverUrl: String = "",
    val username: String = "",
    val appPassword: String = "",
    val remoteDir: String = "qingjian",
    val enabled: Boolean = false,
    val autoSync: Boolean = false,
    val allowSelfSigned: Boolean = false,
    val lastSyncAt: Long = 0L,
    val lastSyncSummary: String = ""
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && appPassword.isNotBlank()
}

/**
 * WebDAV 配置存储。
 * 与 AppLockStore 一样用 SharedPreferences + StateFlow, 让设置页能实时刷新;
 * 密码单独走 KeystoreSecret 加密, 不写明文。
 */
class SyncConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<SyncConfig> = _config.asStateFlow()

    fun save(config: SyncConfig) {
        prefs.edit {
            putString(KEY_SERVER, config.serverUrl.trim())
            putString(KEY_USER, config.username.trim())
            putString(KEY_PASSWORD, KeystoreSecret.encrypt(config.appPassword))
            putString(KEY_DIR, config.remoteDir.trim())
            putBoolean(KEY_ENABLED, config.enabled)
            putBoolean(KEY_AUTO_SYNC, config.autoSync)
            putBoolean(KEY_ALLOW_SELF_SIGNED, config.allowSelfSigned)
        }
        _config.value = load()
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        _config.value = load()
    }

    fun markSynced(summary: String, at: Long = System.currentTimeMillis()) {
        prefs.edit {
            putLong(KEY_LAST_SYNC_AT, at)
            putString(KEY_LAST_SUMMARY, summary)
        }
        _config.value = load()
    }

    private fun load(): SyncConfig = SyncConfig(
        serverUrl = prefs.getString(KEY_SERVER, "").orEmpty(),
        username = prefs.getString(KEY_USER, "").orEmpty(),
        appPassword = KeystoreSecret.decrypt(prefs.getString(KEY_PASSWORD, "").orEmpty()),
        remoteDir = prefs.getString(KEY_DIR, "qingjian").orEmpty(),
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        autoSync = prefs.getBoolean(KEY_AUTO_SYNC, false),
        allowSelfSigned = prefs.getBoolean(KEY_ALLOW_SELF_SIGNED, false),
        lastSyncAt = prefs.getLong(KEY_LAST_SYNC_AT, 0L),
        lastSyncSummary = prefs.getString(KEY_LAST_SUMMARY, "").orEmpty()
    )

    companion object {
        private const val PREFS = "webdav_sync_prefs"
        private const val KEY_SERVER = "server_url"
        private const val KEY_USER = "username"
        private const val KEY_PASSWORD = "app_password_enc"
        private const val KEY_DIR = "remote_dir"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_ALLOW_SELF_SIGNED = "allow_self_signed"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_SUMMARY = "last_sync_summary"
    }
}
