package com.example.notes.util

import com.example.notes.repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

/**
 * WebDAV 双向同步编排。
 *
 * 流程:
 * 1. 确保远端目录存在;
 * 2. 下载远端 notes.json (首次为空);
 * 3. 把远端合并进本地 (按 syncUid + updatedAt, 冲突保留副本);
 * 4. 把合并后的全量数据 PUT 回远端。
 *
 * 并发: 上传带 If-Match (ETag)。若其他设备在下载与上传之间改过文件,
 * 服务端返回 412, 这里会自动重下一轮 (最多 2 次), 不会静默覆盖对方数据。
 */
class WebDavSyncManager(
    private val repository: NotesRepository,
    private val settings: WebDavSettings
) {

    data class SyncResult(
        val uploadedNotes: Int,
        val downloadedNotes: Int,
        val conflicts: Int,
        val message: String
    )

    suspend fun sync(appVersion: String): SyncResult = withContext(Dispatchers.IO) {
        if (!settings.isConfigured()) {
            throw IllegalStateException("请先填写服务器地址、账号和应用密码")
        }
        val client = WebDavClient(settings)
        client.ensureDirectory().getOrThrow()

        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                val remote = client.download()
                val local = repository.exportSyncPayload(appVersion)

                if (remote == null) {
                    val upload = client.upload(BackupManager.toJson(local), null)
                    if (!upload.success) throw IOException(upload.message)
                    return@withContext SyncResult(
                        uploadedNotes = local.notes.size,
                        downloadedNotes = 0,
                        conflicts = 0,
                        message = "首次同步完成, 已上传 ${local.notes.size} 篇笔记"
                    )
                }

                val remotePayload = BackupManager.fromJson(remote.text)
                val merged = repository.mergeWebDav(remotePayload, appVersion)
                val upload = client.upload(BackupManager.toJson(merged.payload), remote.etag)
                if (upload.success) {
                    return@withContext SyncResult(
                        uploadedNotes = merged.payload.notes.size,
                        downloadedNotes = merged.downloaded,
                        conflicts = merged.conflicts,
                        message = buildString {
                            append("同步完成: 上传 ${merged.payload.notes.size} 篇")
                            if (merged.downloaded > 0) append(", 下载 ${merged.downloaded} 篇")
                            if (merged.conflicts > 0) append(", 保留 ${merged.conflicts} 个冲突副本")
                        }
                    )
                }
                if (!upload.conflict) throw IOException(upload.message)
                Timber.tag("WebDav").w("ETag conflict, retry #${attempt + 1}")
                lastError = IOException("远端已被其他设备修改, 正在重试")
            } catch (e: Exception) {
                Timber.tag("WebDav").w(e, "sync attempt #${attempt + 1} failed")
                lastError = e
            }
        }
        throw lastError ?: IOException("同步失败")
    }

    suspend fun test(): Result<String> = withContext(Dispatchers.IO) {
        if (!settings.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("请先填写服务器地址、账号和应用密码"))
        }
        WebDavClient(settings).ensureDirectory()
    }
}
