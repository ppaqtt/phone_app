package com.qingjian.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.qingjian.notes.data.DEFAULT_COLOR
import com.qingjian.notes.data.NoteEntity
import com.qingjian.notes.data.NoteWithCategory
import com.qingjian.notes.util.formatRelativeTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    noteWithCategory: NoteWithCategory,
    onClick: () -> Unit,
    onPinClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    coverImageUri: String? = null,
    /** 可选: 搜索高亮关键字 (来自 SearchScreen 等场景) */
    highlightQuery: String? = null,
    /** 进阶功能: 长按回调 (多选模式) */
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val note = noteWithCategory.note
    // P69: 用 DEFAULT_COLOR 哨兵判断"未选色", 避免和"用户选白色"撞色
    val cardColor = if (note.color == DEFAULT_COLOR) {
        MaterialTheme.colorScheme.surface
    } else {
        Color(note.color)
    }

    // 进阶功能: 若有 onLongClick, 用 combinedClickable 处理点击和长按
    // 注意: 不能用 Card(onClick={}), 因为 Card 内部的 clickable 会拦截触摸事件,
    // 导致外层 combinedClickable 收不到点击。改用 Card(modifier) 不传 onClick。
    if (onLongClick != null) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            NoteCardContent(note, noteWithCategory, coverImageUri, highlightQuery, onPinClick, onMoreClick)
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            NoteCardContent(note, noteWithCategory, coverImageUri, highlightQuery, onPinClick, onMoreClick)
        }
    }
}

/** 笔记卡片内容 (提取为独立函数, 避免在 if/else 分支中重复) */
@Composable
private fun NoteCardContent(
    note: NoteEntity,
    noteWithCategory: NoteWithCategory,
    coverImageUri: String?,
    highlightQuery: String?,
    onPinClick: (() -> Unit)?,
    onMoreClick: (() -> Unit)?
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
                    text = if (highlightQuery.isNullOrBlank()) AnnotatedString(note.title.ifBlank { "无标题" })
                        else highlightAnnotated(note.title.ifBlank { "无标题" }, highlightQuery),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 高价值/低工作量: 星标图标 (星标笔记始终显示在标题旁)
                if (note.isFavorite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Grade,
                        contentDescription = "星标",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
                if (note.colorTag != 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colorTagColor(note.colorTag))
                    )
                }
                if (note.isLocked) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "已锁定",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (note.isDraft) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "草稿",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(0.7f)
                    )
                }
                // 三个点按钮: 列表项的二级入口, 弹操作弹层 (替代原来的右滑手势)
                if (onMoreClick != null) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "更多操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            if (note.content.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (highlightQuery.isNullOrBlank()) AnnotatedString(note.content)
                        else highlightAnnotated(note.content, highlightQuery),
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
                val estimatedMinutes = if (note.readTimeSeconds > 0) {
                    (note.readTimeSeconds + 59) / 60
                } else {
                    note.content.length / 400
                }
                Text(
                    text = "${note.content.length} 字",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "约 $estimatedMinutes 分钟",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatRelativeTime(note.updatedAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun colorTagColor(tag: Int): Color {
    return when (tag) {
        1 -> Color(0xFFE53935)
        2 -> Color(0xFFF57C00)
        3 -> Color(0xFFFDD835)
        4 -> Color(0xFF43A047)
        5 -> Color(0xFF1E88E5)
        else -> Color(0xFF9E9E9E)
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

/**
 * 功能1: 将 [text] 中所有 [query] 出现处用强调样式渲染 (加粗 + 主题色背景)。
 * 大小写不敏感匹配, 不修改原文, 仅在 AnnotatedString 中标注 span。
 */
@Composable
private fun highlightAnnotated(
    text: String,
    query: String
): AnnotatedString {
    val highlightColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val highlightBackground = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
    if (query.isBlank() || text.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        val lowerText = text.lowercase()
        val lowerQuery = query.trim().lowercase()
        if (lowerQuery.isEmpty()) {
            append(text)
            return@buildAnnotatedString
        }
        var cursor = 0
        while (true) {
            val idx = lowerText.indexOf(lowerQuery, startIndex = cursor)
            if (idx < 0) {
                append(text.substring(cursor))
                break
            }
            append(text.substring(cursor, idx))
            withStyle(
                style = SpanStyle(
                    color = highlightColor,
                    background = highlightBackground,
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                append(text.substring(idx, idx + lowerQuery.length))
            }
            cursor = idx + lowerQuery.length
            if (cursor >= text.length) break
        }
    }
}
