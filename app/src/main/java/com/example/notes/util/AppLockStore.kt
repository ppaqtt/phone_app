package com.example.notes.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * F9 + 功能5: 应用锁配置 + 状态管理。
 *
 * 支持两种解锁方式: PIN (数字密码) 或 Pattern (手势图案)。
 * 用户可在设置界面选择其一, 或切换到另一种。
 *
 * 设计:
 * 1) PIN / Pattern 哈希后存到 SharedPreferences, 不留明文 (即使 Root 也难反推)。
 *    F20: 哈希算法已移到 native 层 (NativeSecurity.hashPin), 盐值以异或混淆存储。
 * 2) StateFlow 让 UI 实时反映"已启用 / 已禁用 / 锁类型"。
 * 3) 解锁成功 5 分钟内不重复弹, 由 [lastUnlockTime] 控制; 切到后台再回前台时
 *    [shouldShowLock] 返回 true 时 MainActivity 重置为 LOCKED。
 *
 * 功能5: Pattern 存储格式 — 手势由一串数字组成 ("0,1,4,7,8"), 代表按下的点序号。
 * 为避免存储明文, 对整串字符串 (包含分隔符) 进行 SHA-256 加盐哈希。
 */
class AppLockStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _hasPin = MutableStateFlow(prefs.contains(KEY_PIN_HASH))
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    /** 功能5: 是否已有手势密码 */
    private val _hasPattern = MutableStateFlow(prefs.contains(KEY_PATTERN_HASH))
    val hasPattern: StateFlow<Boolean> = _hasPattern.asStateFlow()

    /** 功能5: 锁类型 — "PIN" 或 "PATTERN" */
    var lockType: String
        get() = prefs.getString(KEY_LOCK_TYPE, LOCK_TYPE_PIN) ?: LOCK_TYPE_PIN
        private set(value) {
            prefs.edit { putString(KEY_LOCK_TYPE, value) }
        }

    /** 当前已设置 PIN 的长度, 4-8 位; 未启用时默认为 6 (作为新设置推荐值)。 */
    val pinLength: Int
        get() = prefs.getInt(KEY_PIN_LENGTH, 6)

    /** 是否启用了指纹解锁 (仅在设备支持且已录入时有效) */
    val isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    /** 上次成功解锁时间 (System.currentTimeMillis), 0L = 从未解锁 / 锁住 */
    @Volatile
    private var lastUnlockTime: Long = prefs.getLong(KEY_LAST_UNLOCK, 0L)

    /**
     * 功能5: 切换锁类型。需要已有对应方式才能切换 (例如切到 PATTERN 前必须先设置过 PATTERN)。
     *
     * @return true 表示切换成功, false 表示目标类型尚未设置, 调用方应引导用户先去设置。
     */
    fun switchLockType(@Suppress("UNUSED_PARAMETER") type: String): Boolean {
        val valid = when (type) {
            LOCK_TYPE_PIN -> prefs.contains(KEY_PIN_HASH)
            LOCK_TYPE_PATTERN -> prefs.contains(KEY_PATTERN_HASH)
            else -> false
        }
        if (!valid) return false
        lockType = type
        return true
    }

    /**
     * 设置 PIN。
     * - 至少 4 位, 至多 8 位 (避免用户手抖输入超长)。
     * - 自动 trim 空白, 仅允许数字。
     * - F20: 哈希通过 NativeSecurity.hashPin 在 native 层计算。
     * - 设置 PIN 后自动将锁类型切到 PIN。
     */
    fun setPin(pin: String): Boolean {
        val clean = pin.trim()
        if (clean.length !in 4..8) return false
        if (!clean.all { it.isDigit() }) return false
        val hash = nativeHashPin(clean)
        prefs.edit {
            putString(KEY_PIN_HASH, hash)
            putBoolean(KEY_ENABLED, true)
            putInt(KEY_PIN_LENGTH, clean.length)
            putString(KEY_LOCK_TYPE, LOCK_TYPE_PIN)
        }
        _hasPin.value = true
        _hasPattern.value = prefs.contains(KEY_PATTERN_HASH)
        _isEnabled.value = true
        return true
    }

    /**
     * 功能5: 设置手势密码。
     * @param pattern 手势点序列, 以逗号分隔的字符串 (例如 "0,3,4,7")
     * @return true 设置成功, false 表示不符合最小点数
     */
    fun setPattern(pattern: String): Boolean {
        val clean = pattern.trim()
        val points = clean.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (points.size < MIN_PATTERN_POINTS) return false
        val hash = nativeHashPin(clean)
        prefs.edit {
            putString(KEY_PATTERN_HASH, hash)
            putBoolean(KEY_ENABLED, true)
            putString(KEY_LOCK_TYPE, LOCK_TYPE_PATTERN)
        }
        _hasPattern.value = true
        _hasPin.value = prefs.contains(KEY_PIN_HASH)
        _isEnabled.value = true
        return true
    }

    /**
     * 校验 PIN。成功时更新 lastUnlockTime。
     */
    fun checkPin(pin: String): Boolean {
        val clean = pin.trim()
        val expected = prefs.getString(KEY_PIN_HASH, null) ?: return false
        if (nativeHashPin(clean) == expected) {
            lastUnlockTime = System.currentTimeMillis()
            prefs.edit { putLong(KEY_LAST_UNLOCK, lastUnlockTime) }
            return true
        }
        return false
    }

    /**
     * 功能5: 校验手势密码。
     */
    fun checkPattern(pattern: String): Boolean {
        val clean = pattern.trim()
        val expected = prefs.getString(KEY_PATTERN_HASH, null) ?: return false
        if (nativeHashPin(clean) == expected) {
            lastUnlockTime = System.currentTimeMillis()
            prefs.edit { putLong(KEY_LAST_UNLOCK, lastUnlockTime) }
            return true
        }
        return false
    }

    /**
     * 关闭应用锁, 清除所有锁方式。
     */
    fun disable() {
        prefs.edit {
            remove(KEY_PIN_HASH)
            remove(KEY_PATTERN_HASH)
            remove(KEY_PIN_LENGTH)
            remove(KEY_LOCK_TYPE)
            remove(KEY_BIOMETRIC_ENABLED)
            putBoolean(KEY_ENABLED, false)
            putLong(KEY_LAST_UNLOCK, 0L)
        }
        _hasPin.value = false
        _hasPattern.value = false
        _isEnabled.value = false
        lastUnlockTime = 0L
    }

    /**
     * 启用/禁用指纹解锁。
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
        val hasAny = prefs.getString(KEY_PIN_HASH, null) != null ||
            prefs.getString(KEY_PATTERN_HASH, null) != null
        if (!hasAny) return false
        val diff = System.currentTimeMillis() - lastUnlockTime
        return diff >= UNLOCK_GRACE_MS || lastUnlockTime == 0L
    }

    /** 强制重新锁住 (用户主动"立即锁定"按钮用) */
    fun forceRelock() {
        lastUnlockTime = 0L
        prefs.edit { putLong(KEY_LAST_UNLOCK, 0L) }
    }

    /** 更新解锁时间 (指纹解锁成功后调用, 等同于 PIN 解锁成功) */
    fun updateUnlockTime() {
        lastUnlockTime = System.currentTimeMillis()
        prefs.edit { putLong(KEY_LAST_UNLOCK, lastUnlockTime) }
    }

    /**
     * F20: 调用 native 层 SHA-256 加盐哈希。
     * 若 native 库加载失败 (极端情况), 回退到 JVM 实现。
     */
    private fun nativeHashPin(pin: String): String {
        return try {
            NativeSecurity.hashPin(pin)
        } catch (e: UnsatisfiedLinkError) {
            Timber.tag("AppLock").w(e, "Native library not available, falling back to JVM SHA-256")
            jvmSha256(pin)
        }
    }

    /**
     * JVM 回退 SHA-256 (仅在 native 库不可用时使用)。
     * 盐值仍保留在代码中作为兜底, 但正常路径走 native。
     */
    private fun jvmSha256(text: String): String {
        val salted = SALT + text
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest(salted.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS = "app_lock_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_ENABLED = "lock_enabled"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val KEY_LAST_UNLOCK = "last_unlock_time"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_PATTERN_HASH = "pattern_hash"
        private const val KEY_LOCK_TYPE = "lock_type"

        const val LOCK_TYPE_PIN = "PIN"
        const val LOCK_TYPE_PATTERN = "PATTERN"

        /** P100-FIX: 固定盐值, 防止彩虹表攻击 (native 层以异或混淆存储) */
        private const val SALT = "QingJian_AppLock_Salt_v1_2024"

        /** 解锁后 5 分钟内不重复弹 */
        const val UNLOCK_GRACE_MS: Long = 5 * 60 * 1000

        /** 失败次数达上限时, 拒绝 30 秒 */
        const val COOLDOWN_MS: Long = 30 * 1000

        /** 功能5: 手势最少点数 (至少 4 个点) */
        const val MIN_PATTERN_POINTS = 4
    }
}
