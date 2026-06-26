package com.qingjian.notes.util

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
    val exportedAt: Long? = null,
    val appVersion: String? = null,
    val categories: List<CategoryBackup>? = null,
    val notes: List<NoteBackup>? = null,
    val images: List<ImageBackup>? = null
) {
    companion object {
        /** DTO 当前版本号, 每次结构变更 +1, 导入时做兼容判断 */
        const val CURRENT_VERSION = 1
    }
}

data class CategoryBackup(
    val oldId: Long = 0L,
    val name: String = "",
    val color: Int = 0,
    /**
     * F12: 老备份中可能没有 parentOldId 字段 (字段为 null 时导入为顶级分类)。
     * 默认 null 保证与 v1 老备份文件兼容。
     */
    val parentOldId: Long? = null,
    val createdAt: Long = 0L
)

data class NoteBackup(
    val oldId: Long = 0L,
    val title: String = "",
    val content: String = "",
    /** 可能为 null —— 旧笔记可能没有分类 */
    val categoryOldId: Long? = null,
    val tags: String = "",
    val isPinned: Boolean = false,
    val priority: Int = 0,
    val color: Int = -1,
    val reminderTime: Long? = null,
    /**
     * F15: 提醒重复模式。默认 "NONE" 保证与 v1 老备份文件兼容。
     */
    val reminderRepeat: String = "NONE",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class ImageBackup(
    val oldId: Long = 0L,
    val noteOldId: Long = 0L,
    val uri: String = "",
    val position: Int = 0
)
