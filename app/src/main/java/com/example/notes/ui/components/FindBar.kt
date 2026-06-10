package com.example.notes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * F5: 笔记内查找条。点击 next / prev 时光标跳到下一个 / 上一个匹配。
 * 不修改原文, 只调整 [TextFieldValue] 的 selection, 由 BasicTextField
 * 自带的 selection 渲染高亮当前选区。
 *
 * 用法:
 * ```
 * var findQuery by remember { mutableStateOf("") }
 * var findIndex by remember { mutableStateOf(0) }
 * ...
 * if (showFindBar) {
 *     FindBar(
 *         query = findQuery,
 *         matchCount = countMatches(content.text, findQuery),
 *         currentIndex = findIndex,
 *         onQueryChange = { findQuery = it; findIndex = 0 },
 *         onNext = { findIndex = (findIndex + 1) % count },
 *         onPrev = { findIndex = (findIndex - 1 + count) % count },
 *         onClose = { showFindBar = false }
 *     )
 * }
 * ```
 */
@Composable
fun FindBar(
    query: String,
    matchCount: Int,
    currentIndex: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("在笔记中查找") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (matchCount > 0) onNext() }
                )
            )

            // 计数显示: "3 / 12" 或 "无结果" 或空查询时 "0 / 0"
            val counter = when {
                query.isBlank() -> ""
                matchCount == 0 -> "无结果"
                else -> "${currentIndex + 1} / $matchCount"
            }
            Text(
                text = counter,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            IconButton(onClick = onPrev, enabled = matchCount > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上一个")
            }
            IconButton(onClick = onNext, enabled = matchCount > 0) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下一个")
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }
        }
    }
}

/**
 * F5: 在 [text] 中找出 [query] 出现的所有位置 (不区分大小写)。
 * @return 每个匹配的 (start, end) 区间, end 是 exclusive。
 */
fun findAllMatches(text: String, query: String): List<IntRange> {
    if (query.isBlank()) return emptyList()
    val result = mutableListOf<IntRange>()
    val lower = text.lowercase()
    val lowerQuery = query.lowercase()
    var idx = 0
    while (true) {
        val found = lower.indexOf(lowerQuery, idx)
        if (found < 0) break
        result.add(found until (found + query.length))
        idx = found + 1
    }
    return result
}
