package com.example.notes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notes.R

/**
 * 通用「法律文本」展示页:
 *  - 顶部 TopAppBar 含返回按钮 + 标题
 *  - 主体 LazyColumn 渲染 [rawResId] 指向的 Markdown / 纯文本
 *
 * 最简 Markdown 渲染:
 *  - `## xxx` / `# xxx`  → 标题 (大号粗体)
 *  - `**xxx**` / `__xxx__` → 粗体
 *  - `> xxx`              → 引用 (左缩进 + 浅灰)
 *  - `- xxx` / `* xxx`    → 列表项
 *  - 其它按段落渲染
 *
 * 加载: 从 `res/raw/{privacy_policy, terms_of_service}.md` 读取。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutLegalScreen(
    title: String,
    rawResId: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var content by remember { mutableStateOf("") }

    LaunchedEffect(rawResId) {
        content = runCatching {
            context.resources.openRawResource(rawResId)
                .bufferedReader()
                .use { it.readText() }
        }.getOrDefault("(内容加载失败)")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val blocks = remember(content) { parseMarkdownBlocks(content) }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
            ) {
                items(blocks) { block ->
                    MarkdownBlockView(block = block, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

/** 单个 Markdown 块: 标题 / 段落 / 列表 / 引用 */
private data class MarkdownBlock(
    val type: Type,
    val text: String,
    val level: Int = 0
) {
    enum class Type { HEADING, PARAGRAPH, LIST_ITEM, QUOTE, BLANK }
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val out = mutableListOf<MarkdownBlock>()
    text.split('\n').forEach { rawLine ->
        val line = rawLine.trimEnd('\r')
        if (line.isBlank()) {
            // 多个空行合并为一个
            if (out.isNotEmpty() && out.last().type != MarkdownBlock.Type.BLANK) {
                out.add(MarkdownBlock(MarkdownBlock.Type.BLANK, ""))
            }
            return@forEach
        }
        when {
            line.startsWith("# ") -> out.add(MarkdownBlock(MarkdownBlock.Type.HEADING, line.removePrefix("# ").trim(), level = 1))
            line.startsWith("## ") -> out.add(MarkdownBlock(MarkdownBlock.Type.HEADING, line.removePrefix("## ").trim(), level = 2))
            line.startsWith("### ") -> out.add(MarkdownBlock(MarkdownBlock.Type.HEADING, line.removePrefix("### ").trim(), level = 3))
            line.startsWith("> ") -> out.add(MarkdownBlock(MarkdownBlock.Type.QUOTE, line.removePrefix("> ").trim()))
            line.startsWith("- ") || line.startsWith("* ") ->
                out.add(MarkdownBlock(MarkdownBlock.Type.LIST_ITEM, line.substring(2).trim()))
            else -> out.add(MarkdownBlock(MarkdownBlock.Type.PARAGRAPH, line.trim()))
        }
    }
    // 去掉尾部空行
    while (out.isNotEmpty() && out.last().type == MarkdownBlock.Type.BLANK) {
        out.removeAt(out.size - 1)
    }
    return out
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock, modifier: Modifier = Modifier) {
    when (block.type) {
        MarkdownBlock.Type.HEADING -> {
            val fontSize = when (block.level) {
                1 -> 22.sp
                2 -> 18.sp
                3 -> 16.sp
                else -> 16.sp
            }
            Text(
                text = stripSimpleInline(block.text),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = modifier.padding(top = if (block.level <= 2) 12.dp else 6.dp, bottom = 4.dp)
            )
        }
        MarkdownBlock.Type.QUOTE -> Text(
            text = stripSimpleInline(block.text),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            ),
            modifier = modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
        )
        MarkdownBlock.Type.LIST_ITEM -> Text(
            text = "• " + stripSimpleInline(block.text),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            ),
            modifier = modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
        )
        MarkdownBlock.Type.BLANK -> Unit
        MarkdownBlock.Type.PARAGRAPH -> Text(
            text = stripSimpleInline(block.text),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            ),
            textAlign = TextAlign.Start,
            modifier = modifier.padding(top = 4.dp, bottom = 4.dp)
        )
    }
}

/** 去掉行内的 **xxx** / __xxx__ markdown 标记, 保留内容 (Compose Text 不支持 span 简化) */
private fun stripSimpleInline(text: String): String {
    return text
        .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        .replace(Regex("""__(.+?)__"""), "$1")
        .replace(Regex("""`(.+?)`"""), "$1")
}
