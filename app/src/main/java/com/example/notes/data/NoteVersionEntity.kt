package com.example.notes.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 进阶功能: 笔记历史版本。
 *
 * 每次笔记内容 (title/content) 变化时, Repository 在 save 前把旧版本
 * 写入此表。保留最近 N 个版本 (默认 20), 超出后删除最早版本。
 *
 * 设计:
 * - 同一 noteId 的多个版本按 saved_at 倒序展示。
 * - 只存快照, 不做 diff (简单可靠; 文本大小通常 < 100KB)。
 * - 删除笔记时 CASCADE 级联删除历史 (外键约束)。
 */
@Entity(
    tableName = "note_versions",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId"), Index("saved_at")]
)
data class NoteVersionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "noteId")
    val noteId: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "saved_at")
    val savedAt: Long = System.currentTimeMillis()
)

/** 进阶功能: 单篇笔记加密 (与 NoteEntity 一起存, 标志位表示是否加密)。
 *  仅加密 content 字段, title 保持明文以便搜索。 */
@Entity(
    tableName = "note_encryption",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class NoteEncryptionEntity(
    @PrimaryKey
    @ColumnInfo(name = "noteId")
    val noteId: Long,

    @ColumnInfo(name = "encrypted_content")
    val encryptedContent: String,

    @ColumnInfo(name = "salt")
    val salt: String,

    @ColumnInfo(name = "encrypted_at")
    val encryptedAt: Long = System.currentTimeMillis()
)
