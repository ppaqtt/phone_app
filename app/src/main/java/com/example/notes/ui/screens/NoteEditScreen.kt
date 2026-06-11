package com.example.notes.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
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
import androidx.compose.ui.text.TextFieldValue
import androidx.compose.ui.text.TextRange
import timber.log.Timber
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.notes.data.NoteEntity
import com.example.notes.repository.NotesRepository
import com.example.notes.ui.components.CodeBlock
import com.example.notes.ui.components.FindBar
import com.example.notes.ui.components.MarkdownTable
import com.example.notes.ui.components.findAllMatches
import com.example.notes.ui.components.findCodeBlocks
import com.example.notes.ui.components.parseMarkdownTable
import com.example.notes.ui.theme.NoteSwatches
import com.example.notes.ui.viewmodel.NotesViewModel
import com.example.notes.util.ImageUtils
import com.example.notes.util.ReminderManager
import com.example.notes.util.insertAtCursor
import com.example.notes.util.toastLong
import com.example.notes.util.toastShort
import com.example.notes.util.selectionIsEmpty
import com.example.notes.util.toggleWrap
import com.example.notes.util.wrapParagraphWithAlign
import com.example.notes.util.wrapSelectionWithMarker
import com.example.notes.util.wrapSelectionWithTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 底部工具栏当前选中的工具 (AI 已移除) */
private enum class BottomTool { COLUMNS, TEXT, LIST, TODO, IMAGE, SPEECH, MORE }

/** 4 个文本样式子页签 */
private enum class ColumnsTab(val label: String) {
    TEXT_STYLE("文字样式"), SYMBOLS("符号"), DIVIDERS("分割线"), TEMPLATES("图文模版")
}

// P-FIX-003: TextFieldValue 的 rememberSaveable Saver，支持配置变更后恢复光标位置和选区
private val TextFieldValueSaver: Saver<TextFieldValue, Any> = mapSaver(
    save = { mapOf("text" to it.text, "selection" to it.selection.start) },
    restore = { TextFieldValue(it["text"] as String, TextRange(it["selection"] as Int)) }
)

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

    // P-FIX-003: 关键状态使用 rememberSaveable, 配置变更(旋转/深色模式切换)后自动恢复
    var title by rememberSaveable { mutableStateOf("") }
    // 内容改用 TextFieldValue 追踪选区, rememberSaveable 恢复光标位置
    var content by rememberSaveable(stateSaver = TextFieldValueSaver) { mutableStateOf(TextFieldValue("")) }
    var color by rememberSaveable { mutableStateOf(NoteSwatches.first()) }
    var isPinned by remember { mutableStateOf(false) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    // 标签: 从 lastSaved.tags 同步过来, 保存时回写
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    // 多图
    val imageUris: SnapshotStateList<String> = rememberSaveable { mutableStateListOf() }
    // 音频 (作为 URI 列表插入正文, 显示为可点击条目)
    val audioUris: SnapshotStateList<String> = rememberSaveable { mutableStateListOf() }
    var reminderTime by rememberSaveable { mutableStateOf<Long?>(null) }
    // F15: 提醒重复模式, 与 NoteEntity.reminderRepeat 字段一一对应
    var reminderRepeat by rememberSaveable { mutableStateOf(com.example.notes.util.ReminderRepeat.NONE) }
    var confirmDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(isNew) }
    var lastSaved by remember { mutableStateOf<NoteEntity?>(null) }
    var selectedTool by remember { mutableStateOf<BottomTool?>(null) }
    var showDoodle by remember { mutableStateOf(false) }
    var showTableDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    // 退出时未保存确认: null=未触发, "discard"=丢弃, "save"=保存后退出
    var showExitConfirm by remember { mutableStateOf(false) }
    var pendingExitAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 防止疯狂点击保存 / 退出导致重复写入数据库
    var busy by remember { mutableStateOf(false) }
    // F5: 笔记内查找
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findIndex by remember { mutableStateOf(0) }
    // P51: 协程作用域提到 Composable 级, 供 saveNote 内部 suspend 函数使用
    val scope = rememberCoroutineScope()

    // F16: 语音转文字
    var speechText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    // P-FIX-002: 使用 applicationContext 避免 Activity 泄漏
    val speechHelper = remember { com.example.notes.util.SpeechToTextHelper(context.applicationContext) }
    // 收集语音识别状态
    LaunchedEffect(speechHelper) {
        speechHelper.state.collect { state ->
            when (state) {
                is com.example.notes.util.SpeechToTextHelper.State.Listening -> {
                    isListening = true
                    speechText = "正在聆听..."
                }
                is com.example.notes.util.SpeechToTextHelper.State.Partial -> {
                    speechText = state.text
                }
                is com.example.notes.util.SpeechToTextHelper.State.Result -> {
                    isListening = false
                    // 将识别结果插入到内容末尾或光标位置
                    val snippet = state.text
                    content = if (content.text.isEmpty()) {
                        content.copy(text = snippet)
                    } else {
                        content.copy(text = content.text + "\n" + snippet)
                    }
                    pushHistory()
                    speechText = ""
                    selectedTool = null
                }
                is com.example.notes.util.SpeechToTextHelper.State.Error -> {
                    isListening = false
                    speechText = "错误: ${state.message}"
                    context.toastShort(state.message)
                }
                else -> { /* Idle */ }
            }
        }
    }
    // 录音权限请求
    val requestRecordAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            speechHelper.startListening("zh-CN")
        } else {
            context.toastShort("需要录音权限才能使用语音转文字")
        }
    }

    // F17: OCR 图片选择器
    var isOcrProcessing by remember { mutableStateOf(false) }
    val pickOcrImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isOcrProcessing = true
                runCatching {
                    val text = com.example.notes.util.OcrHelper.recognizeText(context, uri)
                    if (text.isNotBlank()) {
                        content = if (content.text.isEmpty()) {
                            content.copy(text = text)
                        } else {
                            content.copy(text = content.text + "\n" + text)
                        }
                        pushHistory()
                        context.toastShort("识别完成, 已插入 ${text.length} 字")
                    } else {
                        context.toastShort("未识别到文字")
                    }
                }.onFailure {
                    context.toastLong("识别失败: ${it.message}")
                }
                isOcrProcessing = false
                selectedTool = null
            }
        }
    }

    // 初始快照 (用于判断"内容是否被修改过")
    val initialSnapshot = remember(noteId, loaded) {
        if (loaded) NoteSnapshot(
            title = lastSaved?.title.orEmpty(),
            content = lastSaved?.content.orEmpty(),
            color = lastSaved?.color ?: NoteSwatches.first(),
            isPinned = lastSaved?.isPinned ?: false,
            categoryId = lastSaved?.categoryId,
            tags = (lastSaved?.tags?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) as List<String>,
            reminderTime = lastSaved?.reminderTime
        ) else NoteSnapshot("", "")
    }
    val isDirty by remember {
        derivedStateOf {
            title != initialSnapshot.title ||
                content.text != initialSnapshot.content ||
                color != initialSnapshot.color ||
                isPinned != initialSnapshot.isPinned ||
                categoryId != initialSnapshot.categoryId ||
                tags != initialSnapshot.tags ||
                reminderTime != initialSnapshot.reminderTime
        }
    }

    // === 撤销/重做 ===
    val undoRedo = remember { UndoRedoState<NoteSnapshot>(maxDepth = 80) }
    // 启动时记一次基线
    LaunchedEffect(Unit) { undoRedo.record(NoteSnapshot(title, content.text, color, isPinned, categoryId, tags, reminderTime)) }
    val canUndo by remember { derivedStateOf { undoRedo.canUndo } }
    val canRedo by remember { derivedStateOf { undoRedo.canRedo } }
    // P37: 节流, 200ms 内连续输入不重复入栈 (避免 80 步容量被快速耗尽)
    var lastPushMs by remember { mutableStateOf(0L) }
    fun pushHistory() {
        val now = System.currentTimeMillis()
        if (now - lastPushMs < 200L) return
        lastPushMs = now
        undoRedo.record(NoteSnapshot(title, content.text, color, isPinned, categoryId, tags, reminderTime))
    }

    // === Toast: 选区为空时给提示 ===
    fun showSelectFirstHint() {
        context.toastShort("请先选中要修改的文字")
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
    // 使用 rememberSaveable 防止 Activity 重建时残留旧 URI
    var pendingCameraUri: Uri? by rememberSaveable { mutableStateOf(null) }
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
        // 无论成功/失败/Activity 重建都清理,避免残留
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

    // F10: PDF / 长图导出 - SAF 启动器
    var showExportMenu by remember { mutableStateOf(false) }
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val pages = com.example.notes.util.NoteExporter.exportPdfToUri(
                        context = context,
                        uri = uri,
                        title = title.ifBlank { "无标题" },
                        content = content.text,
                        meta = "清笺 · ${
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(Date())
                        }"
                    )
                    context.toastShort("已导出 PDF ($pages 页)")
                }.onFailure {
                    context.toastLong("导出 PDF 失败: ${it.message}")
                }
            }
        }
    }
    val createImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val (w, h) = com.example.notes.util.NoteExporter.exportImageToUri(
                        context = context,
                        uri = uri,
                        title = title.ifBlank { "无标题" },
                        content = content.text,
                        meta = "清笺 · ${
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(Date())
                        }"
                    )
                    context.toastShort("已导出长图 (${w}×${h})")
                }.onFailure {
                    context.toastLong("导出长图失败: ${it.message}")
                }
            }
        }
    }

    // P67: 不再用共享 Calendar 状态, 每次 showDateTimePicker 新建一个实例,
    // 避免跨日跨月后上次选的日期残留。
    fun showDateTimePicker() {
        val initialCalendar = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, day)
                }
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        picked.set(Calendar.HOUR_OF_DAY, hour)
                        picked.set(Calendar.MINUTE, minute)
                        picked.set(Calendar.SECOND, 0)
                        val chosen = picked.timeInMillis
                        if (chosen <= System.currentTimeMillis()) {
                            context.toastShort("提醒时间已过, 请重新选择")
                        } else {
                            reminderTime = chosen
                        }
                    },
                    initialCalendar.get(Calendar.HOUR_OF_DAY),
                    initialCalendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            initialCalendar.get(Calendar.YEAR),
            initialCalendar.get(Calendar.MONTH),
            initialCalendar.get(Calendar.DAY_OF_MONTH)
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
                    tags = nwc.note.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    reminderTime = nwc.note.reminderTime
                    // F15: 加载重复模式
                    reminderRepeat = com.example.notes.util.ReminderRepeat.fromString(
                        nwc.note.reminderRepeat
                    )
                    lastSaved = nwc.note
                    loaded = true
                    undoRedo.clear()
                    undoRedo.record(NoteSnapshot(title, content.text, color, isPinned, categoryId, tags, reminderTime))
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

    // 从内容中提取音频URI (P62: 加 500ms debounce 避免长文输入卡顿)
    LaunchedEffect(loaded, content.text) {
        if (!loaded || content.text.isEmpty()) return@LaunchedEffect
        delay(500L)
        val audioPattern = Regex("\\[音频\\]\\(([^)]+)\\)")
        val foundUris = audioPattern.findAll(content.text).map { it.groupValues[1] }.toList()
        audioUris.clear()
        audioUris.addAll(foundUris)
    }

    /**
     * P51: 改为纯 suspend 函数, 不再内部 rememberCoroutineScope() (会崩)。
     * 由 [saveNoteThen] 在 Composable scope 内启动协程调用。
     * P52/P53: 协程完成后才执行 [then] 回调, 解决 fire-and-forget 导致
     * 列表数据未刷新就 popBackStack / busy 锁未释放的问题。
     */
    suspend fun saveNote() {
        val noteIdToSave = lastSaved?.id ?: 0L
        val colorArgb = color.toArgb()
        val now = System.currentTimeMillis()
        // P45: 改用挂起 saveNote, 拿到数据库返回的真实 id (新建时 != 0)
        val savedId = viewModel.saveNote(
            context = context,
            id = noteIdToSave,
            title = title,
            content = content.text,
            categoryId = categoryId,
            tags = tags,
            isPinned = isPinned,
            color = colorArgb,
            reminderTime = reminderTime,
            reminderRepeat = reminderRepeat.name,
            imageUris = imageUris.toList()
        )

        // 同步更新 lastSaved, 避免 isDirty 假阳性 / 元信息时间不更新
        val effectiveTitle = title.ifBlank { content.text.lineSequence().firstOrNull().orEmpty().take(40) }
        lastSaved = NoteEntity(
            id = savedId,
            title = effectiveTitle,
            content = content.text,
            categoryId = categoryId,
            tags = tags.joinToString(","),
            isPinned = isPinned,
            color = colorArgb,
            reminderTime = reminderTime,
            reminderRepeat = reminderRepeat.name,
            createdAt = lastSaved?.createdAt ?: now,
            updatedAt = now
        )

        reminderTime?.let { time ->
            val note = NoteEntity(
                id = savedId,
                title = effectiveTitle,
                content = content.text,
                categoryId = categoryId,
                tags = tags.joinToString(","),
                isPinned = isPinned,
                color = colorArgb,
                reminderTime = time,
                reminderRepeat = reminderRepeat.name
            )
            // P3: 调度是挂起函数, Toast 会自动切回主线程
            val result = ReminderManager.scheduleReminder(context, note, time)
            ReminderManager.showScheduleResult(context, result)
        }
    }

    /**
     * P52: 启动协程保存, 完成后才执行 [then]。
     * 调用方应在 onClick 内包 busy 锁, 避免重入。
     */
    fun saveNoteThen(then: () -> Unit) {
        scope.launch {
            try {
                saveNote()
                then()
            } catch (e: Exception) {
                Timber.tag("NoteEditScreen").e(e, "saveNote failed")
                context.toastShort("保存失败: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    /**
     * 智能退出: 有修改则弹"是否保存"对话框, 没修改则直接退出.
     * 同时, 进入 / 退出过程中用 [busy] 锁防重入, 避免疯狂点击导致
     * 多次执行 onBack / save 引起的内容丢失或数据库写入竞争.
     *
     * P82: 不再调 busy = true 后只调 then() (then 是 onBack, 弹回后 busy 永远
     * 不释放, 下次进入笔记所有按钮置灰)。弹回后 onBack 会销毁 Composable,
     * busy 状态随之销毁, 不必再设锁。
     */
    fun tryExit(then: () -> Unit) {
        if (busy) return
        if (!isDirty) {
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
     *
     * P10/P11: 不再用 indexOf + replaceFirst (只能替换首个匹配, 长文位置错乱),
     * 改为先 findTableBlocks 重新定位, 用 endIdx 切片重拼, 并自动修正 caret 位置。
     * P12: caret 落在新块末尾, 撤销时回到旧块末尾。
     */
    fun replaceTableBlock(oldBlock: String, newBlock: String) {
        if (oldBlock == newBlock) return
        val blocks = findTableBlocks(content.text)
        val match = blocks.firstOrNull { (block, _) -> block.text == oldBlock } ?: return
        val tableBlock = match.first
        val start = tableBlock.startIdx
        val end = tableBlock.endIdx
        val newText = content.text.substring(0, start) + newBlock + content.text.substring(end)
        // P12: caret 放在新块的内部, 撤销时回到旧块位置
        val newCaret = start + newBlock.length
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
                        onClick = { showFindBar = !showFindBar; if (!showFindBar) findQuery = "" },
                        enabled = !busy
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "在笔记中查找")
                    }
                    IconButton(
                        onClick = {
                            undoRedo.undo(NoteSnapshot(title, content.text, color, isPinned, categoryId, tags, reminderTime))?.let { snap ->
                                title = snap.title
                                content = TextFieldValue(snap.content, TextRange(snap.content.length))
                                color = snap.color.takeIf { it != androidx.compose.ui.graphics.Color.Unspecified } ?: NoteSwatches.first()
                                isPinned = snap.isPinned
                                categoryId = snap.categoryId
                                tags = snap.tags
                                reminderTime = snap.reminderTime
                            }
                        },
                        enabled = canUndo
                    ) {
                        Icon(Icons.Filled.Undo, contentDescription = "撤销")
                    }
                    IconButton(
                        onClick = {
                            undoRedo.redo(NoteSnapshot(title, content.text, color, isPinned, categoryId, tags, reminderTime))?.let { snap ->
                                title = snap.title
                                content = TextFieldValue(snap.content, TextRange(snap.content.length))
                                color = snap.color.takeIf { it != androidx.compose.ui.graphics.Color.Unspecified } ?: NoteSwatches.first()
                                isPinned = snap.isPinned
                                categoryId = snap.categoryId
                                tags = snap.tags
                                reminderTime = snap.reminderTime
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
                            // P52: 协程完成后才 onBack(), 避免 fire-and-forget 数据未入库
                            saveNoteThen {
                                context.toastShort("已保存")
                                onBack()
                            }
                        },
                        enabled = !busy
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "保存")
                    }
                    // F10: 导出下拉菜单 (PDF / 长图)
                    Box {
                        IconButton(
                            onClick = { showExportMenu = true },
                            enabled = !busy
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("导出为 PDF") },
                                leadingIcon = {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF")
                                },
                                onClick = {
                                    showExportMenu = false
                                    val fileName = com.example.notes.util.NoteExporter
                                        .defaultFileName(title.ifBlank { "笔记" }, "pdf")
                                    createPdfLauncher.launch(fileName)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导出为长图 (PNG)") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Image, contentDescription = "长图")
                                },
                                onClick = {
                                    showExportMenu = false
                                    val fileName = com.example.notes.util.NoteExporter
                                        .defaultFileName(title.ifBlank { "笔记" }, "png")
                                    createImageLauncher.launch(fileName)
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // F5: 笔记内查找条, 展开时插入到顶部
            if (showFindBar) {
                val matches = remember(content.text, findQuery) { findAllMatches(content.text, findQuery) }
                // 调整 findIndex 在查询变化时归零
                if (findQuery.isNotBlank() && findIndex >= matches.size) {
                    findIndex = 0
                }
                FindBar(
                    query = findQuery,
                    matchCount = matches.size,
                    currentIndex = if (matches.isEmpty()) 0 else findIndex.coerceAtMost(matches.size - 1),
                    onQueryChange = { findQuery = it; findIndex = 0 },
                    onNext = {
                        if (matches.isNotEmpty()) {
                            val ni = (findIndex + 1) % matches.size
                            findIndex = ni
                            content = content.copy(selection = TextRange(matches[ni].first, matches[ni].last + 1))
                        }
                    },
                    onPrev = {
                        if (matches.isNotEmpty()) {
                            val pi = (findIndex - 1 + matches.size) % matches.size
                            findIndex = pi
                            content = content.copy(selection = TextRange(matches[pi].first, matches[pi].last + 1))
                        }
                    },
                    onClose = {
                        showFindBar = false
                        findQuery = ""
                        findIndex = 0
                    }
                )
            }
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
                content = content.text,  // F6: 传整段文本, MetaInfoRow 内做中英文分词
                categoryName = state.categories.firstOrNull { it.id == categoryId }?.name
                    ?: "未分类",
                onCategoryClick = { showCategoryDialog = true }
            )

            // === 标签 Chip 行 (点击展开编辑) ===
            TagsRow(
                tags = tags,
                onClick = { showTagsDialog = true }
            )

            // F15: 提醒时间 + 重复模式卡片
            ReminderCard(
                reminderTime = reminderTime,
                repeatMode = reminderRepeat,
                onPickTime = { showDateTimePicker() },
                onClear = {
                    lastSaved?.id?.let { ReminderManager.cancelReminder(context, it) }
                    reminderTime = null
                    reminderRepeat = com.example.notes.util.ReminderRepeat.NONE
                },
                onRepeatChange = { reminderRepeat = it }
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
                        // P14: 实装光标位置插入, 替代之前的 unused 桩
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
                    onTableClick = { showTableDialog = true; selectedTool = null },
                    // F16: 语音转文字
                    onSpeechClick = {
                        if (!speechHelper.isAvailable()) {
                            context.toastShort("设备不支持语音识别")
                            selectedTool = null
                            return@ToolPanel
                        }
                        requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    speechText = speechText,
                    isListening = isListening,
                    // F17: OCR 文字识别
                    onOcrClick = {
                        pickOcrImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                }
            }

            // === 底部工具栏 (7 项, AI 已移除) ===
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
                        // P57: viewModelScope.launch 异步执行, onBack 立即触发 → 改用 scope.launch 等待完成后退出
                        // P82补充: 异常时也要确保 onBack, 否则 Composable 仍存活 + busy=true 锁死
                        scope.launch {
                            try {
                                lastSaved?.let {
                                    viewModel.deleteNote(it.id)
                                    it.reminderTime?.let { _ -> ReminderManager.cancelReminder(context, it.id) }
                                }
                            } catch (e: Exception) {
                                Timber.tag("NoteEditScreen").e(e, "delete failed")
                                context.toastShort("删除失败: ${e.message}")
                            } finally {
                                confirmDelete = false
                                onBack()
                            }
                        }
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
                        // P53: 协程完成后再 invoke pendingExitAction, 数据先入库再退出
                        saveNoteThen {
                            showExitConfirm = false
                            context.toastShort("已保存")
                            pendingExitAction?.invoke()
                        }
                    },
                    enabled = !busy
                ) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            if (busy) return@TextButton
                            // P82: 丢弃修改直接退出, 不设 busy 锁 (弹回后 onBack
                            // 会销毁 Composable, busy 不再被读到)
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

    // 标签编辑对话框
    if (showTagsDialog) {
        TagsEditDialog(
            initial = tags,
            onDismiss = { showTagsDialog = false },
            onConfirm = { newTags ->
                tags = newTags
                showTagsDialog = false
            }
        )
    }
}

/* ============================================================== */
/* ThreadLocal formatters (P91 集中管理 SimpleDateFormat)            */
/* ============================================================== */
private object ThreadLocalFmt {
    val metaInfoSdf = ThreadLocal.withInitial {
        java.text.SimpleDateFormat("yyyy/M/d HH:mm", java.util.Locale.getDefault())
    }
}

/**
 * F6: 中英文混排字数统计。
 * @return (cjkChars, englishWords) — 都是非负整数, 各自为 0 表示该类别没有
 */
private fun countCharsAndWords(text: String): Pair<Int, Int> {
    var cjk = 0
    var words = 0
    var inWord = false
    for (ch in text) {
        val isCjk = (ch in '\u4E00'..'\u9FFF') ||
            (ch in '\u3400'..'\u4DBF') ||
            (ch in '\uF900'..'\uFAFF')
        if (isCjk) {
            cjk++
            // 一个汉字结束一个英文词 (e.g. "hello你好" → 1 词 + 2 字)
            inWord = false
        } else if (ch.isLetterOrDigit()) {
            if (!inWord) {
                words++
                inWord = true
            }
        } else {
            inWord = false
        }
    }
    return cjk to words
}

/* ============================================================== */
/* 元信息行 (置顶/提醒小图标已去除)                                  */
/* ============================================================== */
@Composable
private fun MetaInfoRow(
    dateMs: Long,
    content: String,
    categoryName: String,
    onCategoryClick: () -> Unit
) {
    // P91: 用 ThreadLocal 替代 remember { SimpleDateFormat } (与 P64 TimeFormat 保持一致),
    // 虽然 Composable 内 remember 通常单线程, 但 ThreadLocal 更安全且避免时区/locale 切换
    // 不被捕捉 (remember 缓存的 formatter 不会随系统 locale 变化重建)
    val sdf = remember { ThreadLocalFmt.metaInfoSdf.get() }
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
        // F6: 字数统计。中英文混排时, 用 CJK 字符数 + 英文单词数分开统计, 比单纯 charCount 更实用。
        //  - CJK 范围: U+4E00-U+9FFF, U+3400-U+4DBF, U+F900-U+FAFF (含扩展A / 兼容汉字) → 按 1 字
        //  - 英文 / 数字段: 连续 [A-Za-z0-9] 为 1 词
        //  - 其他字符 (标点 / 空格 / 换行) 不计入
        val (cjkCount, wordCount) = remember(content) { countCharsAndWords(content) }
        Text(
            text = if (wordCount == 0) "$cjkCount 字" else "$cjkCount 字 / $wordCount 词",
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
/* 标签行 (点击弹出编辑)                                            */
/* ============================================================== */
@Composable
private fun TagsRow(
    tags: List<String>,
    onClick: () -> Unit
) {
    if (tags.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Label,
            contentDescription = "标签",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = tags.joinToString("  ") { "#$it" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = "编辑标签",
            modifier = Modifier
                .size(16.dp)
                .clickable(onClick = onClick),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ============================================================== */
/* 标签编辑对话框                                                    */
/* ============================================================== */
@Composable
private fun TagsEditDialog(
    initial: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var text by remember {
        mutableStateOf(initial.joinToString(", "))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑标签") },
        text = {
            Column {
                Text(
                    "多个标签用英文逗号分隔，例如：工作，重要，项目",
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
/* F15: 提醒时间 + 重复模式选择卡片                                    */
/* ============================================================== */
@Composable
private fun ReminderCard(
    reminderTime: Long?,
    repeatMode: com.example.notes.util.ReminderRepeat,
    onPickTime: () -> Unit,
    onClear: () -> Unit,
    onRepeatChange: (com.example.notes.util.ReminderRepeat) -> Unit
) {
    val timeText = remember(reminderTime) {
        if (reminderTime == null) {
            "未设置"
        } else {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(reminderTime))
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPickTime() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "提醒",
                    tint = if (reminderTime != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "提醒 · $timeText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (reminderTime != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (reminderTime != null) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onClear() }
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "清除提醒",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            // 只有设置提醒后才显示重复模式选择条
            if (reminderTime != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    com.example.notes.util.ReminderRepeat.values().forEach { mode ->
                        val selected = mode == repeatMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable { onRepeatChange(mode) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
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
                    // P-FIX-001: 使用 itemsIndexed 替代 items + indexOf, 避免 O(n²) 复杂度
                    val total = imageUris.size
                    itemsIndexed(imageUris, key = { _, it -> it }) { index, uri ->
                        ImageThumb(
                            uri = uri,
                            index = index,
                            total = total,
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
        // F14: 代码块检测 + 高亮渲染
        // 解析整段内容, 找出所有 ```lang ... ``` 块
        val codeBlocks = remember(content.text) { findCodeBlocks(content.text) }
        codeBlocks.forEach { span ->
            if (span.code.isNotEmpty()) {
                item(key = "code_${span.text.hashCode()}_${span.code.hashCode()}") {
                    CodeBlock(code = span.code, language = span.language)
                }
            }
        }
        // 每个表格块渲染为可视化 Excel 风格组件
        // P43: key 用 block.text 的 hashCode 替代 startIdx, 避免表格内容变更后 key 残留
        tableBlocks.forEach { (block, data) ->
            item(key = "tbl_${block.text.hashCode()}") {
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
    onInsertAtCursor: (String) -> Unit,
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
    onTableClick: () -> Unit,
    // F16: 语音转文字
    onSpeechClick: () -> Unit,
    speechText: String,
    isListening: Boolean,
    // F17: OCR 文字识别
    onOcrClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
    ) {
        when (tool) {
            // P55: 分栏面板用 onInsertAtCursor, 符号/模板插入到光标处
            BottomTool.COLUMNS -> ColumnsPanel(
                onInsert = onInsertAtCursor
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
            // 列表的对齐/前缀也是相对光标操作的, 用 onInsertAtCursor
            BottomTool.LIST -> ListPanel(
                onAlignLeft = onAlignLeft,
                onAlignCenter = onAlignCenter,
                onAlignRight = onAlignRight,
                onInsert = onInsertAtCursor
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
                // F17: OCR 文字识别
                ToolSubItem(
                    icon = Icons.Filled.TextFields,
                    label = "识别文字",
                    onClick = onOcrClick
                )
            }
            BottomTool.SPEECH -> SpeechPanel(
                onSpeechClick = onSpeechClick,
                speechText = speechText,
                isListening = isListening
            )
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

/* ---------- F16: 语音转文字面板 ---------- */
@Composable
private fun SpeechPanel(
    onSpeechClick: () -> Unit,
    speechText: String,
    isListening: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 麦克风大按钮
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    if (isListening) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { onSpeechClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = "语音输入",
                tint = if (isListening) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isListening) "正在聆听... 点击停止" else "点击开始语音输入",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (speechText.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = speechText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/* ============================================================== */
/* 底部 7 图标工具栏 (AI 已去除)                                     */
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
        BottomTool.SPEECH to (Icons.Filled.Mic to "语音"),
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
    // F12: 按层级渲染分类, 子分类缩进显示
    val flatRows = remember(categories) { flattenCategoriesForSelect(categories) }
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
                flatRows.forEach { row ->
                    val cat = row.category
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selected = cat.id }
                            .padding(
                                start = (8 + row.level * 20).dp,
                                end = 8.dp,
                                top = 12.dp,
                                bottom = 12.dp
                            ),
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

/**
 * F12: 把分类按"父→子"层级展开, 子节点 start 缩进 20dp。
 * 与 CategoriesScreen 内的 flattenForDisplay 逻辑一致。
 */
private data class FlatCategory(val category: com.example.notes.data.CategoryEntity, val level: Int)

private fun flattenCategoriesForSelect(
    all: List<com.example.notes.data.CategoryEntity>
): List<FlatCategory> {
    val byParent = all.groupBy { it.parentId }
    val roots = byParent[null].orEmpty()
    val result = ArrayList<FlatCategory>(all.size)
    fun walk(items: List<com.example.notes.data.CategoryEntity>, level: Int) {
        items.forEach { c ->
            result.add(FlatCategory(c, level))
            byParent[c.id]?.let { walk(it, level + 1) }
        }
    }
    walk(roots, 0)
    return result
}
