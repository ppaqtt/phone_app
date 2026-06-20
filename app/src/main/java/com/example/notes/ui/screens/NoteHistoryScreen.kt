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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notes.data.NoteVersionEntity
import com.example.notes.util.TimeFormat
import com.example.notes.util.toastShort
import kotlinx.coroutines.launch

/**
 * 进阶功能: 笔记历史版本列表。
 *
 * 显示某篇笔记的所有历史版本 (按时间倒序), 用户可:
 * - 查看历史快照
 * - 一键恢复 (用历史版本的 title/content 覆盖当前)
 * - 手动删除某个旧版本
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteHistoryScreen(
    noteId: Long,
    viewModel: com.example.notes.ui.viewmodel.NotesViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val versions by viewModel.observeNoteVersions(noteId).collectAsState(initial = emptyList())
    var previewVersion by remember { mutableStateOf<NoteVersionEntity?>(null) }
    var confirmRestore by remember { mutableStateOf<NoteVersionEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<NoteVersionEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史版本", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (versions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(48.dp)
                    )
                    Text(
                        "暂无历史版本",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "笔记内容修改时, 系统会自动保存最近 20 个版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(versions, key = { it.id }) { v ->
                    VersionRow(
                        version = v,
                        onPreview = { previewVersion = v },
                        onRestore = { confirmRestore = v },
                        onDelete = { confirmDelete = v }
                    )
                }
            }
        }
    }

    // 预览对话框
    previewVersion?.let { v ->
        AlertDialog(
            onDismissRequest = { previewVersion = null },
            title = { Text(v.title.ifBlank { "(无标题)" }) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .padding(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "保存于 ${TimeFormat.formatDateTime(v.savedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(text = v.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { previewVersion = null; confirmRestore = v }) {
                    Text("恢复此版本")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewVersion = null }) { Text("关闭") }
            }
        )
    }

    // 恢复确认
    confirmRestore?.let { v ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("恢复此版本?") },
            text = { Text("将覆盖当前笔记内容, 当前内容会作为新版本保存。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.restoreNoteVersion(noteId, v)
                        confirmRestore = null
                        context.toastShort("已恢复")
                    }
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = null }) { Text("取消") }
            }
        )
    }

    // 删除确认
    confirmDelete?.let { v ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除此版本?") },
            text = { Text("此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.deleteNoteVersion(v.id)
                        confirmDelete = null
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun VersionRow(
    version: NoteVersionEntity,
    onPreview: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = TimeFormat.formatDateTime(version.savedAt),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "标题: ${version.title.ifBlank { "(无标题)" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = version.content.take(80) + if (version.content.length > 80) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPreview, modifier = Modifier.weight(1f)) {
                    Text("查看")
                }
                TextButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Restore, contentDescription = null)
                    Spacer(Modifier.padding(2.dp))
                    Text("恢复")
                }
            }
        }
    }
}
