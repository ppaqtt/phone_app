@file:Suppress("DEPRECATION")

package com.example.notes.util

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import timber.log.Timber

/**
 * F19: 指纹解锁辅助类 (指纹 + PIN)。
 *
 * 职责:
 * 1) 检测设备是否支持指纹识别 — [canAuthenticate]
 * 2) 启动 BiometricPrompt 进行认证 — [authenticate]
 * 3) 与 AppLockStore 配合, 指纹解锁成功后更新解锁时间
 *
 * 注意: 人脸识别已在 v1.20.17 中移除, 因为 vivo 等厂商不通过标准 API 暴露人脸识别能力。
 */
object BiometricHelper {

    /** 设备指纹识别能力状态 */
    enum class Status {
        /** 可用且已录入 */
        Available,
        /** 设备无硬件 */
        NoHardware,
        /** 硬件当前不可用 */
        HwUnavailable,
        /** 有硬件但未录入指纹 */
        NoneEnrolled,
        /** 未设置锁屏密码 */
        NoKeyguard,
        /** 未知错误 */
        Unknown
    }

    /**
     * 检查设备是否支持并启用了指纹识别。
     * 返回 [Status.Available] 时才可调用 [authenticate]。
     *
     * 策略:
     * 1. 先检查 KeyguardManager — 未设置锁屏密码时返回 [Status.NoKeyguard]
     * 2. 尝试 WEAK (兼容低端指纹)
     * 3. 兜底: 尝试 STRONG (部分设备只支持 Class 3)
     * 4. 旧设备兼容 (API 23-28 使用 FingerprintManager)
     */
    fun canAuthenticate(context: Context): Status {
        // F21: 先检查锁屏状态 — 未设置锁屏密码时 BiometricPrompt 不可用
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard != null && !keyguard.isKeyguardSecure) {
            Timber.tag("Biometric").d("Keyguard not secure, returning NoKeyguard")
            return Status.NoKeyguard
        }

        val manager = BiometricManager.from(context)

        // 1) 优先尝试 WEAK (覆盖更广)
        val weakCode = manager.canAuthenticate(BIOMETRIC_WEAK)
        val weakResult = mapResult(weakCode)
        Timber.tag("Biometric").d(
            "canAuthenticate(BIOMETRIC_WEAK) raw=$weakCode (SUCCESS=0) → mapped=$weakResult; " +
                "BIOMETRIC_SUCCESS const=${BiometricManager.BIOMETRIC_SUCCESS}"
        )
        if (weakResult == Status.Available) return Status.Available

        // 2) 兜底: 尝试 STRONG (部分设备只支持 Class 3)
        val strongCode = manager.canAuthenticate(BIOMETRIC_STRONG)
        val strongResult = mapResult(strongCode)
        Timber.tag("Biometric").d(
            "canAuthenticate(BIOMETRIC_STRONG) raw=$strongCode (SUCCESS=0) → mapped=$strongResult"
        )
        if (strongResult == Status.Available) return Status.Available

        // 3) 旧设备兼容 (API 23-28)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val legacyResult = checkLegacyFingerprintSupport(context)
            Timber.tag("Biometric").d("Legacy fingerprint check: $legacyResult")
            if (legacyResult == Status.Available) return Status.Available
            if (legacyResult != Status.Unknown) return legacyResult
        }

        // 返回最具体的状态 (优先 NoneEnrolled, 其次 NoHardware)
        return when {
            weakResult == Status.NoneEnrolled || strongResult == Status.NoneEnrolled -> Status.NoneEnrolled
            weakResult == Status.NoHardware && strongResult == Status.NoHardware -> Status.NoHardware
            weakResult == Status.HwUnavailable || strongResult == Status.HwUnavailable -> Status.HwUnavailable
            else -> weakResult
        }
    }

    private fun mapResult(code: Int): Status = when (code) {
        0 -> Status.Available
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Status.NoHardware
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Status.HwUnavailable
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Status.NoneEnrolled
        else -> Status.Unknown
    }

    /**
     * F21: 旧设备兼容 (API 23-28)。
     * Android 9 及以下没有 BiometricManager, 使用 FingerprintManager 检测。
     */
    @Suppress("DEPRECATION")
    private fun checkLegacyFingerprintSupport(context: Context): Status {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val fingerprintManager = context.getSystemService(Context.FINGERPRINT_SERVICE)
                    as? FingerprintManager
                if (fingerprintManager == null) {
                    Status.NoHardware
                } else if (!fingerprintManager.isHardwareDetected) {
                    Status.NoHardware
                } else if (!fingerprintManager.hasEnrolledFingerprints()) {
                    Status.NoneEnrolled
                } else {
                    Status.Available
                }
            } else {
                // API < 23: 完全不支持指纹
                Status.NoHardware
            }
        } catch (e: Exception) {
            Timber.tag("Biometric").w(e, "Legacy fingerprint check failed")
            Status.Unknown
        }
    }

    /**
     * F21: 获取厂商友好的显示名称。
     */
    fun getDeviceManufacturer(): String {
        val manufacturer = Build.MANUFACTURER
        val brand = Build.BRAND
        val model = Build.MODEL
        return when {
            manufacturer.contains("vivo", ignoreCase = true) || brand.contains("vivo", ignoreCase = true) -> "vivo"
            manufacturer.contains("iqoo", ignoreCase = true) || brand.contains("iqoo", ignoreCase = true) -> "iQOO"
            manufacturer.contains("xiaomi", ignoreCase = true) || brand.contains("xiaomi", ignoreCase = true) -> "小米"
            manufacturer.contains("redmi", ignoreCase = true) || brand.contains("redmi", ignoreCase = true) -> "Redmi"
            manufacturer.contains("poco", ignoreCase = true) || brand.contains("poco", ignoreCase = true) -> "POCO"
            manufacturer.contains("huawei", ignoreCase = true) || brand.contains("huawei", ignoreCase = true) -> "华为"
            manufacturer.contains("honor", ignoreCase = true) || brand.contains("honor", ignoreCase = true) -> "荣耀"
            manufacturer.contains("oppo", ignoreCase = true) || brand.contains("oppo", ignoreCase = true) -> "OPPO"
            manufacturer.contains("realme", ignoreCase = true) || brand.contains("realme", ignoreCase = true) -> "realme"
            manufacturer.contains("oneplus", ignoreCase = true) || brand.contains("oneplus", ignoreCase = true) -> "一加"
            manufacturer.contains("samsung", ignoreCase = true) || brand.contains("samsung", ignoreCase = true) -> "三星"
            manufacturer.contains("meizu", ignoreCase = true) || brand.contains("meizu", ignoreCase = true) -> "魅族"
            manufacturer.contains("google", ignoreCase = true) || brand.contains("google", ignoreCase = true) -> "Google"
            else -> "$manufacturer $model"
        }
    }

    /**
     * F21: 尝试打开系统指纹设置页面。
     * 不同厂商的设置页面路径不同, 这里提供通用 Intent + 厂商特定 Intent。
     */
    fun openBiometricSettings(context: Context): Boolean {
        return try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val brand = Build.BRAND.lowercase()
            val intent = when {
                // vivo / iQOO: 打开指纹设置
                manufacturer.contains("vivo") || brand.contains("vivo") ||
                manufacturer.contains("iqoo") || brand.contains("iqoo") -> {
                    Intent("android.settings.SECURITY_SETTINGS")
                }
                // 小米: 打开密码与安全
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                    Intent("android.settings.SECURITY_SETTINGS")
                }
                // 华为: 打开指纹和密码
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    Intent("android.settings.SECURITY_SETTINGS")
                }
                // 通用: 打开安全设置
                else -> {
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // 兜底: 打开通用安全设置
            try {
                val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS)
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
                true
            } catch (e2: Exception) {
                Timber.tag("Biometric").w(e2, "Failed to open biometric settings")
                false
            }
        }
    }

    /**
     * 启动指纹识别认证弹窗。
     *
     * @param activity 必须是 [FragmentActivity] (AppCompatActivity 继承自它)
     * @param title 弹窗标题
     * @param subtitle 弹窗副标题
     * @param negativeButtonText 负向按钮文字 (如 "使用 PIN 解锁")
     * @param onSuccess 认证成功回调
     * @param onError 认证失败/错误回调 (errorCode, errString)
     * @param onCancel 用户主动取消回调
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "指纹解锁",
        subtitle: String = "请验证您的指纹",
        negativeButtonText: String = "使用 PIN 解锁",
        onSuccess: () -> Unit,
        onError: (Int, String) -> Unit = { _, _ -> },
        onCancel: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    Timber.tag("Biometric").d("Authentication succeeded")
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Timber.tag("Biometric").w("Authentication error: $errorCode — $errString")
                    // 用户点击负向按钮 (errorCode = ERROR_NEGATIVE_BUTTON) 也算取消
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        onCancel()
                    } else {
                        onError(errorCode, errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    Timber.tag("Biometric").d("Authentication failed (not recognized)")
                    // 指纹不匹配但不终止流程, 继续尝试
                }
            }
        )

        val infoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_WEAK or BIOMETRIC_STRONG)
            .setNegativeButtonText(negativeButtonText)

        prompt.authenticate(infoBuilder.build())
    }
}
