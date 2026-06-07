package com.example.notes.repository

import com.example.notes.data.CategoryDao
import com.example.notes.data.CategoryEntity
import com.example.notes.data.NoteDao
import com.example.notes.data.NoteEntity
import com.example.notes.data.NoteWithCategory
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the UI layer. The ViewModel never talks to the
 * DAOs directly — that keeps test doubles and future data sources (network,
 * sync) easy to plug in.
 */
class NotesRepository(
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao
) {

    // --- Notes -----------------------------------------------------------

    fun observeNotes(): Flow<List<NoteWithCategory>> = noteDao.observeAll()

    fun observeNote(id: Long): Flow<NoteWithCategory?> = noteDao.observeById(id)

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

    suspend fun getAllNotesForSync(): List<NoteEntity> = noteDao.getAllNotesForSync()

    // --- Categories ------------------------------------------------------

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun addCategory(name: String, color: Int): Long =
        categoryDao.insert(CategoryEntity(name = name.trim(), color = color))

    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    suspend fun noteCountForCategory(id: Long): Int = categoryDao.noteCountForCategory(id)
}
