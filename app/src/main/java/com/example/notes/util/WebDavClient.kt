package com.example.notes.util

import android.net.Uri
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * 极简 WebDAV 客户端 (OkHttp)。
 *
 * 覆盖同步所需的 4 个动作:
 * - PROPFIND: 验证地址/账号/目录可访问;
 * - MKCOL: 逐级创建远程目录 (坚果云/Nextcloud 均支持);
 * - PUT: 上传 JSON 快照;
 * - GET: 下载 JSON 快照 (404 视为首次同步)。
 *
 * 认证用 Basic Auth。坚果云需要在"账户信息 → 安全选项"里生成应用密码,
 * 不能直接用登录密码。Nextcloud 同理建议使用应用密码。
 *
 * 自签名证书仅在用户主动勾选"信任自签名证书"时放行, 默认严格校验证书。
 */
class WebDavClient(private val config: SyncConfig) {

    private val baseUrl: String = normalizeBaseUrl(config.serverUrl)
    private val segments: List<String> = config.remoteDir
        .trim()
        .split('/')
        .filter { it.isNotBlank() }

    private val rootUrl: String = buildString {
        append(baseUrl)
        segments.forEach {
            append(Uri.encode(it))
            append('/')
        }
    }

    private val client: OkHttpClient by lazy { buildClient() }

    private val xmlMedia = "application/xml; charset=utf-8".toMediaType()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** 测试连接: 目录可创建/已存在, 且 PROPFIND 返回 207/200 */
    fun testConnection() {
        if (config.serverUrl.isBlank()) throw IOException("请先填写服务器地址")
        if (config.username.isBlank() || config.appPassword.isBlank()) {
            throw IOException("请填写账号和应用密码")
        }
        ensureDirectory()
        val code = propfind(rootUrl)
        when (code) {
            200, 207 -> Unit
            401 -> throw IOException("认证失败, 请检查账号与应用密码")
            403 -> throw IOException("账号无访问该目录的权限")
            404 -> throw IOException("服务器上找不到该目录")
            else -> throw IOException("服务器返回 HTTP $code")
        }
    }

    /** 逐级 MKCOL; 已存在 (405/301) 视为成功 */
    fun ensureDirectory() {
        if (segments.isEmpty()) return
        var current = baseUrl
        segments.forEach { segment ->
            current += Uri.encode(segment) + "/"
            val code = mkcol(current)
            when (code) {
                200, 201, 204, 301, 302, 405 -> Unit
                401 -> throw IOException("认证失败, 请检查账号与应用密码")
                403 -> Unit // 目录已存在但没有创建权限, 交给 PROPFIND 判定
                409 -> throw IOException("云端父目录不存在")
                else -> throw IOException("创建云端目录失败: HTTP $code")
            }
        }
    }

    /** 上传文本快照 */
    fun put(fileName: String, content: String) {
        val request = baseRequest(rootUrl + Uri.encode(fileName))
            .put(content.toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("上传失败: HTTP ${response.code}")
            }
        }
    }

    /** 下载文本快照; 文件不存在返回 null */
    fun get(fileName: String): String? {
        val request = baseRequest(rootUrl + Uri.encode(fileName)).get().build()
        client.newCall(request).execute().use { response ->
            return when {
                response.code == 404 -> null
                response.code == 401 -> throw IOException("认证失败, 请检查账号与应用密码")
                !response.isSuccessful -> throw IOException("下载失败: HTTP ${response.code}")
                else -> response.body?.string()
            }
        }
    }

    private fun propfind(url: String): Int {
        val body = """<?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>
        """.trimIndent()
        val request = baseRequest(url)
            .method("PROPFIND", body.toRequestBody(xmlMedia))
            .header("Depth", "0")
            .build()
        client.newCall(request).execute().use { return it.code }
    }

    private fun mkcol(url: String): Int {
        val request = baseRequest(url).method("MKCOL", null).build()
        client.newCall(request).execute().use { return it.code }
    }

    private fun baseRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(config.username, config.appPassword))
            .header("User-Agent", "QingJian-Android/1.0")

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)

        if (config.allowSelfSigned) {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<X509TrustManager>(trustManager), SecureRandom())
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }

    private fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim()
        if (url.isBlank()) throw IOException("请先填写服务器地址")
        if (url.startsWith("http://", ignoreCase = true)) {
            throw IOException("仅支持 HTTPS 地址, 避免应用密码以明文传输")
        }
        if (!url.startsWith("https://", ignoreCase = true)) {
            url = "https://$url"
        }
        if (!url.endsWith("/")) url += "/"
        return url
    }
}
