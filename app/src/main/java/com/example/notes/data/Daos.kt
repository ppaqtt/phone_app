package com.example.notes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Transaction
    @Query("SELECT * FROM notes ORDER BY is_pinned DESC, updated_at DESC")
    fun observeAll(): Flow<List<NoteWithCategory>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: Long): Flow<NoteWithCategory?>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeWithImages(id: Long): Flow<NoteWithCategoryAndImages?>

    @Transaction
    @Query("SELECT * FROM notes WHERE category_id = :categoryId ORDER BY is_pinned DESC, updated_at DESC")
    fun observeByCategory(categoryId: Long): Flow<List<NoteWithCategory>>

    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%'
        ORDER BY is_pinned DESC, updated_at DESC
        """
    )
    fun search(query: String): Flow<List<NoteWithCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Query("UPDATE notes SET updated_at = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE notes SET is_pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE notes SET priority = :priority WHERE id = :id")
    suspend fun setPriority(id: Long, priority: Int)

    @Query("UPDATE notes SET tags = :tags WHERE id = :id")
    suspend fun setTags(id: Long, tags: String)

    @Query("UPDATE notes SET category_id = :categoryId WHERE id = :id")
    suspend fun setCategory(id: Long, categoryId: Long?)

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesForSync(): List<NoteEntity>

    /**
     * 批量移除所有笔记中的指定标签。
     * 用 ',' || tags || ',' LIKE ',tag,' 的方式精确定位标签项,
     * 避免 "work" 被误匹配到 "homework"。
     */
    @Query("""
        UPDATE notes
        SET tags = REPLACE(',' || tags || ',', ',' || :tag || ',', ',')
    """)
    suspend fun removeTagFromAllNotes(tag: String)
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY created_at ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM notes WHERE category_id = :id")
    suspend fun noteCountForCategory(id: Long): Int

    /** 把某分类下所有笔记的 category_id 置空 (用于删除分类前清理) */
    @Query("UPDATE notes SET category_id = NULL WHERE category_id = :id")
    suspend fun clearCategoryForNotes(id: Long)
}

/** 笔记图片 DAO */
@Dao
interface NoteImageDao {

    @Query("SELECT * FROM note_images WHERE noteId = :noteId ORDER BY position ASC")
    fun observeByNote(noteId: Long): Flow<List<NoteImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: NoteImageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<NoteImageEntity>)

    @Delete
    suspend fun delete(image: NoteImageEntity)

    @Query("DELETE FROM note_images WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM note_images WHERE noteId = :noteId")
    suspend fun deleteByNote(noteId: Long)
}
