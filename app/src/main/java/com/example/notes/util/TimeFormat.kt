package com.example.notes.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

fun formatTimestamp(ts: Long): String = formatter.format(Date(ts))

fun formatTimestampShort(ts: Long): String {
    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
    return sdf.format(Date(ts))
}
