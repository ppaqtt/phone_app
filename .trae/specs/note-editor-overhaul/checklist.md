# Checklist

## REQ-01 AI 工具栏下线
- [x] `BottomTool` 枚举无 `AI` 项 — NoteEditScreen.kt:102
- [x] `toolbarItems` 无 AI 元素 — NoteEditScreen.kt:1080
- [x] `SettingsScreen` 更新日志无「AI」字样 (v1.0.2 已修正, v1.0.3 记录移除)
- [ ] APK 安装后底部 6 个图标 (分栏/Aa/列表/待办/图片/更多) — 需部署验证

## REQ-02 分栏 4 子页签
- [x] 顶部 4 页签可见: 文字样式/符号/分割线/图文模版 — ColumnsTab 枚举
- [x] 页签切换内容区对应变化 — ColumnsPanel 内 when 分支
- [x] 选中页签高亮 — tab == ColumnsTab.X
- [x] 文字样式网格可点击插入 markdown 片段 — TextStyleGrid

## REQ-03 Aa 文字格式
- [x] B/I/U/S/高亮 5 按钮可见 — TextFormatPanel
- [x] 8 档字号 (10/12/14/16/18/20/24/36) 可选
- [x] 7 字体颜色块可点击
- [x] 默认 16 字号高亮

## REQ-04 列表 6 按钮
- [x] 6 个按钮等距排列: 左对齐/居中/右对齐/圆点/数字/字母 — ListPanel
- [x] 点击插入对应 markdown 片段

## REQ-05 待办 ☐ 切换
- [x] 空内容点一次 → 正文为 `☐ ` — onToggleTodo 分支 1
- [x] 末尾是 `☐ ` 时再点 → 去掉 — onToggleTodo 分支 2
- [x] 中间行追加 → 换行后再加 `☐ ` — onToggleTodo 分支 3

## REQ-06 拍照
- [x] 首次触发系统弹窗申请 CAMERA — RequestPermission
- [x] 已授权时调起系统相机 — TakePicture
- [x] 拍照后图片插入 imageUris — takePicture 回调
- [x] 缩略图在正文上方可见 — NoteBody 横向画廊
- [x] 文档扫码入口已移除 — ImagePanel 仅剩 2 入口

## REQ-07 更多面板
- [x] 3 个入口: 涂鸦/表格/音频 — MorePanel
- [x] 不再含 AI/翻译/总结 等其他入口

## REQ-08 涂鸦
- [x] 全屏白板 Dialog 弹出 — DoodleDialog
- [x] 手指拖动可实时画线 — detectDragGestures
- [x] 5 种颜色可切换 — brushColor
- [x] 4 档粗细可切换 — brushWidth
- [x] 撤销/重做/清空按钮工作 — paths/redoStack
- [x] 保存导出 PNG 并插入 imageUris — onDone 回调

## REQ-09 表格
- [x] 行/列两个输入框 — TableInsertDialog
- [x] 校验 1..20 / 1..10 — canConfirm
- [x] 确认后生成等宽对齐 markdown 表格 — buildMarkdownTable

## REQ-10 音频
- [x] 弹出系统文件选择器 (audio/*) — pickAudio.launch(arrayOf("audio/*"))
- [x] 选取后 URI 持久读权限 — takePersistableUriPermission
- [x] 正文末尾追加 🎵 标记 — audioUris.add + onInsertText
- [x] 内联可点击 Row 显示 — NoteBody audioUris

## REQ-11 撤销/重做
- [x] 输入后撤销按钮可点击 — derivedStateOf(canUndo)
- [x] 撤销回到上一快照 — undoRedo.undo
- [x] 撤销后重做按钮可点击 — derivedStateOf(canRedo)
- [x] 重做恢复 — undoRedo.redo

## REQ-12 元信息行
- [x] 只有 日期 | 字数 | 分类 三段 — MetaInfoRow
- [x] 不再显示置顶和提醒小图标 — 已去除

## REQ-13 主界面右滑
- [x] 右滑笔记卡片露出 5 动作背景 — SwipeableNoteRow + NoteActionsBackground
- [x] 滑动超过 35% 阈值后弹出菜单 — positionalThreshold 0.35
- [x] 菜单 5 项依次: 置顶/标签/删除/移动/重要度 — NoteActionsRow
- [x] 关闭后卡片回弹到原位 — confirmValueChange = { false }

## REQ-14 5 动作功能
- [x] 置顶切换 isPinned 并落库 — viewModel.togglePin
- [x] 标签输入逗号分隔并存 — TagsEditDialog + viewModel.setTags
- [x] 删除二次确认后从数据库移除 — AlertDialog + viewModel.deleteNote
- [x] 移动到分类可选「未分类」— MoveCategoryDialog + viewModel.moveToCategory
- [x] 重要度 3 档可切换 — PriorityDialog + viewModel.setPriority

## REQ-MOD-01 Room schema
- [x] `NoteEntity` 含 `priority: Int = 0` — Entities.kt
- [x] `AppDatabase` version = 4 — AppDatabase.kt:10
- [x] v3 → v4 走 `fallbackToDestructiveMigration` — AppDatabase.kt:34

## REQ-MOD-02 Manifest 权限
- [x] CAMERA — AndroidManifest.xml:8
- [x] RECORD_AUDIO — AndroidManifest.xml:9
- [x] READ_MEDIA_IMAGES — AndroidManifest.xml:10
- [x] READ_MEDIA_VIDEO — AndroidManifest.xml:11
- [x] READ_MEDIA_AUDIO — AndroidManifest.xml:12

## 部署验证
- [ ] 旧 APK 已完全卸载 — 需用户在设备上操作
- [ ] `Clean Project` + `Rebuild Project` 完成 — 需用户在 AS 操作
- [ ] `Run` 后 Run 窗口出现 `Installing app-debug.apk` — 需用户在 AS 操作
- [ ] 手机底部工具栏仅 6 个图标 — 需用户截图确认
- [ ] 右滑弹出 5 动作菜单 — 需用户截图确认
- [ ] 撤销/重做 / 拍照 / 涂鸦 / 表格 / 音频 全部生效 — 需用户截图确认
