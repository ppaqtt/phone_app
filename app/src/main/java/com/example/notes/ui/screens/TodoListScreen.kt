package com.example.notes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.notes.NotesApplication
import com.example.notes.data.TodoEntity
import com.example.notes.util.TodoReminderManager
import com.example.notes.util.toastShort
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NotesApplication
    val repository = app.todoRepository
    val scope = rememberCoroutineScope()

    val allTodos by repository.observeAll().collectAsState(initial = emptyList())
    val activeTodos = allTodos.filter { !it.isCompleted }
    val completedTodos = allTodos.filter { it.isCompleted }

    var showEditDialog by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<TodoEntity?>(null) }
    var showCompletedSection by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("待办任务") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (completedTodos.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                repository.clearCompleted()
                                context.toastShort("已清除已完成项")
                            }
                        }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "清除已完成")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTodo = null
                    showEditDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建待办")
            }
        }
    ) { padding ->
        if (allTodos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "暂无待办事项",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "点击 + 添加待办",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                // 未完成区域
                if (activeTodos.isNotEmpty()) {
                    item {
                        Text(
                            "待完成 (${activeTodos.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(
                        items = activeTodos,
                        key = { it.id }
                    ) { todo ->
                        TodoItem(
                            todo = todo,
                            onToggle = {
                                scope.launch {
                                    repository.toggleCompleted(todo.id)
                                    if (!todo.isCompleted) {
                                        TodoReminderManager.cancelReminder(context, todo.id)
                                    } else if (todo.reminderTime != null) {
                                        val updated = todo.copy(isCompleted = false)
                                        TodoReminderManager.scheduleReminder(context, updated)
                                    }
                                }
                            },
                            onClick = {
                                editingTodo = todo
                                showEditDialog = true
                            },
                            onDelete = {
                                scope.launch {
                                    repository.deleteById(todo.id)
                                    TodoReminderManager.cancelReminder(context, todo.id)
                                }
                            }
                        )
                    }
                }

                // 已完成区域
                if (completedTodos.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "已完成 (${completedTodos.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (showCompletedSection) "收起" else "展开",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showCompletedSection = !showCompletedSection }
                            )
                        }
                    }
                    if (showCompletedSection) {
                        items(
                            items = completedTodos,
                            key = { it.id }
                        ) { todo ->
                            TodoItem(
                                todo = todo,
                                onToggle = {
                                    scope.launch {
                                        repository.toggleCompleted(todo.id)
                                    }
                                },
                                onClick = {
                                    editingTodo = todo
                                    showEditDialog = true
                                },
                                onDelete = {
                                    scope.launch {
                                        repository.deleteById(todo.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 编辑对话框
    if (showEditDialog) {
        TodoEditDialog(
            todo = editingTodo,
            onDismiss = {
                showEditDialog = false
                editingTodo = null
            },
            onSave = { todo ->
                scope.launch {
                    repository.save(todo)
                    if (todo.reminderTime != null && !todo.isCompleted) {
                        TodoReminderManager.scheduleReminder(context, todo)
                    } else {
                        TodoReminderManager.cancelReminder(context, todo.id)
                    }
                    showEditDialog = false
                    editingTodo = null
                }
            },
            onDelete = if (editingTodo != null) { todo ->
                scope.launch {
                    repository.deleteById(todo.id)
                    TodoReminderManager.cancelReminder(context, todo.id)
                    showEditDialog = false
                    editingTodo = null
                }
            } else null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoItem(
    todo: TodoEntity,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 完成按钮
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (todo.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (todo.isCompleted) "标记未完成" else "标记完成",
                    tint = if (todo.isCompleted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                // 标题
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null,
                    color = if (todo.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // 内容
                if (todo.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = todo.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 时间和优先级
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 优先级
                    val priorityColor = when (todo.priority) {
                        1 -> Color(0xFFFF9800)
                        2 -> Color(0xFFF44336)
                        else -> Color.Gray
                    }
                    if (todo.priority > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = priorityColor
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (todo.priority == 1) "重要" else "紧急",
                                style = MaterialTheme.typography.labelSmall,
                                color = priorityColor
                            )
                        }
                    }

                    // 提醒时间
                    if (todo.reminderTime != null) {
                        val isOverdue = todo.reminderTime < System.currentTimeMillis() && !todo.isCompleted
                        Text(
                            text = dateFormat.format(Date(todo.reminderTime)),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverdue) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 删除按钮（替换滑动删除）
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
