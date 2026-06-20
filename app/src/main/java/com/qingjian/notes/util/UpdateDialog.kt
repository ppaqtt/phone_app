package com.qingjian.notes.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 版本更新弹窗 (发现新版本时显示)。
 *
 * 显示: 当前版本 / 最新版本 / 发布时间 / release notes 摘要。
 * 按钮:
 *  - 立即下载 (应用内 DownloadManager, 带进度)
 *  - 浏览器打开 (Gitee/GitHub Releases)
 *  - 忽略此版本
 *  - 稍后
 */
@Composable
fun UpdateAvailableDialog(
    currentVersion: String,
    release: AppUpdateChecker.ReleaseInfo,
    errorMessage: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var downloadId by remember { mutableLongStateOf(-1L) }
    var showDownloading by remember { mutableStateOf(false) }
    var showWifiConfirm by remember { mutableStateOf(false) }

    if (showDownloading) {
        DownloadProgressDialog(
            version = release.version,
            downloadId = downloadId,
            release = release,
            onCancel = { showDownloading = false },
            onInstalled = onDismiss
        )
        return
    }

    if (showWifiConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showWifiConfirm = false },
            title = { Text("当前为移动数据") },
            text = { Text("下载设置为 \"仅 WiFi\"，是否继续使用移动数据下载？ (约 10~30 MB)") },
            confirmButton = {
                TextButton(onClick = {
                    showWifiConfirm = false
                    val fallbackId = AppUpdateChecker.enqueueDownloadFallback(
                        context, release, null
                    )
                    if (fallbackId != null) {
                        downloadId = fallbackId
                        showDownloading = true
                        Toast.makeText(context, "开始下载更新...", Toast.LENGTH_SHORT).show()
                    } else {
                        openInBrowser(context, release.htmlUrl.ifBlank { release.bestApkUrl() })
                    }
                }) { Text("继续下载") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showWifiConfirm = false
                    openInBrowser(context, release.htmlUrl.ifBlank { release.bestApkUrl() })
                }) { Text("用浏览器") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${release.version}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("当前版本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("v$currentVersion", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("最新版本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("v${release.version}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                if (release.publishedAt.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "发布于 ${formatPublishedAt(release.publishedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text("更新内容", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    val notes = release.notes
                        .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
                        .replace(Regex("```[\\s\\S]*?```"), "")
                        .replace(Regex("\\[(.+?)]\\((.+?)\\)"), "$1")
                        .trim()
                    Text(
                        text = notes.ifBlank { "本版本修复了若干问题, 优化了使用体验。" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val id = AppUpdateChecker.enqueueDownload(context, release)
                            if (id != null && id >= 0L) {
                                downloadId = id
                                showDownloading = true
                                Toast.makeText(context, "开始下载更新...", Toast.LENGTH_SHORT).show()
                            } else if (id == -2L) {
                                // 被 WiFi 限制: 询问用户
                                showWifiConfirm = true
                            } else {
                                // 普通下载失败: 尝试换源
                                val fallbackId = AppUpdateChecker.enqueueDownloadFallback(
                                    context, release, release.bestApkUrl()
                                )
                                if (fallbackId != null) {
                                    downloadId = fallbackId
                                    showDownloading = true
                                    Toast.makeText(context, "主源不可用, 切换到备用源下载...", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "启动下载失败, 将打开浏览器", Toast.LENGTH_SHORT).show()
                                    openInBrowser(context, release.htmlUrl.ifBlank { release.bestApkUrl() })
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("立即下载")
                    }
                    OutlinedButton(
                        onClick = { openInBrowser(context, release.htmlUrl.ifBlank { release.bestApkUrl() }) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("浏览器")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        AppUpdateChecker.ignoreVersion(context, release.version)
                        Toast.makeText(context, "已忽略 v${release.version}", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }) { Text("忽略此版本") }
                    TextButton(onClick = onDismiss) { Text("稍后") }
                }
            }
        },
        dismissButton = null
    )
}

private fun openInBrowser(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure { e: Throwable ->
        Toast.makeText(
            context,
            "无法打开浏览器: ${e.message}",
            Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * 下载进度对话框。查询 DownloadManager，每 500ms 更新进度条。
 */
@Composable
private fun DownloadProgressDialog(
    version: String,
    downloadId: Long,
    release: AppUpdateChecker.ReleaseInfo,
    onCancel: () -> Unit,
    onInstalled: () -> Unit
) {
    val context = LocalContext.current
    var currentDownloadId by remember { mutableLongStateOf(downloadId) }
    var progress by remember { mutableStateOf(0f) }
    var message by remember { mutableStateOf("正在下载更新包...") }
    var finished by remember { mutableStateOf(false) }
    var switched by remember { mutableStateOf(false) }
    var showSignatureWarning by remember { mutableStateOf(false) }

    LaunchedEffect(currentDownloadId) {
        while (isActive && !finished) {
            val info = AppUpdateChecker.getDownloadProgressEx(context, currentDownloadId)
            if (info == null) {
                // 可能已完成或已失败
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                val cursor = try {
                    dm.query(android.app.DownloadManager.Query().setFilterById(currentDownloadId))
                } catch (_: Exception) { null }
                var status = android.app.DownloadManager.STATUS_FAILED
                var reason = 0
                cursor?.use {
                    if (it.moveToFirst()) {
                        status = it.getInt(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))
                        reason = it.getInt(it.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_REASON))
                    }
                }
                if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                    finished = true
                    message = "下载完成，点击\"立即安装\"更新应用"
                    break
                } else if (!switched) {
                    // 下载失败: 尝试换源
                    switched = true
                    val fallback = AppUpdateChecker.enqueueDownloadFallback(
                        context, release, null
                    )
                    if (fallback != null && fallback >= 0L) {
                        currentDownloadId = fallback
                        message = "当前源下载失败, 已切换到备用源... (错误 $reason)"
                        continue
                    } else if (fallback == -2L) {
                        finished = true
                        message = "当前为移动数据，下载设置为\"仅 WiFi\"，请切换网络或用浏览器下载"
                        break
                    } else {
                        finished = true
                        message = "下载失败, 请在通知栏重试或手动在浏览器下载"
                        break
                    }
                } else {
                    finished = true
                    message = "下载已失败或已取消，请在通知栏查看"
                    break
                }
            }
            val (status, downloaded, total) = info
            if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                finished = true
                message = "下载完成，点击\"立即安装\"更新应用"
                progress = 1f
                break
            }
            if (total > 0L) {
                progress = downloaded.toFloat() / total
            } else {
                progress = -1f // -1 表示大小未知 -> 显示不确定进度条
            }
            if (progress >= 1f && total > 0L) {
                finished = true
                message = "下载完成，点击\"立即安装\"更新应用"
            }
            delay(500)
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("下载 v$version") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (!finished) {
                    if (progress < 0f) {
                        // 大小未知 (total = -1): 显示不确定进度条
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "正在下载...",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.End)
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Done,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (finished) {
                TextButton(onClick = {
                    val uri = AppUpdateChecker.getDownloadedUri(context, currentDownloadId)
                    if (uri != null) {
                        // 安装前校验签名
                        if (AppUpdateChecker.isApkSignatureValid(context, uri)) {
                            AppUpdateChecker.installApk(context, uri)
                            AppUpdateChecker.cleanupOldApks()
                            onInstalled()
                        } else {
                            // 签名不匹配: 删除 APK 并警告用户
                            AppUpdateChecker.deleteDownloadedApk(context, uri)
                            showSignatureWarning = true
                        }
                    } else {
                        Toast.makeText(context, "未找到下载文件，请在通知栏操作", Toast.LENGTH_LONG).show()
                    }
                }) { Text("立即安装") }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(if (finished) "关闭" else "后台继续") }
        }
    )

    // 签名校验失败警告弹窗
    if (showSignatureWarning) {
        AlertDialog(
            onDismissRequest = { showSignatureWarning = false },
            title = { Text("签名校验失败") },
            text = {
                Column {
                    Text("下载的 APK 签名与本应用不一致，可能被篡改或来自非官方渠道。")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "已删除该文件，请通过官方渠道重新下载。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSignatureWarning = false
                    onCancel()
                }) { Text("知道了") }
            }
        )
    }
}

/**
 * 无更新时的提示弹窗 (已是最新版本)。
 */
@Composable
fun NoUpdateDialog(
    currentVersion: String,
    errorMessage: String? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("已是最新版本") },
        text = {
            Column {
                Text("当前版本 v$currentVersion 已是最新。")
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("好的") }
        }
    )
}

/**
 * 把 GitHub 的 ISO-8601 时间戳裁剪成 "2026-06-08" 这种短格式。
 */
private fun formatPublishedAt(iso: String): String {
    return iso.take(10) // "2026-06-08T12:34:56Z" -> "2026-06-08"
}
