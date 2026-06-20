package com.qingjian.notes.util

import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * F15: 提醒重复模式。
 *
 * 设计:
 * 1) 字符串值与数据库 [com.qingjian.notes.data.NoteEntity.reminderRepeat] 一一对应
 *    (NONE / DAILY / WEEKLY / MONTHLY / YEARLY), 备份文件可读。
 * 2) [nextTriggerTime] 以"基准时间 + 步长"为下次触发点, 用 Calendar.add 处理
 *    跨月跨年, 避免 Calendar.setTimeInMillis + add 重复初始化。
 * 3) 月末边界: 若本月 31 日, 下月没有 31, Calendar 会自动滚到下月最后一天 (例如 1/31 → 2/28)。
 *    这是 Calendar 内置行为, 不需要额外代码。
 */
enum class ReminderRepeat(val displayName: String) {
    NONE("不重复"),
    DAILY("每天"),
    WEEKLY("每周"),
    MONTHLY("每月"),
    YEARLY("每年");

    fun nextTriggerTime(baseTime: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = baseTime }
        when (this) {
            NONE -> return baseTime // 不变, 实际不会调用
            DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            MONTHLY -> cal.add(Calendar.MONTH, 1)
            YEARLY -> cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }

    companion object {
        /**
         * 从字符串解析, 旧版本 / 损坏值兜底为 NONE。
         */
        fun fromString(value: String?): ReminderRepeat = when (value) {
            "DAILY" -> DAILY
            "WEEKLY" -> WEEKLY
            "MONTHLY" -> MONTHLY
            "YEARLY" -> YEARLY
            else -> NONE
        }

        /** 便捷: 把 TimeUnit.MILLISECONDS.toDays 格式化成 "X 天" 文本 */
        fun describeInterval(repeat: ReminderRepeat): String = when (repeat) {
            NONE -> "不重复"
            DAILY -> "每 1 天"
            WEEKLY -> "每 ${TimeUnit.DAYS.toDays(7)} 天"
            MONTHLY -> "约每 30 天"
            YEARLY -> "约每 365 天"
        }
    }
}
