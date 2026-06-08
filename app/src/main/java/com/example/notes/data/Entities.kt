package com.example.notes.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    /** Comma-separated tags. Kept simple to avoid an extra join table. */
    @ColumnInfo(name = "tags")
    val tags: String = "",

    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,

    @ColumnInfo(name = "color")
    val color: Int = 0xFFFFFFFF.toInt(),

    @ColumnInfo(name = "reminder_time")
    val reminderTime: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color")
    val color: Int = 0xFF6750A4.toInt(),

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/** 笔记关联的多张图片, 一对多 */
@Entity(
    tableName = "note_images",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId")]
)
data class NoteImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "noteId")
    val noteId: Long,

    @ColumnInfo(name = "uri")
    val uri: String,

    @ColumnInfo(name = "position")
    val position: Int = 0
)

/** Aggregate used by the list screen to avoid an extra mapping step. */
data class NoteWithCategory(
    @Embedded val note: NoteEntity,
    @Relation(
        parentColumn = "category_id",
        entityColumn = "id"
    )
    val category: CategoryEntity?
)

/** 笔记 + 分类 + 所有图片 */
data class NoteWithCategoryAndImages(
    @Embedded val note: NoteEntity,
    @Relation(
        parentColumn = "category_id",
        entityColumn = "id"
    )
    val category: CategoryEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "noteId"
    )
    val images: List<NoteImageEntity>
)
