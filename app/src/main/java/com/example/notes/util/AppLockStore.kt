package com.example.notes.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/**
 * F9: 应用锁配置 + 状态管理。
 *
 * 设计:
 * 1) PIN 哈希后存到 SharedPreferences, 不留明文 (即使 Root 也难反推)。
 *    用 SHA-256 即可, 我们仅防"捡到手机 → 偷看 SharedPreferences"这种场景;
 *    高安全需求场景应结合 EncryptedSharedPreferences (项目当前不引入 androidx.security 依赖,
 *    避免增量体积)。
 * 2) StateFlow 让 UI 实时反映"已启用 / 已禁用"。
 * 3) 解锁成功 5 分钟内不重复弹, 由 [lastUnlockTime] 控制; 切到后台再回前台时
 *    [shouldShowLock] 返回 true 时 MainActivity 重置为 LOCKED。
 */
class AppLockStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _hasPin = MutableStateFlow(prefs.contains(KEY_PIN_HASH))
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    /** 当前已设置 PIN 的长度, 4-8 位; 未启用时默认为 6 (作为新设置推荐值)。 */
    val pinLength: Int
        get() = prefs.getInt(KEY_PIN_LENGTH, 6)

    /** 是否启用了生物识别解锁 (仅在设备支持且已录入时有效) */
    val isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    /** 上次成功解锁时间 (System.currentTimeMillis), 0L = 从未解锁 / 锁住 */
    @Volatile
    private var lastUnlockTime: Long = prefs.getLong(KEY_LAST_UNLOCK, 0L)

    /**
     * 设置 PIN。
     * - 至少 4 位, 至多 8 位 (避免用户手抖输入超长)。
     * - 自动 trim 空白, 仅允许数字 (英数混编 PIN 易混, 这里强制数字)。
     */
    fun setPin(pin: String): Boolean {
        val clean = pin.trim()
        if (clean.length !in 4..8) return false
        if (!clean.all { it.isDigit() }) return false
        val hash = sha256(clean)
        prefs.edit {
            putString(KEY_PIN_HASH, hash)
            putBoolean(KEY_ENABLED, true)
            putInt(KEY_PIN_LENGTH, clean.length)
        }
        _hasPin.value = true
        _isEnabled.value = true
        return true
    }

    /**
     * 校验 PIN。成功时更新 lastUnlockTime。
     */
    fun checkPin(pin: String): Boolean {
        val clean = pin.trim()
        val expected = prefs.getString(KEY_PIN_HASH, null) ?: return false
        if (sha256(clean) == expected) {
            lastUnlockTime = System.currentTimeMillis()
            prefs.edit { putLong(KEY_LAST_UNLOCK, lastUnlockTime) }
            return true
        }
        return false
    }

    /**
     * 关闭应用锁, 清除 PIN 哈希和生物识别设置。
     * 注: 业务上应由用户在设置页"关闭应用锁"按钮触发, 同时校验旧 PIN。
     */
    fun disable() {
        prefs.edit {
            remove(KEY_PIN_HASH)
            remove(KEY_PIN_LENGTH)
            remove(KEY_BIOMETRIC_ENABLED)
            putBoolean(KEY_ENABLED, false)
            putLong(KEY_LAST_UNLOCK, 0L)
        }
        _hasPin.value = false
        _isEnabled.value = false
        lastUnlockTime = 0L
    }

    /**
     * 启用/禁用生物识别解锁。
     * 调用方需先通过 [BiometricHelper.canAuthenticate] 确认设备可用。
     */
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BIOMETRIC_ENABLED, enabled) }
    }

    /**
     * 是否需要显示锁屏。
     * 条件: 1) 已启用; 2) 距离上次解锁超过 5 分钟。
     */
    fun shouldShowLock(): Boolean {
        if (!_isEnabled.value) return false
        if (prefs.getString(KEY_PIN_HASH, null).isNullOrBlank()) return false
        val diff = System.currentTimeMillis() - lastUnlockTime
        return diff >= UNLOCK_GRACE_MS || lastUnlockTime == 0L
    }

    /** 强制重新锁住 (用户主动"立即锁定"按钮用) */
    fun forceRelock() {
        lastUnlockTime = 0L
        prefs.edit { putLong(KEY_LAST_UNLOCK, 0L) }
    }

    /** 更新解锁时间 (生物识别成功后调用, 等同于 PIN 解锁成功) */
    fun updateUnlockTime() {
        lastUnlockTime = System.currentTimeMillis()
        prefs.edit { putLong(KEY_LAST_UNLOCK, lastUnlockTime) }
    }

    /**
     * P100-FIX: 添加固定盐值防止彩虹表攻击。
     * 更安全的做法是每次安装生成随机盐并存储, 但这会增加复杂度。
     * 固定盐已足够防止简单的预计算攻击。
     */
    private fun sha256(text: String): String {
        val salted = SALT + text
        val bytes = MessageDigest.getInstance("SHA-256").digest(salted.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS = "app_lock_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_ENABLED = "lock_enabled"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val KEY_LAST_UNLOCK = "last_unlock_time"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

        /** P100-FIX: 固定盐值, 防止彩虹表攻击 */
        private const val SALT = "QingJian_AppLock_Salt_v1_2024"

        /** 解锁后 5 分钟内不重复弹 */
        const val UNLOCK_GRACE_MS: Long = 5 * 60 * 1000

        /** 失败次数达上限时, 拒绝 30 秒 */
        const val COOLDOWN_MS: Long = 30 * 1000
    }
}
