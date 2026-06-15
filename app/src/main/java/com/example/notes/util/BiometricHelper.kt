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
     */
    fun canAuthenticate(context: Context): Status {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Status.Available
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Status.NoHardware
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Status.HwUnavailable
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Status.NoneEnrolled
            else -> Status.Unknown
        }
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
                override fun onAuthenticationSucceeded(result: AuthenticationResult) {
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
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .setNegativeButtonText(negativeButtonText)
            .build()

        prompt.authenticate(info)
    }
}
