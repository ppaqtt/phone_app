package com.example.notes.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notes.data.CategoryEntity
import com.example.notes.data.NoteEntity
import com.example.notes.data.NoteWithCategory
import com.example.notes.data.NoteWithCategoryAndImages
import com.example.notes.repository.NotesRepository
import com.example.notes.util.BackupManager
import com.example.notes.util.BackupPayload
import com.example.notes.widget.NotesAppWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

enum class NoteSortOrder {
    UPDATED_DESC,    // 更新时间降序 (默认)
    UPDATED_ASC,     // 更新时间升序
    CREATED_DESC,    // 创建时间降序
    CREATED_ASC,     // 创建时间升序
    TITLE_ASC,       // 标题升序
    PRIORITY_DESC    // 重要度降序
}

data class NotesUiState(
    val notes: List<NoteWithCategory> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val activeCategoryId: Long? = null,
    val query: String = "",
    val sortOrder: NoteSortOrder = NoteSortOrder.UPDATED_DESC,
    /**
     * 首次订阅 Flow 期间为 true, 数据到来后切 false。
     * UI 可借此在加载期间显示指示器, 避免空白闪屏。
     */
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val repository: NotesRepository
) : ViewModel() {

    private val activeCategoryId = MutableStateFlow<Long?>(null)
    private val query = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(NoteSortOrder.UPDATED_DESC)

    private val notes = activeCategoryId.flatMapLatest { categoryId ->
        if (categoryId == null) repository.observeNotes() else repository.observeNotesByCategory(categoryId)
    }

    val uiState: StateFlow<NotesUiState> =
        combine(notes, repository.observeCategories(), activeCategoryId, query, sortOrder) { notesList, categories, activeId, q, sort ->
            // SQL 已经 ORDER BY is_pinned DESC, updated_at DESC, 但内存 sortBy*
            // 会丢掉 is_pinned 优先级。所有排序都先按置顶降序, 再按用户选的次级 key。
            val pinnedFirst = compareByDescending<NoteWithCategory> { it.note.isPinned }
            val filtered = if (q.isBlank()) notesList else {
                val needle = q.trim()
                notesList.filter { n ->
                    n.note.title.contains(needle, ignoreCase = true) ||
                        n.note.content.contains(needle, ignoreCase = true) ||
                        n.note.tags.contains(needle, ignoreCase = true)
                }
            }
            val sorted = when (sort) {
                NoteSortOrder.UPDATED_DESC -> filtered.sortedWith(pinnedFirst.thenByDescending { it.note.updatedAt })
                NoteSortOrder.UPDATED_ASC -> filtered.sortedWith(pinnedFirst.thenBy { it.note.updatedAt })
                NoteSortOrder.CREATED_DESC -> filtered.sortedWith(pinnedFirst.thenByDescending { it.note.createdAt })
                NoteSortOrder.CREATED_ASC -> filtered.sortedWith(pinnedFirst.thenBy { it.note.createdAt })
                NoteSortOrder.TITLE_ASC -> filtered.sortedWith(pinnedFirst.thenBy { it.note.title })
                NoteSortOrder.PRIORITY_DESC -> filtered.sortedWith(pinnedFirst.thenByDescending { it.note.priority })
            }
            NotesUiState(
                notes = sorted,
                categories = categories,
                activeCategoryId = activeId,
                query = q,
                sortOrder = sort,
                // combine 首次发射即表示数据已就绪, 关闭 loading。
                // 后续重订阅 (如配置变更) 因为是 stateIn 重启, combine 会在新数据到来
                // 之前不发新值, 故初始值仍带 isLoading=true。
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotesUiState(isLoading = true)
        )

    // --- F13: 统计 --------------------------------------------------------

    /** 4 个基础计数 (combine 一次发射, 避免 UI 多次重组) */
    val statsTotals: StateFlow<com.example.notes.repository.StatsTotals> =
        repository.observeStatsTotals().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = com.example.notes.repository.StatsTotals(0, 0, 0, 0)
        )

    /** 分类列表, StatsScreen 用 */
    val categories: StateFlow<List<CategoryEntity>> = repository.observeCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * F13: 一次性拉全量笔记内容, 客户端做字数 / 月度统计。
     * 不放在 Flow 里是因为这种"全量快照 + 客户端计算"的方式, 重新发射
     * 整张大表的开销不必要; 改由 UI 在 totals.totalNotes 变化时
     * 重新拉一次, 频率极低。
     */
    suspend fun getStatsRowsOnce(): List<com.example.notes.data.NoteStatsRow> =
        repository.getStatsRows()

    // --- Intents ---------------------------------------------------------

    fun setQuery(value: String) { query.value = value }
    fun setCategoryFilter(id: Long?) { activeCategoryId.value = id }
    fun setSortOrder(order: NoteSortOrder) { sortOrder.value = order }

    /**
     * 保存笔记并替换其全部图片。
     * @param imageUris 图片 URI 列表，按顺序保存
     * @return 更新后的笔记 id (新建时由数据库生成)
     */
    suspend fun saveNote(
        context: Context,
        id: Long,
        title: String,
        content: String,
        categoryId: Long?,
        tags: List<String>,
        isPinned: Boolean,
        color: Int,
        reminderTime: Long? = null,
        reminderRepeat: String = "NONE",
        imageUris: List<String> = emptyList()
    ): Long {
        val note = NoteEntity(
            id = id,
            title = title.ifBlank { content.lineSequence().firstOrNull().orEmpty().take(40) },
            content = content,
            categoryId = categoryId,
            tags = tags.joinToString(","),
            isPinned = isPinned,
            color = color,
            reminderTime = reminderTime,
            reminderRepeat = reminderRepeat
        )
        val savedId = repository.saveNote(note)
        // 替换图片 (用于新建和编辑时以最终结果为准)
        repository.replaceNoteImages(savedId, imageUris)
        // F3: 通知桌面小部件刷新
        NotesAppWidget.requestRefresh(context)
        return savedId
    }

    fun deleteNote(id: Long) {
        launchSafe("deleteNote") { repository.deleteNote(id) }
    }

    /**
     * P97: 删除笔记并支持 5 秒内撤销。
     * 流程: 1) 抓取笔记+图片快照; 2) 立即从 DB 删除; 3) 启动 5s 倒计时,
     * 倒计时结束则放弃快照, 删除永久化; 4) UI 通过 [undoLastDelete] 撤销。
     *
     * 返回快照的 noteId (用于 UI 反馈)。同一时间仅支持撤销最近一次删除, 新删除会取消上一次倒计时。
     *
     * F2: 改走软删除 (deleted_at = now), 笔记从主列表消失, 但仍可通过
     * [undoLastDelete] 在 5 秒内立即恢复, 或在 30 天内从回收站恢复。
     */
    private var pendingUndo: NoteWithCategoryAndImages? = null
    private var pendingUndoJob: Job? = null

    fun deleteNoteWithUndo(id: Long) {
        pendingUndoJob?.cancel()
        launchSafe("deleteNoteWithUndo") {
            val snapshot = repository.getNoteSnapshot(id)
            if (snapshot == null) return@launchSafe
            pendingUndo = snapshot
            // F2: 走软删除, 不真删
            repository.deleteNote(id)
            // 5s 倒计时; 若 5s 内用户撤销, 倒计时会被 cancel
            pendingUndoJob = viewModelScope.launch {
                delay(5_000L)
                if (pendingUndo?.note?.id == id) {
                    pendingUndo = null
                }
            }
        }
    }

    /**
     * P97 + F2: 撤销最近一次"软删除" (5 秒内)。
     * 走 [restoreNoteFromSnapshot] 把 deleted_at 置回 NULL, 图片一并还原。
     * @return 是否成功撤销
     */
    suspend fun undoLastDelete(): Boolean {
        pendingUndoJob?.cancel()
        val snapshot = pendingUndo ?: return false
        pendingUndo = null
        return runCatching {
            repository.restoreNoteFromSnapshot(snapshot)
            true
        }.getOrElse {
            Timber.tag("NotesViewModel").e(it, "undoLastDelete failed: ${it.message}")
            false
        }
    }

    fun togglePin(id: Long, pinned: Boolean) {
        launchSafe("togglePin") { repository.togglePin(id, pinned) }
    }

    fun setPriority(id: Long, priority: Int) {
        launchSafe("setPriority") { repository.setPriority(id, priority) }
    }

    fun setTags(id: Long, tags: List<String>) {
        launchSafe("setTags") { repository.setTags(id, tags.joinToString(",")) }
    }

    fun moveToCategory(id: Long, categoryId: Long?) {
        launchSafe("moveToCategory") { repository.moveToCategory(id, categoryId) }
    }

    fun addCategory(name: String, color: Int, parentId: Long? = null) {
        launchSafe("addCategory") { repository.addCategory(name, color, parentId) }
    }

    fun deleteCategory(category: CategoryEntity) {
        launchSafe("deleteCategory") { repository.deleteCategorySafely(category) }
    }

    /** F12: 重新设置分类的父级 (null=顶级) */
    fun setCategoryParent(id: Long, parentId: Long?) {
        launchSafe("setCategoryParent") { repository.setCategoryParent(id, parentId) }
    }

    /**
     * P61: 用 DAO 直接统计某分类下的笔记数, 避免 O(n*m) 内存过滤。
     * 返回异步 Flow, 分类变更时自动刷新。
     */
    fun noteCountForCategoryFlow(categoryId: Long) =
        repository.observeNoteCountForCategory(categoryId)

    fun removeTagFromAllNotes(tag: String) {
        launchSafe("removeTagFromAllNotes") { repository.removeTagFromAllNotes(tag) }
    }

    // --- Trash (F2) ------------------------------------------------------

    /** F2: 回收站列表 (Flow) */
    fun observeTrash() = repository.observeTrash()

    /** F2: 回收站条数 (UI 角标) */
    fun observeTrashCount() = repository.observeTrashCount()

    /** F2: 从回收站恢复一条 */
    fun restoreFromTrash(id: Long) {
        launchSafe("restoreFromTrash") { repository.restoreFromTrash(id) }
    }

    /** F2: 永久删除回收站里某条 (真删) */
    fun permanentlyDeleteTrashed(id: Long) {
        launchSafe("permanentlyDeleteTrashed") { repository.permanentlyDeleteTrashed(id) }
    }

    /** F2: 立即清空回收站 (UI "清空回收站" 按钮) */
    fun emptyTrash() {
        launchSafe("emptyTrash") {
            // 调永久删除循环: 先拿 id 列表, 再逐个 delete
            // 简化: 用 purgeOldTrash(daysOld=0) 删全部
            repository.purgeOldTrash(daysOld = 0)
        }
    }

    /**
     * F9: 忘记 PIN 时调用 —— 清空所有笔记/分类/图片数据 (不含用户偏好主题色等)。
     * 这是故意不做撤销的操作, 配合应用锁重置流程。
     */
    fun clearAllNotesData() {
        launchSafe("clearAllNotesData") {
            repository.clearAllNotesAndRelated()
        }
    }

    // --- Backup / Restore (F1) -----------------------------------------

    /**
     * F1: 备份/恢复操作的 UI 状态。
     * 单一 sealed interface 让 SettingsScreen 只需 collect 一次。
     */
    sealed interface BackupState {
        // P112-FIX: Kotlin 1.8.22 不支持 `data object`, 改用 `object`
        object Idle : BackupState
        object Working : BackupState
        data class Success(val message: String) : BackupState
        data class Error(val message: String) : BackupState
    }

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    /**
     * F1: 把数据库全量导出为 JSON, 写入用户选择的 [targetUri] (SAF)。
     * @param appVersion 写入 JSON 的 appVersion 字段, 仅作记录用
     */
    fun exportBackup(context: Context, targetUri: Uri, appVersion: String) {
        launchSafe("exportBackup") {
            _backupState.value = BackupState.Working
            val result = runCatching {
                val payload = repository.exportBackup(appVersion)
                val json = BackupManager.toJson(payload)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: throw IllegalStateException("无法打开目标 URI: $targetUri")
                }
                // 成功后回传 (笔记数, 分类数, 图片数, 字节数) 便于 UI 摘要
                BackupStats(
                    notes = payload.notes.size,
                    categories = payload.categories.size,
                    images = payload.images.size,
                    bytes = json.toByteArray(Charsets.UTF_8).size
                )
            }
            _backupState.value = result.fold(
                onSuccess = { stats ->
                    BackupState.Success(
                        "备份完成: ${stats.notes} 笔记 / ${stats.categories} 分类 / ${stats.images} 图片 (${stats.bytes / 1024} KB)"
                    )
                },
                onFailure = { e ->
                    Timber.tag("Backup").e(e, "export failed")
                    BackupState.Error("导出失败: ${e.message ?: "未知错误"}")
                }
            )
            // 5s 后自动重置状态, 避免 Success / Error 一直驻留
            delay(5_000L)
            if (_backupState.value is BackupState.Success || _backupState.value is BackupState.Error) {
                _backupState.value = BackupState.Idle
            }
        }
    }

    private data class BackupStats(val notes: Int, val categories: Int, val images: Int, val bytes: Int)

    /**
     * F1: 从 [sourceUri] 读 JSON, 解析后调用 [importBackup] 还原数据库。
     * @param replaceExisting true = 清空旧数据再导入 (典型"恢复"用法);
     *                        false = 追加 (保留现有数据)
     */
    fun importBackup(context: Context, sourceUri: Uri, replaceExisting: Boolean) {
        launchSafe("importBackup") {
            _backupState.value = BackupState.Working
            val result = runCatching {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: throw IllegalStateException("无法打开源 URI: $sourceUri")
                }
                val payload: BackupPayload = BackupManager.fromJson(json)
                val (c, n, img) = repository.importBackup(payload, replaceExisting)
                Triple(c, n, img)
            }
            _backupState.value = result.fold(
                onSuccess = { (c, n, img) ->
                    BackupState.Success("恢复完成: $c 个分类 / $n 条笔记 / $img 张图片")
                },
                onFailure = { e ->
                    Timber.tag("Backup").e(e, "import failed")
                    BackupState.Error("导入失败: ${e.message ?: "文件格式错误"}")
                }
            )
            delay(5_000L)
            if (_backupState.value is BackupState.Success || _backupState.value is BackupState.Error) {
                _backupState.value = BackupState.Idle
            }
        }
    }

    /** 让 UI 主动确认提示, 避免 5s 后状态被自动清掉 */
    fun consumeBackupState() {
        if (_backupState.value is BackupState.Success || _backupState.value is BackupState.Error) {
            _backupState.value = BackupState.Idle
        }
    }

    // --- 进阶功能: 笔记内链 / 模板 / 历史版本 / 加密 -----------------------

    /** 进阶功能: 按精确标题查笔记 (用于内链 [[标题]] 跳转) */
    suspend fun findNoteByTitle(title: String, excludeId: Long = 0L) =
        repository.findByExactTitle(title, excludeId)

    /** 进阶功能: 模糊搜索标题 (用于内链自动补全) */
    suspend fun searchNotesByTitlePrefix(keyword: String, limit: Int = 10) =
        repository.searchByTitlePrefix(keyword, limit)

    /** 进阶功能: 解析内链, 返回 (id, title) 列表 */
    suspend fun resolveNoteLinks(content: String, currentNoteId: Long = 0L): List<Pair<Long?, String>> {
        val titles = com.example.notes.util.NoteLinkHelper.extractUniqueTitles(content)
        return titles.map { title ->
            val id = repository.getIdByTitle(title)
            id to title
        }
    }

    /** 进阶功能: 模板新建笔记 (一步完成: 渲染模板 + 插入数据库) */
    suspend fun createNoteFromTemplate(
        context: Context,
        templateType: Int,
        categoryId: Long? = null
    ): Long {
        val template = com.example.notes.util.NoteTemplates.get(templateType) ?: return 0L
        val title = com.example.notes.util.NoteTemplates.render(template.title)
        val content = com.example.notes.util.NoteTemplates.render(template.content)
        val id = repository.saveNote(
            NoteEntity(
                title = title,
                content = content,
                categoryId = categoryId,
                templateType = templateType
            )
        )
        com.example.notes.widget.NotesAppWidget.requestRefresh(context)
        return id
    }

    /** 进阶功能: 历史版本列表 */
    fun observeNoteVersions(noteId: Long) = repository.observeNoteVersions(noteId)

    /** 进阶功能: 保存一个历史版本快照 (UI 编辑保存时调用) */
    suspend fun saveNoteVersion(noteId: Long, title: String, content: String) {
        repository.saveNoteVersion(noteId, title, content)
    }

    /** 进阶功能: 删除某个历史版本 */
    fun deleteNoteVersion(versionId: Long) {
        launchSafe("deleteNoteVersion") { repository.deleteNoteVersion(versionId) }
    }

    /** 进阶功能: 恢复某历史版本 (会先把当前内容作为新版本存档) */
    suspend fun restoreNoteVersion(noteId: Long, version: com.example.notes.data.NoteVersionEntity) {
        val current = repository.getNoteOnce(noteId) ?: return
        // 把当前快照存入历史
        repository.saveNoteVersion(current.id, current.title, current.content)
        // 用历史覆盖当前
        repository.updateNoteContent(
            id = noteId,
            title = version.title,
            content = version.content
        )
    }

    /** 进阶功能: 加密笔记正文 */
    suspend fun encryptNote(noteId: Long, password: String): Boolean {
        val note = repository.getNoteOnce(noteId) ?: return false
        if (note.content.isEmpty()) return true
        val encrypted = com.example.notes.util.NoteEncryptor.encrypt(note.content, password)
        val salt = com.example.notes.util.NoteEncryptor.newSalt()
        repository.setNoteEncrypted(noteId, encrypted, salt)
        return true
    }

    /** 进阶功能: 解除加密 */
    suspend fun decryptNote(noteId: Long, password: String): String? {
        val record = repository.getNoteEncryption(noteId) ?: return null
        return runCatching {
            com.example.notes.util.NoteEncryptor.decrypt(record.encryptedContent, password)
        }.getOrNull()
    }

    /** 进阶功能: 检查笔记是否已加密 */
    suspend fun isNoteEncrypted(noteId: Long): Boolean =
        repository.getNoteEncryption(noteId) != null

    /** 进阶功能: 批量导出选中笔记为 Markdown 压缩包 (zip) */
    suspend fun exportNotesAsZip(
        context: Context,
        noteIds: List<Long>,
        targetUri: Uri
    ): Int = withContext(Dispatchers.IO) {
        val notes = repository.getNotesByIds(noteIds)
        val tempDir = java.io.File(context.cacheDir, "export_zip_${System.currentTimeMillis()}").apply { mkdirs() }
        var writtenCount = 0
        notes.forEach { n ->
            val title = n.title.ifBlank { "无标题" }
            val safeName = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60)
            val file = java.io.File(tempDir, "$safeName.md")
            val sb = StringBuilder()
            if (n.title.isNotBlank()) sb.append("# ").append(n.title).appendLine().appendLine()
            if (n.tags.isNotBlank()) sb.appendLine("> 标签: ${n.tags}")
            sb.append(n.content)
            file.writeText(sb.toString(), Charsets.UTF_8)
            writtenCount++
        }
        val zipFile = java.io.File(context.cacheDir, "notes_export_${System.currentTimeMillis()}.zip")
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zos ->
            tempDir.listFiles()?.forEach { f ->
                zos.putNextEntry(java.util.zip.ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
            java.io.FileInputStream(zipFile).use { it.copyTo(out) }
        }
        // 清理临时
        tempDir.listFiles()?.forEach { it.delete() }
        tempDir.delete()
        zipFile.delete()
        writtenCount
    }
}

/**
 * P90: 给 ViewModel 扩展一个 [launchSafe], 统一捕获 DAO/IO 异常并打日志,
 * 避免 Room 异常被传到全局 CoroutineExceptionHandler 导致 APP 闪退。
 * 原版 8 处 `viewModelScope.launch { ... }` 全部无 try-catch, 一个
 * 偶发的 SQLiteConstraintException 就会让进程崩。
 */
private fun androidx.lifecycle.ViewModel.launchSafe(
    tag: String,
    block: suspend () -> Unit
) {
    viewModelScope.launch {
        runCatching { block() }
            .onFailure { e -> Timber.tag("NotesViewModel").e(e, "$tag failed: ${e.message}") }
    }
}

class ViewModelFactory(
    private val repository: NotesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(NotesViewModel::class.java) ->
                NotesViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown VM: ${modelClass.name}")
        }
    }
}
