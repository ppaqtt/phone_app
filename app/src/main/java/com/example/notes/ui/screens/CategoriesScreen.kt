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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.notes.data.CategoryEntity
import com.example.notes.ui.viewmodel.NotesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: NotesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分类管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新增分类")
            }
        }
    ) { padding ->
        if (state.categories.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有分类,点右下角新增", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // F12: 按层级缩进渲染。children 始终跟在 parent 之后, 缩进 16dp × level。
            val rows = remember(state.categories) { flattenForDisplay(state.categories) }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(rows, key = { it.category.id }) { row ->
                    CategoryRow(
                        category = row.category,
                        level = row.level,
                        onDelete = { pendingDelete = row.category }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddCategoryDialog(
            allCategories = state.categories,
            excludeId = null,
            initialParentId = null,
            onDismiss = { showAdd = false },
            onConfirm = { name, color, parentId ->
                // P23: 长度上限 20 字, 防误输入超长字符串
                val safeName = name.trim().take(20)
                if (safeName.isNotBlank()) viewModel.addCategory(safeName, color, parentId)
                showAdd = false
            }
        )
    }

    pendingDelete?.let { c ->
        // P61: 用 Flow 直接拿 DB 统计, 不再 O(n*m) 内存过滤
        val noteCount by viewModel.noteCountForCategoryFlow(c.id).collectAsState(initial = 0)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除分类") },
            text = {
                val childCount = state.categories.count { it.parentId == c.id }
                val text = buildString {
                    if (noteCount > 0) append("删除后, 该分类下的 $noteCount 条笔记将变为「未分类」。")
                    else append("确定删除分类「${c.name}」吗?")
                    if (childCount > 0) append("\n该分类下还有 $childCount 个子分类, 删除后子分类将提升为顶级。")
                }
                Text(text)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(c); pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun CategoryRow(category: CategoryEntity, level: Int, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = PaddingValues16_14.paddingWithIndent(level),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (level > 0) {
                Icon(
                    Icons.Filled.SubdirectoryArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(4.dp))
            }
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(category.color))
            )
            Spacer(Modifier.size(12.dp))
            Text(
                category.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// 用来在 Row 上同时处理"缩进 + 内边距" — 避免在每个调用点重复 padding(horizontal=16, vertical=14) 模板
private object PaddingValues16_14 {
    val base = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp)

    @Composable
    fun Modifier.paddingWithIndent(level: Int): Modifier = this
        .padding(start = (16 + level * 20).dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
}

@Composable
private fun AddCategoryDialog(
    allCategories: List<CategoryEntity>,
    excludeId: Long?,
    initialParentId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Long?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(0xFF6750A4.toInt()) }
    var parentId by remember { mutableStateOf(initialParentId) }
    val palette = listOf(
        0xFF6750A4.toInt(), 0xFFE91E63.toInt(), 0xFFFF9800.toInt(),
        0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFF9C27B0.toInt()
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增分类") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(12.dp))
                Text("颜色", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(palette) { c ->
                        val selected = c == color
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable { color = c }
                        ) {
                            if (selected) {
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.Black.copy(alpha = 0.15f))
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
                // F12: 父分类下拉 (仅显示顶级, 防止一级嵌套)
                Text("父分类 (可选)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(6.dp))
                ParentCategoryPicker(
                    allCategories = allCategories,
                    excludeId = excludeId,
                    selected = parentId,
                    onSelect = { parentId = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val safe = name.trim()
                    if (safe.isBlank()) return@TextButton
                    onConfirm(safe, color, parentId)
                },
                enabled = name.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * F12: 父分类选择 — 一行 chip 列表, "无" 代表顶级。
 * 排除自身 (防止循环引用) 以及所有 descendant (避免形成 A→B→A 死循环)。
 */
@Composable
private fun ParentCategoryPicker(
    allCategories: List<CategoryEntity>,
    excludeId: Long?,
    selected: Long?,
    onSelect: (Long?) -> Unit
) {
    val descendants = remember(allCategories, excludeId) {
        if (excludeId == null) emptySet()
        else collectDescendants(allCategories, excludeId)
    }
    val candidates = allCategories.filter {
        it.id != excludeId && it.id !in descendants
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            ParentChip(
                label = "无 (顶级)",
                color = MaterialTheme.colorScheme.outline,
                isSelected = selected == null,
                onClick = { onSelect(null) }
            )
        }
        items(candidates, key = { it.id }) { cat ->
            ParentChip(
                label = cat.name,
                color = Color(cat.color),
                isSelected = selected == cat.id,
                onClick = { onSelect(cat.id) }
            )
        }
    }
}

@Composable
private fun ParentChip(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) color.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.size(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * F12: 把分类按"父→子"层级排好, 返回 (CategoryEntity, level) 列表。
 *
 * 实现要点:
 * 1) 单层缩进, 不递归展示多层 (UI 视觉简洁)。F12 设计决定: 只支持 1 级嵌套,
 *    父分类的 parentId 必须为 null (UI 创建/编辑时已过滤), 避免 stack overflow。
 * 2) 用 BFS 而不是 DFS, 保证同一层级的兄弟按 created_at 出现顺序排列。
 * 3) 防御性: 父分类被删除后子分类的 parentId 已被清空 (Repository.deleteCategorySafely),
 *    故不会出现"父不存在"的孤立子节点。
 */
private fun flattenForDisplay(all: List<CategoryEntity>): List<CategoryRow> {
    val byParent = all.groupBy { it.parentId }
    val roots = byParent[null].orEmpty()
    val result = ArrayList<CategoryRow>(all.size)
    fun walk(items: List<CategoryEntity>, level: Int) {
        items.forEach { c ->
            result.add(CategoryRow(c, level))
            byParent[c.id]?.let { walk(it, level + 1) }
        }
    }
    walk(roots, 0)
    return result
}

private data class CategoryRow(val category: CategoryEntity, val level: Int)

/**
 * 收集 id 的所有后代 (含直接 / 间接子节点), 用于父分类候选过滤。
 * 用 BFS, 避免递归过深。
 */
private fun collectDescendants(all: List<CategoryEntity>, id: Long): Set<Long> {
    val result = HashSet<Long>()
    val queue = ArrayDeque<Long>()
    queue.addLast(id)
    val byParent = all.groupBy { it.parentId }
    while (queue.isNotEmpty()) {
        val cur = queue.removeFirst()
        val children = byParent[cur].orEmpty().map { it.id }
        for (c in children) {
            if (result.add(c)) queue.addLast(c)
        }
    }
    return result
}
