package com.example.notes.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.lang.ThreadLocal

private val formatter = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
}

fun formatTimestamp(ts: Long): String = formatter.get().format(Date(ts))

fun formatTimestampShort(ts: Long): String {
    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
    return sdf.format(Date(ts))
}

/**
 * 相对时间显示: "刚刚" / "X 分钟前" / "X 小时前" / "昨天 HH:mm" / "MM-dd"
 * 超过 1 年的用 "yyyy/MM/dd"。
 */
fun formatRelativeTime(ts: Long, now: Long = System.currentTimeMillis()): String {
    val delta = now - ts
    return when {
        delta < 0 -> formatTimestamp(ts)              // 未来时间(用户调时钟了), 用绝对时间
        delta < 60_000L -> "刚刚"
        delta < 60 * 60_000L -> "${delta / 60_000L} 分钟前"
        delta < 24 * 60 * 60_000L -> "${delta / (60 * 60_000L)} 小时前"
        delta < 2 * 24 * 60 * 60_000L -> "昨天 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))}"
        delta < 365L * 24 * 60 * 60_000L -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(ts))
        else -> formatTimestamp(ts)
    }
}