package com.example.notes.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.lang.ThreadLocal

/**
 * P64: 所有时间格式化方法统一走 ThreadLocal SimpleDateFormat,
 * 避免每次调用 new SimpleDateFormat() 造成的 GC 压力和潜在线程安全问题。
 */
private val fmtFull = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
}
private val fmtShort = ThreadLocal.withInitial {
    SimpleDateFormat("MM/dd", Locale.getDefault())
}
private val fmtTime = ThreadLocal.withInitial {
    SimpleDateFormat("HH:mm", Locale.getDefault())
}
private val fmtDate = ThreadLocal.withInitial {
    SimpleDateFormat("MM-dd", Locale.getDefault())
}

fun formatTimestamp(ts: Long): String = fmtFull.get().format(Date(ts))

fun formatTimestampShort(ts: Long): String = fmtShort.get().format(Date(ts))

/**
 * 相对时间显示: "刚刚" / "X 分钟前" / "X 小时前" / "昨天 HH:mm" / "MM-dd"
 * 超过 1 年的用 "yyyy/MM/dd"。
 */
fun formatRelativeTime(ts: Long, now: Long = System.currentTimeMillis()): String {
    val delta = now - ts
    return when {
        delta < 0 -> formatTimestamp(ts)   // 未来时间(用户调时钟了), 用绝对时间
        delta < 60_000L -> "刚刚"
        delta < 60 * 60_000L -> "${delta / 60_000L} 分钟前"
        delta < 24 * 60 * 60_000L -> "${delta / (60 * 60_000L)} 小时前"
        delta < 2 * 24 * 60 * 60_000L -> "昨天 ${fmtTime.get().format(Date(ts))}"
        delta < 365L * 24 * 60 * 60_000L -> fmtDate.get().format(Date(ts))
        else -> formatTimestamp(ts)
    }
}