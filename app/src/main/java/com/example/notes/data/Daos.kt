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
     * P97: 一次性 (非响应式) 获取笔记 + 全部图片, 用于删除前的快照保存,
     * 撤销删除时再原样插回。
     */
    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getWithImagesOnce(id: Long): NoteWithCategoryAndImages?

    /**
     * P97: 撤销删除时, 强制按指定 id 重新插入笔记 (即使原 id 已空出, 也能保留原 id)。
     * 用 REPLACE 策略确保 id 被复用, 关联图片 (noteId) 也能正确链接。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithId(note: NoteEntity): Long

    /**
     * 批量移除所有笔记中的指定标签。
     * 用 ',' || tags || ',' LIKE ',tag,' 的方式精确定位标签项,
     * 避免 "work" 被误匹配到 "homework"。
     *
     * P83: 旧 SQL `REPLACE(','||tags||',', ','||tag||',', ',')` 会留下首尾逗号。
     * 例子: tags="a,b,c" tag="b" → ",a,c," (残留首尾逗号), 期望 "a,c"。
     * 修法: 在 REPLACE 外面套一层 `TRIM(',' FROM ...)`, 剥掉两侧的逗号。
     * 空 tag 直接 no-op, 避免 ',,' 误匹配。
     */
    @Query("""
        UPDATE notes
        SET tags = CASE
          WHEN :tag = '' OR tags = '' OR (',' || tags || ',') NOT LIKE ('%,' || :tag || ',%')
            THEN tags
          ELSE TRIM(',' FROM REPLACE(',' || tags || ',', ',' || :tag || ',', ','))
        END
    """)
    suspend fun removeTagFromAllNotes(tag: String)

    /**
     * F1: 导入备份前清空 notes 表 (顺序: 图片 → 笔记 → 分类)。
     * 外键约束在 category_id → categories.id 是 SET NULL, 清空笔记
     * 时不会触发外键错误; 清空分类亦然。
     */
    @Query("DELETE FROM notes")
    suspend fun clearAll()
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

    /** P61: 响应式版本, 删除/迁移后自动刷新 */
    @Query("SELECT COUNT(*) FROM notes WHERE category_id = :id")
    fun observeNoteCountForCategory(categoryId: Long): Flow<Int>

    /** 把某分类下所有笔记的 category_id 置空 (用于删除分类前清理) */
    @Query("UPDATE notes SET category_id = NULL WHERE category_id = :id")
    suspend fun clearCategoryForNotes(id: Long)

    /**
     * F1: 备份导出时一次性拿全部分类 (非响应式, 仅用于构建 JSON)。
     */
    @Query("SELECT * FROM categories ORDER BY created_at ASC")
    suspend fun getAllOnce(): List<CategoryEntity>

    /**
     * F1: 导入时按"老 id → 新 id"映射, 强制 REPLACE 让 AUTO_INCREMENT 复用。
     * 配合 idRemap 列表上层做。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithId(category: CategoryEntity): Long

    /**
     * F1: 导入备份前清空 categories 表。
     */
    @Query("DELETE FROM categories")
    suspend fun clearAll()
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

    /**
     * F1: 备份导出时一次性拿全部图片 (非响应式)。
     */
    @Query("SELECT * FROM note_images ORDER BY noteId ASC, position ASC")
    suspend fun getAllOnce(): List<NoteImageEntity>

    /**
     * F1: 导入备份前清空 note_images 表。
     */
    @Query("DELETE FROM note_images")
    suspend fun clearAll()
}
