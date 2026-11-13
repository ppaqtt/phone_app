package com.example.notes.ui.components

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notes.util.TranscriptLine
import com.example.notes.util.VoiceAttachment
import com.example.notes.util.VoiceAttachmentParser
import kotlinx.coroutines.delay

/**
 * 语音附件卡片: 播放 / 进度 / 转写行点击跳转 / 编辑转写 / 删除。
 *
 * 时间戳跳转依赖 [VoiceAttachment.transcript] 中的 timeMs,
 * 点击任一转写行会 seekTo 到对应位置并继续播放。
 */
@Composable
fun VoiceAttachmentCard(
    attachment: VoiceAttachment,
    onDelete: () -> Unit,
    onUpdateTranscript: (String) -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember(attachment.uri) { mutableStateOf(false) }
    var prepared by remember(attachment.uri) { mutableStateOf(false) }
    var failed by remember(attachment.uri) { mutableStateOf(false) }
    var positionMs by remember(attachment.uri) { mutableStateOf(0L) }
    var showEdit by remember { mutableStateOf(false) }

    val player = remember(attachment.uri) { MediaPlayer() }

    DisposableEffect(attachment.uri) {
        try {
            val uri = Uri.parse(attachment.uri)
            if (uri.scheme == "file") {
                val path = uri.path
                if (path != null) player.setDataSource(path) else player.setDataSource(attachment.uri)
            } else {
                player.setDataSource(context, uri)
            }
            player.setOnPreparedListener {
                prepared = true
                positionMs = 0L
            }
            player.setOnCompletionListener {
                isPlaying = false
                positionMs = 0L
            }
            player.setOnErrorListener { _, _, _ ->
                failed = true
                isPlaying = false
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            failed = true
        }
        onDispose {
            runCatching { player.release() }
        }
    }

    // 播放期间轮询进度, 停止后不再跑协程
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
            delay(200L)
        }
    }

    fun seekTo(ms: Long) {
        if (failed) return
        runCatching {
            player.seekTo(ms.toInt())
            positionMs = ms
            if (!isPlaying) {
                player.start()
                isPlaying = true
            }
        }
    }

    val duration = attachment.durationMs.coerceAtLeast(1L)
    val fraction = (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    if (failed) return@IconButton
                    if (!prepared) return@IconButton
                    if (isPlaying) {
                        runCatching { player.pause() }
                        isPlaying = false
                    } else {
                        runCatching {
                            if (positionMs >= duration - 200L) player.seekTo(0)
                            player.start()
                            isPlaying = true
                        }
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "语音附件",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${VoiceAttachmentParser.formatTime(positionMs)} / ${
                        VoiceAttachmentParser.formatTime(attachment.durationMs)
                    }" + if (failed) " · 文件不可用" else if (!prepared) " · 准备中…" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showEdit = true }) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "编辑转写",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除语音",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = fraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant
        )

        if (attachment.transcript.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "转写 · 点击时间戳跳转",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            attachment.transcript.forEach { line ->
                val active = positionMs >= line.timeMs &&
                    positionMs < line.timeMs + estimateSegmentLength(line)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { seekTo(line.timeMs) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "[${VoiceAttachmentParser.formatTime(line.timeMs)}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(54.dp)
                    )
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "暂无转写文本, 点击右上角编辑手动补充",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showEdit) {
        var editText by remember {
            mutableStateOf(attachment.transcript.joinToString("\n") { it.text })
        }
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text("编辑转写") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    placeholder = { Text("可手动补充或修正转写内容, 每行一句") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateTranscript(editText)
                    showEdit = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEdit = false }) { Text("取消") }
            }
        )
    }
}

/** 估算某句在音频中的时长 (用于高亮当前句), 平均每个字符约 220ms, 最少 1.5s */
private fun estimateSegmentLength(line: TranscriptLine): Long =
    (line.text.length * 220L).coerceAtLeast(1_500L)

/** 旧版 `[音频](uri)` 标记的简单展示 (无播放能力, 保持向后兼容) */
@Composable
fun LegacyAudioRow(uri: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = "音频: " + uri.substringAfterLast('/').take(28),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
