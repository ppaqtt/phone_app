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
        Index("is_favorite"),
        Index("updated_at"),
        Index("deleted_at"),
        Index("tags"),
        Index("reminder_time"),
        Index("priority"),
        Index("created_at"),
        Index("is_archived")
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
    val templateType: Int = 0,

    /**
     * 高价值/低工作量: 笔记星标 (收藏)。
     * 独立于置顶 (isPinned), 用于快速筛选重要笔记。
     * 用 Boolean 而非 Int 让 Room 自动处理, 搜索/计数更直观。
     */
    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false,

    /** 功能1: 笔记锁定 (写保护)。true 时编辑页只读。 */
    @ColumnInfo(name = "is_locked", defaultValue = "0")
    val isLocked: Boolean = false,

    /** 功能20: 草稿模式。true 时在列表显示灰色草稿标记。 */
    @ColumnInfo(name = "is_draft", defaultValue = "0")
    val isDraft: Boolean = false,

    /** 功能3: 笔记颜色标签 (用于彩色标签/标记)。0=无, 其它为颜色值。 */
    @ColumnInfo(name = "color_tag", defaultValue = "0")
    val colorTag: Int = 0,

    /** 功能: 笔记阅读时间估算 (秒), 0 表示未计算。 */
    @ColumnInfo(name = "read_time_seconds", defaultValue = "0")
    val readTimeSeconds: Int = 0,

    /** 功能: 笔记归档。true 时主列表默认隐藏, 仅在归档筛选下显示。 */
    @ColumnInfo(name = "is_archived", defaultValue = "0")
    val isArchived: Boolean = false
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true), Index("parent_id"), Index("is_pinned")]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color")
    val color: Int = 0xFF6750A4.toInt(),

    /**
     * 中价值/中工作量: 分类置顶。
     * 置顶分类在列表顶部显示，不受排序顺序影响。
     */
    @ColumnInfo(name = "is_pinned", defaultValue = "0")
    val isPinned: Boolean = false,

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

/**
 * 中价值/中工作量: 标签分组 — 把标签归类为工作/生活/学习等层级。
 * 分组后用户在笔记编辑器选择标签时可按组选择，更清晰。
 */
@Entity(tableName = "tag_groups", indices = [Index(value = ["name"], unique = true)])
data class TagGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color")
    val color: Int = 0xFF6750A4.toInt(),

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 中价值/中工作量: 标签-分组关联表。
 * tag_name 是标签文本 (与 NoteEntity.tags 的逗号分隔对应);
 * group_id 是所属分组的 id。同一标签可属于多个分组 (多对多)。
 */
@Entity(
    tableName = "tag_group_tags",
    primaryKeys = ["tag_name", "group_id"],
    foreignKeys = [ForeignKey(
        entity = TagGroupEntity::class,
        parentColumns = ["id"],
        childColumns = ["group_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("group_id")]
)
data class TagGroupTagEntity(
    @ColumnInfo(name = "tag_name")
    val tagName: String,

    @ColumnInfo(name = "group_id")
    val groupId: Long
)

/** 功能9: 笔记反向链接表。记录笔记 A 引用了笔记 B, 方便从 B 查到 A。 */
@Entity(
    tableName = "note_backlinks",
    primaryKeys = ["source_note_id", "target_note_id"],
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["source_note_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["target_note_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("target_note_id")]
)
data class NoteBacklinkEntity(
    @ColumnInfo(name = "source_note_id")
    val sourceNoteId: Long,

    @ColumnInfo(name = "target_note_id")
    val targetNoteId: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/** 功能11/24: 笔记附件表 (图片/语音/通用文件)。
 *  类型: "image" | "voice" | "file"
 *  备注: note_images 表保留用于图片 (老数据), 新附件走本
 */
@Entity(
    tableName = "note_attachments",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["note_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("note_id"), Index("type")]
)
data class NoteAttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "note_id")
    val noteId: Long,

    /** 附件类型: "image" | "voice" | "file" */
    @ColumnInfo(name = "type")
    val type: String,

    /** 本地文件 URI 或路径 */
    @ColumnInfo(name = "uri")
    val uri: String,

    /** 文件名, 用于展示 */
    @ColumnInfo(name = "name", defaultValue = "")
    val name: String = "",

    /** 对于音频: 时长毫秒 */
    @ColumnInfo(name = "duration_ms", defaultValue = "0")
    val durationMs: Long = 0L,

    /** 大小, 单位 byte */
    @ColumnInfo(name = "size_bytes", defaultValue = "0")
    val sizeBytes: Long = 0L,

    @ColumnInfo(name = "position")
    val position: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/** 功能19: 笔记评论/标注表。对笔记添加评论。 */
@Entity(
    tableName = "note_comments",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["note_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("note_id"), Index("created_at")]
)
data class NoteCommentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "note_id")
    val noteId: Long,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
)

/** 功能18: 搜索历史表。记录用户搜索过的关键词。 */
@Entity(
    tableName = "search_history",
    indices = [Index(value = ["query"], unique = true), Index("last_searched_at")]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "query")
    val query: String,

    @ColumnInfo(name = "last_searched_at")
    val lastSearchedAt: Long = System.currentTimeMillis(),

    /** 搜索次数, 用于排序/自动补全优先级 */
    @ColumnInfo(name = "search_count", defaultValue = "1")
    val searchCount: Int = 1
)

/** 功能: 同步/WebDAV 配置表 (单表存储 key-value 配置)。 */
@Entity(tableName = "sync_config", indices = [Index(value = ["config_key"], unique = true)])
data class SyncConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "config_key")
    val key: String,

    @ColumnInfo(name = "config_value")
    val value: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

/** 功能: 笔记变更记录 (用于同步/增量导出)。 */
@Entity(
    tableName = "note_change_log",
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("note_id"), Index("changed_at")]
)
data class NoteChangeLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "note_id")
    val noteId: Long,

    /** "create" | "update" | "delete" | "restore" */
    @ColumnInfo(name = "change_type")
    val changeType: String,

    @ColumnInfo(name = "changed_at")
    val changedAt: Long = System.currentTimeMillis()
)

/** 功能: 模板市场。用户可创建/保存自己的笔记模板, 也可使用系统模板。 */
@Entity(tableName = "user_note_templates", indices = [Index("created_at")])
data class UserNoteTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "content")
    val content: String,

    /** 逗号分隔的标签, 应用模板时加到笔记 */
    @ColumnInfo(name = "tags", defaultValue = "''")
    val tags: String = "",

    /** 关联分类, null 表示不设 */
    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/** 功能: 笔记反向链接扫描状态, 避免重复扫描全文。 */
@Entity(tableName = "backlink_scan_state")
data class BacklinkScanStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "note_id")
    val noteId: Long,

    /** 上次扫描的时间戳, 0 表示从未扫描 */
    @ColumnInfo(name = "last_scanned_at", defaultValue = "0")
    val lastScannedAt: Long = 0L,

    /** 上次扫描时 content 的 hash, 用于快速判断是否需要重新扫描 */
    @ColumnInfo(name = "last_content_hash", defaultValue = "0")
    val lastContentHash: Long = 0L
)
