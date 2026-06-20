package com.qingjian.notes.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingjian.notes.R
import com.qingjian.notes.util.InlineNode
import com.qingjian.notes.util.LegalDocumentRenderer
import com.qingjian.notes.util.RenderedBlock

private const val LINK_ANNOTATION_KEY = "url"

/**
 * 通用「法律文本」展示页:
 *  - 顶部 TopAppBar 含返回按钮 + 标题
 *  - 主体 LazyColumn 使用 [LegalDocumentRenderer] 渲染 [rawResId] 指向的 Markdown
 *
 * 渲染能力:
 *  - Heading (H1-H3) / Paragraph / BulletList / OrderedList
 *  - BlockQuote (递归) / CodeBlock / Table (GFM)
 *  - HorizontalRule / Blank
 *  - Inline: Bold / Italic / Code / Link (可点击) / SoftLineBreak / HardLineBreak
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
            val blocks = remember(content) { LegalDocumentRenderer.parseMarkdown(content) }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
            ) {
                items(blocks) { block ->
                    RenderedBlockView(
                        block = block,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

// ────────────────────────── Block 渲染 ──────────────────────────

@Composable
private fun RenderedBlockView(
    block: RenderedBlock,
    modifier: Modifier = Modifier
) {
    when (block) {
        is RenderedBlock.Heading -> HeadingView(block, modifier)
        is RenderedBlock.Paragraph -> InlineTextView(block.inlineNodes, modifier)
        is RenderedBlock.BulletList -> BulletListView(block, modifier)
        is RenderedBlock.OrderedList -> OrderedListView(block, modifier)
        is RenderedBlock.BlockQuote -> BlockQuoteView(block, modifier)
        is RenderedBlock.CodeBlock -> CodeBlockView(block, modifier)
        is RenderedBlock.Table -> MarkdownTable(block.headers, block.rows, modifier)
        is RenderedBlock.HorizontalRule -> Divider(modifier = modifier.padding(top = 8.dp, bottom = 8.dp))
        is RenderedBlock.Blank -> Spacer(modifier = modifier.height(8.dp))
    }
}

@Composable
private fun HeadingView(block: RenderedBlock.Heading, modifier: Modifier = Modifier) {
    val fontSize = when (block.level) {
        1 -> 22.sp
        2 -> 18.sp
        3 -> 16.sp
        else -> 16.sp
    }
    Text(
        text = block.text,
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.padding(top = if (block.level <= 2) 12.dp else 6.dp, bottom = 4.dp)
    )
}

@Composable
private fun InlineTextView(
    inlineNodes: List<InlineNode>,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val annotatedText = buildInlineAnnotatedString(inlineNodes)

    if (annotatedText.getStringAnnotations(LINK_ANNOTATION_KEY, 0, annotatedText.length).isNotEmpty()) {
        ClickableText(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            ),
            modifier = modifier.padding(top = 4.dp, bottom = 4.dp),
            onClick = { offset ->
                annotatedText.getStringAnnotations(LINK_ANNOTATION_KEY, offset, offset)
                    .firstOrNull()
                    ?.let { annotation ->
                        uriHandler.openUri(annotation.item)
                    }
            }
        )
    } else {
        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            ),
            textAlign = TextAlign.Start,
            modifier = modifier.padding(top = 4.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun BulletListView(
    block: RenderedBlock.BulletList,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
        block.items.forEachIndexed { _, inlines ->
            val annotatedText = buildInlineAnnotatedString(inlines)
            val uriHandler = LocalUriHandler.current
            val bulletPrefix = "\u2022 "

            val fullText = buildAnnotatedString {
                append(bulletPrefix)
                append(annotatedText)
            }

            if (annotatedText.getStringAnnotations(LINK_ANNOTATION_KEY, 0, annotatedText.length).isNotEmpty()) {
                ClickableText(
                    text = fullText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                    onClick = { offset ->
                        fullText.getStringAnnotations(LINK_ANNOTATION_KEY, offset, offset)
                            .firstOrNull()
                            ?.let { annotation ->
                                uriHandler.openUri(annotation.item)
                            }
                    }
                )
            } else {
                Text(
                    text = fullText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun OrderedListView(
    block: RenderedBlock.OrderedList,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
        block.items.forEachIndexed { index, inlines ->
            val annotatedText = buildInlineAnnotatedString(inlines)
            val uriHandler = LocalUriHandler.current
            val prefix = "${block.startNumber + index}. "

            val fullText = buildAnnotatedString {
                append(prefix)
                append(annotatedText)
            }

            if (annotatedText.getStringAnnotations(LINK_ANNOTATION_KEY, 0, annotatedText.length).isNotEmpty()) {
                ClickableText(
                    text = fullText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                    onClick = { offset ->
                        fullText.getStringAnnotations(LINK_ANNOTATION_KEY, offset, offset)
                            .firstOrNull()
                            ?.let { annotation ->
                                uriHandler.openUri(annotation.item)
                            }
                    }
                )
            } else {
                Text(
                    text = fullText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    ),
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun BlockQuoteView(
    block: RenderedBlock.BlockQuote,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            block.blocks.forEach { childBlock ->
                RenderedBlockView(
                    block = childBlock,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun CodeBlockView(block: RenderedBlock.CodeBlock, modifier: Modifier = Modifier) {
    Text(
        text = block.literal.trimEnd(),
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .padding(top = 8.dp, bottom = 8.dp)
            .background(
                color = Color.LightGray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(12.dp)
    )
}

// ────────────────────────── 表格渲染 ──────────────────────────

@Composable
private fun MarkdownTable(
    headers: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier
) {
    val borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val headerBgColor = Color.LightGray.copy(alpha = 0.3f)

    Column(
        modifier = modifier
            .padding(top = 8.dp, bottom = 8.dp)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(4.dp))
    ) {
        // 表头
        if (headers.isNotEmpty()) {
            Row(modifier = Modifier.background(headerBgColor)) {
                headers.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, borderColor)
                            .padding(8.dp)
                    )
                }
            }
        }
        // 数据行
        rows.forEach { row ->
            Row {
                // 确保每行与表头列数对齐
                val paddedRow = if (row.size < headers.size) {
                    row + List(headers.size - row.size) { "" }
                } else {
                    row
                }
                paddedRow.forEach { cell ->
                    Text(
                        text = cell,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, borderColor)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

// ────────────────────────── Inline 渲染 ──────────────────────────

private fun buildInlineAnnotatedString(
    nodes: List<InlineNode>
): AnnotatedString {
    return buildAnnotatedString {
        appendInlineNodes(nodes)
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineNodes(
    nodes: List<InlineNode>
) {
    for (node in nodes) {
        when (node) {
            is InlineNode.Text -> append(node.text)
            is InlineNode.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInlineNodes(node.children)
            }
            is InlineNode.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlineNodes(node.children)
            }
            is InlineNode.Code -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color.LightGray.copy(alpha = 0.3f)
                )
            ) {
                append(node.literal)
            }
            is InlineNode.Link -> {
                val start = length
                withStyle(
                    SpanStyle(
                        color = Color(0xFF1976D2),
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    appendInlineNodes(node.children)
                }
                addStringAnnotation(LINK_ANNOTATION_KEY, node.destination, start, length)
            }
            is InlineNode.SoftLineBreak -> append(" ")
            is InlineNode.HardLineBreak -> append("\n")
        }
    }
}
