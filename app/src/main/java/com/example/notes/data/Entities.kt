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
    // v8 → v9: 增 is_pinned / updated_at / deleted_at 单列索引
    // v9 → v10: 增 tags / reminder_time / priority / created_at 索引
    indices = [
        Index("category_id"),
        Index("is_pinned"),
        Index("updated_at"),
        Index("deleted_at"),
        Index("tags"),
        Index("reminder_time"),
        Index("priority"),
        Index("created_at")
    ]
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

    /**
     * F15: 提醒重复模式。"NONE" / "DAILY" / "WEEKLY" / "MONTHLY" / "YEARLY"。
     * 存字符串而非 enum ordinal 是为了: 1) 备份文件可读; 2) DB schema 演进加新模式不破坏老数据。
     */
    @ColumnInfo(name = "reminder_repeat", defaultValue = "'NONE'")
    val reminderRepeat: String = "NONE",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    /**
     * F2: 回收站标记。null = 正常笔记; 非 null = 已软删除的时间戳。
     * 30 天后由 [com.example.notes.util.TrashJanitor] 真删。
     * 用 nullable Long 而不是 Boolean 是为了: 1) 记录删除时间; 2) 走现有索引;
     * 3) 0L 当作 1970 不会被误当有效值, 因为回收站逻辑只看 IS NULL。
     */
    @ColumnInfo(name = "deleted_at", defaultValue = "NULL")
    val deletedAt: Long? = null,

    /**
     * 进阶功能: 笔记模板类型。0=无模板, 1=日记, 2=会议, 3=读书, 4=周报。
     * 用整数而非 enum 字符串: 1) 索引小; 2) 排序快; 3) 数据库可读。
     */
    @ColumnInfo(name = "template_type", defaultValue = "0")
    val templateType: Int = 0
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true), Index("parent_id")]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color")
    val color: Int = 0xFF6750A4.toInt(),

    /**
     * F12: 父分类 id, null = 顶级分类。
     * 用 nullable Long 而非 String 是为了: 1) 数据库索引快; 2) 与 id 类型一致; 3) 可空即代表"无父级"。
     * 删除父分类时由 Repository 级联把子分类的 parentId 置空 (不级联删子分类, 避免意外丢笔记)。
     */
    @ColumnInfo(name = "parent_id", defaultValue = "NULL")
    val parentId: Long? = null,

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
    indices = [Index("noteId"), Index("position")]
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
