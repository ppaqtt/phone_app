package com.example.notes.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.notes.NotesApplication
import com.example.notes.data.FlashcardEntity
import com.example.notes.util.Sm2Scheduler
import com.example.notes.util.toastShort
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 闪卡复习页。
 *
 * 队列 = 全部到期卡片 (dueAt <= now), 每次评分后 SM-2 重排到期时间,
 * Flow 自动刷新, 因此"重来"的卡片 10 分钟后会重新出现在队列里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NotesApplication
    val repository = app.flashcardRepository
    val scope = rememberCoroutineScope()

    val allCards by repository.observeAll().collectAsState(initial = emptyList())
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var revealed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // 每分钟刷新一次"到期"判断, 10 分钟后重来的卡片会自动出现
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }

    val dueCards = remember(allCards, now) {
        allCards.filter { it.dueAt <= now }.sortedBy { it.dueAt }
    }
    val current = dueCards.firstOrNull()

    fun rate(card: FlashcardEntity, quality: Int) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { repository.review(card, quality) }
                .onFailure { context.toastShort("保存失败: ${it.message}") }
            revealed = false
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "闪卡复习",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "待复习 ${dueCards.size} · 全部 ${allCards.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
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
                .padding(16.dp)
        ) {
            when {
                allCards.isEmpty() -> EmptyCardsState()
                current == null -> AllDoneState()
                else -> {
                    ReviewCard(
                        card = current,
                        revealed = revealed,
                        onToggleReveal = { revealed = !revealed }
                    )
                    Spacer(Modifier.height(16.dp))
                    AnimatedVisibility(visible = revealed, enter = fadeIn(), exit = fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "这张卡片记得怎么样?",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RatingButton("重来", enabled = !busy, modifier = Modifier.weight(1f)) {
                                    rate(current, Sm2Scheduler.QUALITY_AGAIN)
                                }
                                RatingButton("困难", enabled = !busy, modifier = Modifier.weight(1f)) {
                                    rate(current, Sm2Scheduler.QUALITY_HARD)
                                }
                                RatingButton("一般", enabled = !busy, modifier = Modifier.weight(1f)) {
                                    rate(current, Sm2Scheduler.QUALITY_GOOD)
                                }
                                RatingButton("简单", enabled = !busy, modifier = Modifier.weight(1f)) {
                                    rate(current, Sm2Scheduler.QUALITY_EASY)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "重来: 10 分钟后; 困难/一般/简单: 按 SM-2 计算下次间隔",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!revealed) {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { revealed = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("显示答案") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点击打开来源笔记",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onOpenNote(current.noteId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    card: FlashcardEntity,
    revealed: Boolean,
    onToggleReveal: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
            .clickable(onClick = onToggleReveal),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "正面",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                card.front,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            if (revealed) {
                Spacer(Modifier.height(20.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(20.dp))
                Text(
                    "背面",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    card.back,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(Modifier.height(24.dp))
                Text(
                    "点击卡片查看答案",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RatingButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(label, maxLines = 1)
    }
}

@Composable
private fun EmptyCardsState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Filled.Style,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "还没有闪卡",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "在笔记里写 `#card 正面 :: 背面`，或用 `==高亮==` 标记重点，保存后会自动生成卡片。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AllDoneState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "今天的卡片都复习完了",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "继续写笔记，或明天再来。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
