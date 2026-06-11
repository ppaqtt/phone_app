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
    @Query("SELECT * FROM notes WHERE deleted_at IS NULL ORDER BY is_pinned DESC, updated_at DESC")
    fun observeAll(): Flow<List<NoteWithCategory>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id AND deleted_at IS NULL")
    fun observeById(id: Long): Flow<NoteWithCategory?>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id AND deleted_at IS NULL")
    fun observeWithImages(id: Long): Flow<NoteWithCategoryAndImages?>

    @Transaction
    @Query("SELECT * FROM notes WHERE category_id = :categoryId AND deleted_at IS NULL ORDER BY is_pinned DESC, updated_at DESC")
    fun observeByCategory(categoryId: Long): Flow<List<NoteWithCategory>>

    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE deleted_at IS NULL
          AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
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

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL")
    suspend fun getAllNotesForSync(): List<NoteEntity>

    /**
     * F3: 桌面小部件取最近 N 条笔记 (按 updated_at 倒序, 仅未删除)。
     * suspend fun 让小部件刷新走 IO, 不阻塞 AppWidgetProvider 回调。
     */
    @Query("SELECT * FROM notes WHERE deleted_at IS NULL ORDER BY updated_at DESC LIMIT :limit")
    suspend fun getRecentNotes(limit: Int): List<NoteEntity>

    /** F15: 按主键取一条笔记 (非响应式, Worker / 备份导出用) */
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteOnce(id: Long): NoteEntity?

    /** F15: 更新提醒时间, 不动 reminder_repeat 字段 */
    @Query("UPDATE notes SET reminder_time = :reminderTime WHERE id = :id")
    suspend fun updateReminderTime(id: Long, reminderTime: Long)

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
     *
     * P111-FIX: KSP 解析器在嵌套 CASE WHEN + WHERE + || 拼接时报错
     * "no viable alternative at input 'UPDATE' / Not sure how to convert a Cursor"。
     * 简化方案: 只保留 SET 子句中的简单表达式, 改用 instr() 替代 || 拼接。
     */
    @Query("""
        UPDATE notes
        SET tags = TRIM(',' FROM REPLACE(',' || tags || ',', ',' || :tag || ',', ','))
        WHERE tags != '' AND :tag != '' AND instr(',' || tags || ',', ',' || :tag || ',') > 0
    """)
    suspend fun removeTagFromAllNotes(tag: String)

    /**
     * F1: 导入备份前清空 notes 表 (顺序: 图片 → 笔记 → 分类)。
     * 外键约束在 category_id → categories.id 是 SET NULL, 清空笔记
     * 时不会触发外键错误; 清空分类亦然。
     */
    @Query("DELETE FROM notes")
    suspend fun clearAll()

    // --- Trash (F2) ------------------------------------------------------

    /**
     * F2: 回收站列表, deleted_at IS NOT NULL, 按删除时间倒序。
     */
    @Transaction
    @Query("SELECT * FROM notes WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun observeTrash(): Flow<List<NoteWithCategory>>

    /** F2: 软删除 — 把 deleted_at 设为当前时间戳 */
    @Query("UPDATE notes SET deleted_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long = System.currentTimeMillis())

    /** F2: 从回收站恢复 — 把 deleted_at 置 NULL */
    @Query("UPDATE notes SET deleted_at = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    /** F2: 真删 — 硬删除 (回收站条目永久删除按钮) */
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun permanentDelete(id: Long)

    /**
     * F2: 清空 N 天前的已删笔记 (后台清理任务调用)。
     * 关联的 note_images 通过外键 CASCADE 自动级联删除。
     */
    @Query("DELETE FROM notes WHERE deleted_at IS NOT NULL AND deleted_at < :before")
    suspend fun purgeOldTrash(before: Long): Int

    /** F2: 统计回收站条目数 (UI 显示 "回收站 (3)" 角标) */
    @Query("SELECT COUNT(*) FROM notes WHERE deleted_at IS NOT NULL")
    fun observeTrashCount(): Flow<Int>

    // --- Stats (F13) -----------------------------------------------------

    /** F13: 全部有效笔记数 (deleted_at IS NULL) */
    @Query("SELECT COUNT(*) FROM notes WHERE deleted_at IS NULL")
    fun observeTotalCount(): Flow<Int>

    /** F13: 置顶数 */
    @Query("SELECT COUNT(*) FROM notes WHERE deleted_at IS NULL AND is_pinned = 1")
    fun observePinnedCount(): Flow<Int>

    /** F13: 有提醒的笔记数 */
    @Query("SELECT COUNT(*) FROM notes WHERE deleted_at IS NULL AND reminder_time IS NOT NULL")
    fun observeReminderCount(): Flow<Int>

    /** F13: 全部有效笔记的 (id, content, category_id), 用于客户端统计字数 */
    @Query("SELECT id, content, category_id, created_at FROM notes WHERE deleted_at IS NULL")
    suspend fun getContentForStats(): List<NoteStatsRow>

    /** F15: 更新提醒重复模式 (NONE / DAILY / WEEKLY / MONTHLY / YEARLY) */
    @Query("UPDATE notes SET reminder_repeat = :repeat WHERE id = :id")
    suspend fun setReminderRepeat(id: Long, repeat: String)
}

/** F13: stats 用的轻量投影, 只取 4 个字段减少 IO */
data class NoteStatsRow(
    val id: Long,
    val content: String,
    @androidx.room.ColumnInfo(name = "category_id")
    val categoryId: Long?,
    @androidx.room.ColumnInfo(name = "created_at")
    val createdAt: Long
)

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
    @Query("SELECT COUNT(*) FROM notes WHERE category_id = :categoryId")
    fun observeNoteCountForCategory(categoryId: Long): Flow<Int>

    /** 把某分类下所有笔记的 category_id 置空 (用于删除分类前清理) */
    @Query("UPDATE notes SET category_id = NULL WHERE category_id = :id")
    suspend fun clearCategoryForNotes(id: Long)

    /**
     * F12: 把"以 id 为父分类"的子分类的 parent_id 置空 (用于删除父分类前清理)。
     * 不级联删除子分类 —— 子分类仍是有效数据, 只失去层级关系。
     */
    @Query("UPDATE categories SET parent_id = NULL WHERE parent_id = :id")
    suspend fun clearParentForChildren(id: Long)

    /** F12: 更新父分类 */
    @Query("UPDATE categories SET parent_id = :parentId WHERE id = :id")
    suspend fun setParent(id: Long, parentId: Long?)

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

    /** F13: 图片总数 (与 notes.deleted_at 联表过滤已删笔记) */
    @Query("""
        SELECT COUNT(*) FROM note_images
        WHERE noteId IN (SELECT id FROM notes WHERE deleted_at IS NULL)
    """)
    fun observeImageCount(): Flow<Int>
}
