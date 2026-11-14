package com.example.notes.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.notes.data.NoteLinkRow
import com.example.notes.repository.NotesRepository
import com.example.notes.util.WikiLinkParser
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 双链关系图。
 *
 * 节点 = 未删除笔记 (最多 80 条, 按更新时间倒序);
 * 边 = 笔记正文中的 `[[另一条笔记标题]]`。
 *
 * 布局用轻量力导向算法: 圆环初始化 → 节点斥力 + 边引力 + 中心回拉,
 * 迭代 80 次后归一化到 0..1, 再映射到画布坐标。纯本地计算, 无外部库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklinkGraphScreen(
    repository: NotesRepository,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit
) {
    var rows by remember { mutableStateOf<List<NoteLinkRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        rows = runCatching { repository.getLinkRows() }.getOrDefault(emptyList()).take(80)
        loading = false
    }

    val graph = remember(rows) { buildGraph(rows) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "关系图谱",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${graph.nodes.size} 个节点 · ${graph.edges.size} 条链接",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                graph.edges.isEmpty() -> EmptyGraphState()
                else -> GraphCanvas(
                    graph = graph,
                    onOpenNote = onOpenNote
                )
            }
        }
    }
}

private data class GraphNode(
    val id: Long,
    val title: String,
    val links: Int,
    val x: Float,
    val y: Float
)

private data class GraphEdge(val from: Long, val to: Long)

private data class GraphData(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

@Composable
private fun GraphCanvas(
    graph: GraphData,
    onOpenNote: (Long) -> Unit
) {
    val density = LocalDensity.current
    val nodeSize = 34.dp
    val nodeColor = MaterialTheme.colorScheme.primary
    val nodeTextColor = MaterialTheme.colorScheme.onPrimary
    val edgeColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val radiusPx = with(density) { nodeSize.toPx() } / 2f

        // 边
        Canvas(modifier = Modifier.fillMaxSize()) {
            graph.edges.forEach { edge ->
                val from = graph.nodes.firstOrNull { it.id == edge.from } ?: return@forEach
                val to = graph.nodes.firstOrNull { it.id == edge.to } ?: return@forEach
                drawLine(
                    color = edgeColor,
                    start = androidx.compose.ui.geometry.Offset(from.x * widthPx, from.y * heightPx),
                    end = androidx.compose.ui.geometry.Offset(to.x * widthPx, to.y * heightPx),
                    strokeWidth = 1.5f
                )
            }
        }

        // 节点 + 标题
        graph.nodes.forEach { node ->
            val centerX = node.x * widthPx
            val centerY = node.y * heightPx
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (centerX - radiusPx).toInt(),
                            (centerY - radiusPx).toInt()
                        )
                    }
                    .size(nodeSize)
                    .background(nodeColor, CircleShape)
                    .clickable { onOpenNote(node.id) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = node.links.toString(),
                    color = nodeTextColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = node.title,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (centerX - with(density) { 48.dp.toPx() }).toInt(),
                            (centerY + radiusPx + with(density) { 2.dp.toPx() }).toInt()
                        )
                    }
                    .width(96.dp),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyGraphState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Hub,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "还没有双链",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                "在笔记里写 [[另一条笔记的标题]]，这里就会生成关系图。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
        }
    }
}

private fun buildGraph(rows: List<NoteLinkRow>): GraphData {
    if (rows.isEmpty()) return GraphData(emptyList(), emptyList())

    val titleToId = HashMap<String, Long>()
    rows.forEach { row ->
        val key = WikiLinkParser.normalize(row.title)
        if (key.isNotEmpty()) titleToId.putIfAbsent(key, row.id)
    }

    val edges = LinkedHashSet<Pair<Long, Long>>()
    rows.forEach { row ->
        WikiLinkParser.extractTargets(row.content).forEach targetLoop@{ target ->
            val targetId = titleToId[WikiLinkParser.normalize(target)] ?: return@targetLoop
            if (targetId != row.id) edges.add(row.id to targetId)
        }
    }
    if (edges.isEmpty()) return GraphData(emptyList(), emptyList())

    val positions = layout(rows.map { it.id }, edges.toList())
    val linkCount = HashMap<Long, Int>()
    edges.forEach { (from, to) ->
        linkCount[from] = (linkCount[from] ?: 0) + 1
        linkCount[to] = (linkCount[to] ?: 0) + 1
    }

    val nodes = rows.map { row ->
        val point = positions[row.id] ?: Point(0.5f, 0.5f)
        GraphNode(
            id = row.id,
            title = row.title.ifBlank { "无标题" },
            links = linkCount[row.id] ?: 0,
            x = point.x,
            y = point.y
        )
    }
    return GraphData(
        nodes = nodes,
        edges = edges.map { GraphEdge(it.first, it.second) }
    )
}

private data class Point(val x: Float, val y: Float)

/**
 * 力导向布局, 输出 0..1 的归一化坐标。
 * 参数固定, 同样的输入每次布局结果一致, 避免重组时节点乱跳。
 */
private fun layout(ids: List<Long>, edges: List<Pair<Long, Long>>): Map<Long, Point> {
    val n = ids.size
    if (n == 0) return emptyMap()
    val index = ids.withIndex().associate { it.value to it.index }
    val xs = FloatArray(n) { 0.5f + 0.32f * cos((2.0 * Math.PI * it / n).toFloat()) }
    val ys = FloatArray(n) { 0.5f + 0.32f * sin((2.0 * Math.PI * it / n).toFloat()) }

    repeat(80) {
        // 节点斥力
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                var dx = xs[i] - xs[j]
                var dy = ys[i] - ys[j]
                if (abs(dx) < 0.0001f && abs(dy) < 0.0001f) {
                    dx = 0.001f * (i + 1)
                    dy = 0.001f * (j + 1)
                }
                val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
                val force = 0.0006f / (distance * distance)
                dx /= distance
                dy /= distance
                xs[i] += dx * force
                ys[i] += dy * force
                xs[j] -= dx * force
                ys[j] -= dy * force
            }
        }
        // 边引力
        edges.forEach edgeLoop@{ edge ->
            val i = index[edge.first] ?: return@edgeLoop
            val j = index[edge.second] ?: return@edgeLoop
            val dx = xs[j] - xs[i]
            val dy = ys[j] - ys[i]
            xs[i] += dx * 0.015f
            ys[i] += dy * 0.015f
            xs[j] -= dx * 0.015f
            ys[j] -= dy * 0.015f
        }
        // 向中心回拉 + 边界约束
        for (i in 0 until n) {
            xs[i] += (0.5f - xs[i]) * 0.012f
            ys[i] += (0.5f - ys[i]) * 0.012f
            xs[i] = xs[i].coerceIn(0.08f, 0.92f)
            ys[i] = ys[i].coerceIn(0.08f, 0.92f)
        }
    }

    return ids.withIndex().associate { (i, id) -> id to Point(xs[i], ys[i]) }
}
