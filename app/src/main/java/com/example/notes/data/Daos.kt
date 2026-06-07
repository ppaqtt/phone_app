package com.example.notes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Transaction
    @Query("SELECT * FROM notes ORDER BY is_pinned DESC, updated_at DESC")
    fun observeAll(): Flow<List<NoteWithCategory>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<NoteWithCategory?>

    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE title LIKE '%' || :query || '%' COLLATE NOCASE
           OR content LIKE '%' || :query || '%' COLLATE NOCASE
           OR tags LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY is_pinned DESC, updated_at DESC
        """
    )
    fun search(query: String): Flow<List<NoteWithCategory>>

    @Transaction
    @Query("SELECT * FROM notes WHERE category_id = :categoryId ORDER BY updated_at DESC")
    fun observeByCategory(categoryId: Long): Flow<List<NoteWithCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE notes SET is_pinned = :pinned, updated_at = :ts WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, ts: Long = System.currentTimeMillis())

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesForSync(): List<NoteEntity>
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM notes WHERE category_id = :id")
    suspend fun noteCountForCategory(id: Long): Int
}
