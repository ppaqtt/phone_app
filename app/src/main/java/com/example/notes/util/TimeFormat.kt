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