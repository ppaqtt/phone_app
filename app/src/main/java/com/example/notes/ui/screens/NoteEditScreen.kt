package com.example.notes.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.example.notes.util.ReminderManager
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 底部工具栏当前选中的工具 */
private enum class BottomTool { AI, COLUMNS, TEXT, LIST, TODO, IMAGE, MORE }

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
    // 多图 (取代旧的单 coverImageUri)
    val imageUris: SnapshotStateList<String> = remember { mutableStateListOf() }
    var reminderTime by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(isNew) }
    var lastSaved by remember { mutableStateOf<NoteEntity?>(null) }
    var selectedTool by remember { mutableStateOf<BottomTool?>(null) }

    // 多图选择器
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                imageUris.add(uri.toString())
            }
        }
        // 选完后收起工具面板
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
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: 撤销 */ }) {
                        Icon(Icons.Filled.Undo, contentDescription = "撤销")
                    }
                    IconButton(onClick = { /* TODO: 重做 */ }) {
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
                    onValueChange = { title = it },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // 下划线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )

            // === 元信息行 ===
            MetaInfoRow(
                dateMs = lastSaved?.createdAt ?: System.currentTimeMillis(),
                charCount = content.length,
                categoryName = state.categories.firstOrNull { it.id == categoryId }?.name
                    ?: "未分类",
                isPinned = isPinned,
                reminderTime = reminderTime,
                onCategoryClick = { /* TODO: 分类选择 */ },
                onReminderClick = { showDateTimePicker() },
                onPinClick = { isPinned = !isPinned; selectedTool = null }
            )

            // === 主体: 文字 + 内联图片 ===
            NoteBody(
                content = content,
                onContentChange = { content = it },
                imageUris = imageUris,
                onRemoveImage = { imageUris.remove(it) },
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
            )

            // === 工具面板 (选中工具时显示) ===
            if (selectedTool != null) {
                ToolPanel(
                    tool = selectedTool!!,
                    onPickImages = {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onAddTodo = {
                        content = if (content.isEmpty()) "☐ " else "$content\n☐ "
                        selectedTool = null
                    },
                    onClose = { selectedTool = null }
                )
            }

            // === 底部 7 图标工具栏 ===
            BottomToolbar(
                selected = selectedTool,
                onSelect = { tool ->
                    selectedTool = if (selectedTool == tool) null else tool
                }
            )
        }
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
                        it.reminderTime?.let { _ ->
                            ReminderManager.cancelReminder(context, it.id)
                        }
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
/* 元信息行                                                          */
/* ============================================================== */
@Composable
private fun MetaInfoRow(
    dateMs: Long,
    charCount: Int,
    categoryName: String,
    isPinned: Boolean,
    reminderTime: Long?,
    onCategoryClick: () -> Unit,
    onReminderClick: () -> Unit,
    onPinClick: () -> Unit
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
        Text(
            text = "  |  ",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = "$charCount 字",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "  |  ",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline
        )
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
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onPinClick, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = if (isPinned) "已置顶" else "置顶",
                modifier = Modifier.size(16.dp),
                tint = if (isPinned) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onReminderClick, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = "提醒",
                modifier = Modifier.size(16.dp),
                tint = if (reminderTime != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ============================================================== */
/* 主体: 内容 + 内联多图                                             */
/* ============================================================== */
@Composable
private fun NoteBody(
    content: String,
    onContentChange: (String) -> Unit,
    imageUris: List<String>,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
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
/* 工具面板 (位于工具栏上方, 选中工具时显示)                         */
/* ============================================================== */
@Composable
private fun ToolPanel(
    tool: BottomTool,
    onPickImages: () -> Unit,
    onAddTodo: () -> Unit,
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
            BottomTool.IMAGE -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
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
                        onClick = { /* TODO: 拍照 */ }
                    )
                    ToolSubItem(
                        icon = Icons.Filled.DocumentScanner,
                        label = "文档扫描",
                        onClick = { /* TODO: 文档扫描 */ }
                    )
                }
            }
            BottomTool.TODO -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ToolSubItem(
                        icon = Icons.Filled.CheckBoxOutlineBlank,
                        label = "待办",
                        onClick = onAddTodo
                    )
                }
            }
            BottomTool.AI -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ToolSubItem(
                        icon = Icons.Outlined.AutoAwesome,
                        label = "AI 助手",
                        onClick = { /* TODO: AI */ }
                    )
                }
            }
            BottomTool.COLUMNS -> {
                Text(
                    text = "分栏模板 (开发中)",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BottomTool.TEXT -> {
                Text(
                    text = "文字样式 (开发中)",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BottomTool.LIST -> {
                Text(
                    text = "列表样式 (开发中)",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BottomTool.MORE -> {
                Text(
                    text = "更多 (开发中)",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ============================================================== */
/* 底部 7 图标工具栏                                                */
/* ============================================================== */
@Composable
private fun BottomToolbar(
    selected: BottomTool?,
    onSelect: (BottomTool) -> Unit
) {
    val toolbarItems = listOf(
        BottomTool.AI to (Icons.Outlined.AutoAwesome to "AI"),
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
