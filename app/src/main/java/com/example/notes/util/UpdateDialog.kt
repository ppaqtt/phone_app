package com.example.notes.util

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 版本更新弹窗
 */
@Composable
fun UpdateDialog(
    currentVersion: String,
    latestVersion: String,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本") },
        text = {
            Text(
                "当前版本 v$currentVersion\n最新版本 v$latestVersion\n\n" +
                "更新后将获得最新功能与体验, 建议立即升级。"
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdate) { Text("立即更新") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        }
    )
}
