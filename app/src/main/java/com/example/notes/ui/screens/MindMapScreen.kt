package com.example.notes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notes.util.MindMapNode
import com.example.notes.util.MindMapParser

/**
 * 进阶功能: 思维导图视图。
 *
 * 把 Markdown 笔记的层级标题渲染成可折叠的树形结构, 类似思维导图。
 * - 中心节点是 root (笔记标题)
 * - 二级节点为标题
 * - 三级及以下缩进显示
 * - 点击节点可展开/折叠
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindMapScreen(
    title: String,
    content: String,
    onClose: () -> Unit
) {
    val root = remember(content) { MindMapParser.parse(content).also { it.title = title.ifBlank { it.title } } }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("思维导图", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            MindMapNodeView(
                node = root,
                depth = 0,
                expanded = expanded,
                isRoot = true
            )
        }
    }
}

@Composable
private fun MindMapNodeView(
    node: MindMapNode,
    depth: Int,
    expanded: MutableMap<String, Boolean>,
    isRoot: Boolean = false
) {
    val key = "${node.title}_${node.level}_${node.totalSize}"
    val isExpanded = expanded[key] ?: true
    val hasChildren = node.children.isNotEmpty()

    // 节点颜色: 按深度渐变
    val nodeColor = when (depth) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.tertiary
        2 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (depth <= 1) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 折叠/展开按钮
        if (hasChildren) {
            IconButton(
                onClick = { expanded[key] = !isExpanded },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "折叠" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(Modifier.width(24.dp))
        }

        // 节点圆点
        Box(
            modifier = Modifier
                .size(if (isRoot) 14.dp else 10.dp)
                .clip(CircleShape)
                .background(nodeColor)
        )

        Spacer(Modifier.width(8.dp))

        // 节点文字
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(if (isRoot) 12.dp else 6.dp))
                .background(nodeColor.copy(alpha = if (isRoot) 1f else 0.15f))
                .clickable { if (hasChildren) expanded[key] = !isExpanded }
                .padding(
                    horizontal = if (isRoot) 16.dp else 10.dp,
                    vertical = if (isRoot) 8.dp else 4.dp
                )
        ) {
            Text(
                text = node.title,
                color = if (isRoot) textColor else nodeColor,
                fontWeight = if (isRoot || depth == 1) FontWeight.SemiBold else FontWeight.Normal,
                style = if (isRoot) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (isExpanded) {
        node.children.forEach { child ->
            MindMapNodeView(node = child, depth = depth + 1, expanded = expanded)
        }
    }
}
