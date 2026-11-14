package com.example.notes.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 笔记闪卡。
 *
 * 卡片由笔记内容生成 (`#card 正面 :: 背面` 或 `==高亮==`), 用 SM-2 算法
 * 安排下次复习时间。`sourceKey` 用于幂等生成: 同一篇笔记反复保存时,
 * 相同正反面的卡片不会被重复插入, 也不会重置已有复习进度。
 */
@Entity(
    tableName = "flash_cards",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["note_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("note_id"), Index("due_at")]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "note_id")
    val noteId: Long,

    @ColumnInfo(name = "front")
    val front: String,

    @ColumnInfo(name = "back")
    val back: String,

    /** 下次到期时间戳 (毫秒) */
    @ColumnInfo(name = "due_at")
    val dueAt: Long = System.currentTimeMillis(),

    /** 当前间隔 (天), 0 表示新卡 */
    @ColumnInfo(name = "interval_days")
    val intervalDays: Double = 0.0,

    /** SM-2 难度因子, 初始 2.5, 下限 1.3 */
    @ColumnInfo(name = "ease_factor")
    val easeFactor: Double = 2.5,

    /** 连续答对次数 */
    @ColumnInfo(name = "repetitions")
    val repetitions: Int = 0,

    /** 遗忘次数 */
    @ColumnInfo(name = "lapses")
    val lapses: Int = 0,

    @ColumnInfo(name = "last_reviewed_at")
    val lastReviewedAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** 去重键, 由正反面内容哈希得到 */
    @ColumnInfo(name = "source_key")
    val sourceKey: String = ""
)

@Dao
interface FlashcardDao {

    @Query("""
        SELECT * FROM flash_cards
        WHERE note_id IN (SELECT id FROM notes WHERE deleted_at IS NULL)
        ORDER BY due_at ASC
    """)
    fun observeAll(): Flow<List<FlashcardEntity>>

    @Query("""
        SELECT COUNT(*) FROM flash_cards
        WHERE note_id IN (SELECT id FROM notes WHERE deleted_at IS NULL)
    """)
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT * FROM flash_cards WHERE note_id = :noteId")
    suspend fun getByNote(noteId: Long): List<FlashcardEntity>

    @Query("""
        SELECT * FROM flash_cards
        WHERE due_at <= :now
          AND note_id IN (SELECT id FROM notes WHERE deleted_at IS NULL)
    """)
    suspend fun getDue(now: Long): List<FlashcardEntity>

    @Query("""
        SELECT COUNT(*) FROM flash_cards
        WHERE due_at <= :now
          AND note_id IN (SELECT id FROM notes WHERE deleted_at IS NULL)
    """)
    suspend fun countDue(now: Long): Int

    @Query("SELECT * FROM flash_cards")
    suspend fun getAllOnce(): List<FlashcardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: FlashcardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<FlashcardEntity>)

    @Update
    suspend fun update(card: FlashcardEntity)

    @Delete
    suspend fun delete(card: FlashcardEntity)

    @Query("DELETE FROM flash_cards WHERE note_id = :noteId")
    suspend fun deleteByNote(noteId: Long)
}
