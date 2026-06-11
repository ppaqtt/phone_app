package com.example.notes.repository

import com.example.notes.data.CategoryDao
import com.example.notes.data.CategoryEntity
import com.example.notes.data.NoteDao
import com.example.notes.data.NoteEntity
import com.example.notes.data.NoteImageDao
import com.example.notes.data.NoteImageEntity
import com.example.notes.data.NoteStatsRow
import com.example.notes.data.NoteWithCategory
import com.example.notes.data.NoteWithCategoryAndImages
import com.example.notes.util.BackupPayload
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import timber.log.Timber

/**
 * Single source of truth for the UI layer. The ViewModel never talks to the
 * DAOs directly — that keeps test doubles and future data sources (network,
 * sync) easy to plug in.
 */

/** F13: 4 个基础计数, combine 一次发射避免 UI 多次重组 */
data class StatsTotals(
    val totalNotes: Int,
    val pinnedNotes: Int,
    val notesWithReminder: Int,
    val totalImages: Int
)

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

    /**
     * F2: 删除按钮改走"软删除" — 把 deleted_at 设为当前时间, 笔记从列表消失,
     * 30 天内可在回收站恢复, 30 天后由 [TrashJanitor] 真删。
     * 用"真删"路径只在"回收站永久删除"按钮和备份清空时使用。
     */
    suspend fun deleteNote(id: Long) = noteDao.softDelete(id)

    /**
     * F2: 真正从数据库删除一条笔记 (硬删除), 关联 note_images 通过
     * 外键 CASCADE 自动级联删除。
     */
    suspend fun permanentDeleteNote(id: Long) = noteDao.permanentDelete(id)
    suspend fun togglePin(id: Long, pinned: Boolean) = noteDao.setPinned(id, pinned)

    /**
     * P97: 获取笔记及其图片的快照, 用于删除-撤销逻辑。
     * 调用者负责在删除前保存, 撤销时通过 [restoreNoteFromSnapshot] 还原。
     */
    suspend fun getNoteSnapshot(id: Long): NoteWithCategoryAndImages? =
        noteDao.getWithImagesOnce(id)

    /**
     * P97: 用快照恢复一条笔记 (包括原 id 和全部图片)。
     * 关联图片必须先恢复, 否则 noteId 外键可能找不到对应笔记。
     *
     * F2: 撤销软删除时, 强制把 deleted_at 置 NULL, 否则软删除行还在, 主列表看不到。
     */
    suspend fun restoreNoteFromSnapshot(snapshot: NoteWithCategoryAndImages) {
        database.withTransaction {
            val restored = snapshot.note.copy(deletedAt = null)
            noteDao.insertWithId(restored)
            noteImageDao.deleteByNote(snapshot.note.id)
            val images = snapshot.images.map { it.copy(id = 0) }
            if (images.isNotEmpty()) noteImageDao.insertAll(images)
        }
    }

    suspend fun setPriority(id: Long, priority: Int) = noteDao.setPriority(id, priority)
    suspend fun setTags(id: Long, tags: String) = noteDao.setTags(id, tags)
    suspend fun moveToCategory(id: Long, categoryId: Long?) = noteDao.setCategory(id, categoryId)

    suspend fun getAllNotesForSync(): List<NoteEntity> = noteDao.getAllNotesForSync()

    /** F3: 桌面小部件取最近 N 条笔记 */
    suspend fun getRecentNotes(limit: Int = 5): List<NoteEntity> = noteDao.getRecentNotes(limit)

    /**
     * F15: 一次性取一条笔记 (按主键), 用于 ReminderWorker 检查 repeat 字段。
     * 非响应式, 避免 Room 在 Worker 协程里订阅 Flow 持长生命周期。
     */
    suspend fun getNoteOnce(id: Long): NoteEntity? = noteDao.getNoteOnce(id)

    /** F15: 一次性更新提醒时间 + 重复模式 (触发后重排) */
    suspend fun updateReminder(id: Long, reminderTime: Long, repeat: String) {
        noteDao.updateReminderTime(id, reminderTime)
        noteDao.setReminderRepeat(id, repeat)
    }

    /** 批量移除所有笔记中的指定标签 (一次 SQL 完成) */
    suspend fun removeTagFromAllNotes(tag: String) = noteDao.removeTagFromAllNotes(tag)

    // --- Categories ------------------------------------------------------

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun addCategory(name: String, color: Int, parentId: Long? = null): Long =
        categoryDao.insert(CategoryEntity(name = name.trim(), color = color, parentId = parentId))

    /** F12: 修改分类的父级 */
    suspend fun setCategoryParent(id: Long, parentId: Long?) =
        categoryDao.setParent(id, parentId)

    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    /**
     * P61: 观察某分类下的笔记数, 用 SQL COUNT 替代内存过滤。
     */
    fun observeNoteCountForCategory(categoryId: Long): Flow<Int> =
        categoryDao.observeNoteCountForCategory(categoryId)

    /**
     * 删除分类前先清理笔记的 category_id 和子分类的 parent_id, 避免外键约束失败。
     * P54: 用 [RoomDatabase.withTransaction] 包裹两步操作, 保证原子性。
     * (Room 的 @Transaction 注解只对 DAO 接口方法生效, 在 Repository 上无效。)
     *
     * F12: 增加"清空子分类 parent_id"步骤, 防止删父分类后子分类残留指向不存在的父级。
     */
    suspend fun deleteCategorySafely(category: CategoryEntity) {
        database.withTransaction {
            categoryDao.clearCategoryForNotes(category.id)
            categoryDao.clearParentForChildren(category.id)
            categoryDao.delete(category)
        }
    }

    suspend fun noteCountForCategory(id: Long): Int = categoryDao.noteCountForCategory(id)

    // --- Stats (F13) ------------------------------------------------------

    /** F13: 全部 / 置顶 / 提醒数 (Flow, 增删改自动刷新) */
    fun observeStatsTotals() = combine(
        noteDao.observeTotalCount(),
        noteDao.observePinnedCount(),
        noteDao.observeReminderCount(),
        noteImageDao.observeImageCount()
    ) { total, pinned, reminder, images ->
        StatsTotals(total, pinned, reminder, images)
    }

    /** F13: 一次性取全量笔记内容投影, 客户端做字数/分类/月度统计 */
    suspend fun getStatsRows(): List<NoteStatsRow> = noteDao.getContentForStats()

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

    // --- Trash (F2) ------------------------------------------------------

    /** F2: 回收站列表 (Flow, 自动刷新) */
    fun observeTrash() = noteDao.observeTrash()

    /** F2: 回收站条目数 (用于 UI 角标) */
    fun observeTrashCount(): Flow<Int> = noteDao.observeTrashCount()

    /** F2: 从回收站恢复一条笔记 */
    suspend fun restoreFromTrash(id: Long) = noteDao.restoreFromTrash(id)

    /**
     * F2: 物理删除回收站里某条笔记 (永久删除按钮)。
     * 注意 deleteNote(id) 走软删除, 想真删必须调这个。
     */
    suspend fun permanentlyDeleteTrashed(id: Long) = noteDao.permanentDelete(id)

    /**
     * F2: 清空 N 天前的已删笔记 (后台 TrashJanitor 调)。
     * @return 真删的条数
     */
    suspend fun purgeOldTrash(daysOld: Int = 30): Int {
        val threshold = System.currentTimeMillis() - daysOld * 24L * 60 * 60 * 1000
        return noteDao.purgeOldTrash(threshold)
    }

    // --- Backup / Restore (F1) -----------------------------------------

    /**
     * F1: 一次性把所有数据拉出来, 构建成可序列化的 payload。
     * 用事务保证导出期间 4 个表的数据一致 (避免读到半改状态)。
     */
    suspend fun exportBackup(appVersion: String): BackupPayload {
        return database.withTransaction {
            val categories = categoryDao.getAllOnce()
            val notes = noteDao.getAllNotesForSync()
            val images = noteImageDao.getAllOnce()
            com.example.notes.util.BackupManager.buildPayload(
                appVersion = appVersion,
                categories = categories,
                notes = notes,
                images = images
            )
        }
    }

    /**
     * F1: 用 payload 还原数据, 全部在一个事务里完成。
     *
     * @param replaceExisting true = 清空旧数据再导入 (典型用法);
     *                        false = 保留旧数据, 备份内容作为新增 (id 会重新分配)。
     * @return (导入的分类数, 导入的笔记数, 导入的图片数)
     *
     * 实现要点:
     * 1) 旧 id → 新 id 映射表, 处理数据库 AUTO_INCREMENT 复用问题;
     * 2) 分类先于笔记导入, 否则笔记的 category_id 外键找不到父;
     * 3) 笔记先于图片导入, 否则图片的 noteId 外键找不到父;
     * 4) 不删除旧 category, 用 REPLACE 让 OnConflictStrategy 处理。
     */
    suspend fun importBackup(
        payload: BackupPayload,
        replaceExisting: Boolean
    ): Triple<Int, Int, Int> {
        return database.withTransaction {
            if (replaceExisting) {
                // 清空 3 张表; 图片先删 (外键依赖), 笔记次之, 分类最后
                noteImageDao.clearAll()
                noteDao.clearAll()
                categoryDao.clearAll()
            }

            // 1) 分类
            val categoryIdMap = HashMap<Long, Long>(payload.categories.size)
            payload.categories.forEach { c ->
                val newId = categoryDao.insertWithId(
                    CategoryEntity(
                        id = c.oldId,
                        name = c.name,
                        color = c.color,
                        createdAt = c.createdAt
                    )
                )
                categoryIdMap[c.oldId] = newId
            }

            // F12: 第二轮 — 维护 parent_id。老备份没有 parentOldId 时仍保持顶级。
            // 用单独的循环确保父分类已先插入, 查找 categoryIdMap 不会撞到 -1。
            payload.categories.forEach { c ->
                if (c.parentOldId != null) {
                    val newId = categoryIdMap[c.oldId] ?: return@forEach
                    val newParentId = categoryIdMap[c.parentOldId]
                    categoryDao.setParent(newId, newParentId)
                }
            }

            // 2) 笔记
            val noteIdMap = HashMap<Long, Long>(payload.notes.size)
            payload.notes.forEach { n ->
                val newCategoryId = n.categoryOldId?.let { categoryIdMap[it] }
                val newId = noteDao.insertWithId(
                    NoteEntity(
                        id = n.oldId,
                        title = n.title,
                        content = n.content,
                        categoryId = newCategoryId,
                        tags = n.tags,
                        isPinned = n.isPinned,
                        priority = n.priority,
                        color = n.color,
                        reminderTime = n.reminderTime,
                        reminderRepeat = n.reminderRepeat,
                        createdAt = n.createdAt,
                        updatedAt = n.updatedAt
                    )
                )
                noteIdMap[n.oldId] = newId
            }

            // 3) 图片
            val imageEntities = payload.images.mapNotNull { img ->
                val newNoteId = noteIdMap[img.noteOldId] ?: return@mapNotNull null
                NoteImageEntity(
                    id = img.oldId,
                    noteId = newNoteId,
                    uri = img.uri,
                    position = img.position
                )
            }
            if (imageEntities.isNotEmpty()) {
                noteImageDao.insertAll(imageEntities)
            }

            Timber.tag("Backup")
                .i("imported categories=${categoryIdMap.size} notes=${noteIdMap.size} images=${imageEntities.size}")
            Triple(categoryIdMap.size, noteIdMap.size, imageEntities.size)
        }
    }
}
