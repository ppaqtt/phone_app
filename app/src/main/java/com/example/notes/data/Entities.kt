package com.example.notes.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * P69: 笔记"未选色"的哨兵值。所有新笔记 color 默认等于此值, 表示沿用主题色。
 * 0xFFFFFFFF 不会出现在用户调色板 (调色板从 NoteSwatches 取), 不会撞色。
 */
const val DEFAULT_COLOR: Int = 0xFFFFFFFF.toInt()

@Entity(
    tableName = "notes",
    // P98: 加外键约束, category_id → categories.id, 删除分类时级联 SET NULL
    // (旧版依赖 Repository.deleteCategorySafely 手动清, 若有别处绕过
    // 该方法直接 delete(category) 会留孤儿 category_id; 加 FK 后 SQLite
    // 在 PRAGMA foreign_keys=ON 时自动 SET NULL, 多一层保护。)
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["category_id"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("category_id")]
)
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

    /** 重要度: 0=普通, 1=重要, 2=紧急 */
    @ColumnInfo(name = "priority", defaultValue = "0")
    val priority: Int = 0,

    @ColumnInfo(name = "color")
    // P69: 0xFFFFFFFF 当作"未选色"哨兵, 0xFF000000 当作"用户主动选白色"。
    // 新建/读取时 NoteCard 用 DEFAULT_COLOR 常量判断, 避免撞色。
    val color: Int = DEFAULT_COLOR,

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
