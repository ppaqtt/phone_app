package com.example.notes.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notes.util.AppLockStore
import kotlinx.coroutines.delay

/**
 * F9: PIN 解锁屏。
 *
 * 模式:
 * - setPin: 用户首次设置 PIN (输入 2 次确认) — 内部用 state 区分 setup / unlock
 * - unlock: 已有 PIN, 输入校验
 *
 * 通过 onSuccess 回调通知上层切换 NavGraph 显示, 失败会抖动提示。
 */
@Composable
fun AppLockScreen(
    store: AppLockStore,
    mode: Mode,
    onSuccess: () -> Unit
) {
    var entered by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isShaking by remember { mutableStateOf(false) }
    var cooldownRemaining by remember { mutableStateOf(0L) }

    // 失败 N 次进入冷却
    LaunchedEffect(cooldownRemaining) {
        if (cooldownRemaining > 0) {
            while (cooldownRemaining > 0) {
                delay(1_000L)
                cooldownRemaining -= 1_000L
            }
            errorText = null
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "锁图标",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = when (mode) {
                    Mode.SetPin -> if (firstPin.isEmpty()) "设置 PIN" else "再次输入以确认"
                    Mode.Unlock -> "输入 PIN 解锁"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "清笺 已锁定",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            // 圆点指示器
            val pinLength = if (mode == Mode.SetPin) {
                if (firstPin.isNotEmpty()) 6 else entered.length
            } else 6
            val displayLen = if (mode == Mode.SetPin && firstPin.isNotEmpty()) firstPin.length else entered.length
            PinIndicator(
                filled = displayLen,
                total = pinLength,
                isError = isShaking,
                modifier = Modifier.then(
                    if (isShaking) Modifier.pointerInput(Unit) { /* shake placeholder */ } else Modifier
                )
            )

            Spacer(Modifier.height(12.dp))
            AnimatedVisibility(visible = errorText != null) {
                Text(
                    text = errorText ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (cooldownRemaining > 0) {
                Text(
                    text = "请稍候 ${cooldownRemaining / 1000} 秒后再试",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(24.dp))

            // 数字键盘
            Keypad(
                onDigit = { d ->
                    if (cooldownRemaining > 0) return@Keypad
                    val targetLen = pinLength
                    if (entered.length < targetLen) {
                        entered += d
                        errorText = null
                    }
                },
                onBackspace = {
                    if (cooldownRemaining > 0) return@Keypad
                    if (entered.isNotEmpty()) {
                        entered = entered.dropLast(1)
                        errorText = null
                    }
                },
                onLongBackspace = { entered = "" }
            )
        }
    }

    // 监听 entered 长度, 达到目标即提交
    LaunchedEffect(entered, mode) {
        val target = if (mode == Mode.SetPin) 6 else 6
        if (entered.length == target) {
            handleSubmit(
                mode = mode,
                entered = entered,
                store = store,
                firstPin = firstPin,
                onSetFirst = { firstPin = entered; entered = "" },
                onSuccess = onSuccess,
                onFail = {
                    isShaking = true
                    errorText = "PIN 错误, 请重试"
                    entered = ""
                    delay(400L)
                    isShaking = false
                },
                onCooldown = { cooldownRemaining = AppLockStore.COOLDOWN_MS }
            )
        }
    }
}

private suspend fun handleSubmit(
    mode: Mode,
    entered: String,
    store: AppLockStore,
    firstPin: String,
    onSetFirst: () -> Unit,
    onSuccess: () -> Unit,
    onFail: suspend () -> Unit,
    onCooldown: () -> Unit
) {
    when (mode) {
        Mode.SetPin -> {
            if (firstPin.isEmpty()) {
                // 第一次输入, 暂存, 等用户输第二次
                onSetFirst()
            } else {
                // 第二次输入, 比对
                if (firstPin == entered) {
                    store.setPin(entered)
                    onSuccess()
                } else {
                    onFail()
                }
            }
        }
        Mode.Unlock -> {
            if (store.checkPin(entered)) {
                onSuccess()
            } else {
                onFail()
                onCooldown()
            }
        }
    }
}

enum class Mode { SetPin, Unlock }

@Composable
private fun PinIndicator(
    filled: Int,
    total: Int,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(total) { idx ->
            val active = idx < filled
            val color = when {
                isError -> MaterialTheme.colorScheme.error
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onLongBackspace: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "BS")
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(Modifier.size(72.dp))
                        "BS" -> KeyButton(
                            content = {
                                Icon(
                                    imageVector = Icons.Filled.Backspace,
                                    contentDescription = "删除"
                                )
                            },
                            onClick = onBackspace,
                            onLongClick = onLongBackspace
                        )
                        else -> KeyButton(
                            content = {
                                Text(
                                    key,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            onClick = { onDigit(key[0]) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    content: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    // Compose 1.5+ 才有 combinedClickable 的 LongClick 支持; 简化用 pointerInput 实现
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
