# Tasks

- [x] Task 1: 移除 AI 工具栏
  - [x] 1.1 从 `BottomTool` 枚举删除 `AI` 项
  - [x] 1.2 从 `toolbarItems` 删除 AI 项
  - [x] 1.3 从 `SettingsScreen` 更新日志里删除「AI」字样 (改为 v1.0.3 移除项说明)
  - [x] 1.4 验证底部工具栏只剩 6 项 (分栏/Aa/列表/待办/图片/更多)

- [x] Task 2: 分栏面板拆 4 子页签
  - [x] 2.1 定义 `ColumnsTab` 枚举 (文字样式/符号/分割线/图文模版)
  - [x] 2.2 实现 `ColumnsPanel` + 4 个 `Grid`
  - [x] 2.3 各页签的 snippet 列表
  - [x] 2.4 子页签高亮切换

- [x] Task 3: Aa 文字格式面板
  - [x] 3.1 `TextFormatPanel` B/I/U/S/高亮 5 按钮
  - [x] 3.2 8 档字号选择
  - [x] 3.3 7 色字体颜色块

- [x] Task 4: 列表面板 6 按钮
  - [x] 4.1 `ListPanel` 左/中/右对齐
  - [x] 4.2 圆点 / 数字 / 字母编号

- [x] Task 5: 待办切换 ☐
  - [x] 5.1 `TodoPanel` 单按钮
  - [x] 5.2 `onToggleTodo` 末尾切换逻辑

- [x] Task 6: 拍照调起摄像头 + 去除文档扫码
  - [x] 6.1 AndroidManifest 加 `CAMERA`
  - [x] 6.2 `ActivityResultContracts.TakePicture` 拍照
  - [x] 6.3 `RequestPermission` 申请 CAMERA
  - [x] 6.4 FileProvider 输出 URI
  - [x] 6.5 拍照成功插入 imageUris
  - [x] 6.6 移除文档扫码入口

- [x] Task 7: 更多面板 3 入口 (涂鸦/表格/音频)
  - [x] 7.1 `MorePanel` 3 等距按钮

- [x] Task 8: 涂鸦白板
  - [x] 8.1 `DoodleDialog` Composable
  - [x] 8.2 Canvas + `detectDragGestures` 绘制
  - [x] 8.3 颜色 / 粗细 / 撤销 / 重做 / 清空
  - [x] 8.4 导出 PNG 到 `images/` 目录
  - [x] 8.5 FileProvider URI 插入 imageUris

- [x] Task 9: 表格插入
  - [x] 9.1 `TableInsertDialog` 行 × 列输入
  - [x] 9.2 校验 1..20 行 1..10 列
  - [x] 9.3 `buildMarkdownTable` 生成等宽对齐

- [x] Task 10: 音频读取
  - [x] 10.1 AndroidManifest 加 `READ_MEDIA_AUDIO`
  - [x] 10.2 `OpenDocument(arrayOf("audio/*"))` 选取
  - [x] 10.3 取得 URI 持久读权限
  - [x] 10.4 插入正文 + 内联 Row

- [x] Task 11: 撤销 / 重做按钮
  - [x] 11.1 `UndoRedoState<NoteSnapshot>` 状态机
  - [x] 11.2 `NoteSnapshot(title, content)` data class
  - [x] 11.3 TopAppBar 按钮调用 `undo`/`redo`
  - [x] 11.4 每次 onValueChange 触发 `pushHistory`

- [x] Task 12: 元信息行简化
  - [x] 12.1 删除 `MetaInfoRow` 里的置顶/提醒小图标
  - [x] 12.2 仅保留 日期 / 字数 / 分类

- [x] Task 13: 主界面右滑笔记卡片
  - [x] 13.1 `SwipeableNoteRow` 包装 `SwipeToDismissBox`
  - [x] 13.2 禁止左滑 (StartToEnd), 仅允许右滑 (EndToStart)
  - [x] 13.3 `confirmValueChange = { false }` 弹回
  - [x] 13.4 背景层 5 个示意按钮
  - [x] 13.5 触发后弹出 `NoteActionsRow` 菜单

- [x] Task 14: 5 个动作功能完整
  - [x] 14.1 置顶 → `viewModel.togglePin`
  - [x] 14.2 标签 → `TagsEditDialog` + `viewModel.setTags`
  - [x] 14.3 删除 → 二次确认 + `viewModel.deleteNote`
  - [x] 14.4 移动 → `MoveCategoryDialog` + `viewModel.moveToCategory`
  - [x] 14.5 重要度 → `PriorityDialog` + `viewModel.setPriority`

- [x] Task 15: 数据库底层支撑
  - [x] 15.1 `NoteEntity` 加 `priority: Int = 0`
  - [x] 15.2 `AppDatabase` version 升 3 → 4
  - [x] 15.3 `NoteDao` 加 `setPriority`/`setTags`/`setCategory` 方法
  - [x] 15.4 `NotesRepository` 加对应 suspend 方法
  - [x] 15.5 `NotesViewModel` 加 3 个公开方法

- [ ] Task 16: 部署验证
  - [ ] 16.1 完全卸载旧 APK (`adb uninstall com.example.notes.debug`)
  - [ ] 16.2 `Clean Project` + `Rebuild Project`
  - [ ] 16.3 `Run` 重新安装
  - [ ] 16.4 验证底部工具栏只剩 6 个图标 (无 AI)
  - [ ] 16.5 验证右滑弹出 5 动作菜单
  - [ ] 16.6 验证撤销/重做 / 拍照 / 涂鸦 / 表格 / 音频 全部生效

# Task Dependencies
- Task 1 独立 ✅
- Task 2/3/4/5/6/7 独立 (都是工具面板) ✅
- Task 8/9/10 依赖 Task 7 (更多面板入口) ✅
- Task 11 独立 ✅
- Task 12 独立 ✅
- Task 13 独立 ✅
- Task 14 依赖 Task 13 (右滑触发菜单) + Task 15 (底层数据) ✅
- Task 15 独立 ✅
- Task 16 依赖所有其他 Task — **需用户在设备上验证**
