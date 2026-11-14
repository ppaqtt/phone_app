package com.example.notes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
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
import com.example.notes.data.DEFAULT_COLOR
import com.example.notes.ui.viewmodel.NotesViewModel
import com.example.notes.util.NoteTemplate
import com.example.notes.util.TemplateLibrary
import com.example.notes.util.toastShort
import kotlinx.coroutines.launch

/**
 * 模板库: 10 种内置模板, 选中后可先预览再一键生成笔记。
 * 生成后直接跳到编辑页, 用户可以继续补充内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateGalleryScreen(
    viewModel: NotesViewModel,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val templates = remember { TemplateLibrary.all }
    var preview by remember { mutableStateOf<NoteTemplate?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "模板库",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "共 ${templates.size} 种 · 点击预览",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(templates, key = { it.id }) { template ->
                Card(
                    onClick = { preview = template },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = template.emoji,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = template.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = template.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "预览",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    preview?.let { template ->
        AlertDialog(
            onDismissRequest = { if (!creating) preview = null },
            icon = { Icon(Icons.Filled.Description, contentDescription = null) },
            title = { Text("${template.emoji} ${template.name}") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        template.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        template.content,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !creating,
                    onClick = {
                        if (creating) return@TextButton
                        creating = true
                        scope.launch {
                            runCatching {
                                viewModel.saveNote(
                                    context = context,
                                    id = 0L,
                                    title = template.title,
                                    content = template.content,
                                    categoryId = null,
                                    tags = template.tags
                                        .split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() },
                                    isPinned = false,
                                    color = DEFAULT_COLOR
                                )
                            }.onSuccess { id ->
                                preview = null
                                creating = false
                                onOpenNote(id)
                            }.onFailure {
                                creating = false
                                context.toastShort("创建失败: ${it.message}")
                            }
                        }
                    }
                ) { Text(if (creating) "创建中…" else "使用模板") }
            },
            dismissButton = {
                TextButton(onClick = { preview = null }, enabled = !creating) {
                    Text("取消")
                }
            }
        )
    }
}
