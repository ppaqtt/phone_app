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
 * 真实场景下从 GitHub Releases API 拉取最新版本, 网络失败时回退到 [FALLBACK_LATEST_VERSION]。
 * 数据源仓库: https://github.com/ppaqtt/phone_app/releases
 *
 * 启动时 (NotesApplication) 会后台调用 [checkForUpdate], 结果同时写入内存缓存
 * 与 SharedPreferences, 供主屏 (NotesListScreen) 检测到新版本时弹出 SnackBar 提示。
 */
object AppUpdateChecker {

    private const val GITHUB_API = "https://api.github.com/repos/ppaqtt/phone_app/releases/latest"
    private const val GITHUB_RELEASES_PAGE = "https://github.com/ppaqtt/phone_app/releases/latest"
    // 备用源 1: jsdelivr CDN 镜像 (国内可访问)
    private const val JSDELIVR_API = "https://cdn.jsdelivr.net/gh/ppaqtt/phone_app@main/VERSION"
    // 备用源 2: raw.githubusercontent.com (若 github.com 不可达但 raw 可达)
    private const val RAW_VERSION_URL = "https://raw.githubusercontent.com/ppaqtt/phone_app/main/VERSION"
    private const val PREFS_NAME = "app_update_checker"
    private const val KEY_LAST_KNOWN_VERSION = "last_known_version"
    private const val KEY_LAST_CHECK_AT = "last_check_at"

    /**
     * 网络不可用时的兜底最新版本号。
     * 必须与更新日志 [ChangelogData] 中的最新版本保持一致,
     * 否则网络失败时会给出错误的更新提示。
     * 每次正式发版时手动同步。
     */
    private const val FALLBACK_LATEST_VERSION = "1.20.22"

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
     * 检查网络是否可用。
     *
     * P124-FIX: 放宽 NET_CAPABILITY_VALIDATED 检查。部分网络环境下 (校园网/酒店 WiFi/
     * 需要 captive portal 的网络) validatated 会返回 false, 但实际上网络是可用的。
     * 我们只检查 INTERNET 能力, 让实际请求去验证连通性。
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network: Network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        // 只要有 INTERNET 能力就认为网络可用, 不再强依赖 VALIDATED
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * 错误分类, 用于给用户更明确的提示。
     */
    enum class NetworkErrorType {
        NO_NETWORK,      // 设备无网络
        DNS_FAILED,      // DNS 解析失败
        CONNECTION_TIMEOUT, // 连接超时
        SERVER_ERROR,    // 服务器返回 5xx
        API_ERROR,       // GitHub API 返回非 2xx (如 403 限流)
        PARSE_ERROR,     // JSON 解析失败
        UNKNOWN          // 未知错误
    }

    /**
     * 带分类的异常信息。
     */
    private class ClassifiedException(
        val type: NetworkErrorType,
        message: String,
        cause: Throwable? = null
    ) : Exception(message, cause)

    /**
     * 尝试从多个端点获取最新版本。优先使用 GitHub API, 失败时回退到
     * GitHub Releases 页面重定向 (可从 URL 中解析 tag), 再失败回退到
     * jsdelivr CDN / raw.githubusercontent.com, 最后用 FALLBACK。
     */
    suspend fun fetchLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        // 1. GitHub API (主端点, 返回完整 release 信息)
        tryFetch(GITHUB_API, "GitHub API")
            ?.let { return@withContext Result.success(it) }

        // 2. GitHub Releases 页面重定向 (备用: 解析 URL 中的 tag)
        tryFetchRedirect(GITHUB_RELEASES_PAGE, "GitHub Redirect")
            ?.let { return@withContext Result.success(it) }

        // 3. jsdelivr CDN (国内可访问, 读 VERSION 文件)
        tryFetchSimpleVersion(JSDELIVR_API, "jsDelivr")
            ?.let { return@withContext Result.success(it) }

        // 4. raw.githubusercontent.com (备用 4)
        tryFetchSimpleVersion(RAW_VERSION_URL, "GitHub Raw")
            ?.let { return@withContext Result.success(it) }

        // 5. 全部失败
        Result.failure(
            ClassifiedException(
                NetworkErrorType.UNKNOWN,
                "所有更新检查端点均不可用，请稍后重试"
            )
        )
    }

    /**
     * 从纯文本 URL 读取版本号 (例如 VERSION 文件)。
     * 适用于 jsdelivr / raw.githubusercontent.com 等轻量源。
     */
    private suspend fun tryFetchSimpleVersion(url: String, sourceName: String): ReleaseInfo? =
        withContext(Dispatchers.IO) {
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
     * 从 JSON API 端点获取 release 信息。
     */
    private suspend fun tryFetch(url: String, sourceName: String): ReleaseInfo? =
        withContext(Dispatchers.IO) {
            // 最多重试 3 次, 指数退避 1s / 2s
            repeat(3) { attempt ->
                if (attempt > 0) {
                    Timber.tag("UpdateChecker").d("$sourceName retry $attempt/2, waiting ${attempt}s...")
                    delay(1000L * attempt)
                }
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "Qingjian-Android/${BuildConfig.VERSION_NAME}")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val code = response.code
                            val type = when {
                                code >= 500 -> NetworkErrorType.SERVER_ERROR
                                code == 403 -> NetworkErrorType.API_ERROR
                                else -> NetworkErrorType.API_ERROR
                            }
                            throw ClassifiedException(type, "$sourceName HTTP $code")
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
                } catch (ce: ClassifiedException) {
                    Timber.tag("UpdateChecker").w(ce, "$sourceName failed: ${ce.message}")
                } catch (e: java.net.UnknownHostException) {
                    Timber.tag("UpdateChecker").w(e, "$sourceName DNS failed")
                } catch (e: java.net.SocketTimeoutException) {
                    Timber.tag("UpdateChecker").w(e, "$sourceName timeout")
                } catch (e: Exception) {
                    Timber.tag("UpdateChecker").w(e, "$sourceName unexpected error")
                }
            }
            null
        }

    /**
     * 通过访问 releases/latest 页面, 从重定向 URL 中解析版本号。
     * github.com 在部分网络环境下可达, 但 api.github.com 不可达。
     * 这种方式只能拿到版本号, 拿不到 release notes。
     */
    private suspend fun tryFetchRedirect(url: String, sourceName: String): ReleaseInfo? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Qingjian-Android/${BuildConfig.VERSION_NAME}")
                    .build()
                client.newCall(request).execute().use { response ->
                    // 响应码 200 或 302, 从重定向链或 location 头解析 tag
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
     * 检查是否有更新, 优先用 [fetchLatestRelease], 失败回退到 [FALLBACK_LATEST_VERSION]。
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
        val current = currentVersion()

        // 先检查网络是否可用, 无网络直接返回本地对比结果
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

        val effective = remote?.version ?: FALLBACK_LATEST_VERSION
        val hasUpdate = isNewerAvailable(current, effective)

        // 更清晰的错误提示, 区分"无网络"、"网络超时"和"已是最新"
        val errorType = when {
            remote == null -> NetworkErrorType.CONNECTION_TIMEOUT
            else -> null
        }
        val errorMessage = when {
            remote == null -> "连接更新服务器失败, 请检查网络或稍后重试 (当前显示版本: v$effective)"
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
            errorType = errorType
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

    // 内存缓存 + Mutex 串行化网络层, 避免并发请求击 GitHub API
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
     * 解析版本号 "v1.2.3" / "1.2.3-beta" -> [1, 2, 3]。
     * 过滤掉非数字后缀（如 -beta, -rc1），只取主版本号部分比较。
     */
    fun parseVersion(version: String): List<Int> {
        val cleaned = version.removePrefix("v")
            .substringBefore("-")   // "1.2.3-beta" -> "1.2.3"
            .substringBefore("+")   // "1.2.3+build" -> "1.2.3"
        return cleaned.split(".").mapNotNull { it.toIntOrNull() }
    }

    /**
     * 比较两个版本号, 增加日志便于排查更新问题。
     * a < b 返回负数, a > b 返回正数, 相等返回 0。
     */
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
        val errorMessage: String?,
        val errorType: NetworkErrorType? = null
    )

    /**
     * 自定义 DNS: 在系统 DNS 失败时尝试备用方案。
     * 同时增加 DNS 结果缓存, 避免每次请求都做 DNS 解析。
     */
    private object CachedDns : Dns {
        private val cache = mutableMapOf<String, List<InetAddress>>()
        private val cacheLock = Object()
        private var cacheTime = 0L
        private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 分钟

        override fun lookup(hostname: String): List<InetAddress> {
            val now = System.currentTimeMillis()
            // 命中缓存且未过期
            synchronized(cacheLock) {
                if (now - cacheTime < CACHE_TTL_MS) {
                    cache[hostname]?.let { return it }
                }
            }
            // 正常解析
            val addresses = try {
                InetAddress.getAllByName(hostname)
                    .filter { it is Inet4Address } // 优先 IPv4, 部分网络 IPv6 不可达
                    .ifEmpty { InetAddress.getAllByName(hostname).toList() }
                    .toList()
            } catch (e: Exception) {
                // 解析失败, 尝试上一次的缓存 (即使过期)
                synchronized(cacheLock) {
                    cache[hostname]?.let {
                        Timber.tag("UpdateChecker").w("DNS failed for $hostname, using stale cache")
                        return it
                    }
                }
                throw e
            }
            // 写入缓存
            synchronized(cacheLock) {
                cache[hostname] = addresses
                cacheTime = now
            }
            return addresses
        }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // P124-FIX: 缩短超时, 避免长时间阻塞导致 ANR
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            // P124-FIX: 调用之间复用连接, 减少握手开销
            .connectionPool(ConnectionPool(2, 5, TimeUnit.MINUTES))
            // P124-FIX: 自定义 DNS, 带缓存 + 优先 IPv4, 避免 IPv6-only 网络下失败
            .dns(CachedDns)
            // 支持系统代理
            .proxySelector(java.net.ProxySelector.getDefault())
            // 自动跟随重定向 (备用端点需要)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
