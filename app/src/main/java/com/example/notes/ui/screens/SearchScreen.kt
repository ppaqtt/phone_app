package com.example.notes.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import com.example.notes.ui.components.NoteCard
import com.example.notes.ui.viewmodel.NotesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: NotesViewModel,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val searchHistory by viewModel.observeSearchHistory(limit = 10).collectAsState(initial = emptyList())
    var lastRecordedQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun triggerSearch(q: String) {
        val trimmed = q.trim()
        if (trimmed.isNotBlank() && trimmed != lastRecordedQuery) {
            viewModel.recordSearch(trimmed)
            lastRecordedQuery = trimmed
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
                        leadingIcon = {
                            IconButton(onClick = { triggerSearch(state.query) }) {
                                Icon(Icons.Filled.Search, contentDescription = "搜索")
                            }
                        },
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
                            .onKeyEvent { event ->
                                if (event.key == Key.Enter) {
                                    triggerSearch(state.query)
                                    true
                                } else false
                            }
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
            // 分类过滤 Chips
            if (state.query.isNotBlank() && state.categories.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.activeCategoryId == null,
                        onClick = { viewModel.setCategoryFilter(null) },
                        label = { Text("全部") }
                    )
                    state.categories.forEach { cat ->
                        FilterChip(
                            selected = state.activeCategoryId == cat.id,
                            onClick = { viewModel.setCategoryFilter(cat.id) },
                            label = { Text(cat.name) }
                        )
                    }
                }
            }
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
                            TextButton(onClick = { viewModel.clearSearchHistory() }) {
                                Text("清空", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            searchHistory.forEach { item ->
                                HistoryChip(
                                    query = item.query,
                                    onClick = {
                                        viewModel.setQuery(item.query)
                                        triggerSearch(item.query)
                                    }
                                )
                            }
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
                    state = rememberLazyListState(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.notes, key = { it.note.id }) { nwc ->
                        NoteCard(
                            noteWithCategory = nwc,
                            onClick = { onOpenNote(nwc.note.id) },
                            highlightQuery = state.query
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryChip(
    query: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        leadingIcon = {
            Icon(
                Icons.Filled.History,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        label = { Text(text = query, maxLines = 1) }
    )
}
