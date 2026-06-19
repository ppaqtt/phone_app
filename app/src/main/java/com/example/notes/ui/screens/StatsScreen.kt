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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notes.data.CategoryEntity
import com.example.notes.data.NoteIdTitleContentTags
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
    var tagRows by remember { mutableStateOf<List<NoteIdTitleContentTags>>(emptyList()) }

    LaunchedEffect(Unit) {
        contentRows = viewModel.getStatsRowsOnce()
        tagRows = viewModel.getAllNotesForTagCloud()
    }
    val totalsCount = totals.totalNotes
    LaunchedEffect(totalsCount) {
        contentRows = viewModel.getStatsRowsOnce()
        tagRows = viewModel.getAllNotesForTagCloud()
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
            TagCloudCard(tagRows)
            WordCloudCard(tagRows)
            ReadingTimeCard(contentRows)
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

@Composable
private fun TagCloudCard(rows: List<NoteIdTitleContentTags>) {
    val topTags = remember(rows) { buildTagFrequencies(rows).take(20) }
    SectionCard(title = "标签云") {
        if (topTags.isEmpty()) {
            Text(
                "暂无标签",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val maxCount = topTags.maxOf { it.second }.coerceAtLeast(1)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalSpacing = 6.dp
            ) {
                topTags.forEach { (tag, count) ->
                    val ratio = (count.toFloat() / maxCount).coerceIn(0f, 1f)
                    val fontSize = lerp(12f, 24f, ratio).sp
                    Box(
                        modifier = Modifier
                            .clipCompat(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tag,
                            style = TextStyle(
                                fontSize = fontSize,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WordCloudCard(rows: List<NoteIdTitleContentTags>) {
    val topWords = remember(rows) { buildWordFrequencies(rows).take(30) }
    SectionCard(title = "字数云") {
        if (topWords.isEmpty()) {
            Text(
                "暂无内容",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val maxCount = topWords.maxOf { it.second }.coerceAtLeast(1)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalSpacing = 4.dp
            ) {
                topWords.forEach { (word, count) ->
                    val ratio = (count.toFloat() / maxCount).coerceIn(0f, 1f)
                    val fontSize = lerp(11f, 22f, ratio).sp
                    Text(
                        text = word,
                        style = TextStyle(
                            fontSize = fontSize,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingTimeCard(rows: List<NoteStatsRow>) {
    val totalLength = remember(rows) { rows.sumOf { it.content.length } }
    val totalMinutes = totalLength / 400
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    SectionCard(title = "阅读时间") {
        Text(
            text = if (hours > 0) "$hours 小时 $minutes 分钟" else "$minutes 分钟",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun CategoryDistributionCard(
    categories: List<CategoryEntity>,
    rows: List<NoteStatsRow>
) {
    val counts = remember(categories, rows) {
        val byId = rows.groupingBy { it.categoryId }.eachCount()
        val total = rows.size
        val map = LinkedHashMap<String, Pair<Int, Float>>()
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
                .clipCompat(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .clipCompat(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

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
                            .clipCompat(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(MaterialTheme.colorScheme.primary)
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

private fun buildTagFrequencies(rows: List<NoteIdTitleContentTags>): List<Pair<String, Int>> {
    val freq = LinkedHashMap<String, Int>()
    rows.forEach { r ->
        if (r.tags.isBlank()) return@forEach
        r.tags.split(Regex("[,，\\s]+")).filter { it.isNotBlank() }.forEach { tag ->
            val key = tag.trim()
            if (key.isNotEmpty()) freq[key] = freq.getOrDefault(key, 0) + 1)
        }
    }
    return freq.entries.sortedByDescending { it.value }.map { it.key to it.value }
}

private fun buildWordFrequencies(rows: List<NoteIdTitleContentTags>): List<Pair<String, Int>> {
    val freq = LinkedHashMap<String, Int>()
    val stopwords = setOf(
        "the", "and", "a", "to", "of", "in", "is", "it", "for", "on",
        "with", "as", "that", "this", "at", "by", "an", "be", "are", "or",
        "from", "but", "not", "you", "i", "we", "he", "she", "they", "my",
        "your", "his", "her", "our", "their", "them", "me", "him", "us",
        "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都",
        "一", "一个", "也", "到", "他", "你", "她", "我们", "你们", "他们",
        "这", "那", "这个", "那个", "这些", "那些", "吗", "呢", "啊", "吧",
        "与", "及", "或", "但", "而", "或", "被", "把", "给", "让", "使"
    )
    rows.forEach { r ->
        r.content.split(Regex("[^\\p{L}\\p{Nd]+"))
            .map { it.lowercase().trim() }
            .filter { it.length >= 2 && it !in stopwords }
            .forEach { word ->
                if (word.isNotBlank()) freq[word] = freq.getOrDefault(word, 0) + 1
            }
    }
    return freq.entries.sortedByDescending { it.value }.map { it.key to it.value }
}

private fun lerp(start: Float, end: Float, t: Float): Float {
    return start + (end - start) * t
}

/* ----------------- FlowRow 流式布局 ----------------- */

@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalSpacing: androidx.compose.ui.unit.Dp = 4.dp,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val verticalSpacingPx = with(density) { verticalSpacing.roundToPx() }
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val maxWidth = constraints.maxWidth
        val spacingPx = (maxWidth * 0.02f).toInt().coerceAtLeast(4)

        val actualRows = mutableListOf<MutableList<androidx.compose.ui.layout.Placeable>>()
        var curRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var curWidth = 0

        for (p in placeables) {
            if (curRow.isEmpty()) {
                curRow.add(p)
                curWidth = p.width
            } else {
                if (curWidth + spacingPx + p.width > maxWidth) {
                    actualRows.add(curRow)
                    curRow = mutableListOf(p)
                    curWidth = p.width
                } else {
                    curRow.add(p)
                    curWidth += spacingPx + p.width
                }
            }
        }
        if (curRow.isNotEmpty()) actualRows.add(curRow)

        var totalHeight = 0
        actualRows.forEach { row ->
            totalHeight += row.maxOf { it.height }
        }
        totalHeight += (actualRows.size - 1) * verticalSpacingPx

        layout(maxWidth, totalHeight.coerceAtLeast(0)) {
            var y = 0
            for (row in actualRows) {
                val h = row.maxOf { it.height }
                val rowWidth = row.sumOf { it.width } + spacingPx * (row.size - 1)
                var x = when {
                    horizontalArrangement == Arrangement.Center -> (maxWidth - rowWidth) / 2
                    horizontalArrangement == Arrangement.End -> maxWidth - rowWidth
                    else -> 0
                }
                for (p in row) {
                    p.placeRelative(x, y + (h - p.height) / 2)
                    x += p.width + spacingPx
                }
                y += h + verticalSpacingPx
            }
        }
    }
}

private fun Modifier.clipCompat(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(androidx.compose.ui.draw.clip(shape))

private fun bucketByMonth(rows: List<NoteStatsRow>, months: Int): List<Pair<String, Int>> {
    val cal = Calendar.getInstance()
    val sdf = SimpleDateFormat("M月", Locale.getDefault())
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
