package com.example.notes.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 待办任务实体
 * @param id 主键
 * @param title 标题
 * @param content 内容/描述
 * @param isCompleted 是否已完成
 * @param priority 优先级: 0=普通, 1=重要, 2=紧急
 * @param reminderTime 提醒时间（毫秒），null 表示不提醒
 * @param reminderRepeat 提醒重复模式: NONE / DAILY / WEEKLY / MONTHLY
 * @param ringtoneUri 自定义铃声 URI，null 表示使用默认铃声
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param completedAt 完成时间，null 表示未完成
 */
@Entity(
    tableName = "todos",
    indices = [
        Index("is_completed"),
        Index("reminder_time"),
        Index("priority"),
        Index("created_at")
    ]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content")
    val content: String = "",

    @ColumnInfo(name = "is_completed", defaultValue = "0")
    val isCompleted: Boolean = false,

    /** 优先级: 0=普通, 1=重要, 2=紧急 */
    @ColumnInfo(name = "priority", defaultValue = "0")
    val priority: Int = 0,

    /** 提醒时间（毫秒），null 表示不提醒 */
    @ColumnInfo(name = "reminder_time")
    val reminderTime: Long? = null,

    /** 功能3: 提醒重复模式, 默认不重复 */
    @ColumnInfo(name = "reminder_repeat", defaultValue = "NONE")
    val reminderRepeat: String = "NONE",

    /** 自定义铃声 URI，null 表示使用默认铃声 */
    @ColumnInfo(name = "ringtone_uri")
    val ringtoneUri: String? = null,

    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at", defaultValue = "CURRENT_TIMESTAMP")
    val updatedAt: Long = System.currentTimeMillis(),

    /** 完成时间，null 表示未完成 */
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null
)
