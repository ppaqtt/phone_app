package com.example.notes.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * P95: Android 13+ (API 33) 起 POST_NOTIFICATIONS 变为运行时危险权限, 必须
 * 用户授权后才能发通知。旧版代码只在 manifest 静态声明, 没有主动申请流程,
 * 用户收不到提醒。
 *
 * 该工具封装:
 * 1. [hasNotificationPermission] 检测当前是否已授权
 * 2. [shouldShowRationale] 用户拒绝过但未勾"不再询问" (可再弹窗引导)
 * 3. [isPermanentlyDenied] 用户勾了"不再询问" (只能跳设置页)
 * 4. [rememberNotificationPermissionRequest] Composable 内一键拉起系统弹窗,
 *    永久拒绝时自动跳应用设置页。
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

    /**
     * 用户拒绝过但未勾"不再询问" — 可再次弹窗引导。
     * API < 33 返回 false (不需要弹窗)。
     *
     * P96-FIX: 之前错误地取反了 shouldShowRequestPermissionRationale 的结果,
     * 导致用户拒绝后判断逻辑颠倒, rationale 永远显示不出来。修正为不取反。
     */
    fun shouldShowRationale(activity: android.app.Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.POST_NOTIFICATIONS
        )
    }

    /**
     * 用户已永久拒绝 (勾了"不再询问") — 只能跳设置页。
     * 判断逻辑: 已拒绝 + 不应弹 rationale = 永久拒绝。
     */
    fun isPermanentlyDenied(context: Context, activity: android.app.Activity?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (hasPermission(context)) return false
        if (activity == null) return false
        return !ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.POST_NOTIFICATIONS
        )
    }

    /** 打开应用通知设置页 */
    fun openAppSettings(context: Context) {
        val intent = Intent().apply {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                else -> {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}

/**
 * Composable 拉起 POST_NOTIFICATIONS 申请弹窗, 回调 [onResult] 收到结果。
 * 调用一次后 [request] 状态会重置, 可再次触发。
 *
 * 若用户已永久拒绝, 自动跳应用设置页 (不再弹无意义的系统弹窗)。
 */
@Composable
fun rememberNotificationPermissionRequest(
    onResult: (granted: Boolean) -> Unit = {}
): MutableState<Boolean> {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val pendingResult = remember { mutableStateOf<(Boolean) -> Unit>({}) }
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
                if (NotificationPermission.isPermanentlyDenied(context, activity)) {
                    // 永久拒绝: 跳设置页, 不弹无意义的系统弹窗
                    NotificationPermission.openAppSettings(context)
                    onResult(false)
                } else {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                pendingResult.value(true)
                onResult(true)
            }
            trigger.value = false
        }
    }
    return trigger
}
