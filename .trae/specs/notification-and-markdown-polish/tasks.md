# Tasks

- [x] Task 1: 排查右滑丢失的根因
  - [x] 1.1 用 `git log --oneline -- app/src/main/java/com/example/notes/ui/screens/NotesListScreen.kt` 查最近改动
  - [x] 1.2 用 `git log --all --oneline` 找到右滑功能被改动的提交
  - [x] 1.3 确认 `SwipeableNoteRow` / `NoteActionsRow` 仍存在于 NotesListScreen.kt 第 334/352/470/568 行附近
  - [x] 1.4 检查 `LazyColumn` 中是否仍用 `items { note -> SwipeableNoteRow(...) }` 包裹
  - [x] 1.5 输出根因报告: 右滑功能代码完整, 实际是"左滑"(从右向左拖), 6 个动作全部接上 viewModel

- [x] Task 2: 启动权限引导页 PermissionIntroScreen.kt
  - [x] 2.1 创建 `data class PermissionItem(val icon, val title, val description, val permission: String, val required: Boolean)`
  - [x] 2.2 创建 `PermissionIntroPrefs.kt` 单例 (用 SharedPreferences)
    - `var introShown: Boolean` (默认 false)
  - [x] 2.3 创建 `PermissionIntroScreen` Composable
    - 顶部 TopAppBar 标题「应用权限」
    - 中间 LazyColumn 列出权限项 + 图标 + 名称 + 说明
    - 底部 Row 2 按钮: 「稍后再说」(次要) / 「同意并继续」(主操作)
  - [x] 2.4 「同意并继续」调用 `rememberLauncherForActivityResult(RequestMultiplePermissions)`
  - [x] 2.5 「稍后再说」写入 `introShown = true`, 调 `onComplete()`
  - [x] 2.6 权限清单覆盖所有 Manifest 声明的危险权限 (7 项, 按 SDK 版本动态过滤)

- [x] Task 3: 接入启动流程
  - [x] 3.1 修改 [MainActivity.kt](file:///workspace/app/src/main/java/com/example/notes/MainActivity.kt), 在 Splash 之后加 `showPermissionIntro` 分支
  - [x] 3.2 启动顺序: Splash → (若 !introShown) PermissionIntroScreen → AppLockGate → NotesNavGraph
  - [x] 3.3 通知权限的 `rememberNotificationPermissionRequest` 仍保留, 引导页后按 `LaunchedEffect` 正常拉起

- [x] Task 4: AndroidManifest 权限确认
  - [x] 4.1 确认 CAMERA / RECORD_AUDIO / READ_MEDIA_IMAGES / READ_MEDIA_VIDEO / READ_MEDIA_AUDIO / POST_NOTIFICATIONS 全部声明
  - [x] 4.2 READ_EXTERNAL_STORAGE 已有 `maxSdkVersion="32"`

- [x] Task 5: 升级 build.gradle.kts 依赖
  - [x] 5.1 添加 `implementation("org.commonmark:commonmark:0.21.0")` 到 dependencies 块
  - [x] 5.2 添加 `implementation("org.commonmark:commonmark-ext-gfm-tables:0.21.0")` (用于 GFM 表格扩展)
  - [x] 5.3 依赖已添加, 语法正确

- [x] Task 6: LegalDocumentRenderer.kt
  - [x] 6.1 创建 `object LegalDocumentRenderer`, 用 `commonmark` 解析 Markdown → `Node`
  - [x] 6.2 遍历 Node 树, 转换为 `RenderedBlock` + `InlineNode` 数据结构
  - [x] 6.3 表格通过 `commonmark-ext-gfm-tables` 扩展解析 (TableBlock → TableHead/TableBody → TableRow → TableCell)
  - [x] 6.4 `parseMarkdown(text: String): List<RenderedBlock>` 含 Heading/Paragraph/BulletList/OrderedList/BlockQuote/CodeBlock/Table/HorizontalRule/Blank

- [x] Task 7: 改 AboutLegalScreen.kt
  - [x] 7.1 删除原有简化 `parseMarkdownBlocks` / `MarkdownBlockView` / `stripSimpleInline`
  - [x] 7.2 主体改用 `LegalDocumentRenderer.parseMarkdown(content)` 渲染
  - [x] 7.3 标题 / 段落 / 引用 / 列表 / 代码块用 `Text(AnnotatedString)` 渲染
  - [x] 7.4 表格用 Compose `Column + Row + weight + border` 自实现
  - [x] 7.5 链接点击通过 `LocalUriHandler.current.openUri(href)` + `ClickableText` + `StringAnnotation`

- [x] Task 8: 右滑功能确认
  - [x] 8.1 根因: 右滑功能代码完整存在, 无需修复
  - [x] 8.2 卡片回弹行为正确 (手写 `detectHorizontalDragGestures`, 偏移后 animateTo(0f))
  - [x] 8.3 6 个动作回调完整接上 viewModel (置顶/标签/删除/移动/重要度/分享)
  - [x] 8.4 代码审查确认无编译问题

- [ ] Task 9: 编译与运行验证
  - [ ] 9.1 `gradle :app:assembleDebug` 0 错误 0 warning
  - [ ] 9.2 首次启动展示权限引导页
  - [ ] 9.3 同意 / 跳过均能进入主界面
  - [ ] 9.4 二次启动不再展示引导页
  - [ ] 9.5 设置 → 关于 → 隐私政策, 渲染含表格 / 链接 / 列表 / 引用
  - [ ] 9.6 主页左滑笔记卡片弹出动作菜单 (实际为左滑, 从右向左拖)
  - [ ] 9.7 动作均能正常修改笔记

# Task Dependencies
- Task 3 依赖 Task 2 (PermissionIntroScreen 必须先实现)
- Task 7 依赖 Task 5 + Task 6 (commonmark 解析器 + 依赖)
- Task 8 依赖 Task 1 (根因排查)
- Task 9 依赖所有其他 Task
