package com.example.notes.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * P95: Android 13+ (API 33) 起 POST_NOTIFICATIONS 变为运行时危险权限, 必须
 * 用户授权后才能发通知。旧版代码只在 manifest 静态声明, 没有主动申请流程,
 * 用户收不到提醒。
 *
 * 该工具封装:
 * 1. [hasNotificationPermission] 检测当前是否已授权
 * 2. [rememberNotificationPermissionRequest] Composable 内一键拉起系统弹窗,
 *    用户授权 / 拒绝后回调 [onResult]。
 */
object NotificationPermission {

    /**
     * 当前是否已获得 POST_NOTIFICATIONS 权限 (API < 33 默认 true)。
     */
    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Composable 拉起 POST_NOTIFICATIONS 申请弹窗, 回调 [onResult] 收到结果。
 * 调用一次后 [request] 状态会重置, 可再次触发。
 */
@Composable
fun rememberNotificationPermissionRequest(
    onResult: (granted: Boolean) -> Unit = {}
): MutableState<Boolean> {
    val context = LocalContext.current
    val pendingResult = remember { mutableStateOf<(Boolean) -> Unit> {} }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingResult.value(granted)
        onResult(granted)
    }
    val trigger = remember { mutableStateOf(false) }
    LaunchedEffect(trigger.value) {
        if (trigger.value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                pendingResult.value(true)
                onResult(true)
            }
            trigger.value = false
        }
    }
    return trigger
}
