package com.example.notes.util

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 极简 WebDAV 客户端 (坚果云兼容)。
 *
 * 只实现同步需要的 4 个动作:
 * - PROPFIND 探测目录 / 鉴权
 * - MKCOL 创建目录
 * - GET 拉取 notes.json
 * - PUT 上传 notes.json
 *
 * 使用 Basic Auth: 坚果云要求「账号 + 应用密码」, 不是登录密码。
 */
class WebDavClient(private val settings: WebDavSettings) {

    data class RemoteFile(val text: String, val etag: String?)

    data class UploadResult(val success: Boolean, val conflict: Boolean, val message: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun authHeader(): String = Credentials.basic(settings.username, settings.password)

    private fun xmlType() = "application/xml; charset=utf-8".toMediaTypeOrNull()

    private fun jsonType() = "application/json; charset=utf-8".toMediaTypeOrNull()

    /**
     * 逐级确保远端目录存在 (不存在则 MKCOL)。
     * @return 成功时带一句可展示的提示; 失败时带可读原因
     */
    fun ensureDirectory(): Result<String> {
        val root = settings.serverRootUrl()
        val segments = settings.remoteDir.trim('/').split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return Result.success("使用服务器根目录")

        var acc = root
        segments.forEach { segment ->
            acc += segment
            val (code, _) = propfind(acc)
                ?: return Result.failure(IOException("无法连接 WebDAV 服务器, 请检查网络或地址"))
            when {
                code in 200..299 -> Unit
                code == 401 || code == 403 ->
                    return Result.failure(IOException("账号或应用密码错误 (HTTP $code)"))
                code == 404 -> {
                    val created = mkcol(acc)
                    // 405 = 已存在; 301/302 = 服务器提示目录已就绪
                    if (created !in 200..299 && created != 405 && created != 301 && created != 302) {
                        return Result.failure(IOException("创建远程目录失败 (HTTP $created)"))
                    }
                }
                else -> return Result.failure(IOException("服务器返回 HTTP $code"))
            }
            acc += "/"
        }
        return Result.success("连接成功")
    }

    private fun propfind(url: String): Pair<Int, String>? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader())
            .header("Depth", "0")
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(xmlType()))
            .build()
        client.newCall(request).execute().use { resp ->
            resp.code to resp.body?.string().orEmpty()
        }
    }.getOrNull()

    private fun mkcol(url: String): Int = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader())
            .method("MKCOL", ByteArray(0).toRequestBody(null))
            .build()
        client.newCall(request).execute().use { resp -> resp.code }
    }.getOrDefault(-1)

    /**
     * 下载 notes.json。
     * @return null = 远端还没有同步文件 (首次同步)
     */
    fun download(): RemoteFile? {
        val request = Request.Builder()
            .url(settings.fileUrl())
            .header("Authorization", authHeader())
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            return when {
                resp.code == 404 -> null
                resp.isSuccessful -> {
                    val body = resp.body?.string() ?: throw IOException("远端同步文件为空")
                    RemoteFile(body, resp.header("ETag"))
                }
                resp.code == 401 || resp.code == 403 ->
                    throw IOException("认证失败 (HTTP ${resp.code}), 请检查账号 / 应用密码")
                else -> throw IOException("下载失败 (HTTP ${resp.code})")
            }
        }
    }

    /** 上传 notes.json; [ifMatch] 非空时带 If-Match 做乐观并发控制 */
    fun upload(json: String, ifMatch: String?): UploadResult {
        val builder = Request.Builder()
            .url(settings.fileUrl())
            .header("Authorization", authHeader())
            .put(json.toRequestBody(jsonType()))
        if (!ifMatch.isNullOrBlank() && !ifMatch.startsWith("W/")) {
            builder.header("If-Match", ifMatch)
        }
        client.newCall(builder.build()).execute().use { resp ->
            return when {
                resp.isSuccessful -> UploadResult(true, false, "上传成功")
                resp.code == 412 -> UploadResult(false, true, "远端已被其他设备修改")
                resp.code == 401 || resp.code == 403 ->
                    UploadResult(false, false, "认证失败 (HTTP ${resp.code})")
                else -> UploadResult(false, false, "上传失败 (HTTP ${resp.code})")
            }
        }
    }

    companion object {
        private const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8"?>""" +
                """<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
    }
}
