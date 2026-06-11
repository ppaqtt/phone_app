package com.example.notes.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.Date

object ImageUtils {

    /**
     * P91: SimpleDateFormat 非线程安全, 改为 ThreadLocal, 避免
     * "Expected slash to follow yyyy" / "Unparseable date" 这类并发格式化串扰。
     * (与 P64 TimeFormat 的 ThreadLocal 保持一致)
     */
    private val fmt = ThreadLocal.withInitial {
        java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
    }

    fun createImageFile(context: Context): File {
        val timeStamp = fmt.get()!!.format(Date())
        val storageDir = context.getExternalFilesDir("images")
        return File.createTempFile(
            "NOTE_${timeStamp}",
            ".jpg",
            storageDir
        )
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * 删除外部存储的临时图片文件 (只处理 file:// 协议, content:// 走 ContentResolver)。
     * 对拍照产生的本地文件有效, 对相册选择的 content URI 无效 (会安全忽略)。
     */
    fun deleteImageFile(uri: String?) {
        if (uri.isNullOrBlank()) return
        try {
            val parsed = Uri.parse(uri)
            when (parsed.scheme) {
                "file" -> {
                    parsed.path?.let { path ->
                        val file = File(path)
                        if (file.exists()) file.delete()
                    }
                }
                "content" -> {
                    // 系统拍照临时文件用 file:// 协议, 不在 content:// 上做删除尝试
                }
                else -> {
                    // 未知协议忽略
                }
            }
        } catch (_: Exception) {
            // 安全忽略删除失败
        }
    }
}
