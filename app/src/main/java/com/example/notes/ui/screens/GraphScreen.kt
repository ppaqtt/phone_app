package com.example.notes.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notes.data.NoteEntity
import com.example.notes.ui.viewmodel.NotesViewModel
import com.example.notes.util.WikiLinks
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private data class GraphNode(
    val id: Long,
    val title: String,
    var x: Float = 0f,
    var y: Float = 0f,
    var degree: Int = 0
)

private data class GraphEdge(val from: Int, val to: Int)

private data class GraphLayout(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

/**
 * 双链图谱: 把笔记之间的 `[[标题]]` 引用画成可缩放/平移的关系图。
 *
 * 布局用轻量力导向 (斥力 + 边引力 + 向心), 一次性计算后缓存;
 * 节点大小 = 连接数, 点击节点直接打开对应笔记。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(
    viewModel: NotesViewModel,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit
) {
    val notes by viewModel.allNotes.collectAsState()
    val entities = remember(notes) { notes.map { it.note } }
    val graph = remember(entities) { buildGraph(entities) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("知识图谱", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${graph.nodes.size} 个节点 · ${graph.edges.size} 条双链",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "复位视图")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (graph.nodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "还没有笔记, 先写下第一篇吧",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val labelColor = MaterialTheme.colorScheme.onSurface
            val primary = MaterialTheme.colorScheme.primary
            val outline = MaterialTheme.colorScheme.outlineVariant
            val surface = MaterialTheme.colorScheme.surface
            val textPaint = remember {
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(graph) {
                            detectTapGestures { tap ->
                                val base = minOf(size.width, size.height) * 0.40f
                                var hit: GraphNode? = null
                                var bestDistance = Float.MAX_VALUE
                                graph.nodes.forEach { node ->
                                    val nx = size.width / 2f + node.x * base * scale + offset.x
                                    val ny = size.height / 2f + node.y * base * scale + offset.y
                                    val d = (tap - Offset(nx, ny)).getDistance()
                                    if (d < bestDistance) {
                                        bestDistance = d
                                        hit = node
                                    }
                                }
                                if (hit != null && bestDistance < 40.dp.toPx()) {
                                    onOpenNote(hit!!.id)
                                }
                            }
                        }
                        .pointerInput(graph) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.4f, 3.5f)
                                offset += pan
                            }
                        }
                ) {
                    val base = minOf(size.width, size.height) * 0.40f
                    fun sx(node: GraphNode) = size.width / 2f + node.x * base * scale + offset.x
                    fun sy(node: GraphNode) = size.height / 2f + node.y * base * scale + offset.y

                    // 边
                    graph.edges.forEach { edge ->
                        val a = graph.nodes[edge.from]
                        val b = graph.nodes[edge.to]
                        drawLine(
                            color = primary.copy(alpha = 0.28f),
                            start = Offset(sx(a), sy(a)),
                            end = Offset(sx(b), sy(b)),
                            strokeWidth = 1.2.dp.toPx()
                        )
                    }

                    // 节点
                    graph.nodes.forEach { node ->
                        val x = sx(node)
                        val y = sy(node)
                        val radius = (7f + node.degree * 1.4f).dp.toPx() * scale.coerceIn(0.6f, 1.5f)
                        drawCircle(
                            color = primary.copy(alpha = 0.18f),
                            radius = radius * 1.8f,
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = primary,
                            radius = radius,
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = surface,
                            radius = radius * 0.45f,
                            center = Offset(x, y)
                        )
                        if (scale >= 0.55f) {
                            textPaint.color = labelColor.toArgb()
                            textPaint.textSize = 10.sp.toPx() * scale.coerceIn(0.8f, 1.4f)
                            val label = node.title.take(12)
                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                x,
                                y - radius - 4.dp.toPx(),
                                textPaint
                            )
                        }
                    }

                    // 外框 (提示可交互区域)
                    drawCircle(
                        color = outline.copy(alpha = 0.35f),
                        radius = base * scale,
                        center = Offset(size.width / 2f + offset.x, size.height / 2f + offset.y),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                Text(
                    text = "双指缩放 · 拖动平移 · 点击节点打开笔记\n在笔记中使用 [[标题]] 建立双链",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}

/** 一次性力导向布局 (坐标归一化到 [-1, 1]) */
private fun buildGraph(notes: List<NoteEntity>): GraphLayout {
    if (notes.isEmpty()) return GraphLayout(emptyList(), emptyList())

    val nodes = notes.map { note ->
        GraphNode(
            id = note.id,
            title = note.title.ifBlank {
                note.content.lineSequence().firstOrNull().orEmpty().take(20).ifBlank { "无标题" }
            }
        )
    }
    val titleToIndex = HashMap<String, Int>()
    notes.forEachIndexed { index, note ->
        val key = note.title.trim().lowercase(Locale.getDefault())
        if (key.isNotEmpty()) titleToIndex[key] = index
    }

    val edges = ArrayList<GraphEdge>()
    val seen = HashSet<Long>()
    notes.forEachIndexed { index, note ->
        WikiLinks.extract(note.content).forEach { target ->
            val targetIndex = titleToIndex[target.trim().lowercase(Locale.getDefault())]
            if (targetIndex != null && targetIndex != index) {
                val key = index.toLong() * 1_000_000L + targetIndex
                if (seen.add(key)) {
                    edges.add(GraphEdge(index, targetIndex))
                    nodes[index].degree += 1
                    nodes[targetIndex].degree += 1
                }
            }
        }
    }

    // 初始: 圆环排布
    val count = nodes.size
    nodes.forEachIndexed { index, node ->
        val angle = 2.0 * Math.PI * index / count
        node.x = cos(angle).toFloat() * 0.8f
        node.y = sin(angle).toFloat() * 0.8f
    }

    // 迭代: 斥力 + 边引力 + 向心收缩
    val iterations = if (count > 80) 50 else 110
    repeat(iterations) {
        for (a in 0 until count) {
            for (b in a + 1 until count) {
                val na = nodes[a]
                val nb = nodes[b]
                val dx = na.x - nb.x
                val dy = na.y - nb.y
                val dist2 = dx * dx + dy * dy + 0.0008f
                val dist = sqrt(dist2)
                val force = 0.010f / dist2
                val fx = dx / dist * force
                val fy = dy / dist * force
                na.x += fx
                na.y += fy
                nb.x -= fx
                nb.y -= fy
            }
        }
        edges.forEach { edge ->
            val a = nodes[edge.from]
            val b = nodes[edge.to]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val dist = sqrt(dx * dx + dy * dy + 0.0008f)
            val force = dist * 0.035f
            val fx = dx / dist * force
            val fy = dy / dist * force
            a.x += fx
            a.y += fy
            b.x -= fx
            b.y -= fy
        }
        nodes.forEach { node ->
            node.x = (node.x * 0.993f).coerceIn(-1f, 1f)
            node.y = (node.y * 0.993f).coerceIn(-1f, 1f)
        }
    }
    return GraphLayout(nodes, edges)
}
