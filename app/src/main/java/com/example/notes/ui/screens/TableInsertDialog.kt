package com.example.notes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 表格生成 Dialog: 让用户输入行数和列数, 然后把 [onInsert] 表格 markdown 片段回调。
 * 生成的内容是一段等宽对齐的纯文本表, 直接拼接在 note.content 后面。
 */
@Composable
fun TableInsertDialog(
    onDismiss: () -> Unit,
    onInsert: (rows: Int, cols: Int) -> Unit
) {
    var rowsStr by remember { mutableStateOf("3") }
    var colsStr by remember { mutableStateOf("3") }
    val rows = rowsStr.toIntOrNull() ?: 0
    val cols = colsStr.toIntOrNull() ?: 0
    val canConfirm = rows in 1..20 && cols in 1..10

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("插入表格") },
        text = {
            Column {
                Text("请输入行数和列数 (1-20 行, 1-10 列)")
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = rowsStr,
                        onValueChange = { input ->
                            // 只接受数字串,长度上限放宽到 3 位 (支持 100 行),
                            // 实际范围限制在 canConfirm (1..20) 里再次校验
                            if (input.all(Char::isDigit) && input.length <= 3) rowsStr = input
                        },
                        label = { Text("行数") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = colsStr,
                        onValueChange = { input ->
                            if (input.all(Char::isDigit) && input.length <= 3) colsStr = input
                        },
                        label = { Text("列数") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canConfirm) onInsert(rows, cols); onDismiss() },
                enabled = canConfirm
            ) { Text("插入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 把 (rows x cols) 转成一段 markdown 表格, 用 " | " 分隔, 列宽自动对齐。
 */
fun buildMarkdownTable(rows: Int, cols: Int): String {
    val headers = (1..cols).map { "列$it" }
    val dataRows = (1..rows - 1).map { r -> (1..cols).map { c -> "r${r}c$c" } }

    val allRows = listOf(headers) + dataRows
    val colWidths = (0 until cols).map { idx -> allRows.maxOf { it[idx].length } }

    fun rowToString(cells: List<String>): String {
        val parts = cells.mapIndexed { i, c -> c.padEnd(colWidths[i], ' ') }
        return "| " + parts.joinToString(" | ") + " |"
    }

    val separator = "| " + colWidths.joinToString(" | ") { "-".repeat(it) } + " |"
    return buildString {
        appendLine(rowToString(headers))
        appendLine(separator)
        dataRows.forEach { appendLine(rowToString(it)) }
    }.trimEnd()
}
