package com.example.notes.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * APK 备案元数据读取工具。
 *
 * ICP 备案时, 备案系统需要:
 *  - 应用包名 (applicationId) — 从 `packageName` 取
 *  - 版本名 (versionName) — 从 PackageInfo.versionName 取
 *  - 版本号 (versionCode) — 从 PackageInfo.versionCode 取
 *  - 应用签名 SHA1 — 运行时从 PackageInfo 的签名证书取
 *  - 应用签名 MD5 — 同上, 换 MessageDigest 算法
 *
 * 注意:
 *  - minSdk = 24 (低于 API 28), 所以必须兼容两个分支:
 *    - API 28+ (Android 9.0+): `GET_SIGNING_CERTIFICATES` + `SigningInfo`
 *    - API 24-27:              `GET_SIGNATURES` + `Signature[]`
 *  - APK 签名**不**等于 keystore 文件路径, 而是 [android.content.pm.Signature] 对象
 *  - 计算 SHA1/MD5 用的是 `Signature.toByteArray()` (X.509 DER 编码)
 */
data class ApkSignature(
    val sha1: String,
    val md5: String
) {
    override fun toString(): String = "SHA1: $sha1\nMD5: $md5"
}

/** APK 备案元数据汇总 */
data class ApkMetadata(
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val signature: ApkSignature?
)

/**
 * 读取当前 APP 的包名 / 版本信息。
 *
 * @param context Application context
 * @return [ApkMetadata] (signature 可能为 null, 当读取失败时)
 */
fun readApkMetadata(context: Context): ApkMetadata {
    val pm = context.packageManager
    val pkg = context.packageName
    val packageInfo = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        return ApkMetadata(
            applicationId = pkg,
            versionName = "unknown",
            versionCode = 0,
            signature = null
        )
    }

    val versionName = packageInfo.versionName ?: "unknown"
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode
    }
    val signature = readSignatureFromPackageInfo(pm, pkg, packageInfo)

    return ApkMetadata(
        applicationId = pkg,
        versionName = versionName,
        versionCode = versionCode,
        signature = signature
    )
}

/** 提取签名证书并计算 SHA1 / MD5 */
private fun readSignatureFromPackageInfo(
    pm: PackageManager,
    pkg: String,
    packageInfo: android.content.pm.PackageInfo
): ApkSignature? {
    return try {
        val bytes: ByteArray = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo
                ?: pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                ?: return null
            val sigs = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            sigs.firstOrNull()?.toByteArray() ?: return null
        } else {
            @Suppress("DEPRECATION")
            val sigs = packageInfo.signatures
            sigs?.firstOrNull()?.toByteArray() ?: return null
        }
        val sha1 = digestHex(bytes, "SHA1")
        val md5 = digestHex(bytes, "MD5")
        ApkSignature(sha1 = sha1, md5 = md5)
    } catch (e: Exception) {
        null
    }
}

/** 用 [MessageDigest] 计算 hex 字符串 (大写, 冒号分隔) */
private fun digestHex(bytes: ByteArray, algorithm: String): String {
    val md = MessageDigest.getInstance(algorithm)
    val digest = md.digest(bytes)
    return digest.joinToString(separator = ":") { byte ->
        "%02X".format(byte.toInt() and 0xFF)
    }
}
