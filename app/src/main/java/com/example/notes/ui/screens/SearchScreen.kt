package com.example.notes.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.notes.ui.components.NoteCard
import com.example.notes.ui.viewmodel.NotesViewModel
import com.example.notes.util.SearchHistoryManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: NotesViewModel,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val historyManager = remember { SearchHistoryManager.getInstance(context) }
    val searchHistory by historyManager.history.collectAsState()
    // P29: 记录最后一次手动入栈的 query, 避免历史项被点击后再次入栈
    var lastRecordedQuery by remember { mutableStateOf("") }

    // P30: 页面打开时自动聚焦到搜索框
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // 当用户输入并搜索时，保存到历史
    // P18: trim 后再入历史, 避免 "  test  " 和 "test" 被视为两条
    // P29: 跳过由历史项触发的 query 变化
    LaunchedEffect(state.query) {
        val q = state.query.trim()
        if (q.isNotBlank() && state.notes.isNotEmpty() && q != lastRecordedQuery) {
            // 只有 query 不在历史里时, 才 addSearch 避免重复入栈
            if (q !in searchHistory) {
                historyManager.addSearch(q)
                lastRecordedQuery = q
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { viewModel.setQuery(it) },
                        placeholder = { Text("搜索笔记内容、标题、标签") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "搜索") },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "清空")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.query.isBlank()) {
                // 显示搜索历史
                if (searchHistory.isNotEmpty()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "搜索历史",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { historyManager.clearHistory() }) {
                                Text("清空", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        searchHistory.forEach { query ->
                            HistoryItem(
                                query = query,
                                onClick = { viewModel.setQuery(query) },
                                onDelete = { historyManager.removeSearch(query) }
                            )
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "试试搜索标题、内容或标签",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (state.notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有找到匹配的笔记", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    state = rememberLazyListState(),  // P101: 自动 saveable, 旋转后保留滚动位置
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.notes, key = { it.note.id }) { nwc ->
                        NoteCard(
                            noteWithCategory = nwc,
                            onClick = { onOpenNote(nwc.note.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    query: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = "搜索历史",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(12.dp))
        Text(
            query,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
