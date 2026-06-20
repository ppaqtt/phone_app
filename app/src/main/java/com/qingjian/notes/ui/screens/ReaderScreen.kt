package com.qingjian.notes.ui.screens

import android.speech.tts.TextToSpeech
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.util.regex.Pattern

/**
 * 进阶功能: 阅读模式 (含朗读 TTS)。
 *
 * - 大字号、宽行距、隐藏编辑工具栏
 * - 顶部可调字号 (0.8x - 2.0x)
 * - 底部 TTS 控件: 播放/暂停/停止
 * - 朗读过程中高亮当前段
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    title: String,
    content: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var fontScale by remember { mutableFloatStateOf(1.3f) }

    // TTS 引擎
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var currentParaIdx by remember { mutableStateOf(0) }
    val paragraphs = remember(content) { splitParagraphs(content) }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.value?.language = Locale.getDefault()
                isTtsReady = true
            }
        }
        tts.value = engine
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                isPaused = false
                currentParaIdx = utteranceId?.toIntOrNull() ?: 0
            }
            override fun onDone(utteranceId: String?) {
                val idx = utteranceId?.toIntOrNull() ?: 0
                if (idx + 1 < paragraphs.size) {
                    currentParaIdx = idx + 1
                    speak(tts.value, paragraphs, idx + 1) {}
                } else {
                    isSpeaking = false
                    currentParaIdx = 0
                }
            }
            @Deprecated("Deprecated in API 21+")
            override fun onError(utteranceId: String?) { isSpeaking = false }
        })
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阅读模式", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { /* 字号设置已嵌入正文 */ }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 字号调节
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("字号", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = fontScale,
                    onValueChange = { fontScale = it },
                    valueRange = 0.8f..2.0f,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text("${"%.1f".format(fontScale)}x", style = MaterialTheme.typography.bodySmall)
            }

            // 正文 (可滚动)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Column {
                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            fontSize = (28 * fontScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                    }
                    paragraphs.forEachIndexed { idx, para ->
                        val isCurrentTts = isSpeaking && idx == currentParaIdx
                        val bg = if (isCurrentTts)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.background
                        Text(
                            text = para,
                            fontSize = (18 * fontScale).sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            lineHeight = (28 * fontScale).sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bg, RoundedCornerShape(8.dp))
                                .padding(vertical = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }

            // TTS 控制栏
            if (paragraphs.isNotEmpty() && paragraphs.any { it.isNotBlank() }) {
                TtsControlBar(
                    isReady = isTtsReady,
                    isSpeaking = isSpeaking,
                    isPaused = isPaused,
                    onPlay = {
                        if (isPaused) {
                            tts.value?.let {
                                // TextToSpeech 没有原生 resume; 用重新播放模拟
                                it.stop()
                            }
                            isPaused = false
                        }
                        speak(tts.value, paragraphs, currentParaIdx) {}
                    },
                    onPause = {
                        tts.value?.stop()
                        isPaused = true
                    },
                    onStop = {
                        tts.value?.stop()
                        isSpeaking = false
                        isPaused = false
                        currentParaIdx = 0
                    }
                )
            }
        }
    }
}

@Composable
private fun TtsControlBar(
    isReady: Boolean,
    isSpeaking: Boolean,
    isPaused: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isReady) {
            Text("正在加载朗读引擎...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        IconButton(
            onClick = onStop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Icon(Icons.Filled.Stop, contentDescription = "停止", tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(24.dp))
        IconButton(
            onClick = if (isSpeaking && !isPaused) onPause else onPlay,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                imageVector = if (isSpeaking && !isPaused) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isSpeaking && !isPaused) "暂停" else "播放",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

private fun splitParagraphs(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val pattern = Pattern.compile("\\n+")
    return pattern.split(text).map { it.trim() }.filter { it.isNotEmpty() }
}

private fun speak(
    tts: TextToSpeech?,
    paragraphs: List<String>,
    startIdx: Int,
    onDone: () -> Unit
) {
    if (tts == null || startIdx >= paragraphs.size) return
    val mode = TextToSpeech.QUEUE_ADD
    for (i in startIdx until paragraphs.size) {
        tts.speak(paragraphs[i], mode, null, i.toString())
    }
    onDone()
}
