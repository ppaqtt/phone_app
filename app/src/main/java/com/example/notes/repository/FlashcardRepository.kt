package com.example.notes.repository

import com.example.notes.data.FlashcardDao
import com.example.notes.data.FlashcardEntity
import com.example.notes.util.CardParser
import com.example.notes.util.Sm2Scheduler
import kotlinx.coroutines.flow.Flow

/**
 * 闪卡仓库: 生成、查询、评分。
 * 卡片与笔记同生命周期 (外键 CASCADE), 不单独做备份/同步。
 */
class FlashcardRepository(
    private val dao: FlashcardDao
) {

    fun observeAll(): Flow<List<FlashcardEntity>> = dao.observeAll()

    fun observeTotalCount(): Flow<Int> = dao.observeTotalCount()

    suspend fun countDue(now: Long = System.currentTimeMillis()): Int = dao.countDue(now)

    suspend fun getAllOnce(): List<FlashcardEntity> = dao.getAllOnce()

    suspend fun getByNote(noteId: Long): List<FlashcardEntity> = dao.getByNote(noteId)

    /**
     * 从笔记正文重新生成卡片。
     *
     * 幂等策略:
     * - 已存在的同内容卡片保留复习进度, 只更新正反面文字;
     * - 用户删掉标记后, 对应旧卡片一并删除;
     * - 返回当前笔记的卡片总数。
     */
    suspend fun syncFromNote(noteId: Long, noteTitle: String, content: String): Int {
        val parsed = CardParser.parse(content, noteTitle)
        val existing = dao.getByNote(noteId).associateBy { it.sourceKey }
        val keep = HashSet<String>(parsed.size)

        parsed.forEach { card ->
            keep.add(card.key)
            val old = existing[card.key]
            if (old == null) {
                dao.insert(
                    FlashcardEntity(
                        noteId = noteId,
                        front = card.front,
                        back = card.back,
                        sourceKey = card.key
                    )
                )
            } else if (old.front != card.front || old.back != card.back) {
                dao.update(old.copy(front = card.front, back = card.back))
            }
        }

        existing.values
            .filter { it.sourceKey !in keep }
            .forEach { dao.delete(it) }

        return parsed.size
    }

    /** 复习一次并写回新的到期时间 */
    suspend fun review(card: FlashcardEntity, quality: Int): FlashcardEntity {
        val updated = Sm2Scheduler.review(card, quality)
        dao.update(updated)
        return updated
    }

    suspend fun clearForNote(noteId: Long) = dao.deleteByNote(noteId)
}
