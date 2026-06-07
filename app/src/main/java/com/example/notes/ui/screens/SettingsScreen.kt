package com.example.notes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.notes.data.PreferencesRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesRepository: PreferencesRepository,
    onSyncNow: () -> Unit,
    onBack: () -> Unit,
    isSyncing: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val syncEnabled by preferencesRepository.syncEnabled.collectAsState(initial = false)
    val syncUrl by preferencesRepository.syncUrl.collectAsState(initial = "")
    val lastSyncTime by preferencesRepository.lastSyncTime.collectAsState(initial = "")
    val syncInterval by preferencesRepository.syncInterval.collectAsState(initial = "30")

    var showUrlDialog by remember { mutableStateOf(false) }
    var tempUrl by remember { mutableStateOf(syncUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                .padding(16.dp)
        ) {
            SyncSection(
                syncEnabled = syncEnabled,
                onToggleSync = {
                    scope.launch {
                        preferencesRepository.setSyncEnabled(it)
                    }
                },
                syncUrl = syncUrl,
                onEditUrl = { showUrlDialog = true },
                lastSyncTime = lastSyncTime,
                syncInterval = syncInterval,
                onSyncIntervalChange = {
                    scope.launch {
                        preferencesRepository.setSyncInterval(it)
                    }
                },
                onSyncNow = onSyncNow,
                isSyncing = isSyncing
            )

            Spacer(modifier = Modifier.height(24.dp))

            AboutSection()
        }
    }

    if (showUrlDialog) {
        EditUrlDialog(
            currentUrl = syncUrl,
            onConfirm = { newUrl ->
                scope.launch {
                    preferencesRepository.setSyncUrl(newUrl)
                    tempUrl = newUrl
                    showUrlDialog = false
                }
            },
            onDismiss = { showUrlDialog = false }
        )
    }
}

@Composable
fun SyncSection(
    syncEnabled: Boolean,
    onToggleSync: (Boolean) -> Unit,
    syncUrl: String,
    onEditUrl: () -> Unit,
    lastSyncTime: String,
    syncInterval: String,
    onSyncIntervalChange: (String) -> Unit,
    onSyncNow: () -> Unit,
    isSyncing: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.CloudSync,
                        contentDescription = "云同步",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "云同步",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Switch(
                    checked = syncEnabled,
                    onCheckedChange = onToggleSync
                )
            }

            if (syncEnabled) {
                Spacer(modifier = Modifier.height(16.dp))

                SettingItem(
                    label = "同步地址",
                    value = syncUrl.take(30) + if (syncUrl.length > 30) "..." else "",
                    onClick = onEditUrl
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingItem(
                    label = "同步间隔",
                    value = "${syncInterval}分钟",
                    onClick = {}
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (lastSyncTime.isNotEmpty()) {
                    Text(
                        "上次同步: $lastSyncTime",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isSyncing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TextButton(
                        onClick = onSyncNow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("立即同步")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingItem(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AboutSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = "关于",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "关于",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "笔记应用 v1.0.0",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "© 2024 Notes App",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EditUrlDialog(
    currentUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑同步地址") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("API 地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url) },
                enabled = url.isNotEmpty()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
