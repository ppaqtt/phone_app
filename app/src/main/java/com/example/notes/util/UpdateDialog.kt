package com.example.notes.util

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 版本更新弹窗 (发现新版本时显示)。
 *
 * 显示: 当前版本 / 最新版本 / 发布时间 / release notes 摘要。
 * 「立即更新」按钮会用浏览器打开 GitHub Releases 页面。
 */
@Composable
fun UpdateAvailableDialog(
    currentVersion: String,
    release: AppUpdateChecker.ReleaseInfo,
    errorMessage: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
            TextButton(onClick = {
                // P75: runCatching 空 catch → 加 Toast 反馈 ActivityNotFoundException
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }.onFailure { e ->
                    Toast.makeText(
                        context,
                        "无法打开应用商店: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                onDismiss()
            }) { Text("立即更新") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        }
    )
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
