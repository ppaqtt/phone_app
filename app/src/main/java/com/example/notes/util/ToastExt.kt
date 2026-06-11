package com.example.notes.util

import android.content.Context
import android.widget.Toast

/**
 * Toast 工具扩展, 减少重复的 `Toast.makeText(...).show()` 模板代码。
 *
 * 用法:
 *   context.toastShort("保存成功")
 *   context.toastLong("网络异常, 请稍后重试")
 *
 * 自动捕获 IllegalStateException (某些设备/Window 销毁后调用会抛),
 * 静默失败, 避免崩溃。
 */
fun Context.toastShort(message: CharSequence) {
    runCatching {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

fun Context.toastLong(message: CharSequence) {
    runCatching {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
