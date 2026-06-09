package com.example.notes.util

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 富文本编辑工具函数。
 *
 * 把 "工具栏点击" 映射为 "对当前 TextFieldValue 的 mutation",
 * 统一收口在 [TextStyleActions] 中, 便于:
 *  - 单测
 *  - 撤销/重做 (返回新 value, 调用方入栈)
 *  - 后续替换渲染层
 *
 * 约定:
 *  - 所有函数**纯函数**, 不持状态。
 *  - 返回新 [TextFieldValue], 选区被合理更新, 方便连续编辑。
 *  - 选区为空时样式类操作 [selectionIsEmpty] 为 true, 由调用方决定是 Toast 提示还是插入占位。
 */

/** 当前是否有非空选区 (用户至少选中一个字符) */
fun TextFieldValue.selectionIsEmpty(): Boolean = selection.collapsed

/** 规范化选区: 确保 start <= end */
private fun TextRange.normalized(): TextRange =
    TextRange(min(start, end), max(start, end))

/**
 * 把 markdown 段落 (以 \n 为分隔) 中的某段起止 offset 找出来。
 * 段落定义为「前后两个 \n 之间」或「文本首/尾到首个/末个 \n」。
 *
 * @param text 完整文本
 * @param offset 字符 offset, 通常取 selection.start
 * @return Pair(paragraphStart, paragraphEnd), 范围为左闭右开 [start, end)
 */
fun findParagraphRange(text: String, offset: Int): Pair<Int, Int> {
    if (text.isEmpty()) return 0 to 0
    val safeOffset = offset.coerceIn(0, text.length)
    // 找前一个 \n
    val prevNewline = text.lastIndexOf('\n', (safeOffset - 1).coerceAtLeast(0))
    val pStart = if (prevNewline < 0) 0 else prevNewline + 1
    // 找后一个 \n
    val nextNewline = text.indexOf('\n', safeOffset)
    val pEnd = if (nextNewline < 0) text.length else nextNewline
    return pStart to pEnd
}

/**
 * 用 [marker] 把当前选区包裹起来。
 *
 * - 选区为空: 选区**不会**被插入标记, 而是直接返回原 value (调用方应给 Toast 提示)。
 * - 选区非空: 选区前后各加一份 [marker], 选区移到内部末尾 (便于连续操作)。
 *
 * @param marker 包裹字符, 如 "**" / "_" / "<u>" / "~~" / "=="
 */
fun wrapSelectionWithMarker(value: TextFieldValue, marker: String): TextFieldValue {
    if (value.selectionIsEmpty()) return value
    val text = value.text
    val (s, e) = value.selection.normalized()
    val newText = text.substring(0, s) + marker + text.substring(s, e) + marker + text.substring(e)
    val newCaret = s + marker.length + (e - s) + marker.length
    return value.copy(
        text = newText,
        selection = TextRange(newCaret)
    )
}

/**
 * 切换式包裹: 若选区已被 [marker] 包裹则移除, 否则调用 [wrapSelectionWithMarker]。
 *
 * 这里只做最朴素的判断: 选区前 [marker.length] 个字符 == marker 且 选区后 [marker.length] 个字符 == marker。
 * 注意 [marker] 长度固定, 适用于 "**" / "_" / "<u>" / "~~" / "==", 长度不固定的不走此函数。
 */
fun toggleWrap(value: TextFieldValue, marker: String): TextFieldValue {
    if (value.selectionIsEmpty()) return value
    val text = value.text
    val (s, e) = value.selection.normalized()
    val ml = marker.length
    val hasPrefix = s >= ml && text.substring(s - ml, s) == marker
    val hasSuffix = e + ml <= text.length && text.substring(e, e + ml) == marker
    if (hasPrefix && hasSuffix) {
        // 去掉前后 marker 后, 选区回到被包裹的原始范围
        val stripped = text.substring(0, s - ml) + text.substring(s, e) + text.substring(e + ml)
        return value.copy(
            text = stripped,
            selection = TextRange(s - ml, e - ml)
        )
    }
    return wrapSelectionWithMarker(value, marker)
}

/**
 * 用成对标签包裹选区, 如 `<size=18>...</size>` / `<color=#FF0000>...</color>`。
 *
 * 选区为空时不插入, 直接返回原 value。
 */
fun wrapSelectionWithTag(value: TextFieldValue, openTag: String, closeTag: String): TextFieldValue {
    if (value.selectionIsEmpty()) return value
    val text = value.text
    val (s, e) = value.selection.normalized()
    val newText = text.substring(0, s) + openTag + text.substring(s, e) + closeTag + text.substring(e)
    val newCaret = s + openTag.length + (e - s) + closeTag.length
    return value.copy(
        text = newText,
        selection = TextRange(newCaret)
    )
}

/**
 * 对当前光标所在段落加 [align=...] 包裹。
 *
 * - 选区为空: 找 selection.start 所在段落, 整段包 [align=align]...[/align]
 * - 选区非空跨多段: 每段分别包绕, 选区合并为「首段起点 → 末段终点」
 *
 * @param align 取值 "left" / "center" / "right"
 */
fun wrapParagraphWithAlign(value: TextFieldValue, align: String): TextFieldValue {
    val text = value.text
    if (text.isEmpty()) return value
    val (s, e) = value.selection.normalized()
    val (firstStart, _) = findParagraphRange(text, s)
    val (_, lastEnd) = findParagraphRange(text, (e - 1).coerceAtLeast(s))
    // 逐段处理: 把 from..to 切分为若干段, 分别包绕
    val slices = collectParagraphs(text, firstStart, lastEnd)
    var newText = text
    var offsetDelta = 0
    for ((pStart, pEnd) in slices) {
        val adjustedStart = pStart + offsetDelta
        val adjustedEnd = pEnd + offsetDelta
        val before = newText.length
        val updated = applyAlignToRange(
            value.copy(text = newText),
            adjustedStart,
            adjustedEnd,
            align
        )
        newText = updated.text
        offsetDelta += newText.length - before
    }
    // 选区落在首段包绕后内部
    val newCaretStart = firstStart + "[align=$align]".length
    val innerLen = (lastEnd - firstStart) // 粗略, 多段时仅首段可见
    return value.copy(
        text = newText,
        selection = TextRange(newCaretStart, newCaretStart + innerLen.coerceAtLeast(0))
    )
}

/** 收集 [from, to) 内的所有段落 (start, end) 闭起开。 */
private fun collectParagraphs(text: String, from: Int, to: Int): List<Pair<Int, Int>> {
    if (from >= to) return emptyList()
    val result = mutableListOf<Pair<Int, Int>>()
    var i = from
    while (i < to) {
        val next = text.indexOf('\n', i)
        val pEnd = if (next < 0 || next > to) to else next
        if (pEnd > i) result.add(i to pEnd)
        if (next < 0 || next >= to) break
        i = next + 1
    }
    return result
}

/**
 * 在 [from, to) 范围 (段落起止) 上加 [align=...] 包裹。
 * 已存在 align 标记时直接替换为新值, 不重复嵌套。
 */
private fun applyAlignToRange(value: TextFieldValue, from: Int, to: Int, align: String): TextFieldValue {
    val text = value.text
    if (from >= to) return value
    // 抽取段落内容
    val paragraph = text.substring(from, to)
    val (stripped, leading, trailing) = stripAlign(paragraph)
    val wrapped = "[align=$align]$stripped[/align]"
    val newText = text.substring(0, from) + leading + wrapped + trailing + text.substring(to)
    // 选区定位: 重新落在 [align=...] 之后, 段落内
    val newCaretStart = from + leading.length + "[align=$align]".length
    val newCaretEnd = newCaretStart + stripped.length
    return value.copy(
        text = newText,
        selection = TextRange(newCaretStart, newCaretEnd)
    )
}

/**
 * 把段落里 [align=*]...[/align] 外层标签剥掉, 返回 (内部文本, 前缀空白, 后缀空白)。
 * 这样替换对齐时不会嵌套。
 */
private fun stripAlign(paragraph: String): Triple<String, String, String> {
    val alignRegex = Regex("""^\s*\[align=(left|center|right)\]([\s\S]*?)\[/align\]\s*$""")
    val match = alignRegex.matchEntire(paragraph)
    if (match != null) {
        val leading = paragraph.takeWhile { it.isWhitespace() }
        val trailing = paragraph.takeLastWhile { it.isWhitespace() }
        return Triple(match.groupValues[2], leading, trailing)
    }
    val leading = paragraph.takeWhile { it.isWhitespace() }
    val trailing = paragraph.takeLastWhile { it.isWhitespace() }
    val core = paragraph.trim()
    return Triple(core, leading, trailing)
}

/**
 * 在光标处插入一段文本。
 *
 * 不修改选区起点, 但选区终点会移到插入文本末尾, 便于用户继续编辑。
 */
fun insertAtCursor(value: TextFieldValue, snippet: String): TextFieldValue {
    val text = value.text
    val caret = value.selection.start
    val newText = text.substring(0, caret) + snippet + text.substring(caret)
    val newCaret = caret + snippet.length
    return value.copy(
        text = newText,
        selection = TextRange(newCaret)
    )
}
