package com.qingjian.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Markdown 表格 → Compose 可视化表格组件。
 *
 * 存储仍保留 markdown 字符串 (向后兼容旧笔记),
 * 渲染时用 [parseMarkdownTable] 解析, 失败时回退 [PlainTableText]。
 *
 * compose-bom 2023.08.00 自带 foundation 1.5.x, **没有** `androidx.compose.foundation.layout.Table`
 * (该 API 在 1.6.0 才出)。本组件用 `Column + Row + weight + border` 自实现。
 *
 * 单元格可点击编辑, 触发 [onCellEdit] 把整张表的新 markdown 字符串回调。
 */

/** 解析后的表格数据。rows 是 List<List<String>>, 长度等于 headers.size */
data class TableData(
    val headers: List<String>,
    val rows: List<List<String>>
) {
    val colCount: Int get() = headers.size
}

/**
 * Markdown 表格块匹配正则。
 * - 第 1 行: 标题行, 形如 `| H1 | H2 |` (至少 2 个 |)
 * - 第 2 行: 分隔行, 形如 `| -- | -- |` (允许 `:--` / `--:` 等对齐)
 * - 第 3..n 行: 数据行, 形如 `| A | B |`
 * - 行尾允许 \r
 * - 整块允许 \n 包围
 */
private val TABLE_REGEX = Regex(
    """^[ \t]*\|[^\n]*\|[ \t]*\r?\n[ \t]*\|[ \t]*:?-+:?[ \t]*(\|[ \t]*:?-+:?[ \t]*)+\|[ \t]*\r?\n((?:[ \t]*\|[^\n]*\|[ \t]*\r?\n?)+)""",
    RegexOption.MULTILINE
)

/** 抽取一行中所有单元格, 去掉首尾的 `|`, 拆 trim */
private fun parseCells(line: String): List<String> {
    val trimmed = line.trim().trimEnd('\r')
    val body = if (trimmed.startsWith("|")) trimmed.drop(1) else trimmed
    val body2 = if (body.endsWith("|")) body.dropLast(1) else body
    return body2.split("|").map { it.trim() }
}

/**
 * 解析 markdown 表格块, 成功返回 [TableData], 失败返回 null。
 *
 * @param block 至少包含 1 行分隔 + 1 行数据的整段 markdown 文本
 */
fun parseMarkdownTable(block: String): TableData? {
    val match = TABLE_REGEX.find(block) ?: return null
    val lines = match.value.lines().filter { it.isNotBlank() }
    if (lines.size < 3) return null
    val headerLine = lines.first()
    val sepLine = lines[1]
    val dataLines = lines.drop(2)

    val sepCells = parseCells(sepLine)
    if (sepCells.isEmpty() || sepCells.any { !it.matches(Regex(""":?-+:?""")) }) return null

    val headers = parseCells(headerLine)
    if (headers.isEmpty() || headers.size != sepCells.size) return null

    val rows = dataLines.map { line ->
        val cells = parseCells(line)
        if (cells.size < headers.size) {
            cells + List(headers.size - cells.size) { "" }
        } else {
            cells.take(headers.size)
        }
    }
    return TableData(headers = headers, rows = rows)
}

/**
 * 把 [TableData] 反向序列化为 markdown 表格字符串。
 * 渲染时统一调用, 与编辑态回写共用。
 */
fun serializeTable(data: TableData): String {
    // P42: 把单元格内的 | / \n 转义, 防止破坏表格结构
    fun esc(s: String) = s.replace("|", "\\|").replace("\n", " ")
    val sb = StringBuilder()
    sb.append("| ").append(data.headers.joinToString(" | ") { esc(it) }).append(" |\n")
    sb.append("| ").append((1..data.colCount).joinToString(" | ") { "---" }).append(" |\n")
    data.rows.forEach { row ->
        sb.append("| ").append(row.joinToString(" | ") { esc(it) }).append(" |\n")
    }
    return sb.toString().trimEnd()
}

/**
 * 解析失败时回退: 把整段当作普通文本渲染, 保持原 markdown 表格视觉。
 */
@Composable
fun PlainTableText(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 单元格位置: row=0 表示表头, row>=1 表示数据行; col 是列号 */
private data class CellPos(val row: Int, val col: Int)

/**
 * P56: [CellPos] 是 private data class, 默认无 Saver。
 * 自定义 Saver 处理可空 CellPos?, 让 rememberSaveable 在配置变更/进程恢复时
 * 能正常保存编辑中的单元格位置 (row, col 编码为 "r,c" 字符串)。
 */
private val CellPosSaver: androidx.compose.runtime.saveable.Saver<CellPos?, String> =
    androidx.compose.runtime.saveable.Saver(
        save = { pos -> if (pos == null) "" else "${pos.row},${pos.col}" },
        restore = { s -> if (s.isBlank()) null else s.split(",").let { CellPos(it[0].toInt(), it[1].toInt()) } }
    )

/**
 * 可视化表格组件。
 *
 * @param data 解析后的表格
 * @param onCellEdit 编辑完单元格后回写, 参数是更新后的整张表 markdown 字符串
 * @param readOnly true 时单元格不进入编辑态
 */
@Composable
fun MarkdownTable(
    data: TableData,
    onCellEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false
) {
    var editing by rememberSaveable(stateSaver = CellPosSaver) { mutableStateOf<CellPos?>(null) }
    var editValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
    ) {
        // 表头行 — 用 IntrinsicSize.Max 让列高跟随最长内容
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            data.headers.forEachIndexed { idx, header ->
                val pos = CellPos(0, idx)
                TableCell(
                    text = header,
                    isHeader = true,
                    editing = editing == pos,
                    editValue = editValue,
                    onEditValueChange = { editValue = it },
                    onClick = {
                        if (!readOnly) {
                            // 切换到新单元格前, 先把上次编辑值应用到上次位置
                            if (editing != null && editing != pos) {
                                applyEdit(editing!!, editValue, data, onCellEdit)
                            }
                            editing = pos
                            editValue = TextFieldValue(header)
                        }
                    },
                    onEditDone = {
                        applyEdit(pos, editValue, data, onCellEdit)
                        editing = null
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // 数据行
        data.rows.forEachIndexed { rowIdx, row ->
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                row.forEachIndexed { colIdx, cell ->
                    val pos = CellPos(rowIdx + 1, colIdx)
                    TableCell(
                        text = cell,
                        isHeader = false,
                        editing = editing == pos,
                        editValue = editValue,
                        onEditValueChange = { editValue = it },
                        onClick = {
                            if (!readOnly) {
                                if (editing != null && editing != pos) {
                                    applyEdit(editing!!, editValue, data, onCellEdit)
                                }
                                editing = pos
                                editValue = TextFieldValue(cell)
                            }
                        },
                        onEditDone = {
                            applyEdit(pos, editValue, data, onCellEdit)
                            editing = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** 把 [pos] 单元格的当前值应用到 [data] 并通过 [onCellEdit] 写回 */
private fun applyEdit(
    pos: CellPos,
    value: TextFieldValue,
    data: TableData,
    onCellEdit: (String) -> Unit
) {
    if (pos.row == 0) {
        val newHeaders = data.headers.toMutableList().also {
            if (pos.col < it.size) it[pos.col] = value.text
        }
        onCellEdit(serializeTable(data.copy(headers = newHeaders)))
    } else {
        val newRows = data.rows.map { it.toMutableList() }
        val r = pos.row - 1
        if (r < newRows.size && pos.col < newRows[r].size) {
            newRows[r][pos.col] = value.text
        }
        onCellEdit(serializeTable(data.copy(rows = newRows)))
    }
}

@Composable
private fun TableCell(
    text: String,
    isHeader: Boolean,
    editing: Boolean,
    editValue: TextFieldValue,
    onEditValueChange: (TextFieldValue) -> Unit,
    onClick: () -> Unit,
    onEditDone: () -> Unit,  // P72: 通过 KeyboardActions.onDone 被调用, 不是未使用
    modifier: Modifier = Modifier
) {
    val cellBg = if (isHeader)
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    else Color.Transparent

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 36.dp)
            .background(cellBg)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        if (editing) {
            // P27: 允许单元格内多行, 用户编辑更灵活
            BasicTextField(
                value = editValue,
                onValueChange = onEditValueChange,
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                // P38: 绑 imeAction.Done, 改完按 Enter 立即退出编辑态
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onEditDone() }
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        } else {
            Text(
                text = text,
                style = if (isHeader)
                    MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                else
                    MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}
