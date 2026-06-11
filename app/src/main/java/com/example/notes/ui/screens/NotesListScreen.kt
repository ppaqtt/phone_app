package com.example.notes.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.notes.data.NoteWithCategory
import com.example.notes.ui.components.NoteCard
import com.example.notes.ui.viewmodel.NoteSortOrder
import com.example.notes.ui.viewmodel.NotesViewModel
import com.example.notes.util.AppUpdateChecker
import com.example.notes.util.NoteShareUtil
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri

@Composable
fun NotesListScreen(
    viewModel: NotesViewModel,
    onAddNote: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenStats: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    // P97: SnackBar 宿主, 用于显示删除撤销
    val snackbarHostState = remember { SnackbarHostState() }

    // 当前选中要做动作的笔记 (任意动作菜单弹出时)
    var actionTarget by remember { mutableStateOf<NoteWithCategory?>(null) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    // 防止疯狂点击"删除"导致多次触发数据库删除 (虽然 id 相同是幂等的,
    // 但点击多次会让 UI 闪 / 在某些 Android 版本上触发 recomposition race)
    var deleteInFlight by remember { mutableStateOf(false) }

    fun dismissActions() { actionTarget = null }

    // 删除对话框关闭后,短暂延迟重置防重入锁,确保下次打开仍可点击
    // P66: 改 100ms, 300ms 偏慢导致快速操作时按钮意外置灰
    LaunchedEffect(showDeleteDialog) {
        if (!showDeleteDialog) {
            delay(100)
            deleteInFlight = false
        }
    }

    // P-FIX-001: 冷启动后, 若后台检查发现新版本, 弹出 SnackBar 提示用户。
    // 用户点 "查看" 跳转 GitHub Releases; 点 "稍后" / 滑动关闭则只清标记, 不跳转。
    // 注意: 仅在首屏首次组合时触发, 之后切回前台不会重复弹 (LaunchedEffect key 固定)。
    LaunchedEffect(Unit) {
        val pending = AppUpdateChecker.consumePendingUpdateTip(context)
        if (pending != null) {
            val result = snackbarHostState.showSnackbar(
                message = "发现新版本 v$pending, 点击查看更新内容",
                actionLabel = "查看",
                withDismissAction = true
            )
            when (result) {
                SnackbarResult.ActionPerformed -> {
                    runCatching {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/ppaqtt/phone_app/releases")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                    AppUpdateChecker.clearPendingUpdateTip(context)
                }
                SnackbarResult.Dismissed -> {
                    // 用户滑动关闭 / 自动消失, 标记延后到下次启动再提醒
                }
            }
        }
    }

    Scaffold(
        // P97: 绑定 SnackbarHost, 让删除撤销提示在屏幕底部显示
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("笔记", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "共 ${state.notes.size} 条",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // 3 个最常用入口始终可见
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = onOpenCategories) {
                        Icon(Icons.Filled.Category, contentDescription = "分类")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                    // 标签和排序放入更多菜单,避免窄屏溢出
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("标签管理") },
                                leadingIcon = { Icon(Icons.Filled.Label, contentDescription = "标签") },
                                onClick = {
                                    showMoreMenu = false
                                    onOpenTags()
                                }
                            )
                            // F2: 回收站入口
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("回收站")
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = "回收站") },
                                onClick = {
                                    showMoreMenu = false
                                    onOpenTrash()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("排序方式") },
                                leadingIcon = { Icon(Icons.Filled.Sort, contentDescription = "排序") },
                                onClick = {
                                    showMoreMenu = false
                                    showSortDialog = true
                                }
                            )
                            // F13: 统计入口
                            DropdownMenuItem(
                                text = { Text("统计") },
                                leadingIcon = { Icon(Icons.Filled.QueryStats, contentDescription = "统计") },
                                onClick = {
                                    showMoreMenu = false
                                    onOpenStats()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddNote,
                icon = { Icon(Icons.Filled.Add, contentDescription = "新建笔记") },
                text = { Text("新建笔记") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Category filter chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.activeCategoryId == null,
                        onClick = { viewModel.setCategoryFilter(null) },
                        label = { Text("全部") },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
                items(state.categories, key = { it.id }) { cat ->
                    val indent = computeCategoryIndent(state.categories, cat.id)
                    FilterChip(
                        selected = state.activeCategoryId == cat.id,
                        onClick = {
                            viewModel.setCategoryFilter(if (state.activeCategoryId == cat.id) null else cat.id)
                        },
                        label = {
                            Text(if (indent > 0) "↳ ${cat.name}" else cat.name)
                        }
                    )
                }
            }

            // 加载中: 数据未到时显示进度条, 避免空白闪屏
            AnimatedVisibility(
                visible = state.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            AnimatedVisibility(
                visible = !state.isLoading && state.notes.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                EmptyState(
                    onAdd = onAddNote,
                    modifier = Modifier.fillMaxSize()
                )
            }
            AnimatedVisibility(
                visible = !state.isLoading && state.notes.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.notes, key = { it.note.id }) { nwc ->
                        SwipeableNoteRow(
                            noteWithCategory = nwc,
                            onClick = { onOpenNote(nwc.note.id) },
                            onActionShown = { actionTarget = nwc }
                        )
                    }
                }
            }
        }
    }

    // ============== 动作对话框 ==============
    AnimatedVisibility(
        visible = actionTarget != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        actionTarget?.let { target ->
            NoteActionsRow(
                target = target,
                onDismiss = { dismissActions() },
                onPin = {
                    viewModel.togglePin(target.note.id, !target.note.isPinned)
                    dismissActions()
                },
                onTags = { showTagsDialog = true },
                onDelete = { showDeleteDialog = true },
                onMove = { showMoveDialog = true },
                onPriority = { showPriorityDialog = true },
                onShare = {
                    NoteShareUtil.shareAsText(context, target.note)
                    dismissActions()
                }
            )
        }
    }

    if (showTagsDialog) {
        actionTarget?.let { target ->
            TagsEditDialog(
                initial = target.note.tags,
                onDismiss = { showTagsDialog = false; dismissActions() },
                onConfirm = { newTags ->
                    viewModel.setTags(target.note.id, newTags)
                    showTagsDialog = false; dismissActions()
                }
            )
        }
    }

    if (showPriorityDialog) {
        actionTarget?.let { target ->
            PriorityDialog(
                current = target.note.priority,
                onDismiss = { showPriorityDialog = false; dismissActions() },
                onConfirm = { p ->
                    viewModel.setPriority(target.note.id, p)
                    showPriorityDialog = false; dismissActions()
                }
            )
        }
    }

    if (showMoveDialog) {
        actionTarget?.let { target ->
            MoveCategoryDialog(
                categories = state.categories,
                current = target.note.categoryId,
                onDismiss = { showMoveDialog = false; dismissActions() },
                onConfirm = { catId ->
                    viewModel.moveToCategory(target.note.id, catId)
                    showMoveDialog = false; dismissActions()
                }
            )
        }
    }

    if (showDeleteDialog) {
        actionTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { if (!deleteInFlight) { showDeleteDialog = false; dismissActions() } },
                title = { Text("删除笔记") },
                text = { Text("确认要删除「${target.note.title.ifBlank { "无标题" }}」吗?删除后 5 秒内可撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (deleteInFlight) return@TextButton
                            deleteInFlight = true
                            val title = target.note.title.ifBlank { "无标题" }
                            // P97: 改用 deleteNoteWithUndo, 删除后弹 Snackbar 提供 5 秒内撤销
                            viewModel.deleteNoteWithUndo(target.note.id)
                            showDeleteDialog = false
                            dismissActions()
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "已删除「$title」",
                                    actionLabel = "撤销",
                                    withDismissAction = true,
                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.undoLastDelete()
                                }
                            }
                        },
                        enabled = !deleteInFlight
                    ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { if (!deleteInFlight) { showDeleteDialog = false; dismissActions() } },
                        enabled = !deleteInFlight
                    ) { Text("取消") }
                }
            )
        }
    }

    // 排序对话框
    if (showSortDialog) {
        SortOrderDialog(
            current = state.sortOrder,
            onDismiss = { showSortDialog = false },
            onConfirm = { order ->
                viewModel.setSortOrder(order)
                showSortDialog = false
            }
        )
    }
}

/* ============================================================== */
/* 右滑卡片 — 手写 Draggable 容器 (兼容 material3 1.1.x,            */
/* SwipeToDismissBox 是 1.2.0+ 才有)                              */
/* ============================================================== */
@Composable
private fun SwipeableNoteRow(
    noteWithCategory: NoteWithCategory,
    onClick: () -> Unit,
    onActionShown: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val widthPx = remember { mutableFloatStateOf(0f) }
    val offsetX = remember { Animatable(0f) }
    val threshold = 0.35f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size -> widthPx.floatValue = size.width.toFloat() }
    ) {
        // 背景层: 5 个动作的彩色条 (卡片右滑时露出)
        NoteActionsBackground()
        // 前景层: 卡片 (可水平拖动, 始终跟手)
        // P96: 优化手势冲突 — 用单个 Job 处理 snapTo, 避免每帧 launch
        // (虽然 Animatable 内部已协程化, 重复 launch 仍会浪费调度)。
        val snapJob = remember { kotlinx.coroutines.Job() }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                // P58: 直接改 mutableStateOf, 不用 rememberDraggableState + 每帧 launch。
                // Animatable 在 withFrameNanos 自动驱动, 避免协程风暴。
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val w = widthPx.floatValue
                                if (w > 0f && -offsetX.value > w * threshold) {
                                    onActionShown()
                                }
                                offsetX.animateTo(0f, tween(durationMillis = 220))
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        // P96: 用单一 snapTo 协程, 取消上一次未完成的 snap, 避免积压
                        snapJob.cancel()
                        scope.launch(snapJob) {
                            val target = (offsetX.value + dragAmount)
                                .coerceIn(-widthPx.floatValue, 0f)
                            offsetX.snapTo(target)
                        }
                    }
                }
        ) {
            NoteCard(
                noteWithCategory = noteWithCategory,
                onClick = onClick,
                onPinClick = null
            )
        }
    }
}

/* 右滑时露出的彩色背景条 */
@Composable
private fun NoteActionsBackground() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // P70: 背景补 Share 图标, 与 NoteActionsRow 的 6 项一致
        ActionBgIcon(Icons.Filled.PushPin, "置顶", MaterialTheme.colorScheme.primary)
        ActionBgIcon(Icons.Filled.Label, "标签", Color(0xFF6750A4))
        ActionBgIcon(Icons.Filled.Delete, "删除", MaterialTheme.colorScheme.error)
        ActionBgIcon(Icons.Filled.DriveFileMove, "移动", Color(0xFF2196F3))
        ActionBgIcon(Icons.Filled.Grade, "重要", Color(0xFFFF9800))
        ActionBgIcon(Icons.Filled.Share, "分享", Color(0xFF4CAF50))
    }
}

@Composable
private fun ActionBgIcon(icon: ImageVector, label: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 12.dp)
            .width(46.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/* ============================================================== */
/* 动作菜单 (弹层)                                                   */
/* ============================================================== */
@Composable
private fun NoteActionsRow(
    target: NoteWithCategory,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onTags: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onPriority: () -> Unit,
    onShare: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("笔记操作", maxLines = 1) },
        text = {
            Column {
                ActionMenuItem(
                    icon = if (target.note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    label = if (target.note.isPinned) "取消置顶" else "置顶",
                    onClick = onPin
                )
                ActionMenuItem(
                    icon = Icons.Filled.Label,
                    label = "标签",
                    onClick = onTags
                )
                ActionMenuItem(
                    icon = Icons.Filled.Delete,
                    label = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete
                )
                ActionMenuItem(
                    icon = Icons.Filled.DriveFileMove,
                    label = "移动到分类",
                    onClick = onMove
                )
                ActionMenuItem(
                    icon = if (target.note.priority > 0) Icons.Filled.Star else Icons.Outlined.Star,
                    label = "重要度",
                    onClick = onPriority
                )
                ActionMenuItem(
                    icon = Icons.Filled.Share,
                    label = "分享",
                    onClick = onShare
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/* ============================================================== */
/* 标签编辑对话框                                                    */
/* ============================================================== */
@Composable
private fun TagsEditDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var text by remember {
        mutableStateOf(initial.split(",").filter { it.isNotBlank() }.joinToString(", "))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑标签") },
        text = {
            Column {
                Text(
                    "多个标签用英文逗号分隔, 例如: 工作, 重要, 项目",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("输入标签") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val tags = text.split(",").map { it.trim() }.filter { it.isNotBlank() }
                onConfirm(tags)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/* ============================================================== */
/* 重要度对话框                                                      */
/* ============================================================== */
@Composable
private fun PriorityDialog(
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selected by remember { mutableStateOf(current) }
    val options = listOf(
        Triple(0, Icons.Outlined.Star, "普通"),
        Triple(1, Icons.Filled.Star, "重要"),
        Triple(2, Icons.Filled.Grade, "紧急")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置重要度") },
        text = {
            Column {
                options.forEach { (value, icon, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selected = value }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = if (value == selected) Color(0xFFE6B800)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                        if (value == current) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                "当前",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/* ============================================================== */
/* 移动到分类对话框                                                  */
/* ============================================================== */
@Composable
private fun MoveCategoryDialog(
    categories: List<com.example.notes.data.CategoryEntity>,
    current: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到分类") },
        text = {
            Column {
                // "未分类" 选项
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selected = null }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text("未分类", style = MaterialTheme.typography.bodyLarge)
                    if (current == null) {
                        Spacer(Modifier.weight(1f))
                        Text("当前", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                categories.forEach { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selected = cat.id }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(cat.color))
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(cat.name, style = MaterialTheme.typography.bodyLarge)
                        if (cat.id == current) {
                            Spacer(Modifier.weight(1f))
                            Text("当前", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/* ============================================================== */
/* 排序对话框                                                        */
/* ============================================================== */
@Composable
private fun SortOrderDialog(
    current: NoteSortOrder,
    onDismiss: () -> Unit,
    onConfirm: (NoteSortOrder) -> Unit
) {
    val options = listOf(
        NoteSortOrder.UPDATED_DESC to "更新时间 (新→旧)",
        NoteSortOrder.UPDATED_ASC to "更新时间 (旧→新)",
        NoteSortOrder.CREATED_DESC to "创建时间 (新→旧)",
        NoteSortOrder.CREATED_ASC to "创建时间 (旧→新)",
        NoteSortOrder.TITLE_ASC to "标题 (A→Z)",
        NoteSortOrder.PRIORITY_DESC to "重要度 (高→低)"
    )
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("排序方式") },
        text = {
            Column {
                options.forEach { (order, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selected = order }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (order == current) {
                            Text("当前", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun EmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clickable { onAdd() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text("✎", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("还没有笔记", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "点右下角按钮，写下你的第一条笔记",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * F12: 计算分类在层级树中的深度 (0=顶级, 1=子级, ...)。
 * 沿 parentId 链向上遍历, 循环引用 / 缺失父级用深度上限保护。
 */
private fun computeCategoryIndent(
    all: List<com.example.notes.data.CategoryEntity>,
    id: Long,
    maxDepth: Int = 5
): Int {
    val map = all.associateBy { it.id }
    var depth = 0
    var cur = map[id]?.parentId
    val seen = HashSet<Long>()
    seen.add(id)
    while (cur != null && depth < maxDepth && seen.add(cur)) {
        depth++
        cur = map[cur]?.parentId
    }
    return depth
}
