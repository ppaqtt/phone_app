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
    val sortOrder: NoteSortOrder = NoteSortOrder.UPDATED_DESC
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
            val filtered = if (q.isBlank()) notesList else {
                val needle = q.trim()
                notesList.filter { n ->
                    n.note.title.contains(needle, ignoreCase = true) ||
                        n.note.content.contains(needle, ignoreCase = true) ||
                        n.note.tags.contains(needle, ignoreCase = true)
                }
            }
            val sorted = when (sort) {
                NoteSortOrder.UPDATED_DESC -> filtered.sortedByDescending { it.note.updatedAt }
                NoteSortOrder.UPDATED_ASC -> filtered.sortedBy { it.note.updatedAt }
                NoteSortOrder.CREATED_DESC -> filtered.sortedByDescending { it.note.createdAt }
                NoteSortOrder.CREATED_ASC -> filtered.sortedBy { it.note.createdAt }
                NoteSortOrder.TITLE_ASC -> filtered.sortedBy { it.note.title }
                NoteSortOrder.PRIORITY_DESC -> filtered.sortedByDescending { it.note.priority }
            }
            NotesUiState(
                notes = sorted,
                categories = categories,
                activeCategoryId = activeId,
                query = q,
                sortOrder = sort
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotesUiState()
        )

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
        id: Long,
        title: String,
        content: String,
        categoryId: Long?,
        tags: List<String>,
        isPinned: Boolean,
        color: Int,
        reminderTime: Long? = null,
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
            reminderTime = reminderTime
        )
        val savedId = repository.saveNote(note)
        // 替换图片 (用于新建和编辑时以最终结果为准)
        repository.replaceNoteImages(savedId, imageUris)
        return savedId
    }

    fun deleteNote(id: Long) {
        viewModelScope.launchSafe("deleteNote") { repository.deleteNote(id) }
    }

    /**
     * P97: 删除笔记并支持 5 秒内撤销。
     * 流程: 1) 抓取笔记+图片快照; 2) 立即从 DB 删除; 3) 启动 5s 倒计时,
     * 倒计时结束则放弃快照, 删除永久化; 4) UI 通过 [undoLastDelete] 撤销。
     *
     * 返回快照的 noteId (用于 UI 反馈)。同一时间仅支持撤销最近一次删除, 新删除会取消上一次倒计时。
     */
    private var pendingUndo: NoteWithCategoryAndImages? = null
    private var pendingUndoJob: Job? = null

    fun deleteNoteWithUndo(id: Long) {
        pendingUndoJob?.cancel()
        viewModelScope.launchSafe("deleteNoteWithUndo") {
            val snapshot = repository.getNoteSnapshot(id)
            if (snapshot == null) return@launchSafe
            pendingUndo = snapshot
            repository.deleteNote(id)
            // 5s 倒计时; 若 5s 内用户撤销, 倒计时会被 cancel
            pendingUndoJob = viewModelScope.launch {
                delay(5_000L)
                // 5s 内未撤销, 永久删除
                if (pendingUndo?.note?.id == id) {
                    pendingUndo = null
                }
            }
        }
    }

    /**
     * P97: 撤销最近一次删除 (必须在 5 秒内调用, 否则快照已清空)。
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
            android.util.Log.e("NotesViewModel", "undoLastDelete failed: ${it.message}", it)
            false
        }
    }

    fun togglePin(id: Long, pinned: Boolean) {
        viewModelScope.launchSafe("togglePin") { repository.togglePin(id, pinned) }
    }

    fun setPriority(id: Long, priority: Int) {
        viewModelScope.launchSafe("setPriority") { repository.setPriority(id, priority) }
    }

    fun setTags(id: Long, tags: List<String>) {
        viewModelScope.launchSafe("setTags") { repository.setTags(id, tags.joinToString(",")) }
    }

    fun moveToCategory(id: Long, categoryId: Long?) {
        viewModelScope.launchSafe("moveToCategory") { repository.moveToCategory(id, categoryId) }
    }

    fun addCategory(name: String, color: Int) {
        viewModelScope.launchSafe("addCategory") { repository.addCategory(name, color) }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launchSafe("deleteCategory") { repository.deleteCategorySafely(category) }
    }

    /**
     * P61: 用 DAO 直接统计某分类下的笔记数, 避免 O(n*m) 内存过滤。
     * 返回异步 Flow, 分类变更时自动刷新。
     */
    fun noteCountForCategoryFlow(categoryId: Long) =
        repository.observeNoteCountForCategory(categoryId)

    fun removeTagFromAllNotes(tag: String) {
        viewModelScope.launchSafe("removeTagFromAllNotes") { repository.removeTagFromAllNotes(tag) }
    }

    // --- Backup / Restore (F1) -----------------------------------------

    /**
     * F1: 备份/恢复操作的 UI 状态。
     * 单一 sealed interface 让 SettingsScreen 只需 collect 一次。
     */
    sealed interface BackupState {
        data object Idle : BackupState
        data object Working : BackupState
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
        viewModelScope.launchSafe("exportBackup") {
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
        viewModelScope.launchSafe("importBackup") {
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
            .onFailure { e -> android.util.Log.e("NotesViewModel", "$tag failed: ${e.message}", e) }
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
