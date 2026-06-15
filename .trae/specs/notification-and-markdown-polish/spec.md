# 启动权限引导 + 法律文档 Markdown 渲染 + 右滑恢复 Spec

## Why
清笺笔记 App 体验存在三处明显问题：
1. 启动时只申请 `POST_NOTIFICATIONS` 通知权限，但相机/麦克风/存储等用户首次使用对应功能才弹申请, 体验上像是「先弹一次, 之后用啥卡啥」。需要把启动时一次性引导用户完成所有「应用核心功能依赖」的权限预授权。
2. 「隐私政策」「使用条款」目前用简化版 Markdown 解析（仅识别 `##` `>` `-` 几行语法），不支持表格、链接、`**粗体**`、行内代码、列表嵌套等，渲染效果粗糙。
3. 主页笔记卡片「右滑弹出动作菜单」原本已实现，疑似被后续改动覆盖/隐藏，导致用户看不到。

## What Changes
- 启动时一次性弹「应用核心权限引导页」:
  - 列出本次申请的具体权限（含说明文字 + 图标）
  - 「同意并继续」按钮 → 一次性 `RequestMultiplePermissions` 拉起系统弹窗
  - 「稍后再说」按钮 → 跳过本引导, 后续按需单独申请
  - 引导页只展示一次, 记录在 SharedPreferences (`permission_intro_shown`) 中
- 「应用核心权限」清单:
  - `POST_NOTIFICATIONS` (Android 13+, 提醒通知)
  - `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` (Android 13+, 选图/选视频/选音频)
  - `CAMERA` (拍照, 涂鸦导出)
  - `RECORD_AUDIO` (后续音频录入预留)
  - `READ_EXTERNAL_STORAGE` 仅在 `minSdk <= 32` 时声明
- 法律文档渲染升级为**真 Markdown 解析**:
  - 方案选 A: 引入轻量 Markdown 库 `compose-markdown:0.3.0` (JitPack) 或 `commonmark:0.21.0`
  - 方案选 B: 在现有简化解析基础上扩展表格/链接/嵌套列表/任务列表
  - **采用方案 A** (commonmark 官方库), 体积小 (~200KB), 稳定, 支持标准 CommonMark 0.30
  - 用 `Markwon`/`commonmark-java` + `AnnotatedString` 输出 Compose `Text`
- 主页右滑恢复:
  - 排查 `SwipeableNoteRow` / `NoteActionsRow` 是否还在 `NotesListScreen.kt` 中
  - 若存在, 检查其 `SwipeToDismissBox` 的 `confirmValueChange` 是否被改成 `{ true }` 误删
  - 若缺失, 重新接入
  - 验证右滑 ≥ 35% 阈值后弹出「置顶 / 标签 / 删除 / 移动 / 重要度」5 动作菜单

## Impact
- Affected specs: 启动流程、权限管理、法律文档展示、主界面交互
- Affected code:
  - 新增 [PermissionIntroScreen.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/PermissionIntroScreen.kt) — 应用核心权限引导页
  - 新增 [PermissionIntroPrefs.kt](file:///workspace/app/src/main/java/com/example/notes/util/PermissionIntroPrefs.kt) — 一次性引导标记
  - 修改 [MainActivity.kt](file:///workspace/app/src/main/java/com/example/notes/MainActivity.kt) — 启动后先判断是否展示引导
  - 新增 [LegalDocumentRenderer.kt](file:///workspace/app/src/main/java/com/example/notes/util/LegalDocumentRenderer.kt) — commonmark → AnnotatedString
  - 修改 [AboutLegalScreen.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/AboutLegalScreen.kt) — 改用 `LegalDocumentRenderer`
  - 新增 [app/build.gradle.kts](file:///workspace/app/build.gradle.kts) 依赖: `org.commonmark:commonmark:0.21.0`
  - 排查/恢复 [NotesListScreen.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/NotesListScreen.kt) 中 `SwipeableNoteRow` + `NoteActionsRow`
  - 修改 [AndroidManifest.xml](file:///workspace/app/src/main/AndroidManifest.xml): 增加权限声明（部分已存在, 补齐）

## ADDED Requirements

### Requirement: REQ-01 启动权限引导页
首次启动 / 权限引导标记未写入时, 在启动页后到主界面之间, 插入「应用核心权限」引导页。

#### Scenario: 首次启动展示
- **WHEN** 用户首次安装并启动 APP, 且 `permission_intro_shown` 未写入
- **THEN** Splash 结束后弹出 PermissionIntroScreen
- **AND** 引导页中列出 5 项权限: 通知 / 媒体(图片视频音频) / 相机 / 麦克风 / 存储(API≤32)
- **AND** 每项有图标 + 名称 + 1 句作用说明

#### Scenario: 用户同意
- **WHEN** 用户点击「同意并继续」
- **THEN** 调用 `RequestMultiplePermissions` 一次性拉起系统弹窗
- **AND** 用户在系统弹窗中逐项勾选
- **AND** 回调后写入 `permission_intro_shown = true`
- **AND** 进入主界面

#### Scenario: 用户跳过
- **WHEN** 用户点击「稍后再说」
- **THEN** 直接写入 `permission_intro_shown = true` (避免下次再弹)
- **AND** 进入主界面
- **AND** 后续按需在用到具体功能时再单独申请

#### Scenario: 重复启动不再展示
- **WHEN** 用户第二次启动, `permission_intro_shown == true`
- **THEN** Splash 结束后直接进入主界面

### Requirement: REQ-02 通知权限仍按原有逻辑申请
引导页是「预授权」, 但不替代「按需申请」, `NotificationPermission` 工具类保留, 走 `rememberNotificationPermissionRequest()`。

#### Scenario: 引导页跳过通知
- **WHEN** 用户在引导页选「稍后再说」
- **THEN** 启动时仍可继续按 `LaunchedEffect` 弹通知申请 (因为通知是全局的)

### Requirement: REQ-03 Markdown 解析升级
[AboutLegalScreen.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/AboutLegalScreen.kt) 改用 `commonmark-java` 解析 `privacy_policy.md` / `terms_of_service.md`, 支持完整 CommonMark 0.30 语法。

#### Scenario: 解析表格
- **WHEN** markdown 中含 `| H1 | H2 |\n| -- | -- |\n| A | B |\n`
- **THEN** 渲染为带边框的等宽对齐表格
- **AND** 表头加粗 + 浅灰底色

#### Scenario: 解析链接
- **WHEN** markdown 中含 `[清笺笔记](https://example.com)`
- **THEN** 渲染为蓝色下划线文字
- **AND** 点击触发 `Intent.ACTION_VIEW` 调起系统浏览器

#### Scenario: 解析标题层级
- **WHEN** markdown 含 `# H1` / `## H2` / `### H3`
- **THEN** 三级字号依次为 22sp / 18sp / 16sp, 全部加粗

#### Scenario: 解析列表嵌套
- **WHEN** markdown 含 `- A\n  - B\n  - C\n- D`
- **THEN** 渲染为带缩进的嵌套项目符号

#### Scenario: 解析行内代码 / 粗体 / 斜体
- **WHEN** markdown 含 `` `code` `` / `**bold**` / `*italic*`
- **THEN** 行内代码用浅灰底色 + 等宽字体
- **AND** 粗体 / 斜体正确渲染

#### Scenario: 解析引用块
- **WHEN** markdown 含 `> xxx` 多行
- **THEN** 渲染为左侧带竖线 + 浅色文字的多行引用

### Requirement: REQ-04 主页右滑恢复
[NotesListScreen.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/NotesListScreen.kt) 中 `SwipeableNoteRow` 真实可见, 右滑弹出 5 动作菜单。

#### Scenario: 右滑笔记卡片
- **WHEN** 用户在列表上从右向左滑动一条笔记卡片
- **THEN** 卡片右侧露出彩色动作背景 (5 个动作的图标 + 文字)
- **AND** 滑动超过 35% 阈值后弹出「置顶 / 标签 / 删除 / 移动 / 重要度」5 项菜单
- **AND** 卡片回弹到原位 (不真正删除)

#### Scenario: 菜单项可用
- **WHEN** 动作菜单出现
- **THEN** 5 个菜单项点击均能触发对应功能 (置顶/标签/删除/移动/重要度)
- **AND** 点击「关闭」或外部空白可关闭菜单

## MODIFIED Requirements

### Requirement: REQ-MOD-01 build.gradle.kts 增加 commonmark
新增依赖 `org.commonmark:commonmark:0.21.0` (或更新版本)。

#### Scenario: 依赖生效
- **WHEN** `./gradlew :app:dependencies` 查看
- **THEN** 出现 `commonmark` jar
- **AND** APK 大小增加不超过 300KB

### Requirement: REQ-MOD-02 AndroidManifest 权限补齐
确保声明:
- `POST_NOTIFICATIONS` (API 33+)
- `READ_MEDIA_IMAGES` (API 33+)
- `READ_MEDIA_VIDEO` (API 33+)
- `READ_MEDIA_AUDIO` (API 33+)
- `CAMERA`
- `RECORD_AUDIO`
- `READ_EXTERNAL_STORAGE` (仅 minSdk <= 32)

#### Scenario: 编译清单
- **WHEN** 用户 `Build → Make Project`
- **THEN** Manifest merger 不报错
- **AND** 所有目标权限均在 `AndroidManifest.xml` 中

## REMOVED Requirements
无 (本次不删除任何已有功能, 仅扩展)。

## Open Questions
- [ ] commonmark-java 是否需要 `commonmark-ext-gfm-tables` 扩展, 还是只装核心包就能支持表格? (基本核心包不支持 GFM 表格, 需要 `commonmark-ext-gfm-tables`)
- [ ] 右滑丢失的具体原因: 是被 `LazyColumn` 重构覆盖, 还是 `SwipeToDismissBox` API 升级后签名变了? 需要在实现时先 git diff 排查
