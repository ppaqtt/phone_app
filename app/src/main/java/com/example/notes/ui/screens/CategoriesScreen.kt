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
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.categories, key = { it.id }) { c ->
                    CategoryRow(
                        category = c,
                        onDelete = { pendingDelete = c }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddCategoryDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, color ->
                // P23: 长度上限 20 字, 防误输入超长字符串
                val safeName = name.trim().take(20)
                if (safeName.isNotBlank()) viewModel.addCategory(safeName, color)
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
                Text(
                    if (noteCount > 0)
                        "删除后, 该分类下的 $noteCount 条笔记将变为「未分类」。"
                    else
                        "确定删除分类「${c.name}」吗?"
                )
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
private fun CategoryRow(category: CategoryEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

@Composable
private fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(0xFF6750A4.toInt()) }
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
            }
        },
        confirmButton = {
            // P74: 显式判断空字符串, 避免 AlertDialog disabled 仍触发 onClick
            TextButton(
                onClick = {
                    val safe = name.trim()
                    if (safe.isBlank()) return@TextButton
                    onConfirm(safe, color)
                },
                enabled = name.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
