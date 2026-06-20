package com.example.notes.ui.screens

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notes.util.AppLockStore
import com.example.notes.util.BiometricHelper
import kotlinx.coroutines.delay

/**
 * F9 + 功能5: 应用锁解锁屏。
 *
 * 支持两种锁类型: PIN (数字密码) / PATTERN (手势图案)。
 * - 模式: SetPin (首次设置) / Unlock (解锁) / ChangePin (修改)
 * - 设置时: 用户可在顶部选择 PIN 或 手势
 * - 解锁时: 根据 store.lockType 显示对应的解锁界面, 用户也可临时切换
 */
@Composable
fun AppLockScreen(
    store: AppLockStore,
    mode: Mode,
    onSuccess: () -> Unit,
    newPinLength: Int? = null
) {
    val context = LocalContext.current

    // === 共享状态 ===
    var errorText by remember { mutableStateOf<String?>(null) }
    var cooldownRemaining by remember { mutableStateOf(0L) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    // 功能5: 当前选择的锁类型 (PIN / PATTERN), 仅在 SetPin 模式下由用户切换
    var activeLockType by remember {
        mutableStateOf(
            when (mode) {
                Mode.SetPin -> AppLockStore.LOCK_TYPE_PIN
                else -> store.lockType
            }
        )
    }

    // === PIN 相关状态 ===
    var pinEntered by remember { mutableStateOf("") }
    var pinFirst by remember { mutableStateOf("") }
    var pinVerifyPhase by remember { mutableStateOf(mode == Mode.ChangePin) }
    var pinIsShaking by remember { mutableStateOf(false) }

    // === Pattern 相关状态 ===
    var patternEntered by remember { mutableStateOf<List<Int>>(emptyList()) }
    var patternFirst by remember { mutableStateOf<List<Int>>(emptyList()) }
    var patternVerifyPhase by remember { mutableStateOf(mode == Mode.ChangePin) }
    var patternIsShaking by remember { mutableStateOf(false) }
    // 用户抬起手指时 = 一次提交
    var patternSubmittedAt by remember { mutableIntStateOf(0) }

    // === 通用 ===
    val targetPinLen: Int = when {
        mode == Mode.Unlock && activeLockType == AppLockStore.LOCK_TYPE_PIN -> store.pinLength
        mode == Mode.ChangePin && pinVerifyPhase -> store.pinLength
        else -> newPinLength ?: store.pinLength
    }

    // 失败冷却
    LaunchedEffect(cooldownRemaining) {
        if (cooldownRemaining > 0) {
            while (cooldownRemaining > 0) {
                delay(1_000L)
                cooldownRemaining -= 1_000L
            }
            errorText = null
        }
    }

    // F19: 指纹解锁 (仅 Unlock 模式且已启用时)
    val biometricStatus = remember {
        if (mode == Mode.Unlock && store.isBiometricEnabled)
            BiometricHelper.canAuthenticate(context)
        else
            BiometricHelper.Status.NoHardware
    }
    val showBiometric = mode == Mode.Unlock &&
        store.isBiometricEnabled &&
        biometricStatus == BiometricHelper.Status.Available

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
            // 标题
            Text(
                text = buildTitle(mode, activeLockType, pinFirst, pinVerifyPhase,
                    patternFirst, patternVerifyPhase, targetPinLen),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "清笺 已锁定",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            // === 功能5: 模式切换 (PIN / 手势) ===
            // - 解锁时: 若两种方式都已设置, 可切换
            // - 设置时: 允许在两种方式间选一个设置
            val canSwitch = when (mode) {
                Mode.Unlock ->
                    (activeLockType == AppLockStore.LOCK_TYPE_PIN && store.hasPattern.value) ||
                        (activeLockType == AppLockStore.LOCK_TYPE_PATTERN && store.hasPin.value)
                Mode.SetPin -> true
                Mode.ChangePin -> true
            }
            if (canSwitch) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            errorText = null
                            pinEntered = ""; pinFirst = ""
                            patternEntered = emptyList(); patternFirst = emptyList()
                            activeLockType = AppLockStore.LOCK_TYPE_PIN
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (activeLockType == AppLockStore.LOCK_TYPE_PIN)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("PIN 密码")
                    }
                    Text("|", color = MaterialTheme.colorScheme.outline)
                    TextButton(
                        onClick = {
                            errorText = null
                            pinEntered = ""; pinFirst = ""
                            patternEntered = emptyList(); patternFirst = emptyList()
                            activeLockType = AppLockStore.LOCK_TYPE_PATTERN
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (activeLockType == AppLockStore.LOCK_TYPE_PATTERN)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("手势密码")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 解锁提示
            val hint = when (mode) {
                Mode.SetPin -> when (activeLockType) {
                    AppLockStore.LOCK_TYPE_PIN ->
                        if (pinFirst.isEmpty())
                            "输入 $targetPinLen 位数字作为新 PIN (两次确认)"
                        else
                            "再次输入相同 PIN 以确认"
                    else ->
                        if (patternFirst.isEmpty())
                            "绘制手势图案, 至少连接 ${AppLockStore.MIN_PATTERN_POINTS} 个点 (两次确认)"
                        else
                            "再次绘制相同的图案以确认"
                }
                Mode.Unlock ->
                    "输入 PIN 解锁"
                Mode.ChangePin ->
                    if ((activeLockType == AppLockStore.LOCK_TYPE_PIN && pinVerifyPhase) ||
                        (activeLockType == AppLockStore.LOCK_TYPE_PATTERN && patternVerifyPhase)
                    ) "请输入当前${if (activeLockType == AppLockStore.LOCK_TYPE_PIN) "PIN" else "手势"}"
                    else
                        if (activeLockType == AppLockStore.LOCK_TYPE_PIN)
                            "设置新 PIN ($targetPinLen 位)"
                        else
                            "设置新手势 (至少 ${AppLockStore.MIN_PATTERN_POINTS} 个点)"
            else -> ""
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            // === PIN / Pattern 分支渲染 ===
            if (activeLockType == AppLockStore.LOCK_TYPE_PIN) {
                PinIndicator(
                    filled = pinEntered.length,
                    total = targetPinLen,
                    isError = pinIsShaking
                )
                Spacer(Modifier.height(16.dp))
                AnimatedVisibility(visible = errorText != null && activeLockType == AppLockStore.LOCK_TYPE_PIN) {
                    Text(errorText ?: "", color = MaterialTheme.colorScheme.error)
                }
                if (cooldownRemaining > 0) {
                    Text("请稍候 ${cooldownRemaining / 1000} 秒后再试",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge)
                }
                if (showBiometric) {
                    Spacer(Modifier.height(16.dp))
                    IconButton(
                        onClick = {
                            val activity = findFragmentActivity(context)
                            if (activity != null) {
                                BiometricHelper.authenticate(
                                    activity = activity,
                                    title = "指纹解锁",
                                    subtitle = "验证身份以解锁清笺",
                                    negativeButtonText = "使用 PIN 解锁",
                                    onSuccess = { store.updateUnlockTime(); onSuccess() },
                                    onCancel = { },
                                    onError = { code, msg ->
                                        errorText = "指纹解锁失败 ($code): $msg"
                                    }
                                )
                            } else {
                                errorText = "无法启动指纹解锁"
                            }
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = "指纹解锁",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Spacer(Modifier.height(16.dp))
                if (mode == Mode.Unlock) {
                    TextButton(
                        onClick = {
                            showForgotPasswordDialog = true
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            "忘记密码?",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Keypad(
                    onDigit = { d ->
                        if (cooldownRemaining > 0) return@Keypad
                        if (pinEntered.length < targetPinLen) {
                            pinEntered += d
                            errorText = null
                        }
                    },
                    onBackspace = {
                        if (cooldownRemaining > 0) return@Keypad
                        if (pinEntered.isNotEmpty()) {
                            pinEntered = pinEntered.dropLast(1)
                            errorText = null
                        }
                    },
                    onLongBackspace = { pinEntered = "" }
                )
            } else {
                // === Pattern 绘制区域 ===
                AnimatedVisibility(visible = errorText != null) {
                    Text(errorText ?: "", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                PatternGrid(
                    selected = patternEntered,
                    isError = patternIsShaking,
                    onSubmit = { points ->
                        patternEntered = points
                        patternSubmittedAt++
                    },
                    onClearRequest = { patternEntered = emptyList() }
                )
                // 清空按钮
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { patternEntered = emptyList() }
                    ) { Text("清空") }
                    if (patternFirst.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                patternFirst = emptyList()
                                errorText = null
                            }
                        ) { Text("重新绘制") }
                    }
                }
                if (mode == Mode.Unlock) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            showForgotPasswordDialog = true
                        }
                    ) {
                        Text(
                            "忘记密码?",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showForgotPasswordDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = { Text("忘记密码") },
            text = {
                Text("确定要清除当前密码并重新设置吗？清除后将立即进入应用。")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        store.disable()
                        showForgotPasswordDialog = false
                        onSuccess()
                    }
                ) { Text("确定清除") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showForgotPasswordDialog = false }
                ) { Text("取消") }
            }
        )
    }

    // === PIN 提交逻辑 ===
    LaunchedEffect(pinEntered, mode, pinVerifyPhase, activeLockType) {
        if (activeLockType != AppLockStore.LOCK_TYPE_PIN) return@LaunchedEffect
        if (pinEntered.length != targetPinLen) return@LaunchedEffect
        when {
            mode == Mode.ChangePin && pinVerifyPhase -> {
                if (store.checkPin(pinEntered)) {
                    pinVerifyPhase = false
                    pinFirst = ""; pinEntered = ""
                } else {
                    pinIsShaking = true
                    errorText = "PIN 错误, 请重试"
                    pinEntered = ""
                    delay(400L); pinIsShaking = false
                    cooldownRemaining = AppLockStore.COOLDOWN_MS
                }
            }
            mode == Mode.SetPin || (mode == Mode.ChangePin && !pinVerifyPhase) -> {
                if (pinFirst.isEmpty()) {
                    pinFirst = pinEntered; pinEntered = ""
                } else {
                    if (pinFirst == pinEntered) {
                        store.setPin(pinEntered); onSuccess()
                    } else {
                        pinIsShaking = true
                        errorText = "两次输入不一致, 请重新设置"
                        pinFirst = ""; pinEntered = ""
                        delay(400L); pinIsShaking = false
                    }
                }
            }
            mode == Mode.Unlock -> {
                if (store.checkPin(pinEntered)) onSuccess()
                else {
                    pinIsShaking = true
                    errorText = "PIN 错误, 请重试"; pinEntered = ""
                    delay(400L); pinIsShaking = false
                    cooldownRemaining = AppLockStore.COOLDOWN_MS
                }
            }
        }
    }

    // === Pattern 提交逻辑 ===
    LaunchedEffect(patternSubmittedAt, mode, patternVerifyPhase, activeLockType) {
        if (activeLockType != AppLockStore.LOCK_TYPE_PATTERN) return@LaunchedEffect
        if (patternSubmittedAt == 0) return@LaunchedEffect
        val points = patternEntered
        if (points.size < AppLockStore.MIN_PATTERN_POINTS) {
            errorText = "手势至少需要 ${AppLockStore.MIN_PATTERN_POINTS} 个点"
            return@LaunchedEffect
        }
        val patternStr = points.joinToString(",")
        when {
            mode == Mode.ChangePin && patternVerifyPhase -> {
                if (store.checkPattern(patternStr)) {
                    patternVerifyPhase = false
                    patternFirst = emptyList(); patternEntered = emptyList()
                } else {
                    patternIsShaking = true
                    errorText = "手势错误, 请重试"; patternEntered = emptyList()
                    delay(400L); patternIsShaking = false
                    cooldownRemaining = AppLockStore.COOLDOWN_MS
                }
            }
            mode == Mode.SetPin || (mode == Mode.ChangePin && !patternVerifyPhase) -> {
                if (patternFirst.isEmpty()) {
                    patternFirst = points; patternEntered = emptyList()
                    errorText = "请再次绘制相同的手势以确认"
                } else {
                    if (patternFirst == points) {
                        store.setPattern(patternStr); onSuccess()
                    } else {
                        patternIsShaking = true
                        errorText = "两次手势不一致, 请重新设置"
                        patternFirst = emptyList(); patternEntered = emptyList()
                        delay(400L); patternIsShaking = false
                    }
                }
            }
            mode == Mode.Unlock -> {
                if (store.checkPattern(patternStr)) onSuccess()
                else {
                    patternIsShaking = true
                    errorText = "手势错误, 请重试"; patternEntered = emptyList()
                    delay(400L); patternIsShaking = false
                    cooldownRemaining = AppLockStore.COOLDOWN_MS
                }
            }
        }
    }
}

/** 构建标题文字 (PIN / Pattern 各自语义不同) */
private fun buildTitle(
    mode: Mode,
    lockType: String,
    pinFirst: String,
    pinVerify: Boolean,
    patternFirst: List<Int>,
    patternVerify: Boolean,
    targetPinLen: Int
): String {
    return when {
        mode == Mode.ChangePin && ((lockType == AppLockStore.LOCK_TYPE_PIN && pinVerify) ||
            (lockType == AppLockStore.LOCK_TYPE_PATTERN && patternVerify)) ->
            "请输入当前${if (lockType == AppLockStore.LOCK_TYPE_PIN) "PIN" else "手势"}"
        mode == Mode.SetPin || (mode == Mode.ChangePin) -> {
            when (lockType) {
                AppLockStore.LOCK_TYPE_PIN ->
                    if (pinFirst.isEmpty()) "设置 PIN ($targetPinLen 位)" else "再次输入以确认"
                else ->
                    if (patternFirst.isEmpty()) "设置手势" else "再次绘制以确认"
            }
        }
        else ->
            if (lockType == AppLockStore.LOCK_TYPE_PIN) "输入 PIN 解锁" else "绘制手势解锁"
    }
}

/**
 * F9: 模式枚举。保持与原来一致:
 * - SetPin: 设置 (PIN 或手势, 由上层传入的 activeLockType 决定)
 * - Unlock: 解锁 (根据 store.lockType)
 * - ChangePin: 修改 (先验证旧, 再设置新)
 */
enum class Mode { SetPin, Unlock, ChangePin }

/**
 * F20: 向上递归查找 [FragmentActivity]。
 *
 * 背景: 在 Compose 中, `LocalContext.current` 通常返回的是 Compose 内部包装的 Context,
 * 它*继承*自 Activity 的 Context, 但本身不是 Activity 也不是 FragmentActivity。
 * 直接 `context as? FragmentActivity` 会得到 null, 导致指纹解锁无法启动。
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

/**
 * 功能5: 3x3 手势绘制网格。
 *
 * 实现思路:
 * - Canvas 先绘制 9 个点 + 已选中的点连接线
 * - detectDragGestures 监听滑动, 判断手指当前覆盖的点
 * - 松手 (onDragEnd) 时调用 onSubmit 提交
 */
@Composable
private fun PatternGrid(
    selected: List<Int>,
    isError: Boolean,
    onSubmit: (List<Int>) -> Unit,
    onClearRequest: () -> Unit,
    gridSizePx: Int = 280
) {
    val density = LocalDensity.current
    val gridPx = with(density) { gridSizePx.dp.toPx() }
    // 每个点的中心坐标: 3x3 网格, 等间距
    val cellSize = gridPx / 3f
    val pointRadius = cellSize * 0.18f
    val connectRadius = cellSize * 0.5f // 触发选中的半径
    val points = Array(9) { idx ->
        val row = idx / 3; val col = idx % 3
        Offset(
            x = col * cellSize + cellSize / 2f,
            y = row * cellSize + cellSize / 2f
        )
    }

    // 实时正在绘制的点
    val currentPoints = remember { mutableStateListOf<Int>() }
    var currentFinger by remember { mutableStateOf<Offset?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    // `selected` 是外部传入的 "已提交" 的手势列表, 若外部清空了 selected,
    // 我们清空 currentPoints 以保证下次绘制时从零开始
    LaunchedEffect(selected) {
        if (selected.isEmpty()) {
            currentPoints.clear()
            currentFinger = null
        } else {
            currentPoints.clear()
            currentPoints.addAll(selected)
        }
    }

    // 确定一条从最后一个选中点到当前手指位置的临时线
    val lineColor = if (isError) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary
    val dotColor = if (isError) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurfaceVariant
    val selectedFillColor = if (isError) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(gridSizePx.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        currentPoints.clear()
                        // 找 offset 最近的点
                        addPointIfNear(currentPoints, points, offset, connectRadius)
                    },
                    onDrag = { change, _ ->
                        currentFinger = change.position
                        addPointIfNear(currentPoints, points, change.position, connectRadius)
                    },
                    onDragEnd = {
                        isDragging = false
                        val final = currentPoints.toList()
                        if (final.isNotEmpty()) {
                            onSubmit(final)
                        }
                        currentFinger = null
                    },
                    onDragCancel = {
                        isDragging = false
                        currentFinger = null
                        onClearRequest()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1) 绘制连接线 (已选中点 -> 后续点)
            val path = Path()
            for (i in currentPoints.indices) {
                val p = points[currentPoints[i]]
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            // 到当前手指位置的临时线
            if (isDragging && currentPoints.isNotEmpty() && currentFinger != null) {
                path.lineTo(currentFinger!!.x, currentFinger!!.y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 6f)
            )

            // 2) 绘制 9 个点
            for (i in 0 until 9) {
                val p = points[i]
                val isSel = currentPoints.contains(i)
                drawCircle(
                    color = if (isSel) selectedFillColor else dotColor,
                    radius = if (isSel) pointRadius * 1.3f else pointRadius,
                    center = p
                )
                if (!isSel) {
                    // 未选中点的外圈
                    drawCircle(
                        color = dotColor.copy(alpha = 0.25f),
                        radius = pointRadius * 2.2f,
                        center = p
                    )
                }
            }
        }
    }
}

/** 如果 offset 距某个未选中的点足够近, 则添加到 selected 列表 (按顺序) */
private fun addPointIfNear(
    selected: MutableList<Int>,
    points: Array<Offset>,
    offset: Offset,
    radius: Float
) {
    for (i in points.indices) {
        val p = points[i]
        val dx = p.x - offset.x; val dy = p.y - offset.y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dist <= radius && !selected.contains(i)) {
            // 跳过中间点: 例如从 0 到 2, 如果 1 还没被选, 则自动补 1
            if (selected.isNotEmpty()) {
                val last = selected.last()
                val midIdx = midPointIndex(last, i)
                if (midIdx != null && midIdx !in selected) {
                    selected.add(midIdx)
                }
            }
            selected.add(i)
            return
        }
    }
}

/** 计算两个 3x3 网格点是否为同行/同列/同斜线, 且中间有一个点被跳过, 返回中间点索引 */
private fun midPointIndex(a: Int, b: Int): Int? {
    val ra = a / 3; val ca = a % 3
    val rb = b / 3; val cb = b % 3
    // 同行且列差 2, 例如 0,2
    if (ra == rb && kotlin.math.abs(ca - cb) == 2) return a + (b - a) / 2
    // 同列且行差 2
    if (ca == cb && kotlin.math.abs(ra - rb) == 2) return a + (b - a) / 2
    // 对角线
    if (kotlin.math.abs(ra - rb) == 2 && kotlin.math.abs(ca - cb) == 2) return a + (b - a) / 2
    return null
}
