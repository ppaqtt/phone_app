package com.example.notes.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import androidx.core.content.FileProvider
import com.example.notes.data.NoteEntity
import java.io.File
import java.io.FileOutputStream
import timber.log.Timber

object NoteShareUtil {

    /**
     * 分享笔记为纯文本
     * P86: 旧版没有 runCatching, 在没有 ACTION_SEND 处理的设备 (如部分车机)
     * 上 startActivity 会抛 ActivityNotFoundException 导致崩溃, 与
     * shareAsImage 风格不一致。补上同样的 runCatching + Toast 反馈。
     */
    fun shareAsText(context: Context, note: NoteEntity) {
        runCatching {
            val shareText = buildString {
                appendLine("【${note.title}】")
                appendLine()
                appendLine(note.content)
                appendLine()
                appendLine("—— 来自清笺笔记")
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, note.title)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, "分享笔记"))
        }.onFailure { e ->
            Timber.tag("NoteShareUtil").e(e, "shareAsText failed")
            android.widget.Toast.makeText(
                context, "分享失败: ${e.message}", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 分享笔记为图片 (长截图样式)
     */
    fun shareAsImage(context: Context, note: NoteEntity) {
        // P2: 提前用 runCatching 包裹整个 IO 流程, 单点捕获异常
        runCatching {
            val bitmap = createNoteBitmap(note)
            val file = File(context.cacheDir, "share_note_${note.id}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, note.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享笔记图片"))
        }.onFailure { e ->
            Timber.tag("NoteShareUtil").e(e, "shareAsImage failed")
            android.widget.Toast.makeText(
                context, "生成分享图片失败: ${e.message}", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 生成笔记图片 (防 OOM: 最大宽度 1080px, 最大高度 4096px, Bitmap 复用)
     */
    private fun createNoteBitmap(note: NoteEntity): Bitmap {
        val maxWidth = 1080
        val maxHeight = 4096
        val titlePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 56f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val contentPaint = Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 40f
            isAntiAlias = true
        }
        val timePaint = Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 32f
            isAntiAlias = true
        }

        // 计算行数和高度 (空内容兜底, 防 P2 报除零)
        val titleText = note.title.ifBlank { "无标题" }
        val contentText = note.content.ifBlank { "(无内容)" }
        val titleLines = wrapText(titleText, titlePaint, maxWidth - 80)
        val contentLines = wrapText(contentText, contentPaint, maxWidth - 80)
        val titleHeight = (titleLines.size * 70).coerceAtMost(200)
        val contentHeight = (contentLines.size * 55).coerceAtMost(maxHeight - 400)
        val safeHeight = (60 + titleHeight + 40 + contentHeight + 60 + 40 + 60)
            .coerceAtLeast(120)        // 最低 120px 防 0 高度
            .coerceAtMost(maxHeight)

        val bitmap = Bitmap.createBitmap(maxWidth, safeHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        // 绘制标题
        var y = 60f
        titleLines.forEach { line ->
            canvas.drawText(line, 40f, y, titlePaint)
            y += 70f
        }

        // 绘制分隔线
        y += 20f
        canvas.drawLine(40f, y, (maxWidth - 40).toFloat(), y, Paint().apply {
            color = android.graphics.Color.LTGRAY
            strokeWidth = 2f
        })
        y += 40f

        // 绘制内容
        contentLines.forEach { line ->
            canvas.drawText(line, 40f, y, contentPaint)
            y += 55f
        }

        // 绘制时间
        y += 40f
        val timeText = formatTimestamp(note.updatedAt)
        canvas.drawText("更新时间: $timeText", 40f, y, timePaint)

        // 绘制底部标识
        y += 50f
        canvas.drawText("—— 清笺笔记", (maxWidth - 250).toFloat(), y, timePaint)

        return bitmap
    }

    /**
     * 文本换行
     */
    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        val lines = mutableListOf<String>()
        text.lines().forEach { line ->
            if (paint.measureText(line) <= maxWidth) {
                lines.add(line)
            } else {
                var current = ""
                line.forEach { char ->
                    val test = current + char
                    if (paint.measureText(test) <= maxWidth) {
                        current = test
                    } else {
                        if (current.isNotEmpty()) lines.add(current)
                        current = char.toString()
                    }
                }
                if (current.isNotEmpty()) lines.add(current)
            }
        }
        return lines
    }
}
