package com.example.notes.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * PDF / Word / 纯文本导入。
 *
 * - PDF: 用系统 [PdfRenderer] 逐页渲染成位图, 再交给 ML Kit OCR。
 *   这样扫描件和文字版 PDF 都能导入, 且不引入 PDFBox 之类的大依赖。
 * - DOCX: 直接解压读取 `word/document.xml`, 用 XmlPullParser 提取文本段落,
 *   不依赖 Apache POI (POI 在 Android 上体积大且容易与 Java 版本冲突)。
 * - 旧版 `.doc` 二进制格式不支持, 会提示用户另存为 `.docx`。
 */
object DocumentImporter {

    data class ImportedDocument(
        val title: String,
        val content: String
    )

    private const val MAX_PDF_PAGES = 20

    suspend fun import(context: Context, uri: Uri): ImportedDocument = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(context, uri) ?: "导入文档"
        val title = displayName.substringBeforeLast('.', displayName).ifBlank { "导入文档" }
        val lower = displayName.lowercase()
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()

        when {
            lower.endsWith(".pdf") || mime == "application/pdf" -> {
                val text = extractPdf(context, uri)
                ImportedDocument(title = title, content = text)
            }
            lower.endsWith(".docx") ||
                mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                ImportedDocument(title = title, content = extractDocx(context, uri))
            }
            lower.endsWith(".doc") || mime == "application/msword" -> {
                throw IOException("暂不支持旧版 .doc, 请用 Word 另存为 .docx 后再导入")
            }
            else -> {
                val text = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IOException("无法读取文件")
                ImportedDocument(title = title, content = text)
            }
        }
    }

    private suspend fun extractPdf(context: Context, uri: Uri): String {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("无法打开 PDF 文件")
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pageCount = minOf(renderer.pageCount, MAX_PDF_PAGES)
                val builder = StringBuilder()
                for (index in 0 until pageCount) {
                    val page = renderer.openPage(index)
                    try {
                        val bitmap = Bitmap.createBitmap(
                            page.width * 2,
                            page.height * 2,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val text = OcrHelper.recognizeBitmap(bitmap)
                        bitmap.recycle()
                        if (text.isNotBlank()) {
                            if (builder.isNotEmpty()) builder.append("\n\n")
                            builder.append(text.trim())
                        }
                    } finally {
                        page.close()
                    }
                }
                if (builder.isBlank()) {
                    throw IOException("PDF 中未识别到文字 (可能是纯图片或加密文件)")
                }
                return builder.toString()
            }
        }
    }

    private fun extractDocx(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法打开 Word 文件")
        val parser = Xml.newPullParser()
        val builder = StringBuilder()

        ZipInputStream(BufferedInputStream(stream)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    parser.setInput(zip, "UTF-8")
                    var event = parser.eventType
                    var inText = false
                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.START_TAG -> {
                                when (parser.name.substringAfter(':').lowercase()) {
                                    "t" -> inText = true
                                    "tab" -> builder.append('\t')
                                    "br", "cr" -> builder.append('\n')
                                }
                            }
                            XmlPullParser.TEXT -> {
                                if (inText) builder.append(parser.text)
                            }
                            XmlPullParser.END_TAG -> {
                                when (parser.name.substringAfter(':').lowercase()) {
                                    "t" -> inText = false
                                    "p" -> if (builder.isNotEmpty() && !builder.endsWith("\n")) {
                                        builder.append('\n')
                                    }
                                }
                            }
                        }
                        event = parser.next()
                    }
                    break
                }
                entry = zip.nextEntry
            }
        }

        val text = builder.toString().trim()
        if (text.isBlank()) throw IOException("Word 文档中没有可提取的文字")
        return text
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
}
