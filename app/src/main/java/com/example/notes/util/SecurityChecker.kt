package com.example.notes.util

import android.content.Context
import android.os.Build
import timber.log.Timber
import java.io.File

/**
 * F20: 运行时安全检测工具。
 *
 * 在 MainActivity.onCreate 时调用, 检测以下威胁:
 * 1) Root — su / Magisk / SuperUser 等常见 Root 指标
 * 2) 调试器 — Debug.isDebuggerConnected + android.os.Debug
 * 3) 模拟器 — Build.FINGERPRINT / Build.HARDWARE / Build.PRODUCT 等特征
 * 4) Hook 框架 — Xposed / Frida / EdXposed 堆栈特征
 * 5) 签名校验 — 防止重打包 (对比当前 APK 签名与预期指纹)
 *
 * 策略: 检测到威胁时记录日志 + 返回警告, 不强制退出 (避免影响正常用户)。
 * 后续可在设置页增加"安全警告"提示。
 */
object SecurityChecker {

    /** 安全检测结果 */
    data class SecurityReport(
        val isRooted: Boolean = false,
        val isDebuggerAttached: Boolean = false,
        val isEmulator: Boolean = false,
        val isHookFrameworkDetected: Boolean = false,
        val isSignatureValid: Boolean = true,
        val warnings: List<String> = emptyList()
    ) {
        /** 是否存在任何安全威胁 */
        val hasThreats: Boolean
            get() = isRooted || isDebuggerAttached || isEmulator ||
                isHookFrameworkDetected || !isSignatureValid
    }

    /**
     * 执行全部安全检测。
     * 建议在后台线程调用 (涉及文件 I/O)。
     */
    fun check(context: Context): SecurityReport {
        val warnings = mutableListOf<String>()

        val isRooted = checkRoot(warnings)
        val isDebuggerAttached = checkDebugger(warnings)
        val isEmulator = checkEmulator(warnings)
        val isHookDetected = checkHookFramework(warnings)
        val isSignatureValid = checkSignature(context, warnings)

        if (warnings.isNotEmpty()) {
            Timber.tag("Security").w("Security threats detected: %s", warnings.joinToString("; "))
        }

        return SecurityReport(
            isRooted = isRooted,
            isDebuggerAttached = isDebuggerAttached,
            isEmulator = isEmulator,
            isHookFrameworkDetected = isHookDetected,
            isSignatureValid = isSignatureValid,
            warnings = warnings
        )
    }

    // ============================================================
    // Root 检测
    // ============================================================
    private fun checkRoot(warnings: MutableList<String>): Boolean {
        val rootIndicators = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/su/bin/su",
            "/magisk/.core/bin/magiskdb"
        )

        // 检查文件是否存在
        for (path in rootIndicators) {
            if (File(path).exists()) {
                warnings.add("Root 指标文件存在: $path")
                return true
            }
        }

        // 检查 which su
        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val result = process.inputStream.bufferedReader().readText().trim()
            if (result.isNotEmpty()) {
                warnings.add("which su 返回: $result")
                return true
            }
        } catch (_: Exception) { }

        // 检查 Build.TAGS (包含 test-keys 通常表示 Root/自定义 ROM)
        if (Build.TAGS != null && Build.TAGS.contains("test-keys")) {
            warnings.add("Build.TAGS 包含 test-keys")
            return true
        }

        return false
    }

    // ============================================================
    // 调试器检测
    // ============================================================
    private fun checkDebugger(warnings: MutableList<String>): Boolean {
        if (android.os.Debug.isDebuggerConnected()) {
            warnings.add("调试器已连接 (Debug.isDebuggerConnected)")
            return true
        }
        if (android.os.Debug.waitingForDebugger()) {
            warnings.add("正在等待调试器连接")
            return true
        }
        return false
    }

    // ============================================================
    // 模拟器检测
    // ============================================================
    private fun checkEmulator(warnings: MutableList<String>): Boolean {
        val indicators = mutableListOf<String>()

        // Build.FINGERPRINT
        if (Build.FINGERPRINT.contains("generic")) {
            indicators.add("Build.FINGERPRINT=generic")
        }
        // Build.HARDWARE
        if (Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.HARDWARE.contains("vbox")
        ) {
            indicators.add("Build.HARDWARE=${Build.HARDWARE}")
        }
        // Build.MODEL
        if (Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for")
        ) {
            indicators.add("Build.MODEL=${Build.MODEL}")
        }
        // Build.MANUFACTURER
        if (Build.MANUFACTURER.contains("Genymotion") ||
            Build.MANUFACTURER.contains("unknown")
        ) {
            indicators.add("Build.MANUFACTURER=${Build.MANUFACTURER}")
        }
        // Build.PRODUCT
        if (Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("vbox") ||
            Build.PRODUCT.contains("emulator")
        ) {
            indicators.add("Build.PRODUCT=${Build.PRODUCT}")
        }

        // 检查 QEMU 属性
        val qemuProps = listOf(
            "ro.hardware", "ro.product.device", "ro.build.display.id"
        )
        for (prop in qemuProps) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("getprop", prop))
                val value = process.inputStream.bufferedReader().readText().trim()
                if (value.contains("goldfish") || value.contains("ranchu")) {
                    indicators.add("getprop $prop=$value")
                }
            } catch (_: Exception) { }
        }

        if (indicators.isNotEmpty()) {
            warnings.add("模拟器指标: ${indicators.joinToString(", ")}")
            return true
        }
        return false
    }

    // ============================================================
    // Hook 框架检测
    // ============================================================
    private fun checkHookFramework(warnings: MutableList<String>): Boolean {
        // 方法 1: 堆栈检查
        try {
            val stackTrace = Thread.currentThread().stackTrace
            for (element in stackTrace) {
                val className = element.className
                if (className.contains("de.robv.android.xposed") ||
                    className.contains("com.saurik.substrate") ||
                    className.contains("com.android.internal.os.ZygoteInit")
                ) {
                    warnings.add("Hook 框架堆栈特征: $className")
                    return true
                }
            }
        } catch (_: Exception) { }

        // 方法 2: 检查 Xposed 相关文件
        val xposedPaths = listOf(
            "/system/framework/XposedBridge.jar",
            "/system/lib/libxposed_art.so",
            "/system/lib64/libxposed_art.so"
        )
        for (path in xposedPaths) {
            if (File(path).exists()) {
                warnings.add("Xposed 文件存在: $path")
                return true
            }
        }

        // 方法 3: 检查 Frida 相关
        try {
            val process = Runtime.getRuntime().exec(arrayOf("ps", "-A"))
            val output = process.inputStream.bufferedReader().readText()
            if (output.contains("frida-server") || output.contains("frida-agent")) {
                warnings.add("Frida 进程运行中")
                return true
            }
        } catch (_: Exception) { }

        return false
    }

    // ============================================================
    // 签名校验 — 防止重打包
    // ============================================================
    private fun checkSignature(context: Context, warnings: MutableList<String>): Boolean {
        return try {
            val expected = "78D54DA9C3536987FC80F09F8BDF3474C4E51148"
            val actual = getSha1Hex(context)
            if (actual != expected) {
                warnings.add("APK 签名不匹配! 期望=$expected 实际=$actual")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            Timber.tag("Security").w(e, "签名校验异常")
            true // 校验失败时不阻塞正常使用
        }
    }
}
