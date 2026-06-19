package com.example.notes.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.notes.data.TodoEntity
import com.example.notes.util.toastShort
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditDialog(
    todo: TodoEntity?,
    onDismiss: () -> Unit,
    onSave: (TodoEntity) -> Unit,
    onDelete: ((TodoEntity) -> Unit)? = null
) {
    val isEdit = todo != null
    val context = LocalContext.current

    var title by remember { mutableStateOf(todo?.title ?: "") }
    var content by remember { mutableStateOf(todo?.content ?: "") }
    var priority by remember { mutableLongStateOf(todo?.priority?.toLong() ?: 0L) }
    var reminderTime by remember { mutableLongStateOf(todo?.reminderTime ?: 0L) }
    var ringtoneUri by remember { mutableStateOf(todo?.ringtoneUri) }
    // 功能3: 提醒重复模式, 默认 NONE
    var reminderRepeat by remember { mutableStateOf(todo?.reminderRepeat ?: "NONE") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // 铃声选择器
    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            ringtoneUri = uri?.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "编辑待办" else "新建待办") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 优先级选择
                Text("优先级", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriorityChip(
                        label = "普通",
                        color = Color.Gray,
                        selected = priority == 0L,
                        onClick = { priority = 0L }
                    )
                    PriorityChip(
                        label = "重要",
                        color = Color(0xFFFF9800),
                        selected = priority == 1L,
                        onClick = { priority = 1L }
                    )
                    PriorityChip(
                        label = "紧急",
                        color = Color(0xFFF44336),
                        selected = priority == 2L,
                        onClick = { priority = 2L }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 提醒时间
                Text("提醒时间", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    if (reminderTime > 0) {
                        val date = Date(reminderTime)
                        Text("${dateFormat.format(date)} ${timeFormat.format(date)}")
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            reminderTime = 0L
                            reminderRepeat = "NONE"
                        }) {
                            Text("清除")
                        }
                    } else {
                        Text("未设置", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(if (reminderTime > 0) "修改" else "设置")
                    }
                }

                // 功能3: 提醒重复模式
                if (reminderTime > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("重复", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "NONE" to "不重复",
                            "DAILY" to "每天",
                            "WEEKLY" to "每周",
                            "MONTHLY" to "每月"
                        ).forEach { (value, label) ->
                            androidx.compose.material3.Surface(
                                onClick = { reminderRepeat = value },
                                shape = MaterialTheme.shapes.small,
                                color = if (reminderRepeat == value)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 铃声选择
                Text("提醒铃声", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择提醒铃声")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                ringtoneUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                            }
                            ringtoneLauncher.launch(intent)
                        }
                        .padding(12.dp)
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (ringtoneUri != null) {
                            val uri = Uri.parse(ringtoneUri)
                            RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "自定义铃声"
                        } else {
                            "默认铃声"
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (ringtoneUri != null) {
                        IconButton(onClick = { ringtoneUri = null }) {
                            Icon(Icons.Default.Warning, contentDescription = "清除铃声")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        context.toastShort("请输入标题")
                        return@Button
                    }
                    val newTodo = TodoEntity(
                        id = todo?.id ?: 0L,
                        title = title.trim(),
                        content = content.trim(),
                        priority = priority.toInt(),
                        reminderTime = if (reminderTime > 0) reminderTime else null,
                        // 功能3: 保存重复模式
                        reminderRepeat = if (reminderTime > 0) reminderRepeat else "NONE",
                        ringtoneUri = ringtoneUri,
                        isCompleted = todo?.isCompleted ?: false,
                        createdAt = todo?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        completedAt = todo?.completedAt
                    )
                    onSave(newTodo)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (isEdit && onDelete != null && todo != null) {
                    TextButton(
                        onClick = { onDelete(todo) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Text("删除")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )

    // 日期选择器
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (reminderTime > 0) reminderTime else System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDate = datePickerState.selectedDateMillis
                    if (selectedDate != null) {
                        val calendar = Calendar.getInstance()
                        val currentReminder = if (reminderTime > 0) {
                            Calendar.getInstance().apply { timeInMillis = reminderTime }
                        } else {
                            Calendar.getInstance()
                        }
                        calendar.timeInMillis = selectedDate
                        calendar.set(Calendar.HOUR_OF_DAY, currentReminder.get(Calendar.HOUR_OF_DAY))
                        calendar.set(Calendar.MINUTE, currentReminder.get(Calendar.MINUTE))
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        reminderTime = calendar.timeInMillis
                    }
                    showDatePicker = false
                    showTimePicker = true
                }) {
                    Text("下一步")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 时间选择器
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = if (reminderTime > 0) {
                Calendar.getInstance().apply { timeInMillis = reminderTime }.get(Calendar.HOUR_OF_DAY)
            } else {
                9
            },
            initialMinute = if (reminderTime > 0) {
                Calendar.getInstance().apply { timeInMillis = reminderTime }.get(Calendar.MINUTE)
            } else {
                0
            }
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    val calendar = Calendar.getInstance().apply { timeInMillis = reminderTime }
                    calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    calendar.set(Calendar.MINUTE, timePickerState.minute)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    reminderTime = calendar.timeInMillis
                    showTimePicker = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun PriorityChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) color.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) color else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) color else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
