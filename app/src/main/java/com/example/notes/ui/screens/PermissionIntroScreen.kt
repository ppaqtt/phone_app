package com.example.notes.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.notes.util.PermissionIntroPrefs

/**
 * 权限引导页: 首次启动时向用户展示应用需要的各项权限及用途说明，
 * 用户可选择「同意并继续」一次性申请所有权限，或「稍后再说」跳过。
 * 无论哪种方式，引导页仅展示一次。
 */

/** 权限项数据模型 */
private data class PermissionItem(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val permission: String,
    val minSdk: Int = 1 // 需要的最低 SDK, 1 表示所有版本
)

/** 根据当前 Android 版本动态过滤出需要展示的权限列表 */
@Composable
private fun rememberPermissionList(): List<PermissionItem> {
    return remember {
        buildList {
            // Android 13+ (API 33) 权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionItem(
                    name = "通知",
                    description = "发送笔记提醒和备份通知",
                    icon = Icons.Filled.Notifications,
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    minSdk = Build.VERSION_CODES.TIRAMISU
                ))
                add(PermissionItem(
                    name = "读取图片",
                    description = "在笔记中插入和浏览图片",
                    icon = Icons.Filled.Image,
                    permission = Manifest.permission.READ_MEDIA_IMAGES,
                    minSdk = Build.VERSION_CODES.TIRAMISU
                ))
                add(PermissionItem(
                    name = "读取视频",
                    description = "在笔记中插入和浏览视频",
                    icon = Icons.Filled.VideoFile,
                    permission = Manifest.permission.READ_MEDIA_VIDEO,
                    minSdk = Build.VERSION_CODES.TIRAMISU
                ))
                add(PermissionItem(
                    name = "读取音频",
                    description = "在笔记中插入和播放音频",
                    icon = Icons.Filled.Audiotrack,
                    permission = Manifest.permission.READ_MEDIA_AUDIO,
                    minSdk = Build.VERSION_CODES.TIRAMISU
                ))
            }
            // 所有版本通用权限
            add(PermissionItem(
                name = "相机",
                description = "拍照并插入笔记",
                icon = Icons.Filled.CameraAlt,
                permission = Manifest.permission.CAMERA
            ))
            add(PermissionItem(
                name = "麦克风",
                description = "语音输入和录音",
                icon = Icons.Filled.Mic,
                permission = Manifest.permission.RECORD_AUDIO
            ))
            // Android 12 及以下使用旧版存储权限
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                add(PermissionItem(
                    name = "读取存储",
                    description = "读取设备上的文件和媒体",
                    icon = Icons.Filled.Storage,
                    permission = Manifest.permission.READ_EXTERNAL_STORAGE
                ))
            }
        }
    }
}

/** 单个权限项的 Composable */
@Composable
private fun PermissionItemRow(item: PermissionItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.name,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 权限引导页 Composable。
 *
 * @param onComplete 引导完成（同意或跳过）后的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionIntroScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val permissionList = rememberPermissionList()

    // 记录是否已触发过权限申请（防止回调中重复触发）
    var hasRequested by remember { mutableStateOf(false) }

    // 一次性申请所有权限的 launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // 无论授权结果如何，标记已展示并完成
        PermissionIntroPrefs.markShown(context)
        hasRequested = true
        onComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用权限") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 权限列表
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(permissionList) { item ->
                    PermissionItemRow(item)
                    Divider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 底部按钮区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 稍后再说 — 次要操作
                TextButton(onClick = {
                    PermissionIntroPrefs.markShown(context)
                    onComplete()
                }) {
                    Text("稍后再说")
                }

                // 同意并继续 — 主操作
                Button(onClick = {
                    if (!hasRequested) {
                        hasRequested = true
                        val permissions = permissionList.map { it.permission }.toTypedArray()
                        permissionLauncher.launch(permissions)
                    }
                }) {
                    Text("同意并继续")
                }
            }
        }
    }
}
