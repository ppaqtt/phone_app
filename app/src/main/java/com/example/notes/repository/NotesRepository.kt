package com.example.notes.repository

import com.example.notes.data.CategoryDao
import com.example.notes.data.CategoryEntity
import com.example.notes.data.NoteDao
import com.example.notes.data.NoteEntity
import com.example.notes.data.NoteImageDao
import com.example.notes.data.NoteImageEntity
import com.example.notes.data.NoteWithCategory
import com.example.notes.data.NoteWithCategoryAndImages
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the UI layer. The ViewModel never talks to the
 * DAOs directly — that keeps test doubles and future data sources (network,
 * sync) easy to plug in.
 */
class NotesRepository(
    private val database: RoomDatabase,
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao,
    private val noteImageDao: NoteImageDao
) {

    // --- Notes -----------------------------------------------------------

    fun observeNotes(): Flow<List<NoteWithCategory>> = noteDao.observeAll()

    fun observeNote(id: Long): Flow<NoteWithCategory?> = noteDao.observeById(id)

    fun observeNoteWithImages(id: Long): Flow<NoteWithCategoryAndImages?> =
        noteDao.observeWithImages(id)

    fun observeNotesByCategory(categoryId: Long): Flow<List<NoteWithCategory>> =
        noteDao.observeByCategory(categoryId)

    fun searchNotes(query: String): Flow<List<NoteWithCategory>> =
        if (query.isBlank()) noteDao.observeAll() else noteDao.search(query)

    suspend fun saveNote(note: NoteEntity): Long {
        return if (note.id == 0L) {
            noteDao.insert(note.copy(updatedAt = System.currentTimeMillis()))
        } else {
            noteDao.update(note.copy(updatedAt = System.currentTimeMillis()))
            note.id
        }
    }

    suspend fun deleteNote(note: NoteEntity) = noteDao.delete(note)
    suspend fun deleteNote(id: Long) = noteDao.deleteById(id)
    suspend fun togglePin(id: Long, pinned: Boolean) = noteDao.setPinned(id, pinned)

    suspend fun setPriority(id: Long, priority: Int) = noteDao.setPriority(id, priority)
    suspend fun setTags(id: Long, tags: String) = noteDao.setTags(id, tags)
    suspend fun moveToCategory(id: Long, categoryId: Long?) = noteDao.setCategory(id, categoryId)

    suspend fun getAllNotesForSync(): List<NoteEntity> = noteDao.getAllNotesForSync()

    /** 批量移除所有笔记中的指定标签 (一次 SQL 完成) */
    suspend fun removeTagFromAllNotes(tag: String) = noteDao.removeTagFromAllNotes(tag)

    // --- Categories ------------------------------------------------------

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun addCategory(name: String, color: Int): Long =
        categoryDao.insert(CategoryEntity(name = name.trim(), color = color))

    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    /**
     * P61: 观察某分类下的笔记数, 用 SQL COUNT 替代内存过滤。
     */
    fun observeNoteCountForCategory(categoryId: Long): Flow<Int> =
        categoryDao.observeNoteCountForCategory(categoryId)

    /**
     * 删除分类前先清理笔记的 category_id, 避免外键约束失败。
     * P54: 用 [RoomDatabase.withTransaction] 包裹两步操作, 保证原子性。
     * (Room 的 @Transaction 注解只对 DAO 接口方法生效, 在 Repository 上无效。)
     */
    suspend fun deleteCategorySafely(category: CategoryEntity) {
        database.withTransaction {
            categoryDao.clearCategoryForNotes(category.id)
            categoryDao.delete(category)
        }
    }

    suspend fun noteCountForCategory(id: Long): Int = categoryDao.noteCountForCategory(id)

    // --- Note Images -----------------------------------------------------

    fun observeNoteImages(noteId: Long): Flow<List<NoteImageEntity>> =
        noteImageDao.observeByNote(noteId)

    /** 用一组图片 URI 替换该笔记的全部图片 (按传入顺序写入 position) */
    suspend fun replaceNoteImages(noteId: Long, uris: List<String>) {
        noteImageDao.deleteByNote(noteId)
        if (uris.isEmpty()) return
        val entities = uris.mapIndexed { index, uri ->
            NoteImageEntity(noteId = noteId, uri = uri, position = index)
        }
        noteImageDao.insertAll(entities)
    }

    suspend fun appendNoteImages(noteId: Long, uris: List<String>, startPosition: Int) {
        if (uris.isEmpty()) return
        val entities = uris.mapIndexed { index, uri ->
            NoteImageEntity(noteId = noteId, uri = uri, position = startPosition + index)
        }
        noteImageDao.insertAll(entities)
    }

    suspend fun deleteNoteImage(image: NoteImageEntity) = noteImageDao.delete(image)
}
