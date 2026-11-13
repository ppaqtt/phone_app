package com.example.notes.util

/**
 * 双链语法解析: 笔记正文中的 `[[标题]]` 表示指向另一篇同名笔记的链接。
 *
 * 只做最朴素的按标题匹配 —— 不引入额外 id 语法, 用户写起来自然,
 * 笔记改名后旧链接会自然失效 (与 Obsidian 等工具的行为一致)。
 */
object WikiLinks {

    private val REGEX = Regex("""\[\[([^\[\]\n]{1,80})]]""")

    /** 提取正文中全部链接目标 (去重, 保持出现顺序) */
    fun extract(content: String): List<String> =
        REGEX.findAll(content)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
}
