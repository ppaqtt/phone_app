package com.example.notes.util

import android.util.Log
import com.example.notes.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 应用更新检查工具
 *
 * 真实场景下从 GitHub Releases API 拉取最新版本, 网络失败时回退到 [FALLBACK_LATEST_VERSION]。
 * 数据源仓库: https://github.com/ppaqtt/phone_app/releases
 */
object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"
    private const val GITHUB_API = "https://api.github.com/repos/ppaqtt/phone_app/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/ppaqtt/phone_app/releases"

    /**
     * 网络不可用时的兜底最新版本号。后续每次正式发版时手动同步。
     */
    const val FALLBACK_LATEST_VERSION = "1.0.9"

    /** 当前包版本号 (来自 build.gradle.kts versionName) */
    fun currentVersion(): String = BuildConfig.VERSION_NAME

    /** 当前包版本号 + debug 后缀 */
    fun currentVersionDisplay(): String {
        val raw = BuildConfig.VERSION_NAME
        return if (BuildConfig.DEBUG) "$raw-debug" else raw
    }

    /** 本地比较版本号: 当前 < latest 返回 true */
    fun isNewerAvailable(current: String, latest: String): Boolean =
        compareVersions(current, latest) < 0

    /**
     * 真实从 GitHub 拉取最新 release, 在 [Dispatchers.IO] 上执行。
     * 失败时返回 [Result.failure]。
     */
    suspend fun fetchLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(GITHUB_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "QingJian-Android/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("GitHub API HTTP ${response.code}")
                }
                val body = response.body?.string()
                    ?: throw IllegalStateException("GitHub API empty body")
                val json = JSONObject(body)
                val tag = json.optString("tag_name").removePrefix("v").ifBlank { FALLBACK_LATEST_VERSION }
                val name = json.optString("name").ifBlank { tag }
                val notes = json.optString("body")
                val htmlUrl = json.optString("html_url").ifBlank { RELEASES_PAGE }
                val publishedAt = json.optString("published_at")
                ReleaseInfo(
                    version = tag,
                    name = name,
                    notes = notes,
                    htmlUrl = htmlUrl,
                    publishedAt = publishedAt
                )
            }
        }.onFailure { Log.w(TAG, "fetchLatestRelease failed, fallback to $FALLBACK_LATEST_VERSION", it) }
    }

    /**
     * 检查是否有更新, 优先用 [fetchLatestRelease], 失败回退到 [FALLBACK_LATEST_VERSION]。
     * P31: 同一天重复调用 fetchLatestRelease 会命中内存缓存, 减少 GitHub API 频率滥用。
     * @return [UpdateCheckResult] 包含是否需要更新 + 远端版本信息 + 错误原因 (若有)。
     */
    suspend fun checkForUpdate(forceRefresh: Boolean = false): UpdateCheckResult {
        val current = currentVersion()
        val remote = if (forceRefresh || shouldFetchRemote()) {
            fetchLatestRelease().getOrNull().also { cacheRemoteResult(it) }
        } else {
            cachedRemote
        }
        val effective = remote?.version ?: FALLBACK_LATEST_VERSION
        return UpdateCheckResult(
            currentVersion = current,
            latestVersion = effective,
            releaseInfo = remote,
            hasUpdate = isNewerAvailable(current, effective),
            errorMessage = if (remote == null) "网络异常, 已使用本地版本对比" else null
        )
    }

    // P31: 当日缓存逻辑, 避免反复点 "检查更新" 重复请求
    private var cachedRemote: ReleaseInfo? = null
    private var cachedAt: Long = 0L
    private val cacheValidMillis = 6 * 60 * 60 * 1000L  // 6 小时

    private fun shouldFetchRemote(): Boolean {
        val now = System.currentTimeMillis()
        return cachedRemote == null || (now - cachedAt) > cacheValidMillis
    }

    private fun cacheRemoteResult(info: ReleaseInfo?) {
        if (info != null) {
            cachedRemote = info
            cachedAt = System.currentTimeMillis()
        }
    }

    /**
     * 解析 "1.2.3" -> [1, 2, 3]
     */
    fun parseVersion(version: String): List<Int> =
        version.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }

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

    /** GitHub release 简要信息 */
    data class ReleaseInfo(
        val version: String,
        val name: String,
        val notes: String,
        val htmlUrl: String,
        val publishedAt: String
    )

    /** 检查结果 */
    data class UpdateCheckResult(
        val currentVersion: String,
        val latestVersion: String,
        val releaseInfo: ReleaseInfo?,
        val hasUpdate: Boolean,
        val errorMessage: String?
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}
