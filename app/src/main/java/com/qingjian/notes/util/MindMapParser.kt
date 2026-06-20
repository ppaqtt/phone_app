package com.qingjian.notes.util

/**
 * 进阶功能: 思维导图解析器。
 *
 * 从 Markdown 文本中识别层级标题 (# ## ### #### ##### ######),
 * 解析成树形结构, 用于思维导图视图渲染。
 *
 * 示例:
 * ```
 * # 顶层主题
 * ## 主题 1
 * - 内容 A
 * - 内容 B
 * ## 主题 2
 * ### 子主题
 * ```
 *
 * 解析结果:
 * ```
 * 顶层主题
 *   主题 1
 *     内容 A
 *     内容 B
 *   主题 2
 *     子主题
 * ```
 */
object MindMapParser {

    /** 解析结果: 一棵节点树 */
    fun parse(text: String): MindMapNode {
        val root = MindMapNode(title = "笔记", level = 0)
        var currentParent: MindMapNode = root

        val lines = text.lines()
        for (raw in lines) {
            val line = raw.trimEnd()
            if (line.isBlank()) continue

            val headerMatch = HEADER_REGEX.matchEntire(line)
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].length
                val title = headerMatch.groupValues[2].trim()
                if (level == 1) {
                    // 顶层 # 标题: 替换 root 标题
                    root.title = title
                    currentParent = root
                } else {
                    val node = MindMapNode(title = title, level = level)
                    val parent = findParentForLevel(root, level - 1, currentParent)
                    parent.children.add(node)
                    currentParent = node
                }
                continue
            }

            // 列表项 - 或者 * 开头
            val bulletMatch = BULLET_REGEX.find(line)
            if (bulletMatch != null) {
                val indent = raw.takeWhile { it == ' ' || it == '\t' }.length
                val content = bulletMatch.groupValues[1].trim()
                if (content.isEmpty()) continue
                val bulletLevel = indent / 2 + 1
                val node = MindMapNode(title = content, level = bulletLevel)
                val parent = findParentForLevel(root, bulletLevel - 1, currentParent)
                parent.children.add(node)
                currentParent = node
            }
        }
        return root
    }

    /** 找合适的父节点: 在 root 下, 找层级 <= targetLevel 的最近节点 */
    private fun findParentForLevel(
        root: MindMapNode,
        targetLevel: Int,
        @Suppress("UNUSED_PARAMETER") default: MindMapNode
    ): MindMapNode {
        if (targetLevel <= 0) return root
        // 从 root 找层级 < targetLevel 的最深路径
        val path = mutableListOf<MindMapNode>()
        fun dfs(node: MindMapNode): Boolean {
            path.add(node)
            if (node.level < targetLevel) {
                // 尝试向子节点找层级更深的
                for (c in node.children) {
                    if (dfs(c)) return true
                }
            } else if (node.level == targetLevel) {
                // 找到了层级等于 targetLevel 的兄弟节点, 父节点即 path 中上一个
                if (path.size >= 2) {
                    val parent = path[path.size - 2]
                    // 把 path 替换为 [parent], 然后我们会在外层 add
                    path.removeAt(path.size - 1)
                    path.clear()
                    path.add(parent)
                    return true
                }
            }
            path.removeAt(path.size - 1)
            return false
        }
        dfs(root)
        if (path.isEmpty()) return root
        return path.last()
    }

    private val HEADER_REGEX = Regex("""^(#{1,6})\s+(.+)$""")
    private val BULLET_REGEX = Regex("""^[-*+]\s+(.+)$""")
}

/** 思维导图节点 */
data class MindMapNode(
    var title: String,
    val level: Int,
    val children: MutableList<MindMapNode> = mutableListOf()
) {
    val isLeaf: Boolean get() = children.isEmpty()
    val totalSize: Int get() = 1 + children.sumOf { it.totalSize }
}
