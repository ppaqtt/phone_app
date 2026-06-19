package com.example.notes.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Deselect
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.notes.data.NoteWithCategory
import com.example.notes.ui.components.NoteCard
import com.example.notes.ui.viewmodel.NoteSortOrder
import com.example.notes.ui.viewmodel.NotesViewModel
import com.example.notes.util.AppUpdateChecker
import com.example.notes.util.NoteShareUtil
import com.example.notes.util.toastShort
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun NotesListScreen(
    viewModel: NotesViewModel,
    onAddNote: () -> Unit,
    onCreateFromTemplate: (Int) -> Unit = {},
    onOpenNote: (Long) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenTodos: () -> Unit
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
    // 进阶功能: 多选模式与已选笔记 id 集合
    var multiSelectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    // 防止疯狂点击"删除"导致多次触发数据库删除 (虽然 id 相同是幂等的,
    // 但点击多次会让 UI 闪 / 在某些 Android 版本上触发 recomposition race)
    var deleteInFlight by remember { mutableStateOf(false) }

    // 进阶功能: 批量导出 ZIP - SAF 创建文档启动器
    val createZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null && selectedIds.isNotEmpty()) {
            val ids = selectedIds.toList()
            scope.launch {
                val count = viewModel.exportNotesAsZip(context, ids, uri)
                context.toastShort("已导出 $count 篇笔记到 ZIP")
            }
        }
    }

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
            if (multiSelectMode) {
                // 进阶功能: 多选模式顶栏
                TopAppBar(
                    title = { Text("已选 ${selectedIds.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = {
                            multiSelectMode = false
                            selectedIds.clear()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "退出多选")
                        }
                    },
                    actions = {
                        IconButton(onClick = { selectedIds.clear() }) {
                            Icon(Icons.Filled.Deselect, contentDescription = "全不选")
                        }
                        // 进阶功能: 批量导出 ZIP
                        IconButton(
                            onClick = {
                                if (selectedIds.isEmpty()) {
                                    context.toastShort("请先选择要导出的笔记")
                                } else {
                                    val fileName = "notes_export_${System.currentTimeMillis()}.zip"
                                    createZipLauncher.launch(fileName)
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Archive, contentDescription = "批量导出 ZIP")
                        }
                        // 进阶功能: 批量删除
                        IconButton(onClick = {
                            if (selectedIds.isEmpty()) {
                                context.toastShort("请先选择要删除的笔记")
                            } else {
                                showDeleteDialog = true
                            }
                        }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "批量删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            } else {
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
                            // 待办任务入口
                            DropdownMenuItem(
                                text = { Text("待办任务") },
                                leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = "待办") },
                                onClick = {
                                    showMoreMenu = false
                                    onOpenTodos()
                                }
                            )
                            // 进阶功能: 多选模式
                            DropdownMenuItem(
                                text = { Text("多选模式") },
                                leadingIcon = { Icon(Icons.Filled.Checklist, contentDescription = "多选") },
                                onClick = {
                                    showMoreMenu = false
                                    multiSelectMode = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
            }
        },
        floatingActionButton = {
            var fabExpanded by remember { mutableStateOf(false) }
            Column(horizontalAlignment = Alignment.End) {
                if (fabExpanded) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            fabExpanded = false
                            onAddNote()
                        },
                        text = { Text("空白笔记") },
                        icon = { Icon(Icons.Filled.NoteAdd, contentDescription = null) },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    com.example.notes.util.NoteTemplates.all.forEach { tmpl ->
                        ExtendedFloatingActionButton(
                            onClick = {
                                fabExpanded = false
                                onCreateFromTemplate(tmpl.type)
                            },
                            text = { Text(tmpl.name) },
                            icon = { Icon(Icons.Filled.Description, contentDescription = null) },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = {
                        if (fabExpanded) {
                            fabExpanded = false
                        } else {
                            fabExpanded = true
                        }
                    },
                    icon = {
                        Icon(Icons.Filled.Add, contentDescription = "新建笔记")
                    },
                    text = { Text(if (fabExpanded) "选择模板" else "新建笔记") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 8.dp
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }
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
                        selected = state.activeCategoryId == null && !state.showOnlyFavorites,
                        onClick = {
                            viewModel.setCategoryFilter(null)
                            viewModel.setShowOnlyFavorites(false)
                        },
                        label = { Text("全部") },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
                item {
                    // 高价值/低工作量: 只显示星标笔记
                    FilterChip(
                        selected = state.showOnlyFavorites,
                        onClick = {
                            viewModel.setShowOnlyFavorites(!state.showOnlyFavorites)
                        },
                        label = { Text("星标") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Grade,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
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
                        // 改用"卡片右侧三个点"二级入口, 替代之前难触发的右滑手势
                        NoteCard(
                            noteWithCategory = nwc,
                            onClick = {
                                if (multiSelectMode) {
                                    // 进阶功能: 多选模式 - 点击切换选中
                                    if (selectedIds.contains(nwc.note.id)) {
                                        selectedIds.remove(nwc.note.id)
                                    } else {
                                        selectedIds.add(nwc.note.id)
                                    }
                                } else {
                                    onOpenNote(nwc.note.id)
                                }
                            },
                            onLongClick = {
                                // 进阶功能: 长按进入多选模式
                                if (!multiSelectMode) {
                                    multiSelectMode = true
                                }
                                if (!selectedIds.contains(nwc.note.id)) {
                                    selectedIds.add(nwc.note.id)
                                }
                            },
                            onPinClick = null,
                            onMoreClick = { actionTarget = nwc }
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
                },
                onFavorite = {
                    viewModel.setFavorite(target.note.id, !target.note.isFavorite)
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
        // 进阶功能: 多选模式批量删除走另一条分支
        if (multiSelectMode) {
            AlertDialog(
                onDismissRequest = { if (!deleteInFlight) { showDeleteDialog = false } },
                title = { Text("批量删除") },
                text = { Text("确认要删除已选的 ${selectedIds.size} 篇笔记吗?删除后 5 秒内可撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (deleteInFlight) return@TextButton
                            deleteInFlight = true
                            val ids = selectedIds.toList()
                            val count = ids.size
                            // 逐个调用 (deleteNoteWithUndo 走的是 ViewModel, 内部入栈可撤销)
                            ids.forEach { viewModel.deleteNoteWithUndo(it) }
                            showDeleteDialog = false
                            selectedIds.clear()
                            multiSelectMode = false
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "已删除 $count 篇笔记",
                                    actionLabel = "撤销",
                                    withDismissAction = true,
                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    // 撤销最近一次即可 (因为我们逐个入栈, 撤销只恢复最后一条)
                                    viewModel.undoLastDelete()
                                }
                            }
                        },
                        enabled = !deleteInFlight
                    ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { if (!deleteInFlight) { showDeleteDialog = false } },
                        enabled = !deleteInFlight
                    ) { Text("取消") }
                }
            )
        } else actionTarget?.let { target ->
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
    onShare: () -> Unit,
    onFavorite: () -> Unit
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
                    icon = if (target.note.isFavorite) Icons.Filled.Grade else Icons.Outlined.Star,
                    label = if (target.note.isFavorite) "取消星标" else "加星标",
                    onClick = onFavorite
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
        NoteSortOrder.PRIORITY_DESC to "重要度 (高→低)",
        NoteSortOrder.CONTENT_LENGTH_DESC to "字数 (多→少)",
        NoteSortOrder.CONTENT_LENGTH_ASC to "字数 (少→多)"
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
