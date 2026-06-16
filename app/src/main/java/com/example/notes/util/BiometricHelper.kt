package com.example.notes.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import timber.log.Timber

/**
 * F19: 生物识别解锁辅助类。
 *
 * 职责:
 * 1) 检测设备是否支持生物识别 (指纹/人脸) — [canAuthenticate]
 * 2) 启动 BiometricPrompt 进行认证 — [authenticate]
 * 3) 与 AppLockStore 配合, 生物识别成功后更新解锁时间
 *
 * 使用 BIOMETRIC_WEAK (Class 2) 以兼容更多设备 (包含 2D 人脸识别);
 * 若需要更高安全性可用 BIOMETRIC_STRONG (Class 3, 仅指纹/3D 人脸)。
 */
object BiometricHelper {

    /** 设备生物识别能力状态 */
    enum class Status {
        /** 可用且已录入 */
        Available,
        /** 设备无硬件 */
        NoHardware,
        /** 硬件当前不可用 */
        HwUnavailable,
        /** 有硬件但未录入生物特征 */
        NoneEnrolled,
        /** 未知错误 */
        Unknown
    }

    /**
     * 检查设备是否支持并启用了生物识别。
     * 返回 [Status.Available] 时才可调用 [authenticate]。
     *
     * 策略: 先尝试 WEAK (兼容 2D 人脸/低端指纹), 如果失败再尝试 STRONG (3D 指纹/人脸);
     * 两次都不通过才返回真实状态。某些 ROM (MIUI/EMUI) 在 WEAK 下报 NO_HARDWARE,
     * 但 STRONG 下能正常识别 — 这里把这种"只支持强认证"的设备也判定为可用。
     *
     * 同时检查 Keyguard 状态: 设备虽支持生物识别, 但如果当前未设置锁屏 (None/Swipe),
     * 出于安全考虑 Android 不会允许使用 BiometricPrompt — 此时也返回 NoneEnrolled,
     * 提示用户先设置屏幕锁。
     */
    fun canAuthenticate(context: Context): Status {
        val manager = BiometricManager.from(context)
        // 1) 优先尝试 WEAK (覆盖更广, 2D 人脸也算)
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
        return strongResult
    }

    private fun mapResult(code: Int): Status = when (code) {
        0 -> Status.Available
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Status.NoHardware
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Status.HwUnavailable
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Status.NoneEnrolled
        else -> Status.Unknown
    }

    /**
     * 详细诊断信息, 用于排错。包含 WEAK/STRONG 两种 authenticator 的原始 code。
     * 供 SettingsScreen 的"显示详细状态"使用; 也可通过 Timber 日志在 logcat 中查看。
     */
    data class Diagnostics(
        val status: Status,
        val weakCode: Int,
        val strongCode: Int,
        val weakStatus: Status,
        val strongStatus: Status
    )

    /**
     * 详细诊断: 返回 WEAK/STRONG 两种 authenticator 各自的原始状态码, 便于排错。
     * 例如当 status=NoHardware 时, 可看 weakCode 究竟是 12 (NO_HARDWARE) 还是 1 (UNKNOWN)。
     */
    fun diagnose(context: Context): Diagnostics {
        val manager = BiometricManager.from(context)
        val weakCode = manager.canAuthenticate(BIOMETRIC_WEAK)
        val strongCode = manager.canAuthenticate(BIOMETRIC_STRONG)
        val weakStatus = mapResult(weakCode)
        val strongStatus = mapResult(strongCode)
        val status = if (weakStatus == Status.Available) weakStatus else strongStatus
        return Diagnostics(status, weakCode, strongCode, weakStatus, strongStatus)
    }

    /**
     * 把原始 code 翻译成人话 (用于排错提示)。
     * BiometricManager 返回码:
     *  0 = BIOMETRIC_SUCCESS
     *  1 = BIOMETRIC_ERROR_UNKNOWN
     *  7 = BIOMETRIC_ERROR_HW_UNAVAILABLE
     *  9 = BIOMETRIC_ERROR_NONE_ENROLLED
     *  12 = BIOMETRIC_ERROR_NO_HARDWARE
     *  14 = BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED
     */
    private fun describeCode(code: Int): String = when (code) {
        0 -> "SUCCESS(0)"
        1 -> "UNKNOWN(1)"
        7 -> "HW_UNAVAILABLE(7)"
        9 -> "NONE_ENROLLED(9)"
        12 -> "NO_HARDWARE(12)"
        14 -> "SECURITY_UPDATE_REQUIRED(14)"
        else -> "OTHER($code)"
    }

    /**
     * 启动生物识别认证弹窗。
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
        title: String = "生物识别解锁",
        subtitle: String = "请验证您的身份",
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
                    // 指纹/人脸不匹配但不终止流程, 继续尝试
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_WEAK or BIOMETRIC_STRONG)
            .setNegativeButtonText(negativeButtonText)
            .build()

        prompt.authenticate(info)
    }
}
