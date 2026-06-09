# 富文本操作作用于选区 Spec

## Why
清笺笔记 App 当前编辑器有三个体验断点：
- 「表格」只是把等宽 markdown 文本塞进正文, 既不像 Excel 也不可点击编辑
- 「左对齐 / 居中 / 右对齐」是插入 `[align=...]...[/align]` 标记, 而不是直接修改当前段落的文字对齐
- 「Aa 文字样式」是插入 `**` / `_` / `<u>` / `~~` 标记, 而不是直接修改当前选中文本的样式

这次改造把「富文本的三个动作」从「插入标记」升级为「对当前文字生效」。

## What Changes
- **BREAKING**: 编辑器 `BasicTextField` 的 value/onValueChange 从 `String / (String) -> Unit` 改为 `TextFieldValue / (TextFieldValue) -> Unit`, 以追踪 `selection.start` / `selection.end`
- **BREAKING**: 工具栏「对齐 / 样式」回调签名从 `() -> Unit` 改为 `(TextFieldValue) -> TextFieldValue`, 由调用方拿到最新 value 并返回新 value
- 对齐作用于**当前光标所在段落**: 找到 `selection.start` 前后最近的 `\n` 边界, 在该段落的整段前后包 `[align=left|center|right]...[/align]`
- 文字样式作用于**当前选中文字**:
  - 选区非空时 (`selection.start != selection.end`), 在选区前后包 `**` / `_` / `<u>` / `~~`, 选区移到包内末尾
  - 选区为空时, 不插入标记, 弹 Toast 提示「请先选中要修改的文字」
- 表格升级为**可视化 Excel 风格**:
  - 存储仍保留 markdown 表格字符串 (向后兼容旧笔记)
  - 渲染时用正则识别 `\|...\|\n\|...--...\|\n(...)` 模式, 转换为 Compose `Table` 组件
  - 表格内单元格**可点击编辑**, 编辑后回写 markdown
- 表格工具栏「新建表格」仍走 `TableInsertDialog`, 但生成的可视化表格即插即用
- 撤销/重做继续基于 `NoteSnapshot(content)` 栈, 文本改动自动入栈

## Impact
- Affected specs: 编辑器工具栏、笔记正文渲染、表格数据结构
- Affected code:
  - [NoteEditScreen.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/NoteEditScreen.kt) — TextField 改 TextFieldValue, 工具栏回调改签名
  - [TableInsertDialog.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/TableInsertDialog.kt) — 复用为「新建表格」入口
  - 新增 [MarkdownTableRenderer.kt](file:///workspace/app/src/main/java/com/example/notes/ui/components/MarkdownTableRenderer.kt) — markdown ↔ Compose Table 转换
  - 新增 [TextStyleActions.kt](file:///workspace/app/src/main/java/com/example/notes/util/TextStyleActions.kt) — 对齐/样式作用工具函数

## ADDED Requirements

### Requirement: REQ-01 表格可视化 (Excel 风格)
笔记正文中的 markdown 表格, 在阅读态和编辑态都渲染为可识别的 Excel 风格表格组件。

#### Scenario: 渲染 markdown 表格
- **WHEN** 笔记正文中存在 `| H1 | H2 |\n| -- | -- |\n| A | B |\n` 格式的连续块
- **THEN** 渲染为带边框、表头加粗的 Compose `Table` 组件
- **AND** 列宽按内容等分对齐
- **AND** 表头行有底色区分

#### Scenario: 单元格可编辑
- **WHEN** 用户点击表格中某个单元格
- **THEN** 单元格切换为可输入状态
- **AND** 失焦或点击空白处后回写为 markdown 表格字符串
- **AND** 同步更新到 `NoteSnapshot` 撤销栈

#### Scenario: 新建表格
- **WHEN** 用户点击「更多面板 → 表格」并输入行 × 列
- **THEN** 弹出 `TableInsertDialog` 确认
- **AND** 确认后在光标位置插入一个 N 行 × M 列的可视化表格
- **AND** 存储为 markdown 格式, 渲染为 Excel 风格

#### Scenario: 非表格段落正常渲染
- **WHEN** 段落不是 markdown 表格格式
- **THEN** 走原有 `Text` 渲染逻辑, 不误判

### Requirement: REQ-02 对齐作用于段落
「左对齐 / 居中 / 右对齐」三个按钮, 点击后作用于**当前光标所在段落的全部文字**。

#### Scenario: 段落无标记
- **WHEN** 光标停在「你好世界」段落中, 用户点击「居中」
- **THEN** 该段落在笔记源文本中变为 `[align=center]你好世界[/align]`
- **AND** 渲染时整段居中显示

#### Scenario: 切换对齐
- **WHEN** 同一段已被 `[align=left]xxx[/align]` 包裹, 用户点击「右对齐」
- **THEN** 替换为 `[align=right]xxx[/align]`, 整段右对齐

#### Scenario: 多段处理
- **WHEN** 选区跨越多段 (从段 A 中部到段 C 中部)
- **THEN** 对段 A、段 B、段 C 三段分别包绕对齐标记
- **AND** 选区起始点定为段 A 起点, 选区终点定为段 C 终点

### Requirement: REQ-03 文字样式作用于选区
「Aa 面板」中的 B / I / U / S / 高亮 / 字号 / 字色, 点击后**直接修改当前选中文字**的样式, 而非插入无意义的标记。

#### Scenario: 选区非空
- **WHEN** 用户选中「世界」二字, 点击「加粗」
- **THEN** 选区被 `**` 包裹, 文本变为 `你好**世界**吗`
- **AND** 渲染时「世界」加粗显示
- **AND** 选区自动移到 `**` 之后以便连续操作

#### Scenario: 选区为空
- **WHEN** 用户光标停在某位置但未选文字, 点击「加粗」
- **THEN** **不插入** `**...**` 标记
- **AND** 弹 Toast 提示「请先选中要修改的文字」

#### Scenario: 字号/字色作用域
- **WHEN** 用户选中文字调整字号或颜色
- **THEN** 该选区被对应样式标记包裹 (如 `<size=18>...</size>` / `<color=#FF0000>...</color>`)
- **AND** 选区为空时同样 Toast 提示

#### Scenario: 切换样式
- **WHEN** 选区已被 `**xxx**` 包裹, 再次点击「加粗」
- **THEN** 移除 `**` 包裹, 文本恢复为 `xxx` (不重复包裹)

## MODIFIED Requirements

### Requirement: REQ-M01 TextField 改用 TextFieldValue
编辑器主输入框的 value / onValueChange 必须升级为支持选区追踪。

#### Scenario: 输入文本
- **WHEN** 用户键入字符
- **THEN** 触发 `onValueChange(newTextFieldValue)`, 内部 `value = newTextFieldValue`
- **AND** 同步入撤销栈

#### Scenario: 光标位置追踪
- **WHEN** 用户点击文本某处
- **THEN** 内部 value 的 `selection.start` / `selection.end` 更新
- **AND** 工具栏回调可读到最新选区

## REMOVED Requirements

### Requirement: REQ-R01 旧的对齐插入式
**Reason**: 之前「对齐」是插入 `[align=...]` 字符串到光标处, 用户体验差 (在空选区点击只插入空标记)。新需求要求对齐作用到段落。
**Migration**: 已存在的 `[align=...]...[/align]` 标记仍可被解析; 新的点击直接覆盖到段落, 不再在光标处插入。

### Requirement: REQ-R02 旧的样式插入式
**Reason**: 之前「加粗 / 斜体 / 下划线 / 删除线」是插入 `**` / `_` / `<u>` / `~~` 到光标处, 空选区点击会留下空标记, 既不可见也不可读。
**Migration**: 选区非空时包绕逻辑保持兼容, 选区为空时改为 Toast 提示, 不再插入空标记。
