package com.example.notes.util

/**
 * 双链语法解析: `[[笔记标题]]`。
 *
 * 支持:
 * - `[[标题]]`
 * - `[[标题|显示文本]]`
 * - `[[标题#小节]]`
 *
 * 匹配时统一按 trim + 忽略大小写, 与笔记标题对齐。
 */
object WikiLinkParser {

    private val linkRegex = Regex("""\[\[([^\]|#]+)(?:[#|][^\]]*)?]]""")

    fun extractTargets(content: String): List<String> =
        linkRegex.findAll(content)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

    fun normalize(title: String): String = title.trim().lowercase()
}
