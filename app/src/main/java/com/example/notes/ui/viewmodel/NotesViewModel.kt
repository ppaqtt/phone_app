package com.example.notes.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.notes.data.CategoryEntity
import com.example.notes.data.NoteEntity
import com.example.notes.data.NoteWithCategory
import com.example.notes.repository.NotesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
