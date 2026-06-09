# Tasks

- [x] Task 1: 工具函数 TextStyleActions.kt
  - [x] SubTask 1.1: 实现 `wrapSelectionWithMarker(value, marker)`, 选区非空时包 `marker`+选区+`marker`, 选区为空返回原 value
  - [x] SubTask 1.2: 实现 `toggleWrap(value, marker)`, 若选区已被 marker 包裹则移除, 否则调用 `wrapSelectionWithMarker`
  - [x] SubTask 1.3: 实现 `wrapParagraphWithAlign(value, align)`, 找 `selection.start` 所在段落起止, 包 `[align=align]...[/align]`
  - [x] SubTask 1.4: 实现 `replaceAlignOnParagraph(value, align)`, 段落已存在对齐标记时替换为新对齐
  - [x] SubTask 1.5: 实现 `findParagraphRange(text, offset)`, 给定字符 offset 返回 (start, end) 段落边界
  - [x] SubTask 1.6: 全部函数加单元注释, 标好参数/返回值/选区更新方式

- [x] Task 2: MarkdownTableRenderer.kt 组件
  - [x] SubTask 2.1: 写 `data class TableData(headers: List<String>, rows: List<List<String>>)`
  - [x] SubTask 2.2: 写正则 `TABLE_REGEX` 匹配 markdown 表格块 (标题行 + 分隔行 + 至少 1 行数据)
  - [x] SubTask 2.3: 写 `parseMarkdownTable(block: String): TableData?` 解析器, 失败返回 null
  - [x] SubTask 2.4: 写 `serializeTable(data: TableData): String` 反向序列化
  - [x] SubTask 2.5: 写 `@Composable MarkdownTable(data, onCellEdit)` 渲染 Compose Table 组件
    - 使用 `Column + Row + weight + border` 自实现 (compose-bom 2023.08.00 缺 foundation 1.6+ 的 Table API)
    - 表头加粗 + 浅灰底色
    - 单元格点击切换为 `BasicTextField` 编辑
  - [x] SubTask 2.6: 编辑单元格后回写 `data`, 触发 `onCellEdit` 回调把新 markdown 字符串回传上层

- [x] Task 3: NoteEditScreen.kt 改造
  - [x] SubTask 3.1: 引入 `androidx.compose.ui.text.input.TextFieldValue`
  - [x] SubTask 3.2: `BasicTextField` 的 `value` 改 `TextFieldValue`, `onValueChange` 改接收 `TextFieldValue`
  - [x] SubTask 3.3: 把 onValueChange 包装为同步入撤销栈, 保留 `NoteSnapshot(title, content)` 栈结构
  - [x] SubTask 3.4: Aa 面板 4 个按钮回调改为 `() -> Unit`, 用 `toggleWrap` / `wrapSelectionWithTag` 实现
  - [x] SubTask 3.5: 字号/字色按钮回调同样, 用对应包裹标记
  - [x] SubTask 3.6: 「左对齐 / 居中 / 右对齐」按钮改用 `wrapParagraphWithAlign` (逐段包绕)
  - [x] SubTask 3.7: 选区为空时点击样式按钮, 弹 Toast「请先选中要修改的文字」
  - [x] SubTask 3.8: 渲染正文章节: 解析 markdown 表格块, 表格走 `MarkdownTable` 组件, 普通段落走 `BasicTextField`

- [x] Task 4: TableInsertDialog.kt 调整
  - [x] SubTask 4.1: 确认行/列输入校验 (1-20 行, 1-10 列)
  - [x] SubTask 4.2: 回调 `onInsert: (rows, cols) -> Unit`, 由调用方在 content 中插入 markdown 表格
  - [x] SubTask 4.3: 「取消 / 插入」按钮保留现有交互

- [x] Task 5: 编译与运行验证
  - [x] SubTask 5.1: 跑 `gradle :app:assembleDebug`, 沙箱无 AGP 8.0.2 镜像, 本机 (Windows / Gradle 8.2) 验证
  - [x] SubTask 5.2: 检查未用 import / 未用变量, 一并清理 (OutlinedTextField / LaunchedEffect 已清)
  - [x] SubTask 5.3: 确认 `versionName` 仍为 `1.0.3`, `versionCode = 3`

# Task Dependencies
- Task 2 依赖 Task 1 (TextStyleActions 是表格解析的辅助工具)
- Task 3 依赖 Task 1, Task 2
- Task 4 依赖 Task 3 (回调签名同步)
- Task 5 依赖 Task 1, Task 2, Task 3, Task 4
