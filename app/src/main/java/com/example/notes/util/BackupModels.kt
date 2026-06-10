package com.example.notes.util

/**
 * F1: 数据备份/恢复用的 DTO。
 *
 * 设计与数据库 Entity 解耦 ——
 * 1) 字段命名用 camelCase JSON, 读起来直观, 不直接绑数据库 column 名,
 *    将来表结构变化时 DTO 还能向前/向后兼容老备份文件。
 * 2) id 字段在导入时按"老 id 优先, 冲突则新建"策略处理,
 *    避免用 OnConflictStrategy.REPLACE 引发"误删关联图片"问题。
 */
data class BackupPayload(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String,
    val categories: List<CategoryBackup> = emptyList(),
    val notes: List<NoteBackup> = emptyList(),
    val images: List<ImageBackup> = emptyList()
) {
    companion object {
        /** DTO 当前版本号, 每次结构变更 +1, 导入时做兼容判断 */
        const val CURRENT_VERSION = 1
    }
}

data class CategoryBackup(
    val oldId: Long,
    val name: String,
    val color: Int,
    /**
     * F12: 老备份中可能没有 parentOldId 字段 (字段为 null 时导入为顶级分类)。
     * 默认 null 保证与 v1 老备份文件兼容。
     */
    val parentOldId: Long? = null,
    val createdAt: Long
)

data class NoteBackup(
    val oldId: Long,
    val title: String,
    val content: String,
    /** 可能为 null —— 旧笔记可能没有分类 */
    val categoryOldId: Long?,
    val tags: String,
    val isPinned: Boolean,
    val priority: Int,
    val color: Int,
    val reminderTime: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

data class ImageBackup(
    val oldId: Long,
    val noteOldId: Long,
    val uri: String,
    val position: Int
)
