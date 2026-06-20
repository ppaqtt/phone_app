package com.qingjian.notes.util

import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.ext.gfm.tables.TablesExtension

// ────────────────────────── 数据模型 ──────────────────────────

sealed class RenderedBlock {
    data class Heading(val level: Int, val text: String) : RenderedBlock()
    data class Paragraph(val inlineNodes: List<InlineNode>) : RenderedBlock()
    data class BulletList(val items: List<List<InlineNode>>) : RenderedBlock()
    data class OrderedList(val startNumber: Int, val items: List<List<InlineNode>>) : RenderedBlock()
    data class BlockQuote(val blocks: List<RenderedBlock>) : RenderedBlock()
    data class CodeBlock(val literal: String, val language: String?) : RenderedBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : RenderedBlock()
    class HorizontalRule : RenderedBlock()
    class Blank : RenderedBlock()
}

sealed class InlineNode {
    data class Text(val text: String) : InlineNode()
    data class Bold(val children: List<InlineNode>) : InlineNode()
    data class Italic(val children: List<InlineNode>) : InlineNode()
    data class Code(val literal: String) : InlineNode()
    data class Link(val destination: String, val children: List<InlineNode>) : InlineNode()
    class SoftLineBreak : InlineNode()
    class HardLineBreak : InlineNode()
}

// ────────────────────────── 解析器 ──────────────────────────

object LegalDocumentRenderer {

    private val parser: Parser by lazy {
        val extensions = listOf(TablesExtension.create())
        Parser.builder().extensions(extensions).build()
    }

    /**
     * 将 Markdown 纯文本解析为 [RenderedBlock] 列表。
     */
    fun parseMarkdown(text: String): List<RenderedBlock> {
        val document = parser.parse(text)
        return parseBlocks(document)
    }

    // ────────────── Block 级解析 ──────────────

    private fun parseBlocks(parent: Node): List<RenderedBlock> {
        val result = mutableListOf<RenderedBlock>()
        var child = parent.firstChild
        while (child != null) {
            val block = parseBlock(child)
            if (block != null) {
                result.add(block)
            }
            child = child.next
        }
        return result
    }

    private fun parseBlock(node: Node): RenderedBlock? {
        return when (node) {
            is org.commonmark.node.Heading -> {
                RenderedBlock.Heading(node.level, extractPlainText(node))
            }
            is Paragraph -> {
                RenderedBlock.Paragraph(parseInlineNodes(node))
            }
            is BulletList -> {
                val items = mutableListOf<List<InlineNode>>()
                var item: Node? = node.firstChild
                while (item != null) {
                    if (item is ListItem) {
                        val inlines = collectListItemInlines(item)
                        items.add(inlines)
                    }
                    item = item.next
                }
                RenderedBlock.BulletList(items)
            }
            is OrderedList -> {
                val items = mutableListOf<List<InlineNode>>()
                var item: Node? = node.firstChild
                while (item != null) {
                    if (item is ListItem) {
                        val inlines = collectListItemInlines(item)
                        items.add(inlines)
                    }
                    item = item.next
                }
                RenderedBlock.OrderedList(node.startNumber, items)
            }
            is BlockQuote -> {
                RenderedBlock.BlockQuote(parseBlocks(node))
            }
            is FencedCodeBlock -> {
                RenderedBlock.CodeBlock(node.literal, node.info)
            }
            is IndentedCodeBlock -> {
                RenderedBlock.CodeBlock(node.literal, null)
            }
            is org.commonmark.ext.gfm.tables.TableBlock -> {
                parseGfmTable(node)
            }
            is ThematicBreak -> {
                RenderedBlock.HorizontalRule()
            }
            else -> null
        }
    }

    // ────────────── GFM 表格解析 ──────────────

    private fun parseGfmTable(tableBlock: org.commonmark.ext.gfm.tables.TableBlock): RenderedBlock.Table {
        val headers = mutableListOf<String>()
        val rows = mutableListOf<List<String>>()

        // TableBlock 子节点: TableHead -> TableRow -> TableCell, TableBody -> TableRow -> TableCell
        var child: Node? = tableBlock.firstChild
        while (child != null) {
            when (child) {
                is org.commonmark.ext.gfm.tables.TableHead -> {
                    var cell: Node? = child.firstChild
                    while (cell != null) {
                        if (cell is org.commonmark.ext.gfm.tables.TableCell) {
                            headers.add(extractPlainText(cell))
                        }
                        cell = cell.next
                    }
                }
                is org.commonmark.ext.gfm.tables.TableBody -> {
                    var bodyRow: Node? = child.firstChild
                    while (bodyRow != null) {
                        if (bodyRow is org.commonmark.ext.gfm.tables.TableRow) {
                            val row = mutableListOf<String>()
                            var cell: Node? = bodyRow.firstChild
                            while (cell != null) {
                                if (cell is org.commonmark.ext.gfm.tables.TableCell) {
                                    row.add(extractPlainText(cell))
                                }
                                cell = cell.next
                            }
                            rows.add(row)
                        }
                        bodyRow = bodyRow.next
                    }
                }
            }
            child = child.next
        }

        return RenderedBlock.Table(headers, rows)
    }

    // ────────────── ListItem 辅助 ──────────────

    /**
     * 收集一个 ListItem 中所有 Paragraph 的 inline 节点，
     * 合并为一个平铺的列表。
     */
    private fun collectListItemInlines(listItem: ListItem): List<InlineNode> {
        val inlines = mutableListOf<InlineNode>()
        var child: Node? = listItem.firstChild
        while (child != null) {
            if (child is Paragraph) {
                inlines.addAll(parseInlineNodes(child))
            }
            child = child.next
        }
        return inlines
    }

    // ────────────── Inline 级解析 ──────────────

    private fun parseInlineNodes(parent: Node): List<InlineNode> {
        val result = mutableListOf<InlineNode>()
        var child = parent.firstChild
        while (child != null) {
            val inline = parseInlineNode(child)
            if (inline != null) {
                result.add(inline)
            }
            child = child.next
        }
        return result
    }

    private fun parseInlineNode(node: Node): InlineNode? {
        return when (node) {
            is org.commonmark.node.Text -> {
                InlineNode.Text(node.literal)
            }
            is StrongEmphasis -> {
                InlineNode.Bold(parseInlineNodes(node))
            }
            is Emphasis -> {
                InlineNode.Italic(parseInlineNodes(node))
            }
            is Code -> {
                InlineNode.Code(node.literal)
            }
            is Link -> {
                InlineNode.Link(node.destination, parseInlineNodes(node))
            }
            is SoftLineBreak -> {
                InlineNode.SoftLineBreak()
            }
            is HardLineBreak -> {
                InlineNode.HardLineBreak()
            }
            else -> null
        }
    }

    // ────────────── 纯文本提取 ──────────────

    /**
     * 递归提取节点及其所有后代的纯文本内容，
     * 用于 Heading 等只需要最终文本的场景。
     */
    private fun extractPlainText(node: Node): String {
        val sb = StringBuilder()
        appendPlainText(node, sb)
        return sb.toString()
    }

    private fun appendPlainText(node: Node, sb: StringBuilder) {
        when (node) {
            is org.commonmark.node.Text -> sb.append(node.literal)
            is SoftLineBreak -> sb.append(' ')
            is HardLineBreak -> sb.append('\n')
            is Code -> sb.append(node.literal)
            else -> {
                var child = node.firstChild
                while (child != null) {
                    appendPlainText(child, sb)
                    child = child.next
                }
            }
        }
    }
}
