package com.example.notes.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR 文字识别 (ML Kit Text Recognition v2)。
 *
 * 支持两种语言模型:
 * - 中文模型 (含中文与拉丁字符);
 * - 拉丁模型 (纯英文/数字/符号场景识别更准)。
 *
 * [Script.AUTO] 会先跑中文模型, 若整段没有任何 CJK 字符再用拉丁模型复核,
 * 取字符更多的结果。既保证中文场景, 又避免英文扫描件被中文模型拆错。
 *
 * 另提供表格结构化: 按行/列聚类 ML Kit 的 line boundingBox, 输出 Markdown 表格。
 * 结构不明显时自动退回普通文本。
 */
object OcrHelper {

    enum class Script { AUTO, CHINESE, LATIN }

    private val chineseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private val latinRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** 从图片 URI 识别文字 */
    suspend fun recognizeText(
        context: Context,
        uri: Uri,
        script: Script = Script.AUTO
    ): String {
        val bitmap = loadBitmap(context, uri)
        return try {
            process(bitmap, script, table = false)
        } finally {
            runCatching { bitmap.recycle() }
        }
    }

    /** 从图片 URI 识别表格, 返回 Markdown */
    suspend fun recognizeTable(
        context: Context,
        uri: Uri,
        script: Script = Script.AUTO
    ): String {
        val bitmap = loadBitmap(context, uri)
        return try {
            process(bitmap, script, table = true)
        } finally {
            runCatching { bitmap.recycle() }
        }
    }

    /**
     * 识别内存中的位图 (PDF 导入用)。
     * 不回收调用方的 bitmap, 由调用方负责生命周期。
     */
    suspend fun recognizeBitmap(bitmap: Bitmap, script: Script = Script.AUTO): String =
        process(bitmap, script, table = false)

    private suspend fun process(bitmap: Bitmap, script: Script, table: Boolean): String {
        val scaled = scaleIfNeeded(bitmap)
        return try {
            val text = recognize(scaled, script)
            if (table) buildMarkdownTable(text) else text.text
        } finally {
            if (scaled !== bitmap) runCatching { scaled.recycle() }
        }
    }

    private suspend fun recognize(bitmap: Bitmap, script: Script): Text {
        return when (script) {
            Script.CHINESE -> runRecognizer(chineseRecognizer, bitmap)
            Script.LATIN -> runRecognizer(latinRecognizer, bitmap)
            Script.AUTO -> {
                val chinese = runRecognizer(chineseRecognizer, bitmap)
                if (countCjk(chinese.text) > 0 || chinese.text.isBlank()) {
                    chinese
                } else {
                    // 没有中文, 用拉丁模型复核, 取更完整的结果
                    val latin = runRecognizer(latinRecognizer, bitmap)
                    if (latin.text.length > chinese.text.length) latin else chinese
                }
            }
        }
    }

    private suspend fun runRecognizer(recognizer: TextRecognizer, bitmap: Bitmap): Text =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    Timber.d("OCR recognized ${result.text.length} chars")
                    if (continuation.isActive) continuation.resume(result)
                }
                .addOnFailureListener { error ->
                    Timber.e(error, "OCR failed")
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }

    /**
     * 按行/列聚类版面, 生成 Markdown 表格。
     * 少于 2 行或 2 列时认为不是表格, 直接返回原始文本。
     */
    private fun buildMarkdownTable(text: Text): String {
        val spans = text.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val content = line.text.trim()
                if (content.isEmpty()) null
                else OcrSpan(
                    text = content,
                    height = box.height().toFloat(),
                    centerY = box.centerY().toFloat(),
                    centerX = box.centerX().toFloat()
                )
            }
        if (spans.size < 4) return text.text

        val avgHeight = spans.map { it.height }.average().toFloat().coerceAtLeast(8f)
        val rowTolerance = avgHeight * 0.6f

        // 1) 按 y 聚行
        val rows = mutableListOf<MutableList<OcrSpan>>()
        val sortedByY = spans.sortedBy { it.centerY }
        var currentRow = mutableListOf<OcrSpan>()
        var currentY = sortedByY.first().centerY
        sortedByY.forEach { span ->
            if (currentRow.isEmpty() || kotlin.math.abs(span.centerY - currentY) <= rowTolerance) {
                currentRow.add(span)
                currentY = currentRow.map { it.centerY }.average().toFloat()
            } else {
                rows.add(currentRow)
                currentRow = mutableListOf(span)
                currentY = span.centerY
            }
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)

        // 2) 按 x 聚类列
        val columnGap = avgHeight * 1.5f
        val columnCenters = mutableListOf<Float>()
        spans.sortedBy { it.centerX }.forEach { span ->
            if (columnCenters.none { kotlin.math.abs(it - span.centerX) <= columnGap }) {
                columnCenters.add(span.centerX)
            }
        }
        columnCenters.sort()

        if (rows.size < 2 || columnCenters.size < 2) return text.text

        // 3) 填格子
        val matrix = rows.map { row ->
            val cells = Array(columnCenters.size) { StringBuilder() }
            row.forEach { span ->
                val index = columnCenters.indices.minByOrNull {
                    kotlin.math.abs(columnCenters[it] - span.centerX)
                } ?: 0
                if (cells[index].isNotEmpty()) cells[index].append(' ')
                cells[index].append(span.text)
            }
            cells.map { it.toString().trim() }
        }

        val builder = StringBuilder()
        val header = matrix.first()
        builder.append("| ")
            .append(header.joinToString(" | ") { escapeCell(it) })
            .append(" |\n")
        builder.append("| ")
            .append(header.indices.joinToString(" | ") { "---" })
            .append(" |\n")
        matrix.drop(1).forEach { row ->
            builder.append("| ")
                .append(row.joinToString(" | ") { escapeCell(it) })
                .append(" |\n")
        }
        return builder.toString().trimEnd()
    }

    private fun escapeCell(value: String): String =
        value.replace("|", "\\|").replace("\n", " ").ifBlank { " " }

    private fun countCjk(text: String): Int =
        text.count { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }

    /**
     * 从 ContentResolver 加载 Bitmap, 自动按 [maxDim] 采样压缩。
     * 先用 inJustDecodeBounds 获取尺寸再采样, 避免超大图直接加载导致 OOM。
     */
    private fun loadBitmap(context: Context, uri: Uri, maxDim: Int = 1920): Bitmap {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)

            val w = options.outWidth
            val h = options.outHeight
            var sampleSize = 1
            if (w > maxDim || h > maxDim) {
                val wRatio = kotlin.math.ceil(w.toDouble() / maxDim).toInt()
                val hRatio = kotlin.math.ceil(h.toDouble() / maxDim).toInt()
                sampleSize = maxOf(wRatio, hRatio)
            }
            Timber.d("OCR image ${w}x${h}, sampleSize=$sampleSize")

            context.contentResolver.openInputStream(uri)?.use { input ->
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } ?: throw IllegalArgumentException("无法加载图片: $uri")
    }

    /** 任一边超过 [maxDim] 时等比缩放, 返回新 bitmap (不回收原图) */
    private fun scaleIfNeeded(bitmap: Bitmap, maxDim: Int = 1920): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = w.toFloat() / h.toFloat()
        val newW: Int
        val newH: Int
        if (w > h) {
            newW = maxDim
            newH = (maxDim / ratio).toInt()
        } else {
            newH = maxDim
            newW = (maxDim * ratio).toInt()
        }
        return bitmap.scale(newW, newH)
    }
}

/** OCR 表格聚类用的一行文字块 */
private data class OcrSpan(
    val text: String,
    val height: Float,
    val centerY: Float,
    val centerX: Float
)
