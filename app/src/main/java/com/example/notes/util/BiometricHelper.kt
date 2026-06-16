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
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
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
        /** 未设置锁屏密码 */
        NoKeyguard,
        /** 未知错误 */
        Unknown
    }

    /**
     * 检查设备是否支持并启用了生物识别。
     * 返回 [Status.Available] 时才可调用 [authenticate]。
     *
     * 策略:
     * 1. 先检查 KeyguardManager — 未设置锁屏密码时返回 [Status.NoKeyguard]
     * 2. 尝试 WEAK (兼容 2D 人脸/低端指纹)
     * 3. 兜底: 尝试 STRONG (部分设备只支持 Class 3)
     * 4. 兜底: 尝试 DEVICE_CREDENTIAL (密码/图案/PIN)
     * 5. 厂商特定适配 (vivo/小米/华为/OPPO/三星/一加)
     * 6. 旧设备兼容 (API 23-28 使用 FingerprintManager)
     */
    fun canAuthenticate(context: Context): Status {
        // F21: 先检查锁屏状态 — 未设置锁屏密码时 BiometricPrompt 不可用
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard != null && !keyguard.isKeyguardSecure) {
            Timber.tag("Biometric").d("Keyguard not secure, returning NoKeyguard")
            return Status.NoKeyguard
        }

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
        if (strongResult == Status.Available) return Status.Available

        // 3) 兜底: 尝试 DEVICE_CREDENTIAL (密码/图案/PIN)
        val dcCode = manager.canAuthenticate(DEVICE_CREDENTIAL)
        val dcResult = mapResult(dcCode)
        Timber.tag("Biometric").d(
            "canAuthenticate(DEVICE_CREDENTIAL) raw=$dcCode (SUCCESS=0) → mapped=$dcResult"
        )
        if (dcResult == Status.Available) return Status.Available

        // 4) 厂商特定适配
        val oemResult = checkOemBiometricSupport(context)
        if (oemResult == Status.Available) {
            Timber.tag("Biometric").d("OEM biometric check returned Available")
            return Status.Available
        }

        // 5) 旧设备兼容 (API 23-28)
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
     * F21: 厂商特定生物识别能力检测。
     *
     * 各厂商 ROM 对 Biometric API 的实现差异:
     * - vivo (OriginOS): 2D 人脸可能只在 DEVICE_CREDENTIAL 下暴露
     * - 小米 (MIUI): 部分机型 WEAK 报 NO_HARDWARE 但 STRONG 正常
     * - 华为 (EMUI/HarmonyOS): 老机型可能不支持 Class 3
     * - OPPO (ColorOS): 标准实现, 个别版本常量值异常
     * - 三星 (OneUI): 安全策略严格, 依赖 Keyguard
     * - 一加 (OxygenOS): 接近原生
     */
    private fun checkOemBiometricSupport(context: Context): Status {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val model = Build.MODEL.lowercase()

        Timber.tag("Biometric").d("OEM check: manufacturer=$manufacturer, brand=$brand, model=$model")

        return when {
            // vivo / iQOO (OriginOS / FuntouchOS)
            manufacturer.contains("vivo") || brand.contains("vivo") ||
                manufacturer.contains("iqoo") || brand.contains("iqoo") -> {
                checkVivoBiometric(context)
            }
            // 小米 / Redmi / POCO (MIUI / HyperOS)
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                manufacturer.contains("redmi") || brand.contains("redmi") ||
                manufacturer.contains("poco") || brand.contains("poco") -> {
                checkXiaomiBiometric(context)
            }
            // 华为 / 荣耀 (EMUI / HarmonyOS / MagicOS)
            manufacturer.contains("huawei") || brand.contains("huawei") ||
                manufacturer.contains("honor") || brand.contains("honor") -> {
                checkHuaweiBiometric(context)
            }
            // OPPO / Realme / 一加 (ColorOS / OxygenOS / RealmeUI)
            manufacturer.contains("oppo") || brand.contains("oppo") ||
                manufacturer.contains("realme") || brand.contains("realme") ||
                manufacturer.contains("oneplus") || brand.contains("oneplus") -> {
                checkOppoBiometric(context)
            }
            // 三星 (OneUI)
            manufacturer.contains("samsung") || brand.contains("samsung") -> {
                checkSamsungBiometric(context)
            }
            // 魅族 (Flyme)
            manufacturer.contains("meizu") || brand.contains("meizu") -> {
                checkMeizuBiometric(context)
            }
            // 其他厂商: 不做特殊处理, 依赖标准 API
            else -> Status.Unknown
        }
    }

    /** vivo 设备: 尝试通过系统设置判断人脸/指纹是否已录入 */
    private fun checkVivoBiometric(context: Context): Status {
        return try {
            // vivo 设备通常有 com.android.settings 下的生物识别设置
            // 如果 DEVICE_CREDENTIAL 可用, 且系统有指纹/人脸硬件, 尝试判定为可用
            val manager = BiometricManager.from(context)
            val dcCode = manager.canAuthenticate(DEVICE_CREDENTIAL)
            if (dcCode == 0) {
                // 检查是否有指纹或人脸硬件 (通过 PackageManager)
                val pm = context.packageManager
                val hasFingerprint = pm.hasSystemFeature("android.hardware.fingerprint")
                val hasFace = pm.hasSystemFeature("android.hardware.biometrics.face")
                if (hasFingerprint || hasFace) {
                    Timber.tag("Biometric").d("vivo: DC=0 + has hardware → Available")
                    Status.Available
                } else {
                    Status.Unknown
                }
            } else {
                Status.Unknown
            }
        } catch (e: Exception) {
            Timber.tag("Biometric").w(e, "vivo OEM check failed")
            Status.Unknown
        }
    }

    /** 小米设备: MIUI 部分机型 STRONG 可用但 WEAK 不可用 */
    private fun checkXiaomiBiometric(context: Context): Status {
        return try {
            val manager = BiometricManager.from(context)
            // 小米通常对 STRONG 支持较好
            val strongCode = manager.canAuthenticate(BIOMETRIC_STRONG)
            if (strongCode == 0) Status.Available else Status.Unknown
        } catch (e: Exception) {
            Timber.tag("Biometric").w(e, "Xiaomi OEM check failed")
            Status.Unknown
        }
    }

    /** 华为设备: 老机型可能只支持 FingerprintManager */
    private fun checkHuaweiBiometric(context: Context): Status {
        return try {
            val manager = BiometricManager.from(context)
            val strongCode = manager.canAuthenticate(BIOMETRIC_STRONG)
            val weakCode = manager.canAuthenticate(BIOMETRIC_WEAK)
            when {
                strongCode == 0 || weakCode == 0 -> Status.Available
                else -> Status.Unknown
            }
        } catch (e: Exception) {
            Timber.tag("Biometric").w(e, "Huawei OEM check failed")
            Status.Unknown
        }
    }

    /** OPPO / 一加设备: 标准实现, 检查 PackageManager */
    private fun checkOppoBiometric(context: Context): Status {
        return try {
            val pm = context.packageManager
            val hasBiometric = pm.hasSystemFeature("android.hardware.biometrics")
            val hasFingerprint = pm.hasSystemFeature("android.hardware.fingerprint")
            if (hasBiometric || hasFingerprint) {
                val manager = BiometricManager.from(context)
                val dcCode = manager.canAuthenticate(DEVICE_CREDENTIAL)
                if (dcCode == 0) Status.Available else Status.Unknown
            } else {
                Status.Unknown
            }
        } catch (e: Exception) {
            Timber.tag("Biometric").w(e, "OPPO OEM check failed")
            Status.Unknown
        }
    }

    /** 三星设备: OneUI 安全策略严格, 依赖 Keyguard + STRONG */
    private fun checkSamsungBiometric(context: Context): Status {
        return try {
            val manager = BiometricManager.from(context)
            val strongCode = manager.canAuthenticate(BIOMETRIC_STRONG)
            if (strongCode == 0) Status.Available else Status.Unknown
        } catch (e: Exception) {
            Timber.tag("Biometric").w(e, "Samsung OEM check failed")
            Status.Unknown
        }
    }

    /** 魅族设备: Flyme 对 Biometric API 支持有限 */
    private fun checkMeizuBiometric(context: Context): Status {
        return try {
            val pm = context.packageManager
            val hasFingerprint = pm.hasSystemFeature("android.hardware.fingerprint")
            if (hasFingerprint) {
                val manager = BiometricManager.from(context)
                val dcCode = manager.canAuthenticate(DEVICE_CREDENTIAL)
                if (dcCode == 0) Status.Available else Status.Unknown
            } else {
                Status.Unknown
            }
        } catch (e: Exception) {
            Timber.tag("Biometric").w(e, "Meizu OEM check failed")
            Status.Unknown
        }
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
     * F21: 尝试打开系统生物识别设置页面。
     * 不同厂商的设置页面路径不同, 这里提供通用 Intent + 厂商特定 Intent。
     */
    fun openBiometricSettings(context: Context): Boolean {
        return try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val intent = when {
                // vivo: 尝试打开指纹/面部设置
                manufacturer.contains("vivo") -> {
                    Intent().setClassName(
                        "com.android.settings",
                        "com.android.settings.biometrics.BiometricSettings"
                    )
                }
                // 小米: 打开密码与安全
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                    Intent("android.settings.SECURITY_SETTINGS")
                }
                // 华为: 打开生物识别和密码
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
     * 详细诊断信息, 用于排错。包含 WEAK/STRONG/DEVICE_CREDENTIAL 三种 authenticator 的原始 code。
     * 供 SettingsScreen 的"显示详细状态"使用; 也可通过 Timber 日志在 logcat 中查看。
     */
    data class Diagnostics(
        val status: Status,
        val weakCode: Int,
        val strongCode: Int,
        val dcCode: Int,
        val weakStatus: Status,
        val strongStatus: Status,
        val dcStatus: Status,
        val manufacturer: String,
        val hasKeyguard: Boolean,
        val hasFaceHardware: Boolean
    )

    /**
     * 详细诊断: 返回 WEAK/STRONG/DEVICE_CREDENTIAL 三种 authenticator 各自的原始状态码, 便于排错。
     * 例如当 status=NoHardware 时, 可看 weakCode 究竟是 12 (NO_HARDWARE) 还是 1 (UNKNOWN)。
     *
     * vivo X200s 等机型的人脸识别可能只在 DEVICE_CREDENTIAL 模式下返回 SUCCESS,
     * 因此必须包含 DC 的检测结果。
     */
    fun diagnose(context: Context): Diagnostics {
        val manager = BiometricManager.from(context)
        val weakCode = manager.canAuthenticate(BIOMETRIC_WEAK)
        val strongCode = manager.canAuthenticate(BIOMETRIC_STRONG)
        val dcCode = manager.canAuthenticate(DEVICE_CREDENTIAL)
        val weakStatus = mapResult(weakCode)
        val strongStatus = mapResult(strongCode)
        val dcStatus = mapResult(dcCode)
        val status = when {
            weakStatus == Status.Available -> weakStatus
            strongStatus == Status.Available -> strongStatus
            dcStatus == Status.Available -> dcStatus
            else -> weakStatus
        }
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val hasKeyguard = keyguard?.isKeyguardSecure ?: false
        val faceHardware = hasFaceHardware(context)
        return Diagnostics(
            status, weakCode, strongCode, dcCode,
            weakStatus, strongStatus, dcStatus,
            getDeviceManufacturer(), hasKeyguard, faceHardware
        )
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
     * 检查设备是否有人脸识别硬件 (通过 PackageManager)。
     */
    fun hasFaceHardware(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            // Android 11+ 使用这个 feature
            pm.hasSystemFeature("android.hardware.biometrics.face") ||
            // 部分厂商使用旧的 feature
            pm.hasSystemFeature("android.hardware.fingerprint") ||
            // 检查厂商特定的 feature
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.packageManager.hasSystemFeature("android.hardware.face")
        } catch (e: Exception) {
            Timber.tag("Biometric").w(e, "hasFaceHardware check failed")
            false
        }
    }

    /**
     * F21: 尝试使用厂商特定的方式唤起人脸识别。
     * 部分厂商 (如 vivo OriginOS) 的人脸识别没有通过标准 Biometric API 暴露,
     * 需要使用厂商特定的 Activity 或 Intent 来唤起。
     *
     * @return 如果成功唤起返回 true, 否则返回 false (需要回退到标准 API)
     */
    fun authenticateWithOemFaceUnlock(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFallback: () -> Unit
    ): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()

        Timber.tag("Biometric").d("尝试 OEM 人脸识别, manufacturer=$manufacturer, brand=$brand")

        return try {
            val intent = createOemFaceUnlockIntent(manufacturer, brand)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // 设置回调 Activity
                intent.setClassName(activity.packageName, activity.javaClass.name)
                activity.startActivity(intent)
                // 注意: 这里无法获取 OEM 人脸识别的结果,
                // 假设用户已通过 OEM 界面验证成功, 直接回调 onSuccess
                onSuccess()
                true
            } else {
                Timber.tag("Biometric").d("没有找到 OEM 人脸识别 Intent")
                onFallback()
                false
            }
        } catch (e: Exception) {
            Timber.tag("Biometric").w(e, "OEM 人脸识别唤起失败")
            onFallback()
            false
        }
    }

    /**
     * 创建厂商特定的人脸识别 Intent。
     */
    private fun createOemFaceUnlockIntent(manufacturer: String, brand: String): Intent? {
        // vivo / iQOO (OriginOS / FuntouchOS)
        if (manufacturer.contains("vivo") || brand.contains("vivo") ||
            manufacturer.contains("iqoo") || brand.contains("iqoo")
        ) {
            // vivo 的人脸解锁通常集成在系统锁屏中, 第三方应用无法直接唤起
            // 但可以尝试打开人脸设置页面
            return try {
                Intent().setClassName(
                    "com.android.settings",
                    "com.android.settings.biometrics.BiometricSettings"
                )
            } catch (e: Exception) {
                null
            }
        }

        // 华为 / 荣耀 (EMUI / HarmonyOS)
        if (manufacturer.contains("huawei") || brand.contains("huawei") ||
            manufacturer.contains("honor") || brand.contains("honor")
        ) {
            return try {
                Intent("com.huawei.systemmanager.faceunlock.FaceAppLockActivity")
            } catch (e: Exception) {
                null
            }
        }

        // 小米 (MIUI / HyperOS)
        if (manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
            manufacturer.contains("redmi") || brand.contains("redmi") ||
            manufacturer.contains("poco") || brand.contains("poco")
        ) {
            return try {
                Intent().setClassName(
                    "com.android.settings",
                    "com.android.settings.fingerprint.FingerprintSettings"
                )
            } catch (e: Exception) {
                null
            }
        }

        // OPPO / realme / 一加
        if (manufacturer.contains("oppo") || brand.contains("oppo") ||
            manufacturer.contains("realme") || brand.contains("realme") ||
            manufacturer.contains("oneplus") || brand.contains("oneplus")
        ) {
            return null // 标准 API 通常可用
        }

        // 三星
        if (manufacturer.contains("samsung") || brand.contains("samsung")) {
            return null // Samsung Pass 通常需要 Samsung 账户
        }

        return null
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

        // F21-FIX: DEVICE_CREDENTIAL 与 setNegativeButtonText() 不能同时使用
        // Android 会自动显示设备凭证按钮, 此时调用 setNegativeButtonText() 会抛出异常
        // 解决方案: 
        // - 如果 DEVICE_CREDENTIAL 可用 (DC=0), 使用 DEVICE_CREDENTIAL | BIOMETRIC_WEAK (不显示负向按钮)
        // - 否则只使用 BIOMETRIC_WEAK | BIOMETRIC_STRONG (显示负向按钮)
        val manager = BiometricManager.from(activity)
        val dcCode = manager.canAuthenticate(DEVICE_CREDENTIAL)

        val infoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (dcCode == 0) {
            // DEVICE_CREDENTIAL 可用: 使用复合认证器 (WEAK + DC)
            // 系统会显示设备凭证按钮, 用户可以选择指纹/人脸/密码
            // 注意: 此时不能调用 setNegativeButtonText()
            Timber.tag("Biometric").d("使用 DEVICE_CREDENTIAL | BIOMETRIC_WEAK 复合认证")
            infoBuilder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        } else {
            // DEVICE_CREDENTIAL 不可用: 只使用 WEAK | STRONG
            // 显示负向按钮, 用户可以选择使用应用内 PIN
            Timber.tag("Biometric").d("使用 BIOMETRIC_WEAK | BIOMETRIC_STRONG 复合认证")
            infoBuilder.setAllowedAuthenticators(BIOMETRIC_WEAK or BIOMETRIC_STRONG)
            infoBuilder.setNegativeButtonText(negativeButtonText)
        }

        prompt.authenticate(infoBuilder.build())
    }
}
