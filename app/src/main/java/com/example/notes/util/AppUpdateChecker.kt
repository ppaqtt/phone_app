package com.example.notes.util

import com.example.notes.BuildConfig

/**
 * 应用版本检查工具
 *
 * 实际项目中可改为请求远端版本接口, 这里先用本地常量做演示。
 */
object AppUpdateChecker {
    /** 远端"最新"版本号 (硬编码, 真实场景替换为网络请求结果) */
    const val LATEST_VERSION = "1.0.1"

    /** 当前包版本号 */
    fun currentVersion(): String = BuildConfig.VERSION_NAME

    /**
     * 比较 BuildConfig.VERSION_NAME 与 LATEST_VERSION, 返回是否有新版本。
     * 当本地版本 >= 远端版本时, 返回 false (无更新)。
     */
    fun isUpdateAvailable(): Boolean {
        val local = currentVersion()
        return compareVersions(local, LATEST_VERSION) < 0
    }

    /**
     * 解析 "1.2.3" -> [1, 2, 3]
     */
    fun parseVersion(version: String): List<Int> =
        version.split(".").mapNotNull { it.toIntOrNull() }

    /**
     * 比较两个版本号: a < b 返回负数, a > b 返回正数, 相等返回 0
     */
    fun compareVersions(a: String, b: String): Int {
        val pa = parseVersion(a)
        val pb = parseVersion(b)
        val len = maxOf(pa.size, pb.size)
        for (i in 0 until len) {
            val ai = pa.getOrElse(i) { 0 }
            val bi = pb.getOrElse(i) { 0 }
            if (ai != bi) return ai - bi
        }
        return 0
    }
}
