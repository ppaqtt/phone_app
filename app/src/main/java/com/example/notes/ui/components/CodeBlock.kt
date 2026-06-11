package com.example.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * F14: 代码块。
 * 渲染 markdown 三反引号围栏代码块 (```lang ... ```), 用等宽字体 +
 * 深色背景 + 语言标签。
 *
 * 设计取舍:
 * 1) 不引入 Prism4j / Highlight.js 等第三方依赖 (apk 体积 + 维护成本),
 *    自带轻量关键字着色: Kotlin / Java / Python / JS / Go / Rust / C/C++。
 * 2) 颜色策略: 关键字 (紫) / 字符串 (绿) / 数字 (橙) / 注释 (灰斜体) /
 *    标点 (浅) — 7:1 对比度, 在 #1E1E1E 等深背景上可读。
 * 3) 横向可滚动, 避免长行被自动换行打乱代码缩进。
 */
@Composable
fun CodeBlock(
    code: String,
    language: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CodeBackground)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
    ) {
        // 顶部语言标签
        if (language.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LanguageTagBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.uppercase(),
                    color = LanguageTagFg,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        // 代码内容 (BasicTextField 不能用于展示只读块, 用 Text + AnnotatedString 即可)
        val scroll = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = highlight(code, language),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Color(0xFFE6E6E6)
            )
        }
    }
}

/**
 * 把 ```lang ... ``` 块拆出来, 与文本一起返回 (text, codeBlock) 列表。
 *
 * 输入示例:
 * ```
 * hello
 *
 * ```kotlin
 * fun foo() = 1
 * ```
 *
 * bye
 * ```
 * 返回: [(text "hello\n\n"), code, (text "\nbye\n")]
 */
data class CodeBlockSpan(
    val text: String,
    val code: String,
    val language: String
)

fun findCodeBlocks(text: String): List<CodeBlockSpan> {
    if (text.isEmpty()) return emptyList()
    val result = ArrayList<CodeBlockSpan>()
    val lines = text.split('\n')
    var i = 0
    var runningOffset = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            // 围栏: ```lang 或 ```
            val lang = trimmed.removePrefix("```").trim()
            val startOffset = runningOffset
            i++
            runningOffset += line.length + 1 // +1 for '\n'
            val codeLines = ArrayList<String>()
            var closed = false
            while (i < lines.size) {
                val l = lines[i]
                runningOffset += l.length + 1
                if (l.trim() == "```") {
                    i++
                    closed = true
                    break
                }
                codeLines.add(l)
                i++
            }
            if (closed) {
                // 计算前面的 text 段 (startOffset → startLine 之间的所有内容)
                if (startOffset > 0) {
                    val prevText = text.substring(0, startOffset)
                    result.add(CodeBlockSpan(prevText, "", ""))
                }
                result.add(CodeBlockSpan("", codeLines.joinToString("\n"), lang))
                // 注意: 这里 startOffset 的语义与 MarkdownTable 不同 —
                // 我们重新以"已消费到 runningOffset"为基准切片。
                val consumed = text.substring(0, runningOffset)
                if (consumed.length < text.length) {
                    // 还有剩余 — 后续循环会按 offset 切
                }
            } else {
                // 围栏未闭合, 整段当普通文本
                // 重新从 startLine 之后继续扫描
                continue
            }
        } else {
            runningOffset += line.length + 1
            i++
        }
    }
    // 简化版: 把所有 text 与 code 平铺成 "交替" 列表, 最后一个 text 是剩余部分
    val flat = ArrayList<CodeBlockSpan>()
    val pattern = Regex("```([\\w+#-]*)\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
    var lastEnd = 0
    pattern.findAll(text).forEach { m ->
        val before = text.substring(lastEnd, m.range.first)
        if (before.isNotEmpty()) flat.add(CodeBlockSpan(before, "", ""))
        flat.add(CodeBlockSpan("", m.groupValues[2], m.groupValues[1]))
        lastEnd = m.range.last + 1
    }
    val tail = text.substring(lastEnd)
    if (tail.isNotEmpty()) flat.add(CodeBlockSpan(tail, "", ""))
    return flat
}

// F14 colors — 写到顶层 const, 让 Composable 直接读
private val CodeBackground = Color(0xFF1E1E1E)
private val LanguageTagBg = Color(0xFF2D2D30)
private val LanguageTagFg = Color(0xFF9CDCFE)

/* ============================================================== */
/* 简易关键字着色                                                    */
/* ============================================================== */

private val KEYWORDS_KOTLIN = setOf(
    "fun", "val", "var", "if", "else", "when", "for", "while", "do", "return",
    "class", "object", "interface", "enum", "sealed", "data", "open", "abstract",
    "private", "public", "protected", "internal", "override", "companion",
    "import", "package", "as", "is", "in", "by", "this", "super", "null",
    "true", "false", "throw", "try", "catch", "finally", "init", "constructor"
)
private val KEYWORDS_JAVA = KEYWORDS_KOTLIN + setOf(
    "public", "static", "void", "final", "extends", "implements", "new", "instanceof"
)
private val KEYWORDS_PYTHON = setOf(
    "def", "class", "if", "elif", "else", "for", "while", "return", "import",
    "from", "as", "with", "try", "except", "finally", "raise", "pass", "break",
    "continue", "lambda", "yield", "global", "nonlocal", "True", "False", "None",
    "and", "or", "not", "in", "is", "self", "async", "await"
)
private val KEYWORDS_JS = setOf(
    "function", "var", "let", "const", "if", "else", "for", "while", "return",
    "class", "extends", "new", "this", "import", "export", "from", "as", "async",
    "await", "try", "catch", "finally", "throw", "typeof", "instanceof", "in", "of",
    "true", "false", "null", "undefined"
)
private val KEYWORDS_GO = setOf(
    "func", "var", "const", "if", "else", "for", "range", "return", "package",
    "import", "type", "struct", "interface", "map", "chan", "go", "defer", "select",
    "case", "default", "switch", "break", "continue", "fallthrough", "true", "false", "nil"
)
private val KEYWORDS_RUST = setOf(
    "fn", "let", "mut", "if", "else", "for", "while", "loop", "return", "match",
    "struct", "enum", "trait", "impl", "pub", "use", "mod", "as", "self", "Self",
    "true", "false", "move", "ref", "in", "where", "type", "const", "static"
)
private val KEYWORDS_C = setOf(
    "int", "char", "float", "double", "void", "short", "long", "signed", "unsigned",
    "if", "else", "for", "while", "do", "return", "break", "continue", "switch",
    "case", "default", "struct", "union", "enum", "typedef", "sizeof", "static",
    "const", "extern", "volatile", "register", "goto", "NULL", "true", "false"
)

private fun keywordsFor(lang: String): Set<String> = when (lang.lowercase()) {
    "kotlin", "kt" -> KEYWORDS_KOTLIN
    "java" -> KEYWORDS_JAVA
    "python", "py" -> KEYWORDS_PYTHON
    "javascript", "js", "ts", "typescript" -> KEYWORDS_JS
    "go", "golang" -> KEYWORDS_GO
    "rust", "rs" -> KEYWORDS_RUST
    "c", "cpp", "c++", "cc" -> KEYWORDS_C
    else -> emptySet()
}

/**
 * F14: 给代码块着色, 输出 AnnotatedString。
 * 解析 4 类 token: 注释 (// 或 #) / 字符串 ("..." / '...') / 数字 / 关键字。
 * 实现简化版: 单行注释到行尾, 字符串内全部视作字符串 (不考虑转义),
 * 数字识别连续 [0-9]+。
 */
private fun highlight(code: String, language: String): AnnotatedString {
    val keywords = keywordsFor(language)
    val keywordColor = Color(0xFFC586C0) // 紫
    val stringColor = Color(0xFFCE9178)   // 橙红
    val numberColor = Color(0xFFB5CEA8)   // 绿
    val commentColor = Color(0xFF6A9955)  // 灰绿
    val plainColor = Color(0xFFE6E6E6)
    val isPythonLike = language.lowercase() in setOf("python", "py", "shell", "bash", "sh", "yaml", "yml")
    val lineCommentStart = if (isPythonLike) "#" else "//"

    return buildAnnotatedString {
        val lines = code.split('\n')
        lines.forEachIndexed { lineIdx, line ->
            // 1) 注释优先: 把注释段以 italic 样式附加, 返回非注释部分 codePart
            val commentIdx = line.indexOf(lineCommentStart)
            val codePart: String = if (commentIdx >= 0) {
                withStyle(SpanStyle(color = commentColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(line.substring(commentIdx))
                }
                line.substring(0, commentIdx)
            } else {
                line
            }
            // 2) 扫描 codePart: 字符串 / 数字 / 关键字 / 普通
            var i = 0
            while (i < codePart.length) {
                val c = codePart[i]
                when {
                    // 字符串
                    c == '"' || c == '\'' -> {
                        val quote = c
                        val end = findStringEnd(codePart, i, quote)
                        withStyle(SpanStyle(color = stringColor)) {
                            append(codePart.substring(i, end))
                        }
                        i = end
                    }
                    // 数字
                    c.isDigit() -> {
                        val end = findNumberEnd(codePart, i)
                        withStyle(SpanStyle(color = numberColor)) {
                            append(codePart.substring(i, end))
                        }
                        i = end
                    }
                    // 标识符 (关键字)
                    c.isLetter() || c == '_' -> {
                        val end = findIdentEnd(codePart, i)
                        val word = codePart.substring(i, end)
                        if (word in keywords) {
                            withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold)) {
                                append(word)
                            }
                        } else {
                            withStyle(SpanStyle(color = plainColor)) {
                                append(word)
                            }
                        }
                        i = end
                    }
                    else -> {
                        withStyle(SpanStyle(color = plainColor)) {
                            append(c)
                        }
                        i++
                    }
                }
            }
            if (lineIdx < lines.size - 1) append('\n')
        }
    }
}

private fun findStringEnd(s: String, start: Int, quote: Char): Int {
    var i = start + 1
    while (i < s.length) {
        if (s[i] == '\\' && i + 1 < s.length) {
            i += 2
            continue
        }
        if (s[i] == quote) return i + 1
        i++
    }
    return s.length
}

private fun findNumberEnd(s: String, start: Int): Int {
    var i = start
    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
    return i
}

private fun findIdentEnd(s: String, start: Int): Int {
    var i = start
    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
    return i
}
