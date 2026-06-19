package com.example.notes.util

/**
 * 笔记内链工具。
 *
 * 语法: `[[笔记标题]]` 或 `[[笔记标题|显示文本]]`。
 *
 * 匹配规则:
 * - 必须成对出现 [[ ]]
 * - 标题可包含中英文、数字、空格
 * - 支持 | 分隔的别名
 * - 不区分大小写匹配（中文无大小写）
 *
 * 使用示例:
 * ```
 * val refs = NoteLinkHelper.extractLinks("参考 [[项目计划]] 与 [[开发笔记|笔记]]")
 * // refs[0] = NoteLinkRef(title="项目计划", alias=null)
 * // refs[1] = NoteLinkRef(title="开发笔记", alias="笔记")
 *
 * val html = NoteLinkHelper.renderLinks(text) { ref ->
 *     "<a href=\"note:${ref.title}\">${ref.alias ?: ref.title}</a>"
 * }
 * ```
 */
object NoteLinkHelper {

    /** 内链正则: [[标题]] 或 [[标题|别名]] */
    private val PATTERN = Regex("""\[\[([^\[\]\n|]+)(?:\|([^\[\]\n]+))?]]""")

    /** 提取文本中所有内链引用 */
    fun extractLinks(text: String): List<NoteLinkRef> {
        if (text.isEmpty()) return emptyList()
        return PATTERN.findAll(text).map { match ->
            NoteLinkRef(
                title = match.groupValues[1].trim(),
                alias = match.groupValues[2].takeIf { it.isNotEmpty() }?.trim()
            )
        }.toList()
    }

    /** 提取文本中所有不重复的标题（用于反向链接） */
    fun extractUniqueTitles(text: String): List<String> =
        extractLinks(text).map { it.title }.distinct()

    /** 将内链渲染为 HTML / 自定义格式。callback 接收 ref 返回渲染后的字符串 */
    fun renderLinks(text: String, renderer: (NoteLinkRef) -> String): String {
        if (text.isEmpty()) return text
        return PATTERN.replace(text) { match ->
            val ref = NoteLinkRef(
                title = match.groupValues[1].trim(),
                alias = match.groupValues[2].takeIf { it.isNotEmpty() }?.trim()
            )
            renderer(ref)
        }
    }

    /**
     * 计算内链状态: 给定一个候选笔记列表，标记每个 ref 是否能解析。
     * 用于 UI 显示"内链已失效"提示。
     */
    fun resolveLinks(refs: List<NoteLinkRef>, availableTitles: Set<String>): List<NoteLinkRef> {
        return refs.map { ref ->
            ref.copy(available = ref.title in availableTitles)
        }
    }
}

/** 笔记内链引用 */
data class NoteLinkRef(
    val title: String,
    val alias: String? = null,
    val available: Boolean = true
) {
    /** 渲染显示文本 */
    val display: String get() = alias ?: title
}
