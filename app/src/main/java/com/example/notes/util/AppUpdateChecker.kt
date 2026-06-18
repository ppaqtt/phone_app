package com.example.notes.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.notes.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * 应用更新检查工具
 *
 * P127-FIX: 针对国内网络环境优化。
 * GitHub 域名 (api.github.com / github.com / raw.githubusercontent.com) 在国内网络下
 * 经常被 DNS 污染或防火墙拦截, 导致更新检查始终失败。
 *
 * 解决方案:
 * 1. 优先使用国内可访问源 (Gitee 码云 / fastly.jsdelivr.net)
 * 2. 每个端点独立超时 3s, 快速失败切换
 * 3. 错误提示明确告知 "GitHub 在国内访问受限"
 * 4. 兜底版本始终可用
 *
 * 数据源仓库: https://github.com/ppaqtt/phone_app/releases
 */
object AppUpdateChecker {

    // ========== 端点配置 (按优先级排序) ==========

    /** 主端点 1: Gitee 码云 (国内 fastest) */
    private const val GITEE_API = "https://gitee.com/api/v5/repos/ppaqtt/phone_app/releases/latest"

    /** 主端点 2: fastly.jsdelivr.net CDN (国内加速) */
    private const val JSDELIVR_FASTLY = "https://fastly.jsdelivr.net/gh/ppaqtt/phone_app@main/VERSION"

    /** 备用端点 1: GitHub API (海外) */
    private const val GITHUB_API = "https://api.github.com/repos/ppaqtt/phone_app/releases/latest"

    /** 备用端点 2: GitHub Releases 重定向 */
    private const val GITHUB_RELEASES_PAGE = "https://github.com/ppaqtt/phone_app/releases/latest"

    /** 备用端点 3: raw.githubusercontent.com */
    private const val RAW_VERSION_URL = "https://raw.githubusercontent.com/ppaqtt/phone_app/main/VERSION"

    private const val PREFS_NAME = "app_update_checker"
    private const val KEY_LAST_KNOWN_VERSION = "last_known_version"
    private const val KEY_LAST_CHECK_AT = "last_check_at"

    /**
     * 网络不可用时的兜底最新版本号。
     * 必须与更新日志 [ChangelogData] 中的最新版本保持一致。
     */
    private const val FALLBACK_LATEST_VERSION = "1.20.23"

    fun currentVersion(): String = BuildConfig.VERSION_NAME

    fun currentVersionDisplay(): String {
        val raw = BuildConfig.VERSION_NAME
        return if (BuildConfig.DEBUG) "$raw-debug" else raw
    }

    fun isNewerAvailable(current: String, latest: String): Boolean =
        compareVersions(current, latest) < 0

    /**
     * 检查网络是否可用。
     * 只检查 INTERNET 能力, 不依赖 VALIDATED ( captive portal 环境下可用)。
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network: Network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    enum class NetworkErrorType {
        NO_NETWORK,
        DNS_FAILED,
        CONNECTION_TIMEOUT,
        SERVER_ERROR,
        API_ERROR,
        PARSE_ERROR,
        UNKNOWN
    }

    private class ClassifiedException(
        val type: NetworkErrorType,
        message: String,
        cause: Throwable? = null
    ) : Exception(message, cause)

    /**
     * P127-FIX: 多端点策略, 国内源优先。
     *
     * 优先级:
     * 1. Gitee API (国内 fastest, 返回完整 JSON)
     * 2. fastly.jsdelivr.net (国内 CDN, 纯文本版本)
     * 3. GitHub API (海外)
     * 4. GitHub Releases 重定向 (海外)
     * 5. raw.githubusercontent.com (海外)
     */
    suspend fun fetchLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        // 1. Gitee (国内优先)
        tryFetchGitee(GITEE_API, "Gitee")
            ?.let { return@withContext Result.success(it) }

        // 2. fastly.jsdelivr.net (国内 CDN)
        tryFetchSimpleVersion(JSDELIVR_FASTLY, "jsDelivr-fastly")
            ?.let { return@withContext Result.success(it) }

        // 3. GitHub API (海外)
        tryFetch(GITHUB_API, "GitHub API")
            ?.let { return@withContext Result.success(it) }

        // 4. GitHub Releases 重定向 (海外)
        tryFetchRedirect(GITHUB_RELEASES_PAGE, "GitHub Redirect")
            ?.let { return@withContext Result.success(it) }

        // 5. raw.githubusercontent.com (海外)
        tryFetchSimpleVersion(RAW_VERSION_URL, "GitHub Raw")
            ?.let { return@withContext Result.success(it) }

        // 全部失败
        Result.failure(
            ClassifiedException(
                NetworkErrorType.UNKNOWN,
                "所有更新检查端点均不可用"
            )
        )
    }

    /**
     * P127-FIX: Gitee API 适配。
     * Gitee 的 release API 返回格式与 GitHub 类似, 但字段名略有不同。
     */
    private suspend fun tryFetchGitee(url: String, sourceName: String): ReleaseInfo? =
        withContext(Dispatchers.IO) {
            repeat(2) { attempt ->
                if (attempt > 0) delay(500)
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Qingjian-Android/${BuildConfig.VERSION_NAME}")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Timber.tag("UpdateChecker").w("$sourceName HTTP ${response.code}")
                            return@withContext null
                        }
                        val body = response.body?.string()
                            ?: throw ClassifiedException(NetworkErrorType.PARSE_ERROR, "empty response")
                        val json = JSONObject(body)
                        // Gitee 字段: tag_name, name, body, html_url, published_at
                        val tag = json.optString("tag_name").removePrefix("v").ifBlank {
                            throw ClassifiedException(NetworkErrorType.PARSE_ERROR, "no tag_name")
                        }
                        return@withContext ReleaseInfo(
                            version = tag,
                            name = json.optString("name").ifBlank { tag },
                            notes = json.optString("body"),
                            htmlUrl = json.optString("html_url").ifBlank { GITHUB_RELEASES_PAGE },
                            publishedAt = json.optString("published_at")
                        )
                    }
                } catch (e: Exception) {
                    Timber.tag("UpdateChecker").w(e, "$sourceName failed")
                }
            }
            null
        }

    /**
     * 从纯文本 URL 读取版本号。
     */
    private suspend fun tryFetchSimpleVersion(url: String, sourceName: String): ReleaseInfo? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Qingjian-Android/${BuildConfig.VERSION_NAME}")
                    .build()
                // P127-FIX: 使用短超时 client 快速失败
                shortTimeoutClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.tag("UpdateChecker").w("$sourceName HTTP ${response.code}")
                        return@withContext null
                    }
                    val body = response.body?.string()?.trim()
                    if (body.isNullOrBlank()) {
                        Timber.tag("UpdateChecker").w("$sourceName empty body")
                        return@withContext null
                    }
                    val version = body.removePrefix("v").trim()
                    if (version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
                        Timber.tag("UpdateChecker").d("$sourceName: got version $version")
                        return@withContext ReleaseInfo(
                            version = version,
                            name = "v$version",
                            notes = "",
                            htmlUrl = GITHUB_RELEASES_PAGE,
                            publishedAt = ""
                        )
                    }
                    Timber.tag("UpdateChecker").w("$sourceName: invalid version format: $version")
                    null
                }
            } catch (e: Exception) {
                Timber.tag("UpdateChecker").w(e, "$sourceName failed")
                null
            }
        }

    /**
     * GitHub API 端点。
     */
    private suspend fun tryFetch(url: String, sourceName: String): ReleaseInfo? =
        withContext(Dispatchers.IO) {
            repeat(2) { attempt ->
                if (attempt > 0) delay(1000)
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "Qingjian-Android/${BuildConfig.VERSION_NAME}")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw ClassifiedException(
                                NetworkErrorType.API_ERROR,
                                "$sourceName HTTP ${response.code}"
                            )
                        }
                        val body = response.body?.string()
                            ?: throw ClassifiedException(NetworkErrorType.PARSE_ERROR, "empty response")
                        val json = JSONObject(body)
                        val tag = json.optString("tag_name").removePrefix("v").ifBlank {
                            throw ClassifiedException(NetworkErrorType.PARSE_ERROR, "no tag_name")
                        }
                        return@withContext ReleaseInfo(
                            version = tag,
                            name = json.optString("name").ifBlank { tag },
                            notes = json.optString("body"),
                            htmlUrl = json.optString("html_url").ifBlank { GITHUB_RELEASES_PAGE },
                            publishedAt = json.optString("published_at")
                        )
                    }
                } catch (e: Exception) {
                    Timber.tag("UpdateChecker").w(e, "$sourceName failed")
                }
            }
            null
        }

    /**
     * GitHub Releases 重定向解析。
     */
    private suspend fun tryFetchRedirect(url: String, sourceName: String): ReleaseInfo? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Qingjian-Android/${BuildConfig.VERSION_NAME}")
                    .build()
                client.newCall(request).execute().use { response ->
                    val finalUrl = response.request.url.toString()
                    val tagMatch = "v?([0-9]+\\.[0-9]+\\.[0-9]+)".toRegex()
                        .find(finalUrl)
                        ?.groupValues
                        ?.get(1)
                    if (tagMatch != null && tagMatch.isNotBlank()) {
                        Timber.tag("UpdateChecker").d("Parsed version $tagMatch from redirect URL")
                        return@withContext ReleaseInfo(
                            version = tagMatch,
                            name = "v$tagMatch",
                            notes = "",
                            htmlUrl = GITHUB_RELEASES_PAGE,
                            publishedAt = ""
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag("UpdateChecker").w(e, "$sourceName redirect fetch failed")
            }
            null
        }

    /**
     * 检查是否有更新。
     */
    suspend fun checkForUpdate(
        forceRefresh: Boolean = false,
        persistContext: Context? = null
    ): UpdateCheckResult = fetchMutex.withLock {
        val current = currentVersion()

        // 无网络直接返回兜底
        if (persistContext != null && !isNetworkAvailable(persistContext)) {
            Timber.tag("UpdateChecker").d("No network available, using fallback")
            val hasUpdate = isNewerAvailable(current, FALLBACK_LATEST_VERSION)
            return@withLock UpdateCheckResult(
                currentVersion = current,
                latestVersion = FALLBACK_LATEST_VERSION,
                releaseInfo = null,
                hasUpdate = hasUpdate,
                errorMessage = "当前无网络连接, 已使用本地版本对比 (兜底版本: v$FALLBACK_LATEST_VERSION)",
                errorType = NetworkErrorType.NO_NETWORK
            )
        }

        val inflight = inFlightJob
        val remote: ReleaseInfo? = if (inflight != null && !forceRefresh) {
            inflight.await()
        } else {
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

        val effective = remote?.version ?: FALLBACK_LATEST_VERSION
        val hasUpdate = isNewerAvailable(current, effective)

        // P127-FIX: 更友好的错误提示
        val errorMessage = when {
            remote == null -> "更新服务器连接失败 (GitHub 在国内访问受限), 已使用本地版本对比"
            !hasUpdate && compareVersions(current, effective) > 0 ->
                "当前版本 v$current 比远程版本 v$effective 更新"
            else -> null
        }

        val result = UpdateCheckResult(
            currentVersion = current,
            latestVersion = effective,
            releaseInfo = remote,
            hasUpdate = hasUpdate,
            errorMessage = errorMessage,
            errorType = if (remote == null) NetworkErrorType.CONNECTION_TIMEOUT else null
        )

        if (persistContext != null && result.hasUpdate) {
            persistLastKnownVersion(persistContext, effective)
        }
        result
    }

    fun consumePendingUpdateTip(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastKnown = prefs.getString(KEY_LAST_KNOWN_VERSION, null) ?: return null
        val current = currentVersion()
        if (!isNewerAvailable(current, lastKnown)) {
            prefs.edit().remove(KEY_LAST_KNOWN_VERSION).apply()
            return null
        }
        return lastKnown
    }

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

    @Volatile private var cachedRemote: ReleaseInfo? = null
    @Volatile private var cachedAt: Long = 0L
    private val cacheValidMillis = 6 * 60 * 60 * 1000L

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

    fun parseVersion(version: String): List<Int> {
        val cleaned = version.removePrefix("v")
            .substringBefore("-")
            .substringBefore("+")
        return cleaned.split(".").mapNotNull { it.toIntOrNull() }
    }

    fun compareVersions(a: String, b: String): Int {
        val pa = parseVersion(a)
        val pb = parseVersion(b)
        Timber.tag("UpdateChecker").d("compareVersions: '$a' -> $pa vs '$b' -> $pb")
        val len = maxOf(pa.size, pb.size)
        for (i in 0 until len) {
            val ai = pa.getOrElse(i) { 0 }
            val bi = pb.getOrElse(i) { 0 }
            if (ai != bi) return ai - bi
        }
        return 0
    }

    data class ReleaseInfo(
        val version: String,
        val name: String,
        val notes: String,
        val htmlUrl: String,
        val publishedAt: String
    )

    data class UpdateCheckResult(
        val currentVersion: String,
        val latestVersion: String,
        val releaseInfo: ReleaseInfo?,
        val hasUpdate: Boolean,
        val errorMessage: String?,
        val errorType: NetworkErrorType? = null
    )

    /**
     * P127-FIX: 自定义 DNS, 优先 IPv4, 带缓存。
     */
    private object CachedDns : Dns {
        private val cache = mutableMapOf<String, List<InetAddress>>()
        private val cacheLock = Object()
        private var cacheTime = 0L
        private const val CACHE_TTL_MS = 30 * 60 * 1000L

        override fun lookup(hostname: String): List<InetAddress> {
            val now = System.currentTimeMillis()
            synchronized(cacheLock) {
                if (now - cacheTime < CACHE_TTL_MS) {
                    cache[hostname]?.let { return it }
                }
            }
            val addresses = try {
                InetAddress.getAllByName(hostname)
                    .filter { it is Inet4Address }
                    .ifEmpty { InetAddress.getAllByName(hostname).toList() }
                    .toList()
            } catch (e: Exception) {
                synchronized(cacheLock) {
                    cache[hostname]?.let {
                        Timber.tag("UpdateChecker").w("DNS failed for $hostname, using stale cache")
                        return it
                    }
                }
                throw e
            }
            synchronized(cacheLock) {
                cache[hostname] = addresses
                cacheTime = now
            }
            return addresses
        }
    }

    /**
     * 主 client: 用于需要重定向或复杂请求的场景。
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(2, 5, TimeUnit.MINUTES))
            .dns(CachedDns)
            .proxySelector(java.net.ProxySelector.getDefault())
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * P127-FIX: 短超时 client, 用于轻量端点 (纯文本 VERSION 文件)。
     * 快速失败, 不阻塞主流程。
     */
    private val shortTimeoutClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .dns(CachedDns)
            .proxySelector(java.net.ProxySelector.getDefault())
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
