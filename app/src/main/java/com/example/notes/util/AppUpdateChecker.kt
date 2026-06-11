package com.example.notes.util

import android.content.Context
import android.util.Log
import com.example.notes.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 *
 * 启动时 (NotesApplication) 会后台调用 [checkForUpdate], 结果同时写入内存缓存
 * 与 SharedPreferences, 供主屏 (NotesListScreen) 检测到新版本时弹出 SnackBar 提示。
 */
object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"
    private const val GITHUB_API = "https://api.github.com/repos/ppaqtt/phone_app/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/ppaqtt/phone_app/releases"
    private const val PREFS_NAME = "app_update_checker"
    private const val KEY_LAST_KNOWN_VERSION = "last_known_version"
    private const val KEY_LAST_CHECK_AT = "last_check_at"

    /**
     * 网络不可用时的兜底最新版本号。后续每次正式发版时手动同步。
     */
    const val FALLBACK_LATEST_VERSION = "1.15.0"

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
     * P-FIX-001: 检查是否有更新, 优先用 [fetchLatestRelease], 失败回退到 [FALLBACK_LATEST_VERSION]。
     *
     * 并发安全:
     * - [fetchMutex] 串行化网络层调用, 避免启动 + 手动并发时发出两次 GitHub API 请求。
     * - [inFlightJob] 共享同一个网络 Job, 第二个调用者直接 await 第一个的结果。
     *
     * 结果持久化:
     * - 内存缓存: [cachedRemote] / [cachedAt] (6 小时有效)
     * - 持久化: 把 "上次发现的新版本号" 写入 SharedPreferences, 供 App 主屏
     *   启动后检测到新版本时弹出 SnackBar。
     *
     * @param forceRefresh 跳过内存缓存, 强制重新请求 (设置页"立即检查"用)
     * @param persistContext 用于写入 SharedPreferences, 启动检查时必传, 手动检查可不传
     */
    suspend fun checkForUpdate(
        forceRefresh: Boolean = false,
        persistContext: Context? = null
    ): UpdateCheckResult = fetchMutex.withLock {
        // 已有进行中的网络任务, 共享结果
        val inflight = inFlightJob
        val remote: ReleaseInfo? = if (inflight != null && !forceRefresh) {
            inflight.await()
        } else {
            // 用 CompletableDeferred 串行化网络层, 第二个 await 第一个的结果
            val deferred = CompletableDeferred<ReleaseInfo?>()
            inFlightJob = deferred
            try {
                val result = if (forceRefresh || shouldFetchRemote()) {
                    fetchLatestRelease().getOrNull().also { cacheRemoteResult(it) }
                } else {
                    cachedRemote
                }
                deferred.complete(result)
                result
            } catch (t: Throwable) {
                deferred.complete(null)
                throw t
            } finally {
                if (inFlightJob === deferred) inFlightJob = null
            }
        }
        val current = currentVersion()
        val effective = remote?.version ?: FALLBACK_LATEST_VERSION
        val result = UpdateCheckResult(
            currentVersion = current,
            latestVersion = effective,
            releaseInfo = remote,
            hasUpdate = isNewerAvailable(current, effective),
            errorMessage = if (remote == null) "网络异常, 已使用本地版本对比" else null
        )
        // 把"上次发现的新版本"持久化, 供主屏冷启动后弹出 SnackBar
        if (persistContext != null && result.hasUpdate) {
            persistLastKnownVersion(persistContext, effective)
        }
        result
    }

    /**
     * 读取上次持久化的 "发现的新版本号"; 若当前已是该版本或更新, 返回 null。
     * 供 NotesListScreen 在冷启动后判断是否弹更新提示。
     */
    fun consumePendingUpdateTip(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastKnown = prefs.getString(KEY_LAST_KNOWN_VERSION, null) ?: return null
        val current = currentVersion()
        // 当前版本已经 >= lastKnown, 清掉持久化标记
        if (!isNewerAvailable(current, lastKnown)) {
            prefs.edit().remove(KEY_LAST_KNOWN_VERSION).apply()
            return null
        }
        return lastKnown
    }

    /** 主屏 SnackBar 点击 "查看" 后清掉持久化标记, 避免重复弹 */
    fun clearPendingUpdateTip(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_KNOWN_VERSION)
            .apply()
    }

    private fun persistLastKnownVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_KNOWN_VERSION, version)
            .putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis())
            .apply()
    }

    // P-FIX-002: 内存缓存 + Mutex 串行化网络层, 避免并发请求击 GitHub API
    @Volatile private var cachedRemote: ReleaseInfo? = null
    @Volatile private var cachedAt: Long = 0L
    private val cacheValidMillis = 6 * 60 * 60 * 1000L  // 6 小时

    private val fetchMutex = Mutex()
    private var inFlightJob: CompletableDeferred<ReleaseInfo?>? = null

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
