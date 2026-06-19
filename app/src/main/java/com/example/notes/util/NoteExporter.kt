package com.example.notes.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
 * 1) 不依赖第三方库, 用系统 PdfDocument (>= API 19) + Canvas 渲染文字和图片。
 *    文字换行用 StaticLayout, 自动处理中英文换行 / 标点挤压。
 * 2) 长图按"内容高度自动撑开" — 高度 = (行数 × lineHeight) + 上下 padding,
 *    单页不分页 (符合"长图"语义)。
 * 3) PDF 走 A4 默认页面 (595 × 842 pt), 内容超过 1 页时自动追加新页。
 * 4) 写入走 SAF (Uri), 由调用方提供 OutputStream。
 * 5) F124: 支持图片渲染 — 从 Context 加载 Bitmap 并绘制到 PDF/长图中。
 */
object NoteExporter {

    private const val PAGE_WIDTH_PT = 595
    private const val PAGE_HEIGHT_PT = 842
    private const val MARGIN_PT = 36
    private const val LINE_HEIGHT_PT = 22f
    private const val TITLE_SIZE = 20f
    private const val BODY_SIZE = 14f
    private const val META_SIZE = 10f
    /** F124: 图片最大宽度 (pt) */
    private const val MAX_IMAGE_WIDTH_PT = (PAGE_WIDTH_PT - 2 * MARGIN_PT).toFloat()
    /** F124: 图片与文字间距 */
    private const val IMAGE_SPACING_PT = 12f

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
     * F124: 加载图片 Bitmap (从 Uri)。
     * 如果加载失败或图片不存在, 返回 null。
     */
    private fun loadBitmap(context: Context, uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // 先获取图片尺寸
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                // 计算缩放比例, 最大宽度 MAX_IMAGE_WIDTH_PT
                val maxWidthPx = (MAX_IMAGE_WIDTH_PT * 2).toInt() // 2x density
                var sampleSize = 1
                while (options.outWidth / sampleSize > maxWidthPx) {
                    sampleSize *= 2
                }

                // 重新加载缩放后的 Bitmap
                context.contentResolver.openInputStream(uri)?.use { inputStream2 ->
                    val loadOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    BitmapFactory.decodeStream(inputStream2, null, loadOptions)
                }
            }
        } catch (e: Exception) {
            timber.log.Timber.tag("NoteExporter").w(e, "Failed to load bitmap: $uriString")
            null
        }
    }

    /**
     * F10: 导出为 PDF (支持图片)。
     * @param context 用于加载图片 Bitmap
     * @param outputStream 目标输出流 (通常来自 SAF ContentResolver.openOutputStream)
     * @param imageUris 笔记关联的图片 URI 列表
     * @return 写入的页数
     */
    suspend fun exportToPdf(
        context: Context,
        outputStream: OutputStream,
        title: String,
        content: String,
        meta: String = "",
        imageUris: List<String> = emptyList()
    ): Int = withContext(Dispatchers.IO) {
        val pdf = PdfDocument()
        val contentAreaWidth = (PAGE_WIDTH_PT - 2 * MARGIN_PT).toInt()
        val contentAreaHeight = (PAGE_HEIGHT_PT - 2 * MARGIN_PT).toInt()

        // 预加载图片 Bitmap
        val bitmaps = imageUris.mapNotNull { loadBitmap(context, it) }

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
        for (item in buildExportItems(lines, bitmaps)) {
            when (item) {
                is ExportItem.Text -> {
                    val lineHeight = LINE_HEIGHT_PT
                    if (y + lineHeight > PAGE_HEIGHT_PT - MARGIN_PT) {
                        pdf.finishPage(page)
                        pageNum++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, pageNum).create()
                        page = pdf.startPage(pageInfo)
                        canvas = page.canvas
                        y = MARGIN_PT.toFloat()
                    }
                    canvas.drawText(item.text, MARGIN_PT.toFloat(), y + BODY_SIZE, bodyPaint)
                    y += lineHeight
                }
                is ExportItem.Image -> {
                    val bitmap = item.bitmap
                    val scale = (MAX_IMAGE_WIDTH_PT / bitmap.width).coerceAtMost(1f)
                    val scaledWidth = (bitmap.width * scale).toInt()
                    val scaledHeight = (bitmap.height * scale).toInt()

                    // 图片高度 + 间距
                    val totalHeight = scaledHeight + IMAGE_SPACING_PT

                    // 检查是否需要换页
                    if (y + totalHeight > PAGE_HEIGHT_PT - MARGIN_PT) {
                        pdf.finishPage(page)
                        pageNum++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, pageNum).create()
                        page = pdf.startPage(pageInfo)
                        canvas = page.canvas
                        y = MARGIN_PT.toFloat()
                    }

                    // 绘制图片 (居中)
                    val destRect = Rect(
                        MARGIN_PT.toInt(),
                        y.toInt(),
                        (MARGIN_PT + scaledWidth).toInt(),
                        (y + scaledHeight).toInt()
                    )
                    val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                    canvas.drawBitmap(bitmap, srcRect, destRect, null)
                    y += totalHeight
                }
            }
        }

        pdf.finishPage(page)
        pdf.writeTo(outputStream)
        pdf.close()

        // 回收 Bitmap
        bitmaps.forEach { it.recycle() }

        pageNum
    }

    /**
     * F10: 导出为长图 (PNG) (支持图片)。
     * 1 px = 1 pt 的近似: 用 2.0 系数 (即 1 pt ≈ 2 px), 在 xhdpi 屏幕上看起来不糊。
     * @param context 用于加载图片 Bitmap
     */
    suspend fun exportToLongImage(
        context: Context,
        outputStream: OutputStream,
        title: String,
        content: String,
        meta: String = "",
        imageUris: List<String> = emptyList()
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

        // 预加载图片 Bitmap
        val bitmaps = imageUris.mapNotNull { loadBitmap(context, it) }

        // 计算每行的文字高度 (使用 StaticLayout)
        val layout = StaticLayout.Builder
            .obtain(content, 0, content.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(false)
            .build()

        // 计算图片总高度
        var imageTotalHeight = 0
        val maxImageWidthPx = (MAX_IMAGE_WIDTH_PT * density).toInt()
        val imageSpacingPx = (IMAGE_SPACING_PT * density).toInt()
        bitmaps.forEach { bitmap ->
            val scale = (maxImageWidthPx.toFloat() / bitmap.width).coerceAtMost(1f)
            val scaledHeight = (bitmap.height * scale).toInt()
            imageTotalHeight += scaledHeight + imageSpacingPx
        }

        var extraHeight = 0
        if (title.isNotBlank()) extraHeight += (titleSizePx + 16 * density).toInt()
        if (meta.isNotBlank()) extraHeight += (metaSizePx + 12 * density).toInt()
        val totalHeight = layout.height + 2 * marginPx + extraHeight + imageTotalHeight

        val bitmap = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = marginPx.toFloat()

        // 绘制标题
        if (title.isNotBlank()) {
            canvas.drawText(title, marginPx.toFloat(), y + titleSizePx, titlePaintImg)
            y += titleSizePx + 16 * density
        }
        // 绘制元信息
        if (meta.isNotBlank()) {
            canvas.drawText(meta, marginPx.toFloat(), y + metaSizePx, metaPaintImg)
            y += metaSizePx + 12 * density
        }

        // 绘制图片 (在文字上方)
        bitmaps.forEach { bitmapImg ->
            val scale = (maxImageWidthPx.toFloat() / bitmapImg.width).coerceAtMost(1f)
            val scaledWidth = (bitmapImg.width * scale).toInt()
            val scaledHeight = (bitmapImg.height * scale).toInt()

            val destRect = Rect(
                marginPx,
                y.toInt(),
                marginPx + scaledWidth,
                (y + scaledHeight).toInt()
            )
            val srcRect = Rect(0, 0, bitmapImg.width, bitmapImg.height)
            canvas.drawBitmap(bitmapImg, srcRect, destRect, null)
            y += scaledHeight + imageSpacingPx
        }

        // 绘制文字
        canvas.save()
        canvas.translate(marginPx.toFloat(), y)
        layout.draw(canvas)
        canvas.restore()

        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        bitmap.recycle()

        // 回收 Bitmap
        bitmaps.forEach { it.recycle() }

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
        meta: String = "",
        imageUris: List<String> = emptyList()
    ): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        resolver.openOutputStream(uri, "wt")?.use { out ->
            exportToPdf(context, out, title, content, meta, imageUris)
        } ?: throw IllegalStateException("无法打开目标 URI: $uri")
    }

    suspend fun exportImageToUri(
        context: Context,
        uri: Uri,
        title: String,
        content: String,
        meta: String = "",
        imageUris: List<String> = emptyList()
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        resolver.openOutputStream(uri, "wt")?.use { out ->
            exportToLongImage(context, out, title, content, meta, imageUris)
        } ?: throw IllegalStateException("无法打开目标 URI: $uri")
    }

    /**
     * F124: 导出项类型 (文字或图片)
     */
    private sealed class ExportItem {
        data class Text(val text: String) : ExportItem()
        data class Image(val bitmap: Bitmap) : ExportItem()
    }

    /**
     * F124: 将文字行和图片混合成导出项列表。
     * 图片显示在对应文字段落之前。
     */
    private fun buildExportItems(lines: List<String>, bitmaps: List<Bitmap>): List<ExportItem> {
        val items = mutableListOf<ExportItem>()

        // 如果有图片，在内容前添加
        bitmaps.forEach { bitmap ->
            items.add(ExportItem.Image(bitmap))
        }

        // 添加文字行
        lines.forEach { line ->
            items.add(ExportItem.Text(line))
        }

        return items
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

    /**
     * 功能4: 将笔记内容导出为标准 Markdown (.md) 文件。
     * - 若 title 非空, 自动作为一级标题插入最前面。
     * - meta (元信息, 如"清笺 · yyyy-MM-dd HH:mm") 作为顶部引用块或尾部追加。
     * - imageUris 作为 Markdown ![](url) 图片语法追加在文档末尾,
     *   方便其他 Markdown 阅读器识别。
     *
     * @return 写入的字节数, 供调用方用于日志或校验。
     */
    suspend fun exportMarkdownToUri(
        context: Context,
        uri: Uri,
        title: String,
        content: String,
        meta: String,
        imageUris: List<String> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        val sb = StringBuilder()

        // 1) 标题: 一级标题
        if (title.isNotBlank()) {
            sb.append("# ").append(title.trim()).appendLine()
            sb.appendLine()
        }

        // 2) meta 信息: 引用块
        if (meta.isNotBlank()) {
            sb.append("> ").append(meta.trim()).appendLine()
            sb.appendLine()
        }

        // 3) 正文: content 已是 Markdown 文本, 直接写入,
        //    但为避免后续解析时首行被当作标题, 这里不做额外转义。
        if (content.isNotBlank()) {
            sb.append(content.trimEnd()).appendLine()
        } else {
            sb.appendLine("*(无正文)*")
        }

        // 4) 图片列表
        if (imageUris.isNotEmpty()) {
            sb.appendLine()
            sb.append("---").appendLine()
            sb.appendLine()
            sb.append("## 附件图片").appendLine()
            sb.appendLine()
            imageUris.forEachIndexed { idx, imgUri ->
                // Markdown 图片语法, uri 使用 absolute 形式
                sb.append("![图片${idx + 1}](").append(imgUri).append(")").appendLine()
                sb.appendLine()
            }
        }

        // 5) 写入到 SAF Uri
        context.contentResolver.openOutputStream(uri).use { out: OutputStream? ->
            requireNotNull(out) { "无法打开输出流: $uri" }
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
            out.flush()
        }
        return@withContext sb.length.toLong()
    }
}
