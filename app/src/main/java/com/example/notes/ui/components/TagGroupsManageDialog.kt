package com.example.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.notes.data.TagGroupEntity

/**
 * 中价值/中工作量: 标签分组管理对话框。
 * 显示所有分组 + 每个分组下的标签；支持新建分组、删除分组、增删分组内标签。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagGroupsManageDialog(
    tagGroups: List<TagGroupEntity>,
    allTagsInNote: List<String>,
    getTagsForGroup: suspend (Long) -> List<String>,
    onDismiss: () -> Unit,
    onCreateGroup: (String) -> Unit,
    onDeleteGroup: (TagGroupEntity) -> Unit,
    onAddTagToGroup: (String, Long) -> Unit,
    onRemoveTagFromGroup: (String, Long) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    // 缓存: groupId → 该组下的标签列表
    val tagCache = remember { mutableStateMapOf<Long, List<String>>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标签分组管理") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 新建分组按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showCreateDialog = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "新建分组",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "新建分组",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (tagGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无分组，点击上方按钮创建一个",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tagGroups, key = { it.id }) { group ->
                            var expanded by remember(group.id) { mutableStateOf(false) }
                            val tags = tagCache[group.id] ?: emptyList()

                            // 展开时异步加载标签
                            LaunchedEffect(group.id, expanded) {
                                if (expanded) {
                                    tagCache[group.id] = getTagsForGroup(group.id)
                                }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // 分组标题行
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color(group.color))
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = group.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${tags.size} 个标签",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        IconButton(
                                            onClick = {
                                                onDeleteGroup(group)
                                                tagCache.remove(group.id)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "删除分组",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // 点击展开/收起
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { expanded = !expanded },
                                        color = Color.Transparent
                                    ) {
                                        Text(
                                            if (expanded) "▲ 收起" else "▼ 展开",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }

                                    if (expanded) {
                                        Spacer(Modifier.height(8.dp))
                                        // 显示该分组下的标签
                                        if (tags.isNotEmpty()) {
                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                tags.forEach { tag ->
                                                    Surface(
                                                        shape = RoundedCornerShape(16.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(
                                                                start = 8.dp,
                                                                end = 2.dp,
                                                                top = 4.dp,
                                                                bottom = 4.dp
                                                            ),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = tag,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                                            )
                                                            IconButton(
                                                                onClick = {
                                                                    onRemoveTagFromGroup(tag, group.id)
                                                                    tagCache[group.id] = tags - tag
                                                                },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(
                                                                    Icons.Filled.Close,
                                                                    contentDescription = "移除",
                                                                    modifier = Modifier.size(14.dp),
                                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 添加标签到该分组
                                        val ungroupedTags = allTagsInNote.filter { tag ->
                                            tagCache.values.flatten().none { it == tag }
                                        }
                                        if (ungroupedTags.isNotEmpty()) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                "添加标签:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                ungroupedTags.forEach { tag ->
                                                    FilterChip(
                                                        selected = false,
                                                        onClick = {
                                                            onAddTagToGroup(tag, group.id)
                                                            tagCache[group.id] = tags + tag
                                                        },
                                                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Filled.Add,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )

    // 新建分组对话框
    if (showCreateDialog) {
        var groupName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建分组") },
            text = {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("分组名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            onCreateGroup(groupName.trim())
                            showCreateDialog = false
                        }
                    }
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            }
        )
    }
}
