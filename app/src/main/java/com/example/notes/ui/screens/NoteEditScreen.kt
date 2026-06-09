package com.example.notes.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.notes.data.NoteEntity
import com.example.notes.repository.NotesRepository
import com.example.notes.ui.theme.NoteSwatches
import com.example.notes.ui.viewmodel.NotesViewModel
import com.example.notes.util.ImageUtils
import com.example.notes.util.ReminderManager
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 底部工具栏当前选中的工具 (AI 已移除) */
private enum class BottomTool { COLUMNS, TEXT, LIST, TODO, IMAGE, MORE }

/** 4 个文本样式子页签 */
private enum class ColumnsTab(val label: String) {
    TEXT_STYLE("文字样式"), SYMBOLS("符号"), DIVIDERS("分割线"), TEMPLATES("图文模版")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    noteId: Long,
    viewModel: NotesViewModel,
    repository: NotesRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val isNew = noteId <= 0L

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(NoteSwatches.first()) }
    var isPinned by remember { mutableStateOf(false) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    // 多图
    val imageUris: SnapshotStateList<String> = remember { mutableStateListOf() }
    // 音频 (作为 URI 列表插入正文, 显示为可点击条目)
    val audioUris: SnapshotStateList<String> = remember { mutableStateListOf() }
    var reminderTime by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(isNew) }
    var lastSaved by remember { mutableStateOf<NoteEntity?>(null) }
    var selectedTool by remember { mutableStateOf<BottomTool?>(null) }
    var showDoodle by remember { mutableStateOf(false) }
    var showTableDialog by remember { mutableStateOf(false) }

    // === 撤销/重做 ===
    val undoRedo = remember { UndoRedoState<NoteSnapshot>(maxDepth = 80) }
    // 启动时记一次基线
    LaunchedEffect(Unit) { undoRedo.record(NoteSnapshot(title, content)) }
    val canUndo by remember { derivedStateOf { undoRedo.canUndo } }
    val canRedo by remember { derivedStateOf { undoRedo.canRedo } }
    fun pushHistory() { undoRedo.record(NoteSnapshot(title, content)) }

    // === 多图选择器 ===
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                imageUris.add(uri.toString())
            }
        }
        selectedTool = null
    }

    // === 拍照 ===
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            pendingCameraUri?.let { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                imageUris.add(uri.toString())
            }
        }
        pendingCameraUri = null
        selectedTool = null
    }
    val requestCamera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            val file = ImageUtils.createImageFile(context)
            val uri = ImageUtils.getUriForFile(context, file)
            pendingCameraUri = uri
            takePicture.launch(uri)
        }
    }

    // === 音频选择 ===
    val pickAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            audioUris.add(uri.toString())
            val marker = "\n🎵 [音频](${uri})\n"
            content = if (content.isEmpty()) marker.trimStart() else content + marker
            pushHistory()
        }
        selectedTool = null
    }

    val calendar = Calendar.getInstance()
    fun showDateTimePicker() {
        val datePicker = DatePickerDialog(
            context,
            { _, year, month, day ->
                calendar.set(year, month, day)
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        reminderTime = calendar.timeInMillis
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.datePicker.minDate = System.currentTimeMillis()
        datePicker.show()
    }

    // 加载已有笔记
    LaunchedEffect(noteId) {
        if (noteId > 0L) {
            repository.observeNote(noteId).collectLatest { nwc ->
                if (!loaded && nwc != null) {
                    title = nwc.note.title
                    content = nwc.note.content
                    color = Color(nwc.note.color)
                    isPinned = nwc.note.isPinned
                    categoryId = nwc.note.categoryId
                    reminderTime = nwc.note.reminderTime
                    lastSaved = nwc.note
                    loaded = true
                    undoRedo.clear()
                    undoRedo.record(NoteSnapshot(title, content))
                }
            }
        }
    }

    // 加载已有图片
    LaunchedEffect(noteId, loaded) {
        if (noteId > 0L && loaded) {
            repository.observeNoteImages(noteId).collectLatest { images ->
                imageUris.clear()
                imageUris.addAll(images.map { it.uri })
            }
        }
    }

    fun saveNote() {
        val noteIdToSave = lastSaved?.id ?: 0L
        val colorArgb = color.toArgb()
        viewModel.saveNote(
            id = noteIdToSave,
            title = title,
            content = content,
            categoryId = categoryId,
            tags = emptyList(),
            isPinned = isPinned,
            color = colorArgb,
            reminderTime = reminderTime,
            imageUris = imageUris.toList()
        )

        reminderTime?.let { time ->
            val note = NoteEntity(
                id = noteIdToSave,
                title = title.ifBlank { content.lineSequence().firstOrNull().orEmpty().take(40) },
                content = content,
                categoryId = categoryId,
                tags = "",
                isPinned = isPinned,
                color = colorArgb,
                reminderTime = time
            )
            ReminderManager.scheduleReminder(context, note, time)
        }

        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            undoRedo.undo(NoteSnapshot(title, content))?.let { snap ->
                                title = snap.title; content = snap.content
                            }
                        },
                        enabled = canUndo
                    ) {
                        Icon(Icons.Filled.Undo, contentDescription = "撤销")
                    }
                    IconButton(
                        onClick = {
                            undoRedo.redo(NoteSnapshot(title, content))?.let { snap ->
                                title = snap.title; content = snap.content
                            }
                        },
                        enabled = canRedo
                    ) {
                        Icon(Icons.Filled.Redo, contentDescription = "重做")
                    }
                    IconButton(onClick = { saveNote() }) {
                        Icon(Icons.Filled.Check, contentDescription = "完成")
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
            // === 标题 (带下划线) ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                if (title.isEmpty()) {
                    Text(
                        text = "标题",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it; pushHistory() },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )

            // === 元信息行 (置顶/提醒小图标已移除) ===
            MetaInfoRow(
                dateMs = lastSaved?.createdAt ?: System.currentTimeMillis(),
                charCount = content.length,
                categoryName = state.categories.firstOrNull { it.id == categoryId }?.name
                    ?: "未分类",
                onCategoryClick = { /* TODO: 分类选择 */ }
            )

            // === 主体: 文字 + 内联图片 + 内联音频 ===
            NoteBody(
                content = content,
                onContentChange = { content = it; pushHistory() },
                imageUris = imageUris,
                audioUris = audioUris,
                onRemoveImage = { imageUris.remove(it) },
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
            )

            // === 工具面板 ===
            if (selectedTool != null) {
                ToolPanel(
                    tool = selectedTool!!,
                    onPickImages = {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onTakePhoto = {
                        requestCamera.launch(Manifest.permission.CAMERA)
                    },
                    onPickAudio = {
                        pickAudio.launch(arrayOf("audio/*"))
                    },
                    onToggleTodo = {
                        // 切换: 若内容末尾是 ☐, 去掉它; 否则加上
                        val marker = "☐ "
                        if (content.endsWith(marker)) {
                            content = content.dropLast(marker.length)
                        } else if (content.isEmpty()) {
                            content = marker
                        } else {
                            content = content.trimEnd('\n') + "\n" + marker
                        }
                        pushHistory()
                        selectedTool = null
                    },
                    onInsertText = { snippet ->
                        content = if (content.isEmpty()) snippet else content + "\n" + snippet
                        pushHistory()
                    },
                    onInsertAtCursor = { snippet ->
                        // 简化: 总是 append
                        content = if (content.isEmpty()) snippet else content + snippet
                        pushHistory()
                    },
                    onDoodleClick = { showDoodle = true; selectedTool = null },
                    onTableClick = { showTableDialog = true; selectedTool = null },
                    onClose = { selectedTool = null }
                )
            }

            // === 底部工具栏 (6 项, AI 已移除) ===
            BottomToolbar(
                selected = selectedTool,
                onSelect = { tool ->
                    selectedTool = if (selectedTool == tool) null else tool
                }
            )
        }
    }

    // 涂鸦 Dialog
    if (showDoodle) {
        DoodleDialog(
            onDismiss = { showDoodle = false },
            onDone = { uri ->
                imageUris.add(uri.toString())
            }
        )
    }

    // 表格 Dialog
    if (showTableDialog) {
        TableInsertDialog(
            onDismiss = { showTableDialog = false },
            onInsert = { rows, cols ->
                val table = buildMarkdownTable(rows, cols)
                content = if (content.isEmpty()) table else content + "\n" + table
                pushHistory()
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除笔记") },
            text = { Text("确认要删除这条笔记吗?该操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    lastSaved?.let {
                        viewModel.deleteNote(it.id)
                        it.reminderTime?.let { _ -> ReminderManager.cancelReminder(context, it.id) }
                    }
                    confirmDelete = false
                    onBack()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

/* ============================================================== */
/* 元信息行 (置顶/提醒小图标已去除)                                  */
/* ============================================================== */
@Composable
private fun MetaInfoRow(
    dateMs: Long,
    charCount: Int,
    categoryName: String,
    onCategoryClick: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = sdf.format(dateMs),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("  |  ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
        Text(
            text = "$charCount 字",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("  |  ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onCategoryClick() }
        ) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ============================================================== */
/* 主体: 内容 + 内联多图 + 内联音频                                  */
/* ============================================================== */
@Composable
private fun NoteBody(
    content: String,
    onContentChange: (String) -> Unit,
    imageUris: List<String>,
    audioUris: List<String>,
    onRemoveImage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (imageUris.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    items(imageUris, key = { it }) { uri ->
                        ImageThumb(
                            uri = uri,
                            index = imageUris.indexOf(uri),
                            total = imageUris.size,
                            onRemove = { onRemoveImage(uri) }
                        )
                    }
                }
            }
        }
        if (audioUris.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    audioUris.forEach { uri ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = "音频: " + uri.substringAfterLast('/').take(28),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        item {
            BasicTextField(
                value = content,
                onValueChange = onContentChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (content.isEmpty()) {
                        Text(
                            text = "记录此刻的想法...",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 24.sp
                            )
                        )
                    }
                    inner()
                }
            )
        }
    }
}

@Composable
private fun ImageThumb(
    uri: String,
    index: Int,
    total: Int,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "图片 ${index + 1}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clickable { onRemove() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "删除图片",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
        ) {
            Text(
                text = "${index + 1}/$total",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

/* ============================================================== */
/* 工具面板 — 包含 6 个子工具完整实现                                */
/* ============================================================== */
@Composable
private fun ToolPanel(
    tool: BottomTool,
    onPickImages: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickAudio: () -> Unit,
    onToggleTodo: () -> Unit,
    onInsertText: (String) -> Unit,
    onInsertAtCursor: (String) -> Unit,
    onDoodleClick: () -> Unit,
    onTableClick: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
    ) {
        when (tool) {
            BottomTool.COLUMNS -> ColumnsPanel(
                onInsert = onInsertText
            )
            BottomTool.TEXT -> TextFormatPanel(
                onInsert = onInsertAtCursor
            )
            BottomTool.LIST -> ListPanel(
                onInsert = onInsertText
            )
            BottomTool.TODO -> TodoPanel(
                onToggle = onToggleTodo
            )
            BottomTool.IMAGE -> Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ToolSubItem(
                    icon = Icons.Filled.PhotoLibrary,
                    label = "图片或视频",
                    onClick = onPickImages
                )
                ToolSubItem(
                    icon = Icons.Filled.CameraAlt,
                    label = "拍照",
                    onClick = onTakePhoto
                )
            }
            BottomTool.MORE -> Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ToolSubItem(
                    icon = Icons.Filled.Brush,
                    label = "涂鸦",
                    onClick = onDoodleClick
                )
                ToolSubItem(
                    icon = Icons.Filled.GridOn,
                    label = "表格",
                    onClick = onTableClick
                )
                ToolSubItem(
                    icon = Icons.Filled.AudioFile,
                    label = "音频",
                    onClick = onPickAudio
                )
            }
        }
    }
}

/* ---------- 分栏: 4 个子页签 ---------- */
@Composable
private fun ColumnsPanel(onInsert: (String) -> Unit) {
    var tab by remember { mutableStateOf(ColumnsTab.TEXT_STYLE) }
    Column(modifier = Modifier.padding(8.dp)) {
        // 页签条
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ColumnsTab.values().forEach { t ->
                ColumnTabChip(
                    label = t.label,
                    selected = t == tab,
                    onClick = { tab = t }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        when (tab) {
            ColumnsTab.TEXT_STYLE -> TextStyleGrid(onInsert)
            ColumnsTab.SYMBOLS -> SymbolsGrid(onInsert)
            ColumnsTab.DIVIDERS -> DividersGrid(onInsert)
            ColumnsTab.TEMPLATES -> TemplatesGrid(onInsert)
        }
    }
}

@Composable
private fun ColumnTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(if (selected) Color(0xFFE6B800) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TextStyleGrid(onInsert: (String) -> Unit) {
    val items = listOf(
        "文字" to "[文字]",
        "/文字/" to "/文字/",
        "<文字>" to "<文字>",
        "\"文字\"" to "\"文字\"",
        "文字" to "**文字**",
        "文字" to "_文字_"
    )
    StyleGrid(items, onInsert)
}

@Composable
private fun SymbolsGrid(onInsert: (String) -> Unit) {
    val items = listOf(
        "★", "♥", "☀", "☂", "☃", "♠", "♣", "♦", "♪", "♫",
        "→", "←", "↑", "↓", "✓", "✗", "※", "∞", "©", "®"
    )
    SymbolGrid(items, onInsert)
}

@Composable
private fun DividersGrid(onInsert: (String) -> Unit) {
    val items = listOf(
        "───", "═══", "━━━", "┄┄┄", "╌╌╌", "■■■",
        "·····", "★★★★★", "▬▬▬", "▰▰▰"
    )
    SymbolGrid(items, onInsert)
}

@Composable
private fun TemplatesGrid(onInsert: (String) -> Unit) {
    val items = listOf(
        "📌 待办清单" to "\n【待办清单】\n☐ \n☐ \n☐ \n",
        "💡 想法" to "\n【灵感】\n\n",
        "📅 日程" to "\n【日程】\n时间:\n地点:\n参与者:\n",
        "📖 日记" to "\n【日记】\n日期:\n心情:\n\n",
        "🎯 目标" to "\n【目标】\n目标:\n计划:\n进度:\n",
        "📝 笔记" to "\n【笔记】\n主题:\n要点:\n"
    )
    StyleGrid(items, onInsert)
}

@Composable
private fun StyleGrid(items: List<Pair<String, String>>, onInsert: (String) -> Unit) {
    // 2x3 网格
    val rows = items.chunked(3)
    Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (display, snippet) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { onInsert(snippet) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            display,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SymbolGrid(symbols: List<String>, onInsert: (String) -> Unit) {
    val rows = symbols.chunked(5)
    Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { sym ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { onInsert(sym) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            sym,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                // 补齐空位
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/* ---------- Aa 文字格式面板 ---------- */
@Composable
private fun TextFormatPanel(onInsert: (String) -> Unit) {
    Column(modifier = Modifier.padding(8.dp)) {
        // 第 1 行: B I U S 高亮
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FormatIconBtn(Icons.Filled.FormatBold, "B") { onInsert("**") }
            FormatIconBtn(Icons.Filled.FormatItalic, "I") { onInsert("_") }
            FormatIconBtn(Icons.Filled.FormatUnderlined, "U") { onInsert("<u>") }
            FormatIconBtn(Icons.Filled.FormatStrikethrough, "S") { onInsert("~~") }
            FormatIconBtn(Icons.Filled.Brush, "高亮") { onInsert("==") }
        }
        Spacer(Modifier.height(8.dp))
        // 第 2 行: 字号
        Text("字号", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(10 to "小", 12 to "12", 14 to "14", 16 to "中", 18 to "18", 20 to "20", 24 to "大", 36 to "巨")
                .forEach { (size, label) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onInsert("[size=$size]") }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = if (size == 16) MaterialTheme.typography.titleSmall.copy(
                                color = Color(0xFFE6B800)
                            ) else MaterialTheme.typography.bodyMedium,
                            color = if (size == 16) Color(0xFFE6B800)
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
        }
        Spacer(Modifier.height(4.dp))
        // 第 3 行: 字体颜色
        Text("字体颜色", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val colors = listOf(
                Color.Black to "#000",
                Color(0xFF424242) to "#424",
                Color(0xFF9E9E9E) to "#9E9",
                Color(0xFFE53935) to "#E53",
                Color(0xFFFB8C00) to "#FB8",
                Color(0xFFE6B800) to "#E6B",
                Color(0xFF81C784) to "#81C"
            )
            colors.forEach { (c, hex) ->
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            width = if (hex == "#000") 2.dp else 0.5.dp,
                            color = if (hex == "#000") Color(0xFFE6B800) else Color.LightGray,
                            shape = CircleShape
                        )
                        .clickable { onInsert("[color=$hex]") }
                )
            }
        }
    }
}

@Composable
private fun FormatIconBtn(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/* ---------- 列表: 6 按钮 ---------- */
@Composable
private fun ListPanel(onInsert: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 左对齐
        ToolSubItem(
            icon = Icons.Filled.FormatAlignLeft,
            label = "左对齐",
            onClick = { onInsert("\n[align=left]") }
        )
        ToolSubItem(
            icon = Icons.Filled.FormatAlignCenter,
            label = "居中",
            onClick = { onInsert("\n[align=center]") }
        )
        ToolSubItem(
            icon = Icons.Filled.FormatAlignRight,
            label = "右对齐",
            onClick = { onInsert("\n[align=right]") }
        )
        ToolSubItem(
            icon = Icons.Filled.FormatListBulleted,
            label = "圆点",
            onClick = { onInsert("\n• ") }
        )
        ToolSubItem(
            icon = Icons.Filled.FormatListNumbered,
            label = "数字",
            onClick = { onInsert("\n1. ") }
        )
        ToolSubItem(
            icon = Icons.Filled.TextFields,
            label = "字母",
            onClick = { onInsert("\na. ") }
        )
    }
}

/* ---------- 待办面板 ---------- */
@Composable
private fun TodoPanel(onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ToolSubItem(
            icon = Icons.Filled.CheckBoxOutlineBlank,
            label = "待办",
            onClick = onToggle
        )
    }
}

@Composable
private fun ToolSubItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/* ============================================================== */
/* 底部 6 图标工具栏 (AI 已去除)                                     */
/* ============================================================== */
@Composable
private fun BottomToolbar(
    selected: BottomTool?,
    onSelect: (BottomTool) -> Unit
) {
    val toolbarItems = listOf(
        BottomTool.COLUMNS to (Icons.Filled.Layers to "分栏"),
        BottomTool.TEXT to (Icons.Filled.TextFields to "Aa"),
        BottomTool.LIST to (Icons.Filled.FormatListBulleted to "列表"),
        BottomTool.TODO to (Icons.Filled.CheckBoxOutlineBlank to "待办"),
        BottomTool.IMAGE to (Icons.Filled.Image to "图片"),
        BottomTool.MORE to (Icons.Filled.Add to "⊕")
    )
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            toolbarItems.forEach { (tool, iconAndLabel) ->
                val (icon, label) = iconAndLabel
                val isSelected = tool == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(tool) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (isSelected) Color(0xFFE6B800)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) Color(0xFFE6B800)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
