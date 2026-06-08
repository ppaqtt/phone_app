package com.example.notes.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.notes.BuildConfig
import com.example.notes.util.AppUpdateChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FEEDBACK_URL =
    "https://docs.qq.com/form/page/DVk56eEJwc3diVUVZ"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showUpdateDialog by remember { mutableStateOf(false) }
    var showNoUpdateTip by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    val latestVersion = AppUpdateChecker.LATEST_VERSION

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AboutHeaderCard()
            AboutInfoCard()
            UpdateCheckCard(
                isChecking = isChecking,
                onCheck = {
                    scope.launch {
                        isChecking = true
                        // 模拟网络检查耗时
                        delay(800)
                        isChecking = false
                        if (AppUpdateChecker.isUpdateAvailable(context)) {
                            showUpdateDialog = true
                        } else {
                            showNoUpdateTip = true
                        }
                    }
                }
            )
            FeedbackCard()
            ChangelogCard()
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showUpdateDialog) {
        UpdateDialog(
            currentVersion = BuildConfig.VERSION_NAME,
            latestVersion = latestVersion,
            onDismiss = { showUpdateDialog = false },
            onUpdate = {
                showUpdateDialog = false
                // 实际项目中此处可跳转到应用市场或下载页
            }
        )
    }

    if (showNoUpdateTip) {
        AlertDialog(
            onDismissRequest = { showNoUpdateTip = false },
            title = { Text("已是最新版本") },
            text = { Text("当前版本 v${BuildConfig.VERSION_NAME} 已经是最新版本。") },
            confirmButton = {
                TextButton(onClick = { showNoUpdateTip = false }) { Text("好的") }
            }
        )
    }
}

@Composable
private fun AboutHeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = "清笺",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "一款简洁的笔记应用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AboutInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            InfoRow(label = "应用名称", value = "清笺")
            InfoRow(label = "当前版本", value = "v${BuildConfig.VERSION_NAME}")
            InfoRow(label = "开发者", value = "平平的小破站")
            InfoRow(label = "版权所有", value = "© 2026 清笺")
            InfoRow(label = "ICP 备案", value = "暂无")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun UpdateCheckCard(
    isChecking: Boolean,
    onCheck: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isChecking, onClick = onCheck)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "检查更新",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isChecking) "正在检查…" else "点击检查最新版本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isChecking) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
private fun FeedbackCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, FEEDBACK_URL.toUri())
                runCatching {
                    context.startActivity(intent)
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Feedback,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "问题反馈",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "点击跳转腾讯文档填写反馈",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.OpenInBrowser,
                contentDescription = "打开链接",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChangelogCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "更新日志",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            ChangelogVersion(
                version = "v1.0.2",
                date = "2026-06-08",
                items = listOf(
                    "重写：笔记编辑界面为极简风格 (顶部 4 按钮 + 元信息行 + 大文本区 + 底部 7 图标工具栏)",
                    "新增：工具面板 (选中工具时浮起, 含图片/待办/分栏/文字/列表/AI 等子项)",
                    "新增：多图支持 — 笔记可添加多张图片 (不再只是封面), 横向缩略图画廊 + 序号 + 删除按钮",
                    "优化：图片选择器支持一次性多选 (最多 9 张)",
                    "修复：颜色保存/读取的 toArgb 转换 bug"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v${AppUpdateChecker.LATEST_VERSION}",
                date = "2026-06-07",
                items = listOf(
                    "新增：应用启动时自动检查更新",
                    "新增：笔记支持多张图片（不再只是封面）",
                    "新增：笔记分享功能",
                    "新增：关于页面与更新日志",
                    "新增：问题反馈入口（腾讯文档）",
                    "优化：新建笔记界面极简改版",
                    "优化：默认笔记颜色调整为白色",
                    "移除：云同步相关功能"
                )
            )
        }
    }
}

@Composable
private fun ChangelogVersion(version: String, date: String, items: List<String>) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = version,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "• ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
