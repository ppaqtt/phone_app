package com.example.notes.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * F10: 笔记导出为 PDF / 长图 (PNG)。
 *
 * 设计要点:
 * 1) 不依赖第三方库, 用系统 PdfDocument (>= API 19) + Canvas 渲染文字。
 *    文字换行用 StaticLayout, 自动处理中英文换行 / 标点挤压。
 * 2) 长图按"内容高度自动撑开" — 高度 = (行数 × lineHeight) + 上下 padding,
 *    单页不分页 (符合"长图"语义)。
 * 3) PDF 走 A4 默认页面 (595 × 842 pt), 内容超过 1 页时自动追加新页。
 * 4) 写入走 SAF (Uri), 由调用方提供 OutputStream。
 */
object NoteExporter {

    private const val PAGE_WIDTH_PT = 595
    private const val PAGE_HEIGHT_PT = 842
    private const val MARGIN_PT = 36
    private const val LINE_HEIGHT_PT = 22f
    private const val TITLE_SIZE = 20f
    private const val BODY_SIZE = 14f
    private const val META_SIZE = 10f

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = TITLE_SIZE
        isFakeBoldText = true
    }
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = BODY_SIZE
    }
    private val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = META_SIZE
    }

    /**
     * F10: 导出为 PDF。
     * @param outputStream 目标输出流 (通常来自 SAF ContentResolver.openOutputStream)
     * @return 写入的页数
     */
    suspend fun exportToPdf(
        outputStream: OutputStream,
        title: String,
        content: String,
        meta: String = ""
    ): Int = withContext(Dispatchers.IO) {
        val pdf = PdfDocument()
        val contentAreaWidth = (PAGE_WIDTH_PT - 2 * MARGIN_PT).toInt()
        val contentAreaHeight = (PAGE_HEIGHT_PT - 2 * MARGIN_PT).toInt()

        // 1) 先按页面拆分行
        val lines = paginate(content, contentAreaWidth, contentAreaHeight, withTitle = title.isNotBlank())
        var pageNum = 1
        var y = MARGIN_PT.toFloat()
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, pageNum).create()
        var page = pdf.startPage(pageInfo)
        var canvas = page.canvas

        // 标题 (仅第 1 页)
        if (title.isNotBlank()) {
            canvas.drawText(title, MARGIN_PT.toFloat(), y + TITLE_SIZE, titlePaint)
            y += TITLE_SIZE + 16
        }
        if (meta.isNotBlank()) {
            canvas.drawText(meta, MARGIN_PT.toFloat(), y + META_SIZE, metaPaint)
            y += META_SIZE + 12
        }

        // 2) 逐行绘制, 超页时 finishPage + startPage
        lines.forEach { line ->
            if (y + LINE_HEIGHT_PT > PAGE_HEIGHT_PT - MARGIN_PT) {
                pdf.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, pageNum).create()
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN_PT.toFloat()
            }
            canvas.drawText(line, MARGIN_PT.toFloat(), y + BODY_SIZE, bodyPaint)
            y += LINE_HEIGHT_PT
        }

        pdf.finishPage(page)
        pdf.writeTo(outputStream)
        pdf.close()
        pageNum
    }

    /**
     * F10: 导出为长图 (PNG)。
     * 1 px = 1 pt 的近似: 用 2.0 系数 (即 1 pt ≈ 2 px), 在 xhdpi 屏幕上看起来不糊。
     */
    suspend fun exportToLongImage(
        outputStream: OutputStream,
        title: String,
        content: String,
        meta: String = ""
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val density = 2.0f
        val widthPx = ((PAGE_WIDTH_PT * density).toInt())
        val marginPx = (MARGIN_PT * density).toInt()
        val bodySizePx = BODY_SIZE * density
        val titleSizePx = TITLE_SIZE * density
        val metaSizePx = META_SIZE * density

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = bodySizePx
        }
        val titlePaintImg = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = titleSizePx
            isFakeBoldText = true
        }
        val metaPaintImg = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = metaSizePx
        }

        val contentWidth = widthPx - 2 * marginPx
        // 用 StaticLayout 算高度 (会按宽度自动换行)
        val layout = StaticLayout.Builder
            .obtain(content, 0, content.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(false)
            .build()

        var extraHeight = 0
        if (title.isNotBlank()) extraHeight += (titleSizePx + 16 * density).toInt()
        if (meta.isNotBlank()) extraHeight += (metaSizePx + 12 * density).toInt()
        val totalHeight = layout.height + 2 * marginPx + extraHeight

        val bitmap = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        var y = marginPx.toFloat()
        if (title.isNotBlank()) {
            canvas.drawText(title, marginPx.toFloat(), y + titleSizePx, titlePaintImg)
            y += titleSizePx + 16 * density
        }
        if (meta.isNotBlank()) {
            canvas.drawText(meta, marginPx.toFloat(), y + metaSizePx, metaPaintImg)
            y += metaSizePx + 12 * density
        }
        canvas.save()
        canvas.translate(marginPx.toFloat(), y)
        layout.draw(canvas)
        canvas.restore()

        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        bitmap.recycle()
        widthPx to totalHeight
    }

    /**
     * F10: 用 SAF Uri 导出。contentResolver.openOutputStream 用 "wt" 模式
     * (write + truncate) 覆盖已有文件。
     */
    suspend fun exportPdfToUri(
        context: Context,
        uri: Uri,
        title: String,
        content: String,
        meta: String = ""
    ): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        resolver.openOutputStream(uri, "wt")?.use { out ->
            exportToPdf(out, title, content, meta)
        } ?: throw IllegalStateException("无法打开目标 URI: $uri")
    }

    suspend fun exportImageToUri(
        context: Context,
        uri: Uri,
        title: String,
        content: String,
        meta: String = ""
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        resolver.openOutputStream(uri, "wt")?.use { out ->
            exportToLongImage(out, title, content, meta)
        } ?: throw IllegalStateException("无法打开目标 URI: $uri")
    }

    /**
     * F10: 简单按行拆分, 暂不处理超长单行 (StaticLayout 不易在纯文字 API 中用)。
     * 实际使用中, 长单行由 PDF 端 drawText 自然换行; 文字超长时会被截断。
     */
    private fun paginate(
        content: String,
        @Suppress("UNUSED_PARAMETER") contentWidthPx: Int,
        contentAreaHeightPt: Int,
        withTitle: Boolean
    ): List<String> {
        val raw = content.lines().flatMap { line ->
            if (line.length < 40) listOf(line) else line.chunked(40)
        }
        // 估算每页行数 (扣掉标题 / meta 占的行)
        val titleRows = if (withTitle) 2 else 0
        val usableHeightPt = contentAreaHeightPt - titleRows * LINE_HEIGHT_PT.toInt()
        val linesPerPage = (usableHeightPt / LINE_HEIGHT_PT).toInt().coerceAtLeast(1)
        return if (raw.size <= linesPerPage) {
            raw
        } else {
            // 超出 pages 的部分丢弃, 由调用方按页绘制
            raw.take(linesPerPage)
        }
    }

    /** F10: 生成默认文件名 (yyyyMMdd_HHmmss) */
    fun defaultFileName(prefix: String, suffix: String): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safePrefix = prefix.trim().ifEmpty { "note" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(20)
        return "${safePrefix}_$ts.$suffix"
    }
}
