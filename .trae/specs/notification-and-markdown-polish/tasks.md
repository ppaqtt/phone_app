# Tasks

- [ ] Task 1: 排查右滑丢失的根因
  - [ ] 1.1 用 `git log --oneline -- app/src/main/java/com/example/notes/ui/screens/NotesListScreen.kt` 查最近改动
  - [ ] 1.2 用 `git log --all --oneline` 找到右滑功能被改动的提交
  - [ ] 1.3 确认 `SwipeableNoteRow` / `NoteActionsRow` 仍存在于 NotesListScreen.kt 第 334/352/470/568 行附近
  - [ ] 1.4 检查 `LazyColumn` 中是否仍用 `items { note -> SwipeableNoteRow(...) }` 包裹
  - [ ] 1.5 输出根因报告 (代码缺失 / 签名变更 / 参数误改)

- [ ] Task 2: 启动权限引导页 PermissionIntroScreen.kt
  - [ ] 2.1 创建 `data class PermissionItem(val icon, val title, val description, val permission: String, val required: Boolean)`
  - [ ] 2.2 创建 `PermissionIntroPrefs.kt` 单例 (用 SharedPreferences)
    - `var introShown: Boolean` (默认 false)
  - [ ] 2.3 创建 `PermissionIntroScreen` Composable
    - 顶部 TopAppBar 标题「应用核心权限」
    - 中间 LazyColumn 列出 5 项权限 + 图标 + 名称 + 1 句说明
    - 底部 Row 2 按钮: 「稍后再说」(次要) / 「同意并继续」(主操作)
  - [ ] 2.4 「同意并继续」调用 `rememberLauncherForActivityResult(RequestMultiplePermissions)`
  - [ ] 2.5 「稍后再说」写入 `introShown = true`, 调 `onComplete()`
  - [ ] 2.6 单元测试: 5 项权限清单覆盖所有 Manifest 声明的危险权限

- [ ] Task 3: 接入启动流程
  - [ ] 3.1 修改 [MainActivity.kt](file:///workspace/app/src/main/java/com/example/notes/MainActivity.kt), 在 Splash 之后加一个 `if (!prefs.introShown) showIntro` 分支
  - [ ] 3.2 启动顺序: Splash → (若 !introShown) PermissionIntroScreen → (否则直接) AppLockGate → NotesNavGraph
  - [ ] 3.3 通知权限的 `rememberNotificationPermissionRequest` 仍保留, 引导页后按 `LaunchedEffect` 正常拉起

- [ ] 4.1 修 [AndroidManifest.xml](file:///workspace/app/src/main/AndroidManifest.xml) 补齐权限
  - [ ] 4.1 确认 CAMERA / RECORD_AUDIO / READ_MEDIA_IMAGES / READ_MEDIA_VIDEO / READ_MEDIA_AUDIO / POST_NOTIFICATIONS 全部声明
  - [ ] 4.2 READ_EXTERNAL_STORAGE 仅 `minSdk <= 32` 时声明 (用 `<uses-permission android:name="..." android:maxSdkVersion="32" />`)

- [ ] Task 5: 升级 build.gradle.kts 依赖
  - [ ] 5.1 添加 `implementation("org.commonmark:commonmark:0.21.0")` 到 dependencies 块
  - [ ] 5.2 添加 `implementation("org.commonmark:commonmark-ext-gfm-tables:0.21.0")` (用于 GFM 表格扩展)
  - [ ] 5.3 `gradle :app:dependencies` 验证依赖解析

- [ ] Task 6: LegalDocumentRenderer.kt
  - [ ] 6.1 创建 `object LegalDocumentRenderer`, 用 `commonmark` 解析 Markdown → `Node`
  - [ ] 6.2 遍历 Node 树, 转换为 `AnnotatedString` (包含字体大小 / 粗体 / 斜体 / 链接)
  - [ ] 6.3 表格通过 `commonmark-ext-gfm-tables` 扩展解析, 输出 2D 字符串数组
  - [ ] 6.4 写 `parseMarkdown(text: String): List<RenderedBlock>` data class:
    - `Heading(level, text, style)`
    - `Paragraph(annotated)`
    - `ListBlock(items)`
    - `Quote(annotated)`
    - `CodeBlock(text, language)`
    - `Table(headers, rows)`

- [ ] Task 7: 改 AboutLegalScreen.kt
  - [ ] 7.1 删除原有简化 `parseMarkdownBlocks` / `MarkdownBlockView` / `stripSimpleInline`
  - [ ] 7.2 主体改用 `LegalDocumentRenderer.parseMarkdown(content)` 渲染
  - [ ] 7.3 标题 / 段落 / 引用 / 列表 / 代码块用 `Text(AnnotatedString)` 渲染
  - [ ] 7.4 表格用 Compose `Column + Row + weight + border` 自实现
  - [ ] 7.5 链接点击通过 `LocalUriHandler.current.openUri(href)`

- [ ] Task 8: 恢复右滑
  - [ ] 8.1 根据 Task 1 根因, 修复 / 恢复 `SwipeableNoteRow` + `NoteActionsRow`
  - [ ] 8.2 确认 `SwipeToDismissBox` 的 `confirmValueChange = { false }` 让卡片回弹
  - [ ] 8.3 5 个动作回调完整接上 (`viewModel.togglePin` / `setTags` / `deleteNote` / `moveToCategory` / `setPriority`)
  - [ ] 8.4 编译 + 真机 / 模拟器验证

- [ ] Task 9: 编译与运行验证
  - [ ] 9.1 `gradle :app:assembleDebug` 0 错误 0 warning
  - [ ] 9.2 首次启动展示权限引导页
  - [ ] 9.3 同意 / 跳过均能进入主界面
  - [ ] 9.4 二次启动不再展示引导页
  - [ ] 9.5 设置 → 关于 → 隐私政策, 渲染含表格 / 链接 / 列表 / 引用
  - [ ] 9.6 主页右滑笔记卡片弹出 5 动作菜单
  - [ ] 9.7 5 个动作均能正常修改笔记

# Task Dependencies
- Task 3 依赖 Task 2 (PermissionIntroScreen 必须先实现)
- Task 7 依赖 Task 5 + Task 6 (commonmark 解析器 + 依赖)
- Task 8 依赖 Task 1 (根因排查)
- Task 9 依赖所有其他 Task
