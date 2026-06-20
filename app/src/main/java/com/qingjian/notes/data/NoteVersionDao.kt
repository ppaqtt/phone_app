package com.qingjian.notes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteVersionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(version: NoteVersionEntity): Long

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY saved_at DESC LIMIT :limit")
    fun observeByNote(noteId: Long, limit: Int = 50): Flow<List<NoteVersionEntity>>

    @Query("SELECT * FROM note_versions WHERE id = :id")
    suspend fun getById(id: Long): NoteVersionEntity?

    /** 超出保留数量的旧版本 (按时间正序) 一次性删除 */
    @Query("""
        SELECT * FROM note_versions
        WHERE noteId = :noteId
        ORDER BY saved_at DESC
        LIMIT -1 OFFSET :keepCount
    """)
    suspend fun getOverflow(noteId: Long, keepCount: Int): List<NoteVersionEntity>

    @Query("DELETE FROM note_versions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 统计某笔记历史版本数 */
    @Query("SELECT COUNT(*) FROM note_versions WHERE noteId = :noteId")
    suspend fun countByNote(noteId: Long): Int

    @Query("DELETE FROM note_versions WHERE noteId = :noteId")
    suspend fun clearForNote(noteId: Long)
}

@Dao
interface NoteEncryptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: NoteEncryptionEntity)

    @Query("SELECT * FROM note_encryption WHERE noteId = :noteId")
    suspend fun get(noteId: Long): NoteEncryptionEntity?

    @Query("DELETE FROM note_encryption WHERE noteId = :noteId")
    suspend fun delete(noteId: Long)

    /** 返回所有加密笔记的 id 集合, 用于仓库层解密判定 */
    @Query("SELECT noteId FROM note_encryption")
    fun observeEncryptedIds(): Flow<List<Long>>
}
