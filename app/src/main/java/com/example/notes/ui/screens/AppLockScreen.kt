package com.example.notes.ui.screens

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.fragment.app.FragmentActivity
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notes.util.AppLockStore
import com.example.notes.util.BiometricHelper
import kotlinx.coroutines.delay

/**
 * F9: PIN 解锁屏。
 *
 * 模式:
 * - SetPin: 用户首次设置 PIN (输入 2 次确认)
 * - Unlock: 已有 PIN, 输入校验
 * - ChangePin: 先输入旧 PIN 验证, 再设置新 PIN
 *
 * 通过 onSuccess 回调通知上层切换 NavGraph 显示, 失败会抖动提示。
 *
 * @param newPinLength 新设置 PIN 时使用的长度 (4-8), 若为 null 则使用 store.pinLength
 */
@Composable
fun AppLockScreen(
    store: AppLockStore,
    mode: Mode,
    onSuccess: () -> Unit,
    newPinLength: Int? = null
) {
    val context = LocalContext.current
    var entered by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    // ChangePin 阶段: true = 正在验证旧 PIN, false = 正在设置新 PIN
    var verifyPhase by remember { mutableStateOf(mode == Mode.ChangePin) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isShaking by remember { mutableStateOf(false) }
    var cooldownRemaining by remember { mutableStateOf(0L) }

    // F19: 生物识别相关状态 (仅在 Unlock 模式且用户启用了生物识别时)
    val biometricStatus = remember {
        if (mode == Mode.Unlock && store.isBiometricEnabled)
            BiometricHelper.canAuthenticate(context)
        else
            BiometricHelper.Status.NoHardware
    }
    val showBiometric = mode == Mode.Unlock &&
        store.isBiometricEnabled &&
        biometricStatus == BiometricHelper.Status.Available

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

    // 根据当前模式/阶段决定目标 PIN 长度
    val targetLen: Int = when {
        mode == Mode.Unlock -> store.pinLength
        mode == Mode.ChangePin && verifyPhase -> store.pinLength
        else -> newPinLength ?: store.pinLength
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
                text = when {
                    mode == Mode.ChangePin && verifyPhase -> "请输入当前 PIN"
                    mode == Mode.ChangePin -> if (firstPin.isEmpty()) "设置新 PIN (${targetLen}位)" else "再次输入以确认"
                    mode == Mode.SetPin -> if (firstPin.isEmpty()) "设置 PIN (${targetLen}位)" else "再次输入以确认"
                    else -> "输入 PIN 解锁"
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
            PinIndicator(
                filled = entered.length,
                total = targetLen,
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

            // F19: 生物识别按钮 (仅在 Unlock 模式且设备支持时显示)
            if (showBiometric) {
                IconButton(
                    onClick = {
                        // F20: 向上递归查找 FragmentActivity (AppCompatActivity 间接继承自它)
                        val activity = findFragmentActivity(context)
                        if (activity != null) {
                            BiometricHelper.authenticate(
                                activity = activity,
                                title = "指纹/人脸解锁",
                                subtitle = "验证身份以解锁清笺",
                                negativeButtonText = "使用 PIN 解锁",
                                onSuccess = {
                                    // 生物识别成功 = 等同于 PIN 解锁成功
                                    store.updateUnlockTime()
                                    onSuccess()
                                },
                                onCancel = { /* 用户选择 PIN, 不做任何事, 继续显示 PIN 键盘 */ },
                                onError = { code, msg ->
                                    errorText = "生物识别失败 ($code): $msg"
                                }
                            )
                        } else {
                            errorText = "无法启动生物识别: 当前 Activity 不支持"
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = "指纹/人脸解锁",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // 数字键盘
            Keypad(
                onDigit = { d ->
                    if (cooldownRemaining > 0) return@Keypad
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
    LaunchedEffect(entered, mode, verifyPhase, targetLen) {
        if (entered.length == targetLen) {
            handleSubmit(
                mode = mode,
                verifyPhase = verifyPhase,
                entered = entered,
                store = store,
                firstPin = firstPin,
                onVerifyOk = { verifyPhase = false; firstPin = ""; entered = "" },
                onSetFirst = { firstPin = entered; entered = "" },
                onSuccess = onSuccess,
                onFail = {
                    isShaking = true
                    errorText = "PIN 错误, 请重试"
                    entered = ""
                    delay(400L)
                    isShaking = false
                },
                onMismatch = {
                    isShaking = true
                    errorText = "两次输入不一致, 请重新设置"
                    firstPin = ""
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
    verifyPhase: Boolean,
    entered: String,
    store: AppLockStore,
    firstPin: String,
    onVerifyOk: () -> Unit,
    onSetFirst: () -> Unit,
    onSuccess: () -> Unit,
    onFail: suspend () -> Unit,
    onMismatch: suspend () -> Unit,
    onCooldown: () -> Unit
) {
    when {
        mode == Mode.ChangePin && verifyPhase -> {
            if (store.checkPin(entered)) {
                onVerifyOk()
            } else {
                onFail()
                onCooldown()
            }
        }
        mode == Mode.ChangePin /* !verifyPhase */ || mode == Mode.SetPin -> {
            if (firstPin.isEmpty()) {
                onSetFirst()
            } else {
                if (firstPin == entered) {
                    store.setPin(entered)
                    onSuccess()
                } else {
                    onMismatch()
                }
            }
        }
        mode == Mode.Unlock -> {
            if (store.checkPin(entered)) {
                onSuccess()
            } else {
                onFail()
                onCooldown()
            }
        }
    }
}

enum class Mode { SetPin, Unlock, ChangePin }

/**
 * F20: 向上递归查找 [FragmentActivity]。
 *
 * 背景: 在 Compose 中, `LocalContext.current` 通常返回的是 Compose 内部包装的 Context,
 * 它*继承*自 Activity 的 Context, 但本身不是 Activity 也不是 FragmentActivity。
 * 直接 `context as? FragmentActivity` 会得到 null, 导致生物识别无法启动。
 *
 * 通过 ContextWrapper 链一路向上 unwrap, 找到真正的 Activity 实例。
 */
private fun findFragmentActivity(context: Context): FragmentActivity? {
    var c: Context? = context
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    // 兜底: 如果上面没找到, 再尝试 unwrap 一次 (某些 ROM 会用不同的 wrapper)
    return c as? FragmentActivity
}

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
