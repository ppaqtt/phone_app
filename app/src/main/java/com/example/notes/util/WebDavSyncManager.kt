package com.example.notes.util

import com.example.notes.repository.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * WebDAV 双向同步。
 *
 * 同步单元是一个 JSON 快照 (与本地备份同 schema), 放在远程目录下的
 * `qingjian_sync_v1.json`。流程:
 *
 * 1. 给本地缺 sync_id 的笔记/分类补 UUID;
 * 2. 导出本地快照 (含软删除墓碑);
 * 3. 下载云端快照 (404 视为首次);
 * 4. 按 syncId 合并, 同一条笔记 updatedAt 更新者胜;
 * 5. 把合并结果写回本地;
 * 6. 重新导出并上传, 让云端成为两端并集。
 *
 * 已知边界: 图片 (content:// URI) 不参与同步, 仅同步文字/分类/标签/提醒。
 * 另外, 回收站里的"永久删除"和 30 天自动清理会移除删除墓碑, 若其他设备
 * 仍持有该笔记, 下次同步可能把它带回来。
 */
class WebDavSyncManager(
    private val repository: NotesRepository,
    private val store: SyncConfigStore
) {

    data class SyncOutcome(
        val uploadedNotes: Int,
        val notesCreated: Int,
        val notesUpdated: Int,
        val categoriesCreated: Int,
        val remoteExisted: Boolean
    )

    suspend fun sync(appVersion: String): SyncOutcome = withContext(Dispatchers.IO) {
        val config = store.config.value
        if (!config.isConfigured) {
            throw IllegalStateException("请先在设置中填写服务器地址、账号和应用密码")
        }

        repository.ensureSyncIds()
        val local = repository.buildSyncPayload(appVersion)

        val client = WebDavClient(config)
        client.ensureDirectory()

        val remoteJson = client.get(FILE_NAME)
        val remote = remoteJson?.let { json ->
            BackupManager.fromJsonSafe(json).getOrElse {
                throw IllegalStateException("云端同步文件格式错误: ${it.message}")
            }
        }

        val merged = merge(local, remote)
        val applied = repository.applySyncPayload(merged)

        // 写回后再补一次 sync_id (远端老数据可能没有)
        repository.ensureSyncIds()
        val toUpload = repository.buildSyncPayload(appVersion)
        client.put(FILE_NAME, BackupManager.toJson(toUpload))

        val summary = "云端 ${toUpload.notes.size} 条 · 新增 ${applied.notesCreated} · 更新 ${applied.notesUpdated}"
        store.markSynced(summary)
        Timber.tag("WebDavSync").i("sync done: $summary")

        SyncOutcome(
            uploadedNotes = toUpload.notes.size,
            notesCreated = applied.notesCreated,
            notesUpdated = applied.notesUpdated,
            categoriesCreated = applied.categoriesCreated,
            remoteExisted = remote != null
        )
    }

    /**
     * 合并本地与云端快照。local 优先用于分类结构; 笔记逐条比较 updatedAt。
     * 云端老数据没有 syncId 时, 用 "标题 + 创建时间" 匹配本地, 避免重复导入。
     */
    private fun merge(local: BackupPayload, remote: BackupPayload?): BackupPayload {
        if (remote == null) return local

        val categoryMap = LinkedHashMap<String, CategoryBackup>()
        local.categories.forEach { categoryMap[categoryKey(it)] = it }
        remote.categories.forEach { remoteCategory ->
            val key = categoryKey(remoteCategory)
            if (!categoryMap.containsKey(key)) categoryMap[key] = remoteCategory
        }

        val localBySignature = local.notes.associateBy { noteSignature(it) }
        val normalizedRemoteNotes = remote.notes.map { note ->
            if (!note.syncId.isNullOrBlank()) {
                note
            } else {
                val matchedSyncId = localBySignature[noteSignature(note)]
                    ?.syncId
                    ?.takeIf { it.isNotBlank() }
                if (matchedSyncId != null) note.copy(syncId = matchedSyncId) else note
            }
        }

        val noteMap = LinkedHashMap<String, NoteBackup>()
        local.notes.forEach { noteMap[noteKey(it)] = it }
        normalizedRemoteNotes.forEach { remoteNote ->
            val key = noteKey(remoteNote)
            val existing = noteMap[key]
            if (existing == null || remoteNote.updatedAt > existing.updatedAt) {
                noteMap[key] = remoteNote
            }
        }

        return local.copy(
            version = BackupPayload.CURRENT_VERSION,
            exportedAt = System.currentTimeMillis(),
            categories = categoryMap.values.toList(),
            notes = noteMap.values.toList(),
            images = emptyList()
        )
    }

    private fun categoryKey(category: CategoryBackup): String =
        category.syncId?.takeIf { it.isNotBlank() }?.let { "id:$it" } ?: "name:${category.name}"

    private fun noteKey(note: NoteBackup): String =
        note.syncId?.takeIf { it.isNotBlank() }?.let { "id:$it" }
            ?: "legacy:${note.oldId}:${note.createdAt}"

    private fun noteSignature(note: NoteBackup): String =
        "${note.title}\u0000${note.createdAt}"

    companion object {
        const val FILE_NAME = "qingjian_sync_v1.json"
    }
}
