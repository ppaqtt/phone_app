package com.example.notes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notes.data.CategoryEntity
import com.example.notes.data.NoteStatsRow
import com.example.notes.repository.StatsTotals
import com.example.notes.ui.viewmodel.NotesViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: NotesViewModel,
    onBack: () -> Unit
) {
    val totals by viewModel.statsTotals.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var contentRows by remember { mutableStateOf<List<NoteStatsRow>>(emptyList()) }

    // 一次性拉全量, 客户端做字数 / 月度统计
    LaunchedEffect(Unit) {
        contentRows = viewModel.getStatsRowsOnce()
    }
    // 笔记增删改后, 重新拉取
    val totalsCount = totals.totalNotes
    LaunchedEffect(totalsCount) {
        contentRows = viewModel.getStatsRowsOnce()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TotalsRow(totals)
            WordCountCard(contentRows)
            CategoryDistributionCard(categories, contentRows)
            MonthlyTrendCard(contentRows)
        }
    }
}

@Composable
private fun TotalsRow(totals: StatsTotals) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.StickyNote2,
            value = totals.totalNotes.toString(),
            label = "笔记"
        )
        StatTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.PushPin,
            value = totals.pinnedNotes.toString(),
            label = "置顶"
        )
        StatTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Event,
            value = totals.notesWithReminder.toString(),
            label = "提醒"
        )
        StatTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Image,
            value = totals.totalImages.toString(),
            label = "图片"
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * F13: 字数统计卡片。
 * 中文字符按 1 字, 英文/数字段按 1 词 计数 (与 NoteEditScreen.MetaInfoRow 保持一致)。
 */
@Composable
private fun WordCountCard(rows: List<NoteStatsRow>) {
    val totalChars = remember(rows) { rows.sumOf { countChars(it.content) } }
    val totalWords = remember(rows) { rows.sumOf { countWords(it.content) } }
    val avgChars = if (rows.isEmpty()) 0 else totalChars / rows.size

    SectionCard(title = "字数") {
        StatRow("中文字符", "$totalChars")
        StatRow("英文单词", "$totalWords")
        StatRow("平均每篇", "$avgChars 字")
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * F13: 分类分布 — 条形图, 横向。每个分类显示 1 个比例条 + 笔记数。
 */
@Composable
private fun CategoryDistributionCard(
    categories: List<CategoryEntity>,
    rows: List<NoteStatsRow>
) {
    val counts = remember(categories, rows) {
        val byId = rows.groupingBy { it.categoryId }.eachCount()
        val total = rows.size
        val map = LinkedHashMap<String, Pair<Int, Float>>()
        // 未分类
        val noneCount = byId[null] ?: 0
        if (noneCount > 0) map["未分类"] = noneCount to (noneCount.toFloat() / total)
        categories.forEach { c ->
            val n = byId[c.id] ?: 0
            if (n > 0) map[c.name] = n to (n.toFloat() / total)
        }
        map
    }
    SectionCard(title = "分类分布") {
        if (counts.isEmpty()) {
            Text(
                "暂无笔记",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            counts.forEach { (name, pair) ->
                val (n, ratio) = pair
                CategoryBar(name = name, count = n, ratio = ratio)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CategoryBar(name: String, count: Int, ratio: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$count 篇 · ${(ratio * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/**
 * F13: 最近 6 个月每月笔记数, 横向柱状图。
 * 没有笔记的月份也占 1 个柱 (高度 0), 保持时间轴连续。
 */
@Composable
private fun MonthlyTrendCard(rows: List<NoteStatsRow>) {
    val buckets = remember(rows) { bucketByMonth(rows, 6) }
    val maxCount = buckets.maxOf { it.second }.coerceAtLeast(1)
    SectionCard(title = "最近 6 个月") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            buckets.forEach { (label, count) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    val heightDp = (count.toFloat() / maxCount * 80f).coerceAtLeast(4f)
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(heightDp.dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (count > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/* ----------------- Helpers ----------------- */

private fun countChars(text: String): Int {
    var c = 0
    for (ch in text) {
        if ((ch in '\u4E00'..'\u9FFF') ||
            (ch in '\u3400'..'\u4DBF') ||
            (ch in '\uF900'..'\uFAFF')
        ) c++
    }
    return c
}

private fun countWords(text: String): Int {
    var words = 0
    var inWord = false
    for (ch in text) {
        val isCjk = (ch in '\u4E00'..'\u9FFF') ||
            (ch in '\u3400'..'\u4DBF') ||
            (ch in '\uF900'..'\uFAFF')
        if (isCjk) {
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
    return words
}

/**
 * 把笔记按 createdAt 分到最近 [months] 个月桶, 返回 ("M月", count) 列表, 升序。
 * 没有笔记的月份补 count=0, 保证图表 X 轴连续。
 */
private fun bucketByMonth(rows: List<NoteStatsRow>, months: Int): List<Pair<String, Int>> {
    val cal = Calendar.getInstance()
    val sdf = SimpleDateFormat("M月", Locale.getDefault())
    // 倒推 months-1 个月, 直到当前月
    val base = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val buckets = HashMap<Long, Int>()
    val ordered = ArrayList<Long>()
    for (i in months - 1 downTo 0) {
        val c = base.clone() as Calendar
        c.add(Calendar.MONTH, -i)
        val key = c.timeInMillis
        buckets[key] = 0
        ordered.add(key)
    }
    for (r in rows) {
        cal.timeInMillis = r.createdAt
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val k = cal.timeInMillis
        buckets[k] = buckets.getOrDefault(k, 0) + 1
    }
    return ordered.map { ts ->
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        sdf.format(c.time) to (buckets[ts] ?: 0)
    }
}
