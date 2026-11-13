package com.example.notes.util

import com.example.notes.data.NoteEntity
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 一键生成静态站: 把一组笔记导出为可直接托管的 HTML 小站 (ZIP)。
 *
 * 产物结构:
 * ```
 * index.html          # 文章列表
 * style.css           # 样式
 * notes/note-1.html   # 每篇笔记一个页面
 * ```
 * 解压后把目录丢到任意静态托管 (GitHub Pages / 对象存储 / 平平的小破站)
 * 即可访问。不依赖任何后端或 JS。
 */
object StaticSiteExporter {

    private val parser: Parser = Parser.builder()
        .extensions(listOf(TablesExtension.create()))
        .build()

    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(listOf(TablesExtension.create()))
        .escapeHtml(true)
        .build()

    /**
     * @return 导出的笔记篇数
     */
    fun export(output: OutputStream, siteTitle: String, notes: List<NoteEntity>): Int {
        val safeSiteTitle = siteTitle.ifBlank { "我的笔记" }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val zip = ZipOutputStream(BufferedOutputStream(output))

        zip.putNextEntry(ZipEntry("style.css"))
        zip.write(CSS.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        val index = StringBuilder()
        index.append(pageHead(safeSiteTitle, "style.css"))
        index.append("<header><h1>").append(escape(safeSiteTitle)).append("</h1>")
        index.append("<p class=\"meta\">共 ").append(notes.size).append(" 篇 · 导出于 ")
            .append(dateFormat.format(Date())).append("</p></header>\n<main><ul class=\"note-list\">\n")

        notes.forEach { note ->
            val fileName = "notes/note-${note.id}.html"
            val title = note.title.ifBlank {
                note.content.lineSequence().firstOrNull().orEmpty().take(30).ifBlank { "无标题" }
            }
            val date = dateFormat.format(Date(note.updatedAt))
            index.append("<li><a href=\"").append(fileName).append("\">")
                .append(escape(title)).append("</a><span class=\"date\">")
                .append(date).append("</span></li>\n")

            val html = StringBuilder()
            html.append(pageHead(title, "../style.css"))
            html.append("<nav><a href=\"../index.html\">← 返回目录</a></nav>\n")
            html.append("<article><h1>").append(escape(title)).append("</h1>\n")
            html.append("<p class=\"meta\">").append(date)
            if (note.tags.isNotBlank()) {
                html.append(" · ").append(escape(note.tags.replace(",", " / ")))
            }
            html.append("</p>\n")
            html.append("<div class=\"content\">")
            html.append(renderMarkdown(note.content))
            html.append("</div></article>\n")
            html.append("<footer><a href=\"../index.html\">← 返回目录</a></footer>")
            html.append(pageFoot())

            zip.putNextEntry(ZipEntry(fileName))
            zip.write(html.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        index.append("</ul></main>\n")
        index.append(pageFoot())
        zip.putNextEntry(ZipEntry("index.html"))
        zip.write(index.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.finish()
        zip.flush()
        return notes.size
    }

    private fun renderMarkdown(markdown: String): String = runCatching {
        renderer.render(parser.parse(markdown))
    }.getOrElse {
        "<pre>" + escape(markdown) + "</pre>"
    }

    private fun pageHead(title: String, cssPath: String): String = """
        |<!DOCTYPE html>
        |<html lang="zh-CN">
        |<head>
        |<meta charset="utf-8">
        |<meta name="viewport" content="width=device-width, initial-scale=1">
        |<title>${escape(title)}</title>
        |<link rel="stylesheet" href="$cssPath">
        |</head>
        |<body>
        |""".trimMargin()

    private fun pageFoot(): String = "</body>\n</html>\n"

    private fun escape(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private const val CSS = """
        :root { color-scheme: light dark; }
        * { box-sizing: border-box; }
        body {
            margin: 0 auto; max-width: 760px; padding: 32px 20px 64px;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
                "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
            line-height: 1.75; color: #24292f; background: #ffffff;
        }
        header h1 { margin: 0 0 8px; font-size: 28px; }
        .meta { color: #6b7280; font-size: 13px; margin: 0 0 20px; }
        .note-list { list-style: none; padding: 0; margin: 0; }
        .note-list li {
            display: flex; align-items: baseline; justify-content: space-between;
            gap: 12px; padding: 12px 0; border-bottom: 1px solid #eee;
        }
        .note-list a { color: #0b6bcb; text-decoration: none; font-weight: 500; }
        .note-list a:hover { text-decoration: underline; }
        .note-list .date { color: #9ca3af; font-size: 12px; white-space: nowrap; }
        nav, footer { margin: 0 0 20px; font-size: 14px; }
        footer { margin-top: 40px; }
        nav a, footer a { color: #0b6bcb; text-decoration: none; }
        article h1 { font-size: 26px; margin: 0 0 6px; }
        .content h1, .content h2, .content h3 { line-height: 1.35; margin: 1.4em 0 .6em; }
        .content p { margin: .7em 0; }
        .content blockquote {
            margin: 1em 0; padding: 4px 16px; border-left: 4px solid #d0d7de; color: #57606a;
        }
        .content code {
            background: #f3f4f6; border-radius: 4px; padding: 1px 5px;
            font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: .9em;
        }
        .content pre {
            background: #f6f8fa; border-radius: 8px; padding: 14px; overflow: auto;
        }
        .content pre code { background: transparent; padding: 0; }
        .content img { max-width: 100%; border-radius: 8px; }
        .content table { border-collapse: collapse; width: 100%; margin: 1em 0; }
        .content th, .content td { border: 1px solid #d0d7de; padding: 6px 10px; }
        @media (prefers-color-scheme: dark) {
            body { color: #e6edf3; background: #0d1117; }
            .note-list li { border-bottom-color: #21262d; }
            .note-list a, nav a, footer a { color: #58a6ff; }
            .content blockquote { border-left-color: #30363d; color: #8b949e; }
            .content code { background: #161b22; }
            .content pre { background: #161b22; }
            .content th, .content td { border-color: #30363d; }
        }
    """.trimIndent()
}
