package com.example.notes.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.notes.data.NoteEntity
import com.example.notes.repository.NotesRepository
import com.example.notes.ui.components.MarkdownTable
import com.example.notes.ui.components.parseMarkdownTable
import com.example.notes.ui.theme.NoteSwatches
import com.example.notes.ui.viewmodel.NotesViewModel
import com.example.notes.util.ImageUtils
import com.example.notes.util.ReminderManager
import com.example.notes.util.insertAtCursor
import com.example.notes.util.selectionIsEmpty
import com.example.notes.util.toggleWrap
import com.example.notes.util.wrapParagraphWithAlign
import com.example.notes.util.wrapSelectionWithMarker
import com.example.notes.util.wrapSelectionWithTag
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
    // 内容改用 TextFieldValue 追踪选区
    var content by remember { mutableStateOf(TextFieldValue("")) }
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
    var showCategoryDialog by remember { mutableStateOf(false) }
    // 退出时未保存确认: null=未触发, "discard"=丢弃, "save"=保存后退出
    var showExitConfirm by remember { mutableStateOf(false) }
    var pendingExitAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 防止疯狂点击保存 / 退出导致重复写入数据库
    var busy by remember { mutableStateOf(false) }

    // 初始快照 (用于判断"内容是否被修改过")
    val initialSnapshot = remember(noteId, loaded) {
        if (loaded) NoteSnapshot(
            title = lastSaved?.title.orEmpty(),
            content = lastSaved?.content.orEmpty()
        ) else NoteSnapshot("", "")
    }
    val isDirty = remember(title, content.text, initialSnapshot) {
        title != initialSnapshot.title || content.text != initialSnapshot.content
    }

    // === 撤销/重做 ===
    val undoRedo = remember { UndoRedoState<NoteSnapshot>(maxDepth = 80) }
    // 启动时记一次基线
    LaunchedEffect(Unit) { undoRedo.record(NoteSnapshot(title, content.text)) }
    val canUndo by remember { derivedStateOf { undoRedo.canUndo } }
    val canRedo by remember { derivedStateOf { undoRedo.canRedo } }
    fun pushHistory() { undoRedo.record(NoteSnapshot(title, content.text)) }

    // === Toast: 选区为空时给提示 ===
    fun showSelectFirstHint() {
        Toast.makeText(context, "请先选中要修改的文字", Toast.LENGTH_SHORT).show()
    }

    // === 多图选择器 ===
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val granted = uris.mapNotNull { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    uri.toString()
                }.getOrNull()
            }
            imageUris.addAll(granted)
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
            content = if (content.text.isEmpty()) {
                content.copy(text = marker.trimStart())
            } else {
                content.copy(text = content.text + marker)
            }
            pushHistory()
        }
        selectedTool = null
    }

    val calendar = remember { Calendar.getInstance() }
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
                    content = TextFieldValue(nwc.note.content, TextRange(nwc.note.content.length))
                    color = Color(nwc.note.color)
                    isPinned = nwc.note.isPinned
                    categoryId = nwc.note.categoryId
                    reminderTime = nwc.note.reminderTime
                    lastSaved = nwc.note
                    loaded = true
                    undoRedo.clear()
                    undoRedo.record(NoteSnapshot(title, content.text))
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
            content = content.text,
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
                title = title.ifBlank { content.text.lineSequence().firstOrNull().orEmpty().take(40) },
                content = content.text,
                categoryId = categoryId,
                tags = "",
                isPinned = isPinned,
                color = colorArgb,
                reminderTime = time
            )
            ReminderManager.scheduleReminder(context, note, time)
        }
    }

    /**
     * 智能退出: 有修改则弹"是否保存"对话框, 没修改则直接退出.
     * 同时, 进入 / 退出过程中用 [busy] 锁防重入, 避免疯狂点击导致
     * 多次执行 onBack / save 引起的内容丢失或数据库写入竞争.
     */
    fun tryExit(then: () -> Unit) {
        if (busy) return
        if (!isDirty) {
            busy = true
            then()
            return
        }
        pendingExitAction = then
        showExitConfirm = true
    }

    // === 内容更新 + 表格块替换回写 ===
    fun updateContent(newValue: TextFieldValue) {
        content = newValue
        pushHistory()
    }

    /**
     * 把 [oldBlock] (markdown 表格) 替换为 [newBlock] (新 markdown 字符串)。
     * 用来支持单元格编辑回写: 在原文本里找到旧表格块, 替换为新表格块。
     */
    fun replaceTableBlock(oldBlock: String, newBlock: String) {
        if (oldBlock == newBlock) return
        val idx = content.text.indexOf(oldBlock)
        if (idx < 0) return
        val newText = content.text.replaceFirst(oldBlock, newBlock)
        // 选区落在新块之后
        val newCaret = idx + newBlock.length
        content = content.copy(text = newText, selection = TextRange(newCaret))
        pushHistory()
    }

    // === 系统返回键 / 手势返回: 同样走 tryExit ===
    BackHandler(enabled = true) {
        tryExit(onBack)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { tryExit(onBack) }, enabled = !busy) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            undoRedo.undo(NoteSnapshot(title, content.text))?.let { snap ->
                                title = snap.title
                                content = TextFieldValue(snap.content, TextRange(snap.content.length))
                            }
                        },
                        enabled = canUndo
                    ) {
                        Icon(Icons.Filled.Undo, contentDescription = "撤销")
                    }
                    IconButton(
                        onClick = {
                            undoRedo.redo(NoteSnapshot(title, content.text))?.let { snap ->
                                title = snap.title
                                content = TextFieldValue(snap.content, TextRange(snap.content.length))
                            }
                        },
                        enabled = canRedo
                    ) {
                        Icon(Icons.Filled.Redo, contentDescription = "重做")
                    }
                    IconButton(
                        onClick = {
                            if (busy) return@IconButton
                            busy = true
                            saveNote()
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        enabled = !busy
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "保存")
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
            // 加载中遮罩
            if (!loaded && !isNew) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
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
                charCount = content.text.length,
                categoryName = state.categories.firstOrNull { it.id == categoryId }?.name
                    ?: "未分类",
                onCategoryClick = { showCategoryDialog = true }
            )

            // === 主体: 文字 + 内联图片 + 内联音频 + 表格 ===
            NoteBody(
                content = content,
                onContentChange = { updateContent(it) },
                imageUris = imageUris,
                audioUris = audioUris,
                onRemoveImage = { imageUris.remove(it) },
                onTableEdit = { oldBlock, newBlock -> replaceTableBlock(oldBlock, newBlock) },
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
            )

            // === 工具面板 ===
            AnimatedVisibility(
                visible = selectedTool != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                selectedTool?.let { tool ->
                ToolPanel(
                    tool = tool,
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
                        val newText = if (content.text.endsWith(marker)) {
                            content.text.dropLast(marker.length)
                        } else if (content.text.isEmpty()) {
                            marker
                        } else {
                            content.text.trimEnd('\n') + "\n" + marker
                        }
                        content = content.copy(text = newText)
                        pushHistory()
                        selectedTool = null
                    },
                    onInsertText = { snippet ->
                        // 在末尾 append 一行
                        val newText = if (content.text.isEmpty()) snippet else content.text + "\n" + snippet
                        content = content.copy(text = newText)
                        pushHistory()
                    },
                    onInsertAtCursor = { snippet ->
                        content = insertAtCursor(content, snippet)
                        pushHistory()
                    },
                    // 文字样式: 作用于选区, 选区空则 Toast
                    onWrapBold = {
                        if (content.selectionIsEmpty()) showSelectFirstHint()
                        else content = toggleWrap(content, "**")
                        pushHistory()
                    },
                    onWrapItalic = {
                        if (content.selectionIsEmpty()) showSelectFirstHint()
                        else content = toggleWrap(content, "_")
                        pushHistory()
                    },
                    onWrapUnderline = {
                        if (content.selectionIsEmpty()) showSelectFirstHint()
                        else content = wrapSelectionWithTag(content, "<u>", "</u>")
                        pushHistory()
                    },
                    onWrapStrike = {
                        if (content.selectionIsEmpty()) showSelectFirstHint()
                        else content = toggleWrap(content, "~~")
                        pushHistory()
                    },
                    onWrapHighlight = {
                        if (content.selectionIsEmpty()) showSelectFirstHint()
                        else content = wrapSelectionWithMarker(content, "==")
                        pushHistory()
                    },
                    onWrapSize = { size ->
                        if (content.selectionIsEmpty()) showSelectFirstHint()
                        else content = wrapSelectionWithTag(content, "<size=$size>", "</size>")
                        pushHistory()
                    },
                    onWrapColor = { hex ->
                        if (content.selectionIsEmpty()) showSelectFirstHint()
                        else content = wrapSelectionWithTag(content, "<color=$hex>", "</color>")
                        pushHistory()
                    },
                    // 对齐: 作用于光标所在段落
                    onAlignLeft = {
                        content = wrapParagraphWithAlign(content, "left"); pushHistory()
                    },
                    onAlignCenter = {
                        content = wrapParagraphWithAlign(content, "center"); pushHistory()
                    },
                    onAlignRight = {
                        content = wrapParagraphWithAlign(content, "right"); pushHistory()
                    },
                    onDoodleClick = { showDoodle = true; selectedTool = null },
                    onTableClick = { showTableDialog = true; selectedTool = null }
                )
                }
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
                val newText = if (content.text.isEmpty()) table else content.text + "\n" + table
                content = content.copy(text = newText)
                pushHistory()
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!busy) { confirmDelete = false } },
            title = { Text("删除笔记") },
            text = { Text("确认要删除这条笔记吗?该操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (busy) return@TextButton
                        busy = true
                        lastSaved?.let {
                            viewModel.deleteNote(it.id)
                            it.reminderTime?.let { _ -> ReminderManager.cancelReminder(context, it.id) }
                        }
                        confirmDelete = false
                        onBack()
                    },
                    enabled = !busy
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!busy) confirmDelete = false },
                    enabled = !busy
                ) { Text("取消") }
            }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { if (!busy) { showExitConfirm = false } },
            title = { Text("未保存的修改") },
            text = { Text("当前笔记有未保存的修改, 是否保存?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (busy) return@TextButton
                        busy = true
                        saveNote()
                        showExitConfirm = false
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        pendingExitAction?.invoke()
                    },
                    enabled = !busy
                ) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            if (busy) return@TextButton
                            busy = true
                            // 丢弃修改直接退出
                            showExitConfirm = false
                            pendingExitAction?.invoke()
                        },
                        enabled = !busy
                    ) { Text("丢弃") }
                    Spacer(Modifier.width(4.dp))
                    TextButton(
                        onClick = { if (!busy) showExitConfirm = false },
                        enabled = !busy
                    ) { Text("取消") }
                }
            }
        )
    }

    // 分类选择对话框
    if (showCategoryDialog) {
        CategorySelectDialog(
            categories = state.categories,
            current = categoryId,
            onDismiss = { showCategoryDialog = false },
            onConfirm = { newCategoryId ->
                categoryId = newCategoryId
                showCategoryDialog = false
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
                contentDescription = "选择分类",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ============================================================== */
/* 主体: 内容 + 内联多图 + 内联音频 + 表格                            */
/* ============================================================== */
@Composable
private fun NoteBody(
    content: TextFieldValue,
    onContentChange: (TextFieldValue) -> Unit,
    imageUris: List<String>,
    audioUris: List<String>,
    onRemoveImage: (String) -> Unit,
    onTableEdit: (oldBlock: String, newBlock: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 解析整段内容, 找出所有表格块的 (startIndex, endIndex) 和 TableData
    val tableBlocks = remember(content.text) { findTableBlocks(content.text) }

    // 全屏图片查看器状态 (uri 非空时显示)
    var viewerUri by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                            onClick = { viewerUri = uri },
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
                            Icon(Icons.Filled.AudioFile, contentDescription = "音频文件", tint = MaterialTheme.colorScheme.primary)
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
        // 主文本编辑区: 渲染全部文本 (含 markdown 表格原始字符)
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
                    if (content.text.isEmpty()) {
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
        // 每个表格块渲染为可视化 Excel 风格组件
        tableBlocks.forEach { (block, data) ->
            item(key = "tbl_${block.startIdx}_${block.endIdx}") {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    MarkdownTable(
                        data = data,
                        onCellEdit = { newMarkdown ->
                            onTableEdit(block.text, newMarkdown)
                        }
                    )
                }
            }
        }
    }

        // 全屏图片查看器覆盖层 (置顶)
        viewerUri?.let { uri ->
            PhotoViewer(
                uri = uri,
                onDismiss = { viewerUri = null }
            )
        }
    }
}

/** 找到的所有表格块 (text, startIdx, endIdx) 及其 [TableData] */
private data class TableBlock(
    val text: String,
    val startIdx: Int,
    val endIdx: Int
)

/** 找正文中所有 markdown 表格块, 按出现顺序返回 */
private fun findTableBlocks(text: String): List<Pair<TableBlock, com.example.notes.ui.components.TableData>> {
    if (text.isEmpty()) return emptyList()
    val lines = text.split('\n')
    val blocks = mutableListOf<Pair<TableBlock, com.example.notes.ui.components.TableData>>()
    var i = 0
    var runningOffset = 0
    while (i < lines.size) {
        val line = lines[i]
        val next = lines.getOrNull(i + 1) ?: ""
        val trimmedLine = line.trim()
        val trimmedNext = next.trim()
        val isHeader = trimmedLine.startsWith("|") && trimmedLine.endsWith("|") &&
            trimmedLine.count { it == '|' } >= 2
        val isSep = trimmedNext.startsWith("|") && trimmedNext.endsWith("|") &&
            trimmedNext.removePrefix("|").removeSuffix("|")
                .split("|").all { it.trim().matches(Regex(""":?-+:?""")) }
        if (isHeader && isSep) {
            val startOffset = runningOffset
            val tableLines = mutableListOf(line, next)
            var j = i + 2
            while (j < lines.size) {
                val l = lines[j]
                val tl = l.trim()
                if (tl.startsWith("|") && tl.endsWith("|") && tl.count { it == '|' } >= 2) {
                    tableLines.add(l)
                    j++
                } else {
                    break
                }
            }
            val blockText = tableLines.joinToString("\n")
            // 计算 trailing \n 长度: 如果原文本在 blockText 之后紧跟一个 \n, 算入 endOffset
            val afterBlock = startOffset + blockText.length
            val trailingNewline = if (afterBlock < text.length && text[afterBlock] == '\n') 1 else 0
            val endOffset = startOffset + blockText.length + trailingNewline
            val data = parseMarkdownTable(blockText)
            if (data != null) {
                blocks.add(TableBlock(blockText, startOffset, endOffset) to data)
            }
            runningOffset = endOffset
            i = j
        } else {
            val afterLine = runningOffset + line.length
            val trailingNewline = if (afterLine < text.length && text[afterLine] == '\n') 1 else 0
            runningOffset += line.length + trailingNewline
            i++
        }
    }
    return blocks
}

@Composable
private fun ImageThumb(
    uri: String,
    index: Int,
    total: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
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
                .size(40.dp)
                .clickable { onRemove() },
            contentColor = Color.White
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "删除图片",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
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
    @Suppress("UNUSED_PARAMETER") onInsertAtCursor: (String) -> Unit,
    onWrapBold: () -> Unit,
    onWrapItalic: () -> Unit,
    onWrapUnderline: () -> Unit,
    onWrapStrike: () -> Unit,
    onWrapHighlight: () -> Unit,
    onWrapSize: (Int) -> Unit,
    onWrapColor: (String) -> Unit,
    onAlignLeft: () -> Unit,
    onAlignCenter: () -> Unit,
    onAlignRight: () -> Unit,
    onDoodleClick: () -> Unit,
    onTableClick: () -> Unit
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
                onBold = onWrapBold,
                onItalic = onWrapItalic,
                onUnderline = onWrapUnderline,
                onStrike = onWrapStrike,
                onHighlight = onWrapHighlight,
                onSize = onWrapSize,
                onColor = onWrapColor
            )
            BottomTool.LIST -> ListPanel(
                onAlignLeft = onAlignLeft,
                onAlignCenter = onAlignCenter,
                onAlignRight = onAlignRight,
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

/* ---------- Aa 文字格式面板 (选区作用版) ---------- */
@Composable
private fun TextFormatPanel(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onStrike: () -> Unit,
    onHighlight: () -> Unit,
    onSize: (Int) -> Unit,
    onColor: (String) -> Unit
) {
    Column(modifier = Modifier.padding(8.dp)) {
        // 第 1 行: B I U S 高亮 — 全部作用于选区
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FormatIconBtn(Icons.Filled.FormatBold, "B", onBold)
            FormatIconBtn(Icons.Filled.FormatItalic, "I", onItalic)
            FormatIconBtn(Icons.Filled.FormatUnderlined, "U", onUnderline)
            FormatIconBtn(Icons.Filled.FormatStrikethrough, "S", onStrike)
            FormatIconBtn(Icons.Filled.Brush, "高亮", onHighlight)
        }
        Spacer(Modifier.height(8.dp))
        // 第 2 行: 字号 — 作用于选区
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
                            .clickable { onSize(size) }
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
        // 第 3 行: 字体颜色 — 作用于选区
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
                        .clickable { onColor(hex) }
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

/* ---------- 列表: 6 按钮 (对齐作用于段落) ---------- */
@Composable
private fun ListPanel(
    onAlignLeft: () -> Unit,
    onAlignCenter: () -> Unit,
    onAlignRight: () -> Unit,
    onInsert: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 左对齐
        ToolSubItem(
            icon = Icons.Filled.FormatAlignLeft,
            label = "左对齐",
            onClick = onAlignLeft
        )
        ToolSubItem(
            icon = Icons.Filled.FormatAlignCenter,
            label = "居中",
            onClick = onAlignCenter
        )
        ToolSubItem(
            icon = Icons.Filled.FormatAlignRight,
            label = "右对齐",
            onClick = onAlignRight
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

/* ============================================================== */
/* 分类选择对话框                                                    */
/* ============================================================== */
@Composable
private fun CategorySelectDialog(
    categories: List<com.example.notes.data.CategoryEntity>,
    current: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择分类") },
        text = {
            Column {
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
