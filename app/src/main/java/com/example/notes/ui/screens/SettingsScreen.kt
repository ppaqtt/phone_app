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
import com.example.notes.util.ChangelogData
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
                        // P-FIX-002: 用户点"检查更新"是明确意图, 跳过内存缓存 forceRefresh=true
                        val result = AppUpdateChecker.checkForUpdate(forceRefresh = true)
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
    // P-FIX-005: 从 ChangelogData 数据驱动渲染, 不再在 Composable 里硬编码 20+ 版本。
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
            ChangelogData.entries.forEachIndexed { index, entry ->
                if (index > 0) Spacer(Modifier.height(16.dp))
                ChangelogVersion(
                    version = entry.version,
                    date = entry.date,
                    items = entry.items
                )
            }
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
