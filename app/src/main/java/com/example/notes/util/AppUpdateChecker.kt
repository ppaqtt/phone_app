package com.example.notes.util

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.widget.Toast
import com.example.notes.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
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

    // P131-FIX: 国内网络优先, 超时延长, 重试次数增加。
    // GitHub 域名在国内基本不可达, 主要依赖 Gitee + jsDelivr CDN。

    /** 主端点 1: Gitee 码云 (国内最快) */
    private const val GITEE_API = "https://gitee.com/api/v5/repos/ppkjgzs/phone_app/releases/latest"

    /** 主端点 2: Gitee 直接下载（release 附件 APK） */
    private const val GITEE_DOWNLOAD_TEMPLATE =
        "https://gitee.com/ppkjgzs/phone_app/releases/download/v%s/qingjian-%s.apk"

    /** 主端点 3: jsDelivr CDN (fastly, 国内可访问) - 省略分支 = 使用默认分支 */
    private const val JSDELIVR_FASTLY = "https://fastly.jsdelivr.net/gh/ppaqtt/phone_app/VERSION"

    /** 主端点 4: jsDelivr CDN (cdn.jsdelivr.net, 国内备用) - 省略分支 = 使用默认分支 */
    private const val JSDELIVR_CDNJS = "https://cdn.jsdelivr.net/gh/ppaqtt/phone_app/VERSION"

    /** 备用端点 1: GitHub API (海外, 国内大概率超时) */
    private const val GITHUB_API = "https://api.github.com/repos/ppaqtt/phone_app/releases/latest"

    /** 备用端点 2: GitHub Releases 重定向 (海外) */
    private const val GITHUB_RELEASES_PAGE = "https://github.com/ppaqtt/phone_app/releases/latest"

    /** 备用端点 3: GitHub 直接下载 APK（release 附件） */
    private const val GITHUB_DOWNLOAD_TEMPLATE =
        "https://github.com/ppaqtt/phone_app/releases/download/v%s/qingjian-%s.apk"

    /** 备用端点 4: raw.githubusercontent.com (海外) - try main 分支 */
    private const val RAW_VERSION_URL_MAIN = "https://raw.githubusercontent.com/ppaqtt/phone_app/main/VERSION"

    /** 备用端点 4b: raw.githubusercontent.com (海外) - try master 分支 */
    private const val RAW_VERSION_URL_MASTER = "https://raw.githubusercontent.com/ppaqtt/phone_app/master/VERSION"

    private const val PREFS_NAME = "app_update_checker"
    private const val KEY_LAST_KNOWN_VERSION = "last_known_version"
    private const val KEY_LAST_CHECK_AT = "last_check_at"
    private const val KEY_IGNORED_VERSION = "ignored_version"
    private const val KEY_LAST_AUTO_CHECK_AT = "last_auto_check_at"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_AUTO_CHECK = "auto_check"

    /**
     * 网络不可用时的兜底最新版本号。
     * 必须与更新日志 [ChangelogData] 中的最新版本保持一致。
     */
    private const val FALLBACK_LATEST_VERSION = "1.36.00"

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

    /** 是否当前为 WiFi / 以太网 (不计量) 网络。用于 WiFi 下载限制。 */
    fun isWifiNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network: Network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        val isInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val hasTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return isInternet && hasTransport
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
     * P131-FIX: 增加 jsDelivr CDN 多镜像 (fastly / cdnjs), 国内两路探测。
     *
     * 优先级:
     * 1. Gitee API (国内最快, 返回完整 JSON, 5s超时 / 重试3次)
     * 2. jsDelivr CDN 两个镜像 (国内加速, 纯文本版本, 5s超时 / 重试2次)
     * 3. GitHub API (海外, 国内大概率超时)
     * 4. GitHub Releases 重定向 (海外)
     * 5. raw.githubusercontent.com (海外)
     */
    suspend fun fetchLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        // 1. Gitee (国内优先, 更长超时+更多重试)
        tryFetchGitee(GITEE_API, "Gitee")
            ?.let { return@withContext Result.success(it) }

        // 2. jsDelivr CDN 两个镜像 (国内 CDN, 纯文本版本)
        // 2a. fastly
        tryFetchSimpleVersion(JSDELIVR_FASTLY, "jsDelivr-fastly")
            ?.let { return@withContext Result.success(it) }
        // 2b. cdnjs
        tryFetchSimpleVersion(JSDELIVR_CDNJS, "jsDelivr-cdnjs")
            ?.let { return@withContext Result.success(it) }

        // 3. GitHub API (海外)
        tryFetch(GITHUB_API, "GitHub API")
            ?.let { return@withContext Result.success(it) }

        // 4. GitHub Releases 重定向 (海外)
        tryFetchRedirect(GITHUB_RELEASES_PAGE, "GitHub Redirect")
            ?.let { return@withContext Result.success(it) }

        // 5. raw.githubusercontent.com (海外) - 先试 main, 再试 master
        tryFetchSimpleVersion(RAW_VERSION_URL_MAIN, "GitHub Raw-main")
            ?.let { return@withContext Result.success(it) }
        tryFetchSimpleVersion(RAW_VERSION_URL_MASTER, "GitHub Raw-master")
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
     * P131-FIX: 重试次数从 2 次增加到 3 次。
     * Gitee 的 release API 返回格式与 GitHub 类似, 但字段名略有不同。
     */
    private suspend fun tryFetchGitee(url: String, sourceName: String): ReleaseInfo? =
        withContext(Dispatchers.IO) {
            // P131-FIX: 重试 3 次 (国内网络不稳定)
            repeat(3) { attempt ->
                if (attempt > 0) delay(1000)
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Qingjian-Android/${BuildConfig.VERSION_NAME}")
                        .build()
                    // P131-FIX: 使用长超时 client
                    domesticClient.newCall(request).execute().use { response ->
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
                        val giteeHtml = json.optString("html_url")
                        return@withContext ReleaseInfo(
                            version = tag,
                            name = json.optString("name").ifBlank { tag },
                            notes = json.optString("body"),
                            htmlUrl = giteeHtml.ifBlank { GITHUB_RELEASES_PAGE },
                            publishedAt = json.optString("published_at"),
                            apkUrlGitee = giteeHtml.takeIf { it.isNotBlank() }
                                ?.plus("/attach_files") ?: "",
                            apkUrlGithub = String.format(Locale.US, GITHUB_DOWNLOAD_TEMPLATE, tag, tag)
                        )
                    }
                } catch (e: Exception) {
                    Timber.tag("UpdateChecker").w(e, "$sourceName failed (attempt ${attempt + 1})")
                }
            }
            null
        }

    /**
     * 从纯文本 URL 读取版本号。
     * P131-FIX: 使用国内端点 client (5s/8s/5s), 重试 2 次。
     */
    private suspend fun tryFetchSimpleVersion(url: String, sourceName: String): ReleaseInfo? =
        withContext(Dispatchers.IO) {
            // P131-FIX: 重试 2 次 (CDN 国内也可能有抖动)
            repeat(2) { attempt ->
                if (attempt > 0) delay(800)
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Qingjian-Android/${BuildConfig.VERSION_NAME}")
                        .build()
                    // P131-FIX: 使用国内端点 client (更长超时)
                    domesticClient.newCall(request).execute().use { response ->
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
                                publishedAt = "",
                                apkUrlGitee = String.format(Locale.US, GITEE_DOWNLOAD_TEMPLATE, version, version),
                                apkUrlGithub = String.format(Locale.US, GITHUB_DOWNLOAD_TEMPLATE, version, version)
                            )
                        }
                        Timber.tag("UpdateChecker").w("$sourceName: invalid version format: $version")
                        null
                    }
                } catch (e: Exception) {
                    Timber.tag("UpdateChecker").w(e, "$sourceName failed (attempt ${attempt + 1})")
                }
            }
            null
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
                            publishedAt = json.optString("published_at"),
                            apkUrlGitee = String.format(Locale.US, GITEE_DOWNLOAD_TEMPLATE, tag, tag),
                            apkUrlGithub = json.optString("html_url")
                                .takeIf { it.isNotBlank() }
                                ?.plus("/download/v$tag/qingjian-$tag.apk")
                                ?: String.format(Locale.US, GITHUB_DOWNLOAD_TEMPLATE, tag, tag)
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
                            publishedAt = "",
                            apkUrlGitee = String.format(Locale.US, GITEE_DOWNLOAD_TEMPLATE, tagMatch, tagMatch),
                            apkUrlGithub = String.format(Locale.US, GITHUB_DOWNLOAD_TEMPLATE, tagMatch, tagMatch)
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

    // ========== 忽略版本 ==========
    fun ignoreVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IGNORED_VERSION, version)
            .apply()
    }

    fun getIgnoredVersion(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_IGNORED_VERSION, null)

    fun isVersionIgnored(context: Context, version: String): Boolean =
        getIgnoredVersion(context) == version

    // ========== 启动时自动检查 ==========
    private const val AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L // 6 小时

    fun shouldAutoCheck(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_AUTO_CHECK_AT, 0L)
        return (System.currentTimeMillis() - last) > AUTO_CHECK_INTERVAL_MS
    }

    fun markAutoChecked(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_AUTO_CHECK_AT, System.currentTimeMillis())
            .apply()
    }

    // ========== 用户偏好设置 ==========

    /** 是否仅 WiFi 下下载 APK (默认 true)。 */
    fun isWifiOnlyDownload(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WIFI_ONLY, true)
    }

    fun setWifiOnlyDownload(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WIFI_ONLY, value)
            .apply()
    }

    /** 是否启动时自动检查更新 (默认 true)。 */
    fun isAutoCheckEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_CHECK, true)
    }

    fun setAutoCheckEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_CHECK, value)
            .apply()
    }

    /**
     * 在启动自动检查路径上额外判断: WiFi 限制 + 自动检查开关。
     */
    fun shouldAutoCheckWithPreferences(context: Context): Boolean {
        if (!isAutoCheckEnabled(context)) return false
        return shouldAutoCheck(context)
    }

    // ========== 应用内下载 APK (DownloadManager) ==========

    /**
     * 用指定 URL 启动 DownloadManager 下载 APK。
     * 返回 DownloadManager 的 downloadId。
     */
    private fun enqueueDownloadByUrl(context: Context, url: String, version: String): Long? {
        val fileName = "qingjian-$version.apk"
        val wifiOnly = isWifiOnlyDownload(context)
        val request = android.app.DownloadManager.Request(Uri.parse(url))
            .setTitle("轻笺笔记 v$version")
            .setDescription("正在下载更新包...")
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                android.os.Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(!wifiOnly)
            .setAllowedOverRoaming(false)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        return try {
            dm.enqueue(request)
        } catch (e: Exception) {
            Timber.tag("UpdateChecker").w(e, "DownloadManager enqueue failed for $url")
            null
        }
    }

    /**
     * 启动下载 APK (首选源)。
     * 返回 downloadId，或在 WiFi 限制下返回 null。
     */
    fun enqueueDownload(context: Context, release: ReleaseInfo): Long? {
        if (isWifiOnlyDownload(context) && !isWifiNetwork(context)) {
            Timber.tag("UpdateChecker").d("Blocked by WiFi-only setting")
            return -2L // 特殊标记: 被 WiFi 限制
        }
        return enqueueDownloadByUrl(context, release.bestApkUrl(), release.version)
    }

    /**
     * 多源下载换源。当主源 (Gitee) 下载失败时自动切换到次源 (GitHub)。
     * 返回新的 downloadId，全部失败返回 null。
     *
     * 注意: fallback 路径同样遵守 WiFi-only 限制。被限制时返回 -2L 让调用方处理。
     */
    fun enqueueDownloadFallback(context: Context, release: ReleaseInfo, failedUrl: String?): Long? {
        if (isWifiOnlyDownload(context) && !isWifiNetwork(context)) {
            return -2L
        }
        val urls = listOfNotNull(
            String.format(Locale.US, GITEE_DOWNLOAD_TEMPLATE, release.version, release.version),
            String.format(Locale.US, GITHUB_DOWNLOAD_TEMPLATE, release.version, release.version)
        ).filter { it.isNotBlank() && it != failedUrl }
        for (url in urls) {
            val id = enqueueDownloadByUrl(context, url, release.version)
            if (id != null) {
                Timber.tag("UpdateChecker").d("Fallback: switched to $url")
                return id
            }
        }
        return null
    }

    /**
     * 清理 DownloadManager 中已下载但未安装的旧版本 APK 记录，释放存储空间。
     * 仅清理本应用下载目录下文件名匹配 "qingjian-*.apk" 且版本号 < 当前版本的文件。
     */
    fun cleanupOldApks() {
        try {
            val dir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val current = currentVersion()
            val files = dir?.listFiles { f ->
                f?.name?.matches(Regex("qingjian-.*\\.apk")) == true
            } ?: return
            for (f in files) {
                try {
                    val versionInName = f.name.replace("qingjian-", "")
                        .replace(".apk", "")
                        .trim()
                    if (compareVersions(versionInName, current) < 0) {
                        f.delete()
                        Timber.tag("UpdateChecker").d("Deleted old APK: ${f.name}")
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Timber.tag("UpdateChecker").w(e, "cleanupOldApks failed")
        }
    }

    /** 查询下载进度。
     * 返回 Triple(status, downloaded, total)，其中:
     * - status: STATUS_SUCCESSFUL / STATUS_FAILED / STATUS_RUNNING 等
     * - total 为 -1 表示大小未知 (此时应显示不确定进度条)
     * 无法查询返回 null。
     */
    fun getDownloadProgressEx(context: Context, downloadId: Long): Triple<Int, Long, Long>? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val query = android.app.DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query)
        return try {
            if (!cursor.moveToFirst()) return null
            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS)
            )
            val bytesDownloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val bytesTotal = cursor.getLong(
                cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            Triple(status, bytesDownloaded, bytesTotal)
        } catch (e: Exception) {
            Timber.tag("UpdateChecker").w(e, "getDownloadProgressEx failed")
            null
        } finally {
            cursor.close()
        }
    }

    /** 获取下载完成的 APK 文件 Uri，供安装使用。 */
    fun getDownloadedUri(context: Context, downloadId: Long): Uri? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        return dm.getUriForDownloadedFile(downloadId)
    }

    /** 用包管理器安装 APK。需要 READ_INSTALL_PACKAGES 权限/应用安装来源权限。 */
    fun installApk(context: Context, apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure { e ->
            Toast.makeText(
                context,
                "无法启动安装: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            Timber.tag("UpdateChecker").w(e, "installApk failed")
        }
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
        val publishedAt: String,
        val apkUrlGitee: String,
        val apkUrlGithub: String
    ) {
        /** 优先使用模板生成的 Gitee 下载 URL（Gitee 页面解析出的 URL 不可靠） */
        fun bestApkUrl(): String = String.format(
            Locale.US, GITEE_DOWNLOAD_TEMPLATE, version, version
        ).ifBlank { apkUrlGithub }
    }

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
     * P131-FIX: 国内端点 client — 用于 Gitee / jsDelivr CDN 等国内可访问的端点。
     * 超时更长 (5s/8s/5s), 重试交给上层方法处理。
     */
    private val domesticClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(3, 5, TimeUnit.MINUTES))
            .dns(CachedDns)
            .proxySelector(java.net.ProxySelector.getDefault())
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * 主 client: 用于需要重定向或复杂请求的场景 (海外 GitHub 端点)。
     * 较短超时 (3s/5s/3s), 快速失败。
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
}
