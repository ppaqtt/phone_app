package com.example.notes.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.example.notes.ui.theme.ColorTheme
import com.example.notes.ui.theme.DarkMode
import com.example.notes.ui.theme.FontScale
import com.example.notes.ui.theme.rememberThemePreference
import com.example.notes.ui.viewmodel.NotesViewModel
import com.example.notes.util.AppLockStore
import com.example.notes.util.AppUpdateChecker
import com.example.notes.util.NoUpdateDialog
import com.example.notes.util.UpdateAvailableDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FEEDBACK_URL =
    "https://docs.qq.com/form/page/DVk56eEJwc3diVUVZ"

/** 内部法律页面枚举 (用于本地切换 AboutLegalScreen) */
private enum class LegalPage { PRIVACY, TERMS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: NotesViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showUpdateDialog by remember { mutableStateOf(false) }
    var showNoUpdateTip by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var lastCheckResult by remember { mutableStateOf<AppUpdateChecker.UpdateCheckResult?>(null) }

    // 法律页面本地切换 (隐私政策 / 使用条款)
    var legalPage by remember { mutableStateOf<LegalPage?>(null) }

    // F1: 备份相关
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val backupState by viewModel.backupState.collectAsState()

    // F1: 把备份结果以 Toast / Snackbar 形式反馈
    LaunchedEffect(backupState) {
        when (val s = backupState) {
            is NotesViewModel.BackupState.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.consumeBackupState()
            }
            is NotesViewModel.BackupState.Error -> {
                Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                viewModel.consumeBackupState()
            }
            else -> Unit
        }
    }

    // F1: SAF - 创建文档 (导出) — 用户选好目标文件后回调
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(context, uri, BuildConfig.VERSION_NAME)
        }
    }

    // F1: SAF - 打开文档 (导入) — 用户选好源文件后先弹确认对话框
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportConfirm = true
        }
    }

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
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
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
            // F7/F8/F11: 外观设置 (深色模式 / 字号 / 主题色)
            AppearanceCard()
            // F1: 数据备份 / 恢复
            BackupCard(
                isWorking = backupState is NotesViewModel.BackupState.Working,
                onExport = {
                    val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val name = "qingjian_backup_$date.json"
                    createDocumentLauncher.launch(name)
                },
                onImport = {
                    openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            )
            // F9: 应用锁
            AppLockCard()
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
                onOpenTerms = { legalPage = LegalPage.TERMS }
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

    // F1: 导入二次确认 — 警告用户"清空旧数据"
    if (showImportConfirm && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("恢复数据") },
            text = {
                Text(
                    "恢复后, 当前数据库中的全部笔记 / 分类 / 图片将被清空, " +
                        "并替换为备份文件中的内容。此操作不可撤销, 建议先导出一份当前数据。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri?.let { viewModel.importBackup(context, it, replaceExisting = true) }
                    showImportConfirm = false
                    pendingImportUri = null
                }) { Text("确认恢复") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    pendingImportUri = null
                }) { Text("取消") }
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
                contentDescription = "应用信息",
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

/**
 * F1: 数据备份 / 恢复卡片。
 * - 导出: 触发 SAF CreateDocument, 选好目标文件后 ViewModel 写 JSON
 * - 导入: 触发 SAF OpenDocument, 选好源文件后弹二次确认, 再 ViewModel 还原数据库
 */
@Composable
private fun BackupCard(
    isWorking: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "数据备份",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "把全部笔记 / 分类 / 图片导出为 JSON 文件, 或从备份文件恢复",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            BackupRowButton(
                icon = Icons.Filled.CloudUpload,
                title = "导出备份",
                subtitle = if (isWorking) "正在导出…" else "保存到本地 (JSON)",
                enabled = !isWorking,
                onClick = onExport
            )
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            BackupRowButton(
                icon = Icons.Filled.CloudDownload,
                title = "从备份恢复",
                subtitle = if (isWorking) "正在恢复…" else "从 JSON 文件恢复 (会清空现有数据)",
                enabled = !isWorking,
                onClick = onImport
            )
            if (isWorking) {
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BackupRowButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * F9: 应用锁配置卡片。
 * - 未启用时显示"启用应用锁"按钮, 点击后引导设置 6 位 PIN
 * - 已启用时显示"已启用 · 修改 PIN / 关闭" 选项
 */
@Composable
private fun AppLockCard() {
    val context = LocalContext.current
    val app = context.applicationContext as com.example.notes.NotesApplication
    val store = remember { app.appLockStore }
    val isEnabled by store.isEnabled.collectAsState()
    var showSetup by remember { mutableStateOf(false) }
    var showDisableConfirm by remember { mutableStateOf(false) }

    if (showSetup) {
        AppLockScreen(
            store = store,
            mode = Mode.SetPin,
            onSuccess = { showSetup = false }
        )
    } else {
        if (showDisableConfirm) {
            AlertDialog(
                onDismissRequest = { showDisableConfirm = false },
                title = { Text("关闭应用锁?") },
                text = { Text("关闭后再次打开应用将不再需要输入 PIN") },
                confirmButton = {
                    TextButton(onClick = {
                        store.disable()
                        showDisableConfirm = false
                    }) { Text("关闭", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDisableConfirm = false }) { Text("取消") }
                }
            )
        }
        AppLockCardContent(
            isEnabled = isEnabled,
            onSetup = { showSetup = true },
            onDisable = { showDisableConfirm = true }
        )
    }
}

@Composable
private fun AppLockCardContent(
    isEnabled: Boolean,
    onSetup: () -> Unit,
    onDisable: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "应用锁",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isEnabled) "已启用, 进入应用时需要输入 6 位 PIN"
                else "启用后, 进入应用 / 切回前台时需要输入 PIN",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            if (isEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onSetup, modifier = Modifier.weight(1f)) {
                        Text("修改 PIN")
                    }
                    TextButton(onClick = onDisable, modifier = Modifier.weight(1f)) {
                        Text("关闭", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                androidx.compose.material3.Button(
                    onClick = onSetup,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("启用应用锁")
                }
            }
        }
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
                contentDescription = "检查更新",
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
private fun AppearanceCard() {
    val pref = rememberThemePreference()
    val current = pref.state.collectAsState().value

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "外观",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            // F7: 深色模式
            Text("深色模式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            SegmentedRow(
                options = DarkMode.values().toList(),
                selected = current.darkMode,
                label = { it.label },
                onSelect = { pref.setDarkMode(it) }
            )

            Spacer(Modifier.height(16.dp))

            // F8: 字号
            Text("字号", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            SegmentedRow(
                options = FontScale.values().toList(),
                selected = current.fontScale,
                label = { it.displayName },
                onSelect = { pref.setFontScale(it) }
            )

            Spacer(Modifier.height(16.dp))

            // F11: 主题色
            Text("主题色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            ColorSwatchRow(
                options = ColorTheme.values().toList(),
                selected = current.colorTheme,
                onSelect = { pref.setColorTheme(it) }
            )
        }
    }
}

private val DarkMode.label: String get() = when (this) {
    DarkMode.SYSTEM -> "跟随系统"
    DarkMode.LIGHT -> "浅色"
    DarkMode.DARK -> "深色"
}

/**
 * 通用 1 行水平按钮组 (用于 3-4 个互斥单选)。
 */
@Composable
private fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            val isSelected = opt == selected
            androidx.compose.material3.Surface(
                onClick = { onSelect(opt) },
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label(opt),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                )
            }
        }
    }
}

/**
 * F11: 主题色色板行, 显示圆点 + 名称。
 */
@Composable
private fun ColorSwatchRow(
    options: List<ColorTheme>,
    selected: ColorTheme,
    onSelect: (ColorTheme) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            val isSelected = opt == selected
            val swatch = when (opt) {
                ColorTheme.TEAL -> androidx.compose.ui.graphics.Color(0xFF2E5D5A)
                ColorTheme.BLUE -> androidx.compose.ui.graphics.Color(0xFF1976D2)
                ColorTheme.PURPLE -> androidx.compose.ui.graphics.Color(0xFF7B1FA2)
                ColorTheme.GREEN -> androidx.compose.ui.graphics.Color(0xFF388E3C)
                ColorTheme.ORANGE -> androidx.compose.ui.graphics.Color(0xFFE65100)
            }
            androidx.compose.material3.Surface(
                onClick = { onSelect(opt) },
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) swatch.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) swatch else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(swatch, MaterialTheme.shapes.small)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = opt.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun FeedbackCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, FEEDBACK_URL.toUri())
                // P75: runCatching 空 catch 静默吞异常 → 加 Toast 反馈
                runCatching { context.startActivity(intent) }
                    .onFailure { e ->
                        Toast.makeText(context, "无法打开链接: ${e.message}", Toast.LENGTH_SHORT).show()
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
                contentDescription = "问题反馈",
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
                "更新日志",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            ChangelogVersion(
                version = "v1.15.0",
                date = "2026-06-10",
                items = listOf(
                    "新增：OCR 文字识别 — 集成 Google ML Kit 中文文本识别 (on-device, 无需网络)。笔记编辑页 IMAGE 工具面板加「识别文字」按钮, 选图后自动识别并插入到笔记正文; 大图自动缩放到 1920px",
                    "升级：版本号 v1.14.0 → v1.15.0 (versionCode 24 → 25)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.14.0",
                date = "2026-06-10",
                items = listOf(
                    "新增：语音转文字 — 封装 Android SpeechRecognizer (系统内置, 无需 API Key)。笔记编辑页底部工具栏加「语音」按钮, 点击请求录音权限后开始聆听; 识别完成自动插入到笔记正文",
                    "升级：版本号 v1.13.0 → v1.14.0 (versionCode 23 → 24)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.13.0",
                date = "2026-06-10",
                items = listOf(
                    "新增：代码块高亮 — 渲染 ```lang ... ``` 围栏代码块, 深色背景 + 语言标签 + 横向滚动; 自带轻量关键字着色 (Kotlin / Java / Python / JS / Go / Rust / C/C++), 不引入第三方库",
                    "升级：版本号 v1.12.0 → v1.13.0 (versionCode 22 → 23)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.12.0",
                date = "2026-06-10",
                items = listOf(
                    "新增：统计仪表盘 — 4 个计数卡 (笔记 / 置顶 / 提醒 / 图片) + 字数卡 (中文字符 / 英文单词 / 平均每篇) + 分类分布卡 (横向比例条) + 月度趋势卡 (最近 6 个月竖向柱状图)",
                    "升级：版本号 v1.11.0 → v1.12.0 (versionCode 21 → 22)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.11.0",
                date = "2026-06-10",
                items = listOf(
                    "新增：嵌套分类 — CategoryEntity 加 parentId 字段, 单层缩进 (0=顶级, 1=子级)。分类管理列表按父→子顺序渲染, 子分类缩进 20dp + ↳ 箭头图标; 新增分类时可选父分类, 父分类候选自动排除自身和所有 descendants 防止循环引用; 删除父分类时自动把子分类提升为顶级; 笔记编辑分类选择 / 列表过滤 chip 同步缩进显示",
                    "升级：Room v7→v8 AutoMigration; 备份导出/导入同步维护 parentOldId 映射, 老备份默认顶级; 版本号 v1.10.0 → v1.11.0 (versionCode 20 → 21)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.10.0",
                date = "2026-06-10",
                items = listOf(
                    "新增：每日重复提醒 — NoteEntity 加 reminderRepeat 字段 (NONE/DAILY/WEEKLY/MONTHLY/YEARLY)。ReminderWorker 触发后若 repeat != NONE, 自动用 Calendar.add 排下次触发; 笔记编辑页加 ReminderCard 卡片, 设置提醒后显示 5 段重复模式选择条",
                    "升级：Room v6→v7 AutoMigration; 版本号 v1.9.0 → v1.10.0 (versionCode 19 → 20)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.9.0",
                date = "2026-06-10",
                items = listOf(
                    "新增：PDF / 长图导出 — 笔记编辑页顶部 MoreVert 下拉新增「导出为 PDF」和「导出为长图 (PNG)」两项, 走 SAF CreateDocument。PDF 走 android.graphics.pdf.PdfDocument 渲染 (A4 自动分页), 长图走 Bitmap + StaticLayout 拼接 (2x 像素密度)",
                    "升级：版本号 v1.8.0 → v1.9.0 (versionCode 18 → 19)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.8.0",
                date = "2026-06-10",
                items = listOf(
                    "新增：应用锁 — AppLockStore 持久化 PIN 的 SHA-256 哈希 (不存明文); AppLockGate 包裹 NavGraph, 启动 / 切回前台检测 5 分钟解锁宽限期; AppLockScreen PIN 数字键盘 + 圆点指示器, 失败 30s 冷却",
                    "升级：版本号 v1.7.0 → v1.8.0 (versionCode 17 → 18)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.7.0",
                date = "2026-06-10",
                items = listOf(
                    "新增：App 快捷方式 (长按桌面图标) — res/xml/shortcuts.xml 注册 3 个动态快捷方式 (新建笔记 / 搜索 / 回收站)。AndroidManifest MainActivity meta-data 指向 shortcuts.xml",
                    "升级：版本号 v1.6.0 → v1.7.0 (versionCode 16 → 17)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.6.0",
                date = "2026-06-10",
                items = listOf(
                    "新增：桌面小部件 (AppWidget) — 4x2 圆角卡片显示最近 5 条笔记 (按 updated_at 倒序), 标题 + 内容预览; 列表项点击通过 setOnClickFillInIntent 打开笔记; 底部 + 按钮快速新建; saveNote 后调 NotesAppWidget.requestRefresh 触发刷新",
                    "升级：版本号 v1.5.0 → v1.6.0 (versionCode 15 → 16)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.5.0",
                date = "2026-06-10",
                items = listOf(
                    "新增：回收站 — NoteEntity 加 deletedAt 字段 (null=正常, 非 null=已删除); 删除改走软删除, 列表 / 搜索 / 按分类观察自动加 deleted_at IS NULL 过滤; TrashScreen 显示 30 天内已删笔记, 每条 2 动作: 恢复 / 永久删除; 顶栏「清空」二次确认; TrashJanitorWorker 24h 后跑一次, 自动清理 30 天前条目 (KEEP 策略幂等)",
                    "升级：Room v5→v6 AutoMigration; 版本号 v1.4.0 → v1.5.0 (versionCode 14 → 15)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.4.0",
                date = "2026-06-10",
                items = listOf(
                    "新增：数据备份 / 恢复 — 全部笔记 / 分类 / 图片导出为 JSON, 走 SAF CreateDocument / OpenDocument; DTO 与 Entity 解耦, 兼容老备份; AUTO_INCREMENT 冲突通过「老 id → 新 id」映射表解决; 外键约束按「图片→笔记→分类」顺序清空 + 反向顺序插入; 导入前 AlertDialog 二次确认",
                    "升级：版本号 v1.3.0 → v1.4.0 (versionCode 13 → 14)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.1.0",
                date = "2026-06-10",
                items = listOf(
                    "修复：编辑页退出时 busy 锁死 — 移除 tryExit/丢弃按钮的 busy = true, 删除路径补 try-finally, 防止下次进入笔记所有按钮永久置灰",
                    "修复：标签批量删除后首尾残留逗号 — removeTagFromAllNotes SQL 套 TRIM(',') 包裹, tags='a,b,c' 删 b 后正确变为 'a,c'",
                    "修复：DAO 异常会闪退 — NotesViewModel 新增 launchSafe 扩展, 8 处 viewModelScope.launch 统一捕获异常并打日志",
                    "修复：SplashScreen 死参数 ready — 移除, 把淡入 / 600ms 等待 / 回调合并为一个 LaunchedEffect",
                    "修复：搜索历史 addSearch/removeSearch 竞态 — 改用 MutableStateFlow.update CAS, 原子完成 read-modify-write",
                    "修复：分享笔记为纯文本时无错误反馈 — shareAsText 补 runCatching + Toast, 与 shareAsImage 风格一致",
                    "升级：版本号 v1.0.9 → v1.1.0 (versionCode 9 → 10)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.0.9",
                date = "2026-06-10",
                items = listOf(
                    "修复：「问题反馈」/「立即更新」无反馈 — runCatching 空 catch 补 Toast, ActivityNotFoundException 给用户提示",
                    "修复：删除笔记后立即返回会丢数据 — viewModelScope.launch 异步改 scope.launch 等待完成后 onBack",
                    "修复：音频 URI 提取无 debounce — 加 500ms 节流, 避免长文输入卡顿",
                    "修复：搜索历史 IO 仍主线程 — addSearch/removeSearch 内部 JSON 移协程 IO",
                    "修复：SimpleDateFormat 每次 new — 统一 4 个 ThreadLocal, 与 fmtFull 保持一致",
                    "修复：右滑背景缺 Share 图标 — NoteActionsBackground 补第 6 个图标与 6 项菜单一致",
                    "修复：deleteInFlight 重置 300ms 太慢 — 改为 100ms, 快速操作时按钮不意外置灰",
                    "修复：MarkdownTable onEditDone @Suppress 误导 — 删除, 注明通过 KeyboardActions.onDone 实际被调用",
                    "修复：MainActivity ViewModel by lazy 模糊生命周期 — 改 onCreate 直接初始化",
                    "升级：版本号 v1.0.8 → v1.0.9 (versionCode 8 → 9)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.0.8",
                date = "2026-06-10",
                items = listOf(
                    "修复：编辑页 saveNote 内部 rememberCoroutineScope 崩溃 (P0) — 提到 Composable 顶部, saveNote 改纯 suspend",
                    "修复：保存按钮 / 退出确认 fire-and-forget 丢数据 (P0) — saveNoteThen 包装, 协程完成后再回调",
                    "修复：CellPos 无 Saver 配置变更崩溃 (P0) — 自定义 CellPosSaver 编码 'row,col'",
                    "修复：删除分类事务不原子 (P0) — Room @Transaction 注解在 Repository 上无效, 改 withTransaction 包裹",
                    "修复：onInsertAtCursor @Suppress 误导 — 贯通到 ColumnsPanel / ListPanel, 符号/模板插入到光标处",
                    "修复：右滑手势每帧 launch 协程风暴 — 改 pointerInput + detectHorizontalDragGestures + Animatable",
                    "修复：分类计数 O(n*m) — 新增 observeNoteCountForCategory, 改用 SQL COUNT",
                    "升级：版本号 v1.0.7 → v1.0.8 (versionCode 7 → 8)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.0.6",
                date = "2026-06-10",
                items = listOf(
                    "修复：「疯狂点击应用导致内容消失」严重 bug — 移除 Room 的 fallbackToDestructiveMigration(), 改为 fallbackToDestructiveMigrationOnDowngrade()",
                    "修复：编辑页保存按钮防重入锁 — 点击一次后立即置灰, 防止快速点击引发数据竞态",
                    "修复：删除对话框防重入锁 — 点击删除后按钮置灰, 关闭 300ms 后重置",
                    "修复：导航页面堆叠 — 所有 navigate 添加 launchSingleTop=true",
                    "升级：版本号 v1.0.5 → v1.0.6 (versionCode 5 → 6)"
                )
            )
            Spacer(Modifier.height(16.dp))
            ChangelogVersion(
                version = "v1.0.5",
                date = "2026-06-09",
                items = listOf(
                    "新增：隐私政策 / 使用条款 Markdown 文档 (res/raw/privacy_policy.md + terms_of_service.md), 含 SDK 清单 / 权限说明 / 数据存储 / 第三方服务 / 联系方式 / 生效日期",
                    "新增：AboutLegalScreen 通用法律文本展示页 (极简 Markdown 渲染: ## 标题 / > 引用 / - 列表 / ** 粗体)",
                    "新增：设置 → 关于 加 2 入口 (隐私政策 / 使用条款), 点击跳转 AboutLegalScreen",
                    "新增：AndroidManifest.xml MainActivity 注册 DeepLink: app://privacy + https://qing-jian.ppaqtt.com/privacy (后者 autoVerify)",
                    "新增：MainActivity 解析 intent.data, 深链接直达隐私政策页 (覆盖启动流程)",
                    "新增：PackageSignatureReader.kt 工具类, 运行时读取 APK 签名 SHA1 / MD5 (兼容 API 24+: API 28+ 走 SigningInfo, API < 28 走 Signature[])",
                    "美化：隐私政策 / 使用条款加 TL;DR 一分钟速览 + emoji 视觉锚点 (📌 / 💾 / 🔐 / ⚖️ / 📬) + 表格化布局 + 联系方式条目化",
                    "优化：隐私政策 / 使用条款「联系我们」对齐 (邮箱 2474922840@qq.com + 项目主页 + 点击「关于 - 问题反馈」)",
                    "升级：版本号 v1.0.4 → v1.0.5 (versionCode 4 → 5)"
                )
            )
            Spacer(Modifier.height(16.dp))
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

/** 「关于」下方 2 个法律相关入口 (隐私政策 / 使用条款) */
@Composable
private fun LegalEntriesCard(
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit
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
            contentDescription = title,
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
            contentDescription = "进入",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DividerRow() {
    androidx.compose.material3.Divider(
        modifier = Modifier.padding(start = 52.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
