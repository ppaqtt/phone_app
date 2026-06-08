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

data class NotesUiState(
    val notes: List<NoteWithCategory> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val activeCategoryId: Long? = null,
    val query: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val repository: NotesRepository
) : ViewModel() {

    private val activeCategoryId = MutableStateFlow<Long?>(null)
    private val query = MutableStateFlow("")

    private val notes = activeCategoryId.flatMapLatest { categoryId ->
        if (categoryId == null) repository.observeNotes() else repository.observeNotesByCategory(categoryId)
    }

    val uiState: StateFlow<NotesUiState> =
        combine(notes, repository.observeCategories(), activeCategoryId, query) { notesList, categories, activeId, q ->
            val filtered = if (q.isBlank()) notesList else {
                val needle = q.trim()
                notesList.filter { n ->
                    n.note.title.contains(needle, ignoreCase = true) ||
                        n.note.content.contains(needle, ignoreCase = true) ||
                        n.note.tags.contains(needle, ignoreCase = true)
                }
            }
            NotesUiState(
                notes = filtered,
                categories = categories,
                activeCategoryId = activeId,
                query = q
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotesUiState()
        )

    // --- Intents ---------------------------------------------------------

    fun setQuery(value: String) { query.value = value }
    fun setCategoryFilter(id: Long?) { activeCategoryId.value = id }

    /**
     * 保存笔记并替换其全部图片。
     * @param imageUris 图片 URI 列表，按顺序保存
     * @return 更新后的笔记 id (新建时由数据库生成)
     */
    fun saveNote(
        id: Long,
        title: String,
        content: String,
        categoryId: Long?,
        tags: List<String>,
        isPinned: Boolean,
        color: Int,
        reminderTime: Long? = null,
        imageUris: List<String> = emptyList()
    ) {
        viewModelScope.launch {
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
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch { repository.deleteNote(id) }
    }

    fun togglePin(id: Long, pinned: Boolean) {
        viewModelScope.launch { repository.togglePin(id, pinned) }
    }

    fun addCategory(name: String, color: Int) {
        viewModelScope.launch { repository.addCategory(name, color) }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch { repository.deleteCategory(category) }
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
