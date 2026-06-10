package com.example.notes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.notes.data.NoteWithCategory
import com.example.notes.ui.viewmodel.NotesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * F2: 回收站页面。
 *
 * 显示 30 天内的已删除笔记。每条 2 个动作:
 * - 恢复: 把 deleted_at 置 NULL
 * - 永久删除: 物理删除 (级联图片)
 *
 * 顶栏右侧 "清空回收站" 二次确认后真删全部。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: NotesViewModel,
    onBack: () -> Unit
) {
    val trash by viewModel.observeTrash().collectAsState(initial = emptyList())
    var showEmptyConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("回收站", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "共 ${trash.size} 条 · 30 天后自动清理",
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
                actions = {
                    if (trash.isNotEmpty()) {
                        TextButton(onClick = { showEmptyConfirm = true }) {
                            Text("清空", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            if (trash.isEmpty()) {
                EmptyTrashState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(trash, key = { it.note.id }) { item ->
                        TrashItemCard(
                            item = item,
                            onRestore = { viewModel.restoreFromTrash(item.note.id) },
                            onDeleteForever = { viewModel.permanentlyDeleteTrashed(item.note.id) }
                        )
                    }
                }
            }
        }
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text("清空回收站?") },
            text = { Text("回收站中的 ${trash.size} 条笔记将被永久删除, 此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.emptyTrash()
                    showEmptyConfirm = false
                }) {
                    Text("永久删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TrashItemCard(
    item: NoteWithCategory,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    val daysAgo = item.note.deletedAt?.let { deletedAt ->
        val diffMs = System.currentTimeMillis() - deletedAt
        val days = TimeUnit.MILLISECONDS.toDays(diffMs)
        when {
            days <= 0 -> "今天删除"
            days == 1L -> "1 天前删除"
            else -> "$days 天前删除"
        }
    } ?: "未知"
    val exactTime = item.note.deletedAt?.let {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
    } ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.note.title.ifBlank { "(无标题)" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.note.content.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.note.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$daysAgo · $exactTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            // 恢复
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Filled.Restore,
                    contentDescription = "恢复",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // 永久删除
            IconButton(onClick = onDeleteForever) {
                Icon(
                    Icons.Filled.DeleteForever,
                    contentDescription = "永久删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EmptyTrashState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.DeleteForever,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "回收站是空的",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "删除的笔记会在这里保留 30 天",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
