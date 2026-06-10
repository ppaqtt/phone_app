package com.example.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.notes.data.NoteWithCategory
import com.example.notes.util.formatRelativeTime

@Composable
fun NoteCard(
    noteWithCategory: NoteWithCategory,
    onClick: () -> Unit,
    onPinClick: (() -> Unit)? = null,
    coverImageUri: String? = null,
    modifier: Modifier = Modifier
) {
    val note = noteWithCategory.note
    val cardColor = if (note.color == 0xFFFFFFFF.toInt()) {
        MaterialTheme.colorScheme.surface
    } else {
        Color(note.color)
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            coverImageUri?.let { uri ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(
                modifier = Modifier
                    .padding(PaddingValues(horizontal = 16.dp, vertical = 14.dp))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.title.ifBlank { "无标题" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (note.isPinned) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "已置顶",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(enabled = onPinClick != null) { onPinClick?.invoke() }
                        )
                    }
                }
                if (note.content.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    noteWithCategory.category?.let { cat ->
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(cat.color))
                        )
                        Text(
                            text = cat.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (note.tags.isNotBlank()) {
                        // P13/P41: trim + 加空格避免显示粘连
                        val tagList = note.tags.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        if (tagList.isNotEmpty()) {
                            Text(
                                text = "· " + tagList.joinToString("  ") { "#$it" },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // P33/P34: 音频/图片/表格附件数提示
                    val audioCount = remember(note.content) { audioCountInContent(note.content) }
                    val tableCount = remember(note.content) { tableCountInContent(note.content) }
                    if (audioCount > 0 || tableCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (audioCount > 0) {
                                Icon(
                                    Icons.Filled.GraphicEq,
                                    contentDescription = "音频",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = audioCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            if (tableCount > 0) {
                                Icon(
                                    Icons.Filled.TableChart,
                                    contentDescription = "表格",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = tableCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatRelativeTime(note.updatedAt),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 统计 [content] 中音频附件数 (匹配 [音频](...) 语法) */
private fun audioCountInContent(content: String): Int {
    if (content.isBlank()) return 0
    val audioPattern = Regex("\\[音频\\]\\(([^)]+)\\)")
    return audioPattern.findAll(content).count()
}

/** 统计 [content] 中 markdown 表格块数 */
private fun tableCountInContent(content: String): Int {
    if (content.isBlank()) return 0
    val lines = content.lines()
    var count = 0
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()
        if (line.startsWith("|") && i + 1 < lines.size) {
            val next = lines[i + 1].trim()
            if (next.startsWith("|") && next.contains("---")) {
                count++
                // 跳到表格结尾
                i += 2
                while (i < lines.size && lines[i].trim().startsWith("|")) i++
                continue
            }
        }
        i++
    }
    return count
}
