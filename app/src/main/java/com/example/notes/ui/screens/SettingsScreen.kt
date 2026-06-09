package com.example.notes.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.notes.BuildConfig
import com.example.notes.R
import com.example.notes.util.AppUpdateChecker
import com.example.notes.util.NoUpdateDialog
import com.example.notes.util.UpdateAvailableDialog
import com.example.notes.util.readApkMetadata
import kotlinx.coroutines.launch

private const val FEEDBACK_URL =
    "https://docs.qq.com/form/page/DVk56eEJwc3diVUVZ"

private const val PRIVACY_POLICY_URL =
    "https://qing-jian.ppaqtt.com/privacy"

/** 内部法律页面枚举 (用于本地切换 AboutLegalScreen) */
private enum class LegalPage { PRIVACY, TERMS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var showUpdateDialog by remember { mutableStateOf(false) }
    var showNoUpdateTip by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var lastCheckResult by remember { mutableStateOf<AppUpdateChecker.UpdateCheckResult?>(null) }

    // 法律页面本地切换 (隐私政策 / 使用条款)
    var legalPage by remember { mutableStateOf<LegalPage?>(null) }
    // 备案信息 Dialog 开关
    var showFilingDialog by remember { mutableStateOf(false) }

    if (legalPage != null) {
        val (title, rawResId) = when (legalPage) {
            LegalPage.PRIVACY -> "隐私政策" to R.raw.privacy_policy
            LegalPage.TERMS -> "使用条款" to R.raw.terms_of_service
            else -> "" to 0
        }
        AboutLegalScreen(
            title = title,
            rawResId = rawResId,
            onBack = { legalPage = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
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
                        val result = AppUpdateChecker.checkForUpdate()
                        lastCheckResult = result
                        isChecking = false
                        if (result.hasUpdate) {
                            showUpdateDialog = true
                        } else {
                            showNoUpdateTip = true
                        }
                    }
                }
            )
            FeedbackCard()
            ChangelogCard()
            LegalEntriesCard(
                onOpenPrivacy = { legalPage = LegalPage.PRIVACY },
                onOpenTerms = { legalPage = LegalPage.TERMS },
                onOpenFiling = { showFilingDialog = true }
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showUpdateDialog && lastCheckResult?.releaseInfo != null) {
        UpdateAvailableDialog(
            currentVersion = lastCheckResult!!.currentVersion,
            release = lastCheckResult!!.releaseInfo!!,
            errorMessage = lastCheckResult!!.errorMessage,
            onDismiss = { showUpdateDialog = false }
        )
    }

    if (showNoUpdateTip && lastCheckResult != null) {
        NoUpdateDialog(
            currentVersion = lastCheckResult!!.currentVersion,
            errorMessage = lastCheckResult!!.errorMessage,
            onDismiss = { showNoUpdateTip = false }
        )
    }

    if (showFilingDialog) {
        FilingInfoDialog(onDismiss = { showFilingDialog = false })
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
                version = "v1.0.4",
                date = "2026-06-09",
                items = listOf(
                    "新增：APP 图标更换为「清笺」卷轴+毛笔 PNG, 5 密度 mipmap 自适配",
                    "新增：app_name 便签 → 清笺, 与图标命名一致",
                    "新增：启动画面改用 ic_launcher_source.png 居中 + app_name 紧贴下方, 移除 Lottie",
                    "新增：富文本对齐作用于当前光标所在段落 (左/中/右)",
                    "新增：Aa 文字样式 (B/I/U/S/高亮/字号/字色) 作用于当前选区, 选区为空时 Toast 提示",
                    "新增：富文本样式切换 (再次点击同一样式自动移除 marker)",
                    "升级：表格插入升级为 Excel 风格可视化渲染, 单元格可点击编辑后回写 markdown",
                    "升级：编辑器 BasicTextField 改用 TextFieldValue, 可追踪光标 / 选区",
                    "升级：撤销/重做栈接上 TextFieldValue 改动",
                    "升级：版本号 v1.0.3 → v1.0.4 (versionCode 3 → 4)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.0.3",
                date = "2026-06-08",
                items = listOf(
                    "移除：AI 工具栏 (底部工具栏从 7 图标减为 6 图标)",
                    "新增：分栏面板 4 子页签 (文字样式 / 符号 / 分割线 / 图文模版)",
                    "新增：Aa 文字格式面板 (B / I / U / S / 高亮 + 8 档字号 + 7 色字体颜色)",
                    "新增：列表面板 6 按钮 (左/中/右对齐 + 圆点/数字/字母编号)",
                    "新增：待办切换 ☐ (单按钮插入或去除)",
                    "新增：拍照调起系统摄像头 + 去除文档扫码",
                    "新增：更多面板 3 入口 (涂鸦 / 表格 / 音频)",
                    "新增：涂鸦白板 Dialog (颜色/粗细/撤销/重做/清空, 导出 PNG)",
                    "新增：表格插入 (输入行×列, 生成等宽对齐 markdown 表格)",
                    "新增：音频读取 (OpenDocument audio/*)",
                    "新增：撤销 / 重做按钮接上快照栈",
                    "新增：主界面右滑笔记卡片弹出 5 动作菜单 (置顶/标签/删除/移动/重要度)",
                    "新增：5 个动作全部接上 ViewModel/Repository (重要度 0/1/2 三档)",
                    "简化：元信息行去除置顶和提醒小图标",
                    "升级：Room v3 → v4 (NoteEntity 新增 priority 字段)",
                    "新增：5 个运行时权限 (CAMERA / RECORD_AUDIO / READ_MEDIA_*)",
                    "升级：检查更新接 GitHub Releases API (OkHttp 真实请求), 失败时回退本地版本",
                    "升级：发现新版本时显示 release notes 摘要, 一键跳转到 GitHub Releases 页面",
                    "升级：版本号 v1.0.0 → v1.0.3 (versionCode 1 → 3)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.0.2",
                date = "2026-06-08",
                items = listOf(
                    "重写：笔记编辑界面为极简风格 (顶部 4 按钮 + 元信息行 + 大文本区 + 底部工具栏)",
                    "新增：工具面板 (选中工具时浮起, 含图片/待办/分栏/文字/列表等子项)",
                    "新增：多图支持 — 笔记可添加多张图片 (不再只是封面), 横向缩略图画廊 + 序号 + 删除按钮",
                    "优化：图片选择器支持一次性多选 (最多 9 张)",
                    "修复：颜色保存/读取的 toArgb 转换 bug"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.0.1",
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

/** 「关于」下方 3 个法律相关入口 (隐私政策 / 使用条款 / 应用备案信息) */
@Composable
private fun LegalEntriesCard(
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenFiling: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            LegalEntryRow(
                icon = Icons.Filled.PrivacyTip,
                title = "隐私政策",
                subtitle = "查看 APP 隐私政策全文",
                onClick = onOpenPrivacy
            )
            DividerRow()
            LegalEntryRow(
                icon = Icons.Filled.Gavel,
                title = "使用条款",
                subtitle = "查看 APP 使用条款全文",
                onClick = onOpenTerms
            )
            DividerRow()
            LegalEntryRow(
                icon = Icons.Filled.Verified,
                title = "应用备案信息",
                subtitle = "ICP 备案: 包名 / 版本 / 签名 SHA1 / MD5",
                onClick = onOpenFiling
            )
        }
    }
}

@Composable
private fun LegalEntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DividerRow() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** 「应用备案信息」Dialog: 显示 6 字段 + 每行复制按钮 */
@Composable
private fun FilingInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val meta = remember { readApkMetadata(context) }
    val signatureSha1 = meta.signature?.sha1 ?: "获取失败"
    val signatureMd5 = meta.signature?.md5 ?: "获取失败"
    val applicationId = meta.applicationId
    val versionName = meta.versionName
    val versionCode = meta.versionCode

    val rows = listOf(
        FilingRow("应用包名", applicationId),
        FilingRow("版本名 (versionName)", versionName),
        FilingRow("版本号 (versionCode)", versionCode.toString()),
        FilingRow("签名 SHA1", signatureSha1),
        FilingRow("签名 MD5", signatureMd5),
        FilingRow("隐私政策 URL", PRIVACY_POLICY_URL)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("应用备案信息", fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "以下信息用于 ICP 备案填写, 点击右侧「复制」可复制单行内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                rows.forEachIndexed { index, row ->
                    FilingInfoRow(row = row, onCopy = { copyToClipboard(context, row.label, row.value) })
                    if (index != rows.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

private data class FilingRow(val label: String, val value: String)

@Composable
private fun FilingInfoRow(row: FilingRow, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
        TextButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("复制")
        }
    }
}

/** 复制文本到系统剪贴板, 弹 Toast 提示 */
private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "已复制: $label", Toast.LENGTH_SHORT).show()
}
