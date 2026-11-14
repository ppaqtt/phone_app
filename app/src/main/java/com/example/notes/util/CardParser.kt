package com.example.notes.util

/**
 * 闪卡卡片解析。
 *
 * 支持的写法:
 * 1) 单行: `#card 光合作用的场所 :: 叶绿体`
 * 2) 单行 + 下一行答案:
 *    ```
 *    #card 光合作用的场所
 *    叶绿体
 *    ```
 * 3) 块级:
 *    ```
 *    #card
 *    光合作用的场所
 *    ---
 *    叶绿体
 *    #end
 *    ```
 * 4) 高亮: `==叶绿体==` — 正面是高亮文字, 背面是所在行 (去掉 `==` 后)。
 *
 * 同一篇笔记里重复的卡片按"正反面完全相同"去重。
 */
object CardParser {

    data class ParsedCard(val front: String, val back: String) {
        /** String.hashCode 在各端稳定, 适合作为去重 key */
        val key: String = "${front}\u0000${back}".hashCode().toString()
    }

    fun parse(content: String, noteTitle: String): List<ParsedCard> {
        val result = LinkedHashMap<String, ParsedCard>()
        val add: (String, String) -> Unit = { front, back ->
            val f = front.trim()
            val b = back.trim()
            if (f.isNotEmpty() && b.isNotEmpty()) {
                val card = ParsedCard(f, b)
                if (!result.containsKey(card.key)) result[card.key] = card
            }
        }

        val lines = content.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (!line.startsWith("#card")) {
                i++
                continue
            }

            val rest = line.removePrefix("#card").trim()
            if (rest.isNotEmpty()) {
                val inlineCard = splitInline(rest)
                if (inlineCard != null) {
                    add(inlineCard.first, inlineCard.second)
                    i++
                } else if (rest.contains("::")) {
                    // 写了 "::" 但背面还空着, 视为未完成的卡片, 跳过
                    i++
                } else {
                    // 题目在 #card 同行, 答案取下一行非空内容
                    val answer = lines.drop(i + 1).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                    if (answer.isNotEmpty() && !answer.startsWith("#")) add(rest, answer)
                    i++
                }
                continue
            }

            // 块级: 收集到 #end 或下一个 #card
            val block = mutableListOf<String>()
            var j = i + 1
            while (j < lines.size) {
                val l = lines[j]
                val t = l.trim()
                if (t.startsWith("#end") || t.startsWith("#card")) break
                block.add(l)
                j++
            }
            if (j < lines.size && lines[j].trim().startsWith("#end")) j++

            val sep = block.indexOfFirst { it.trim() == "---" || it.contains("::") }
            if (sep >= 0) {
                val front = block.take(sep).joinToString("\n").trim()
                val sepLine = block[sep]
                val back = if (sepLine.contains("::")) {
                    (sepLine.substringAfter("::").trim() + "\n" +
                        block.drop(sep + 1).joinToString("\n")).trim()
                } else {
                    block.drop(sep + 1).joinToString("\n").trim()
                }
                add(front, back)
            }
            i = j
        }

        // 高亮卡片
        val highlightRegex = Regex("==([^=\\n]{1,200})==")
        lines.forEach { line ->
            highlightRegex.findAll(line).forEach { match ->
                val front = match.groupValues[1].trim()
                if (front.isNotEmpty()) {
                    val back = line.replace("==", "").trim().ifBlank { noteTitle }
                    add(front, back)
                }
            }
        }

        return result.values.toList()
    }

    private fun splitInline(text: String): Pair<String, String>? {
        val idx = text.indexOf("::")
        if (idx <= 0) return null
        val front = text.substring(0, idx).trim()
        val back = text.substring(idx + 2).trim()
        return if (front.isNotEmpty() && back.isNotEmpty()) front to back else null
    }
}
