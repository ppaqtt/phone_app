# 📋 清笺 更新日志

> 全部更新日志同步在 [ChangelogData.kt](app/src/main/java/com/example/notes/util/ChangelogData.kt) 维护。
> 此文件是给 GitHub / 文档站用的纯文本镜像。

---

## v1.20.1 (2026-06-16)

- 🐛 **修复**：MainActivity GlobalScope DelicateCoroutinesApi warning — 改用 `lifecycleScope.launch(Dispatchers.IO)` 启动安全检测，协程绑定到 Activity 生命周期，Activity 销毁时自动取消
- 🐛 **修复**：AppLockGate 函数缺失导致的编译错误 — 在 MainActivity.kt 末尾补回 AppLockGate Composable，使用 LocalLifecycleOwner + LifecycleEventObserver 监听前后台切换
- 🐛 **修复**：SecurityChecker 签名校验编译错误 — `getSha1Hex()` 是 PackageSignatureReader.kt 的顶层函数，直接调用而非 `PackageSignatureReader.getSha1Hex()`
- 🔖 **升级**：版本号 v1.20.0 → v1.20.1 (versionCode 30 → 31)

---

## v1.20.0 (2026-06-16)

- 🔒 **安全**：NDK Native 层 PIN 哈希 — SHA-256 加盐哈希逻辑从 Kotlin/JVM 移到 C/C++ (OpenSSL)，盐值以异或混淆存储；NativeSecurity JNI 桥接类 + CMake 构建配置；native 库加载失败时自动回退到 JVM 实现
- 🔒 **安全**：运行时安全检测 — SecurityChecker 工具类，启动时后台检测 Root/调试器/模拟器/Hook 框架/签名重打包；检测到威胁时记录日志（不强制退出，避免影响正常用户）
- 🔒 **安全**：APK 签名校验 — 对比运行时签名 SHA1 与预期指纹，防止重打包；PackageSignatureReader 新增 getSha1Hex() 便捷函数
- 🔒 **安全**：ProGuard 规则补充 — 保留 NativeSecurity JNI 类 + SecurityChecker 数据类
- 🔒 **安全**：NDK 构建配置 — externalNativeBuild cmake + ndkVersion 25.2.9519653，ABI 过滤仅保留 armeabi-v7a/arm64-v8a
- 🔖 **升级**：版本号 v1.19.0 → v1.20.0 (versionCode 29 → 30)

---

## v1.19.0 (2026-06-15)

- ✨ **新增**：应用锁生物识别解锁 — 支持指纹/人脸 (2D/3D) 解锁，自动检测设备能力；设置页新增「指纹/人脸解锁」开关，显示设备支持状态（可用/未录入/不支持/不可用）；解锁页新增指纹图标按钮，点击唤起 BiometricPrompt；生物识别失败时自动回退到 PIN 输入，PIN 始终作为兜底方案
- ✨ **新增**：BiometricHelper 工具类 — 封装 BiometricManager.canAuthenticate (BIOMETRIC_WEAK) 检测 + BiometricPrompt 认证流程，支持 onSuccess/onError/onCancel 回调
- 🔖 **升级**：版本号 v1.18.0 → v1.19.0 (versionCode 28 → 29)；新增 androidx.biometric:biometric:1.2.0-alpha05 依赖

---

## v1.18.0 (2026-06-15)

- 🔧 **优化**：列表项操作入口 — 移除不可靠的右滑手势（detectHorizontalDragGestures），改为卡片右上角「⋮」MoreVert 按钮，点击弹出操作弹层（置顶/标签/删除/移动/重要度/分享）；NoteCard 新增 onMoreClick 可选参数，列表 items 直接渲染 NoteCard + onMoreClick
- 🐛 **修复**：NotesListScreen 编译错误 — 清理 SwipeableNoteRow 相关 import 时遗漏 NoteSortOrder，导致 SortOrderDialog 内 9 处引用 Unresolved reference，补回 import 后恢复正常
- 🔖 **升级**：版本号 v1.17.0 → v1.18.0 (versionCode 27 → 28)

---

## v1.17.0 (2026-06-15)

- ✨ **新增**：启动权限引导页 — 首次启动列出 7 项核心权限（通知 / 图片 / 视频 / 音频 / 相机 / 麦克风 / 存储），支持「同意并继续」一次性申请全部或「稍后再说」跳过；引导页仅展示一次，状态持久化到 SharedPreferences
- ✨ **新增**：Markdown 渲染升级 — 引入 commonmark-java (org.commonmark:commonmark:0.21.0 + commonmark-ext-gfm-tables:0.21.0) 解析完整 CommonMark 语法，支持标题 / 段落 / 无序有序列表 / 引用 / 代码块 / GFM 表格 / 链接 / 粗体 / 斜体 / 行内代码；LegalDocumentRenderer 统一管理解析与渲染
- 🔧 **优化**：关于 → 隐私政策 / 使用条款渲染 — 从手写简化解析器切换为 CommonMark 解析器，表格带边框对齐，链接可点击跳浏览器（LocalUriHandler），AnnotatedString.Builder 扩展函数 appendInlineNodes 递归渲染所有行内节点
- 🐛 **修复**：AboutLegalScreen 编译错误 — 扩展函数接收者误写为 androidx.compose.ui.text.buildAnnotatedString.Builder（函数名），修正为 androidx.compose.ui.text.AnnotatedString.Builder（类名）
- 🐛 **修复**：移除 buildInlineAnnotatedString 未使用的 context 参数（消除 lint warning）
- 🔧 **优化**：.gitignore 补全 — 新增忽略 aliyun.gradle（个人镜像安装脚本）/ app/release/（APK 产物）/ app/schemas/（Room schema）/ crash.log / gradle-*-bin.zip / gradle-*/ 等构建产物，避免污染提交历史
- 🔖 **升级**：版本号 v1.16.0 → v1.17.0 (versionCode 26 → 27)

---

## v1.16.0 (2026-06-12)

- ✨ **新增**：应用锁增强 — 修改 PIN 需先验证旧 PIN（ChangePin 模式）；PIN 长度 4-8 位可选；「立即锁定」一键锁住应用；「忘记 PIN?」清除所有数据并重置应用锁；PIN 长度持久化到 SharedPreferences
- 🐛 **修复**：release 包打开闪退（16KB 页大小对齐） — Android 15+ 设备拒绝加载未对齐的 .so 库，打包配置启用 jniLibs.useLegacyPackaging = false，配合 proguard-rules.pro 完整 keep Compose @Composable / Saver / rememberSaveable / Room / DataStore / WorkManager / App Widget / Coil / ML Kit / Timber 等反射点
- 🐛 **修复**：release 包打开闪退（rememberSaveable 类型不兼容） — Color / SnapshotStateList 不能序列化到 Bundle，新增 ColorSaver（Color ↔ ARGB Int），把 imageUris / audioUris / reminderTime / reminderRepeat 改用 remember，走 ViewModel 流恢复，不依赖 Activity 重建状态
- 🐛 **修复**：主题色 / 深色模式 / 字号切换不生效 — ThemePreference 之前用 remember(context) 每次 new 一个实例，状态不共享，改为 NotesApplication 单例 + rememberThemePreference() 从 Application 拿同一实例，关闭 NotesAppTheme 默认 dynamicColor=true（会覆盖自定义色），改为只有选 ColorTheme.TEAL 才用 Material You 动态色
- 🔧 **清理**：19 个 lint warning — 删除未用变量（CodeBlock.startLine / AppLockScreen.lastClickTime / StatsScreen.keys / Settings/Theme.context 等）/ 未用参数（onInsertText）/ 冗余 cast ((List<String>)) / 不必要 !!（releaseInfo!!）/ ThreadLocal 返回可空补 !!（TimeFormat × 4 处 + ImageUtils）
- 🔖 **升级**：版本号 v1.15.0 → v1.16.0 (versionCode 25 → 26)

---

## v1.15.0 (2026-06-10)

- ✨ **新增**：OCR 文字识别 — 集成 Google ML Kit 中文文本识别（on-device，无需网络）；笔记编辑页 IMAGE 工具面板加「识别文字」按钮，选图后自动识别并插入到笔记正文；大图自动缩放到 1920px
- 🔖 **升级**：版本号 v1.14.0 → v1.15.0 (versionCode 24 → 25)

---

## v1.14.0 (2026-06-10)

- ✨ **新增**：语音转文字 — 封装 Android SpeechRecognizer（系统内置，无需 API Key）；笔记编辑页底部工具栏加「语音」按钮，点击请求录音权限后开始聆听；识别完成自动插入到笔记正文
- 🔖 **升级**：版本号 v1.13.0 → v1.14.0 (versionCode 23 → 24)

---

## v1.13.0 (2026-06-10)

- ✨ **新增**：代码块高亮 — 渲染 `lang ... ` 围栏代码块，深色背景 + 语言标签 + 横向滚动；自带轻量关键字着色（Kotlin / Java / Python / JS / Go / Rust / C/C++），不引入第三方库
- 🔖 **升级**：版本号 v1.12.0 → v1.13.0 (versionCode 22 → 23)

---

## v1.12.0 (2026-06-10)

- ✨ **新增**：统计仪表盘 — 4 个计数卡（笔记 / 置顶 / 提醒 / 图片）+ 字数卡（中文字符 / 英文单词 / 平均每篇）+ 分类分布卡（横向比例条）+ 月度趋势卡（最近 6 个月竖向柱状图）
- 🔖 **升级**：版本号 v1.11.0 → v1.12.0 (versionCode 21 → 22)

---

## v1.11.0 (2026-06-10)

- ✨ **新增**：嵌套分类 — CategoryEntity 加 parentId 字段，单层缩进（0=顶级，1=子级）；分类管理列表按父→子顺序渲染，子分类缩进 20dp + ↳ 箭头图标；新增分类时可选父分类，父分类候选自动排除自身和所有 descendants 防止循环引用；删除父分类时自动把子分类提升为顶级
- 🔖 **升级**：Room v7→v8 AutoMigration；备份导出/导入同步维护 parentOldId 映射，老备份默认顶级；版本号 v1.10.0 → v1.11.0 (versionCode 20 → 21)

---

## v1.10.0 (2026-06-10)

- ✨ **新增**：每日重复提醒 — NoteEntity 加 reminderRepeat 字段（NONE/DAILY/WEEKLY/MONTHLY/YEARLY）；ReminderWorker 触发后若 repeat != NONE，自动用 Calendar.add 排下次触发
- 🔖 **升级**：Room v6→v7 AutoMigration；版本号 v1.9.0 → v1.10.0 (versionCode 19 → 20)

---

## v1.9.0 (2026-06-10)

- ✨ **新增**：PDF / 长图导出 — 笔记编辑页顶部 MoreVert 下拉新增「导出为 PDF」和「导出为长图（PNG）」两项，走 SAF CreateDocument；PDF 走 android.graphics.pdf.PdfDocument 渲染（A4 自动分页），长图走 Bitmap + StaticLayout 拼接（2x 像素密度）
- 🔖 **升级**：版本号 v1.8.0 → v1.9.0 (versionCode 18 → 19)

---

## v1.8.0 (2026-06-10)

- ✨ **新增**：应用锁 — AppLockStore 持久化 PIN 的 SHA-256 哈希（不存明文）；AppLockGate 包裹 NavGraph，启动 / 切回前台检测 5 分钟解锁宽限期；AppLockScreen PIN 数字键盘 + 圆点指示器，失败 30s 冷却
- 🔖 **升级**：版本号 v1.7.0 → v1.8.0 (versionCode 17 → 18)

---

## v1.7.0 (2026-06-10)

- ✨ **新增**：App 快捷方式（长按桌面图标） — res/xml/shortcuts.xml 注册 3 个动态快捷方式（新建笔记 / 搜索 / 回收站）；AndroidManifest MainActivity meta-data 指向 shortcuts.xml
- 🔖 **升级**：版本号 v1.6.0 → v1.7.0 (versionCode 16 → 17)

---

## v1.6.0 (2026-06-10)

- ✨ **新增**：桌面小部件（AppWidget） — 4x2 圆角卡片显示最近 5 条笔记（按 updated_at 倒序），标题 + 内容预览；列表项点击通过 setOnClickFillInIntent 打开笔记；底部 + 按钮快速新建；saveNote 后调 NotesAppWidget.requestRefresh 触发刷新
- 🔖 **升级**：版本号 v1.5.0 → v1.6.0 (versionCode 15 → 16)

---

## v1.5.0 (2026-06-10)

- ✨ **新增**：回收站 — NoteEntity 加 deletedAt 字段（null=正常，非 null=已删除）；删除改走软删除，列表 / 搜索 / 按分类观察自动加 deleted_at IS NULL 过滤；TrashScreen 显示 30 天内已删笔记，每条 2 动作：恢复 / 永久删除
- 🔖 **升级**：Room v5→v6 AutoMigration；版本号 v1.4.0 → v1.5.0 (versionCode 14 → 15)

---

## v1.4.0 (2026-06-08)

- ✨ **新增**：数据备份 / 恢复 — 全部笔记 / 分类 / 图片导出为 JSON，走 SAF CreateDocument / OpenDocument；DTO 与 Entity 解耦，兼容老备份；AUTO_INCREMENT 冲突通过「老 id → 新 id」映射表解决
- 🔖 **升级**：版本号 v1.3.0 → v1.4.0 (versionCode 13 → 14)

---

## v1.3.0 (2026-05-25)

- ✨ **新增**：标签系统 — NoteEntity 加 tags 字段（String 存储逗号分隔）；笔记编辑页底部 TagChips 显示 + 增删；列表页顶部加 tag 筛选器（单选/多选切换）；详情页显示标签云；备份 JSON 同步导出 tags
- ✨ **新增**：Markdown 表格 — 解析 `col1 | col2` 表格语法，渲染为可编辑 Excel 风格组件，点击单元格编辑后转回 Markdown 写回正文
- ✨ **新增**：图片多选 — 选图时从 ACTION_PICK 改 ACTION_OPEN_DOCUMENT（支持多选）；选完按插入顺序写入正文
- 🔖 **升级**：Room v4→v5 AutoMigration；版本号 v1.2.0 → v1.3.0 (versionCode 12 → 13)

---

## v1.2.0 (2026-05-15)

- ✨ **新增**：深色模式 — ThemePreference 持久化用户选择（跟随系统/浅色/深色），整个 App 走 Material 3 darkColorScheme
- ✨ **新增**：自定义主题色 — 5 种 primary（青绿/蓝/紫/绿/橙），设置页 AppearanceCard 提供切换
- ✨ **新增**：字号缩放 — 小/中/大/超大 4 档，Typography.scale() 全局生效
- 🔧 **优化**：NotesListScreen 加 LazyColumn stickyHeader 按日期分组（今天/昨天/本周/更早）
- 🔧 **优化**：搜索页加高亮显示匹配片段，点击搜索结果跳到编辑页光标定位
- 🔖 **升级**：版本号 v1.1.0 → v1.2.0 (versionCode 11 → 12)

---

## v1.1.0 (2026-06-05)

- ✨ **新增**：分类管理 — CategoryEntity 持久化分类（id/name/color/order）；分类 CRUD + 排序拖拽；笔记可关联 categoryId，列表页顶部 chip 切换
- ✨ **新增**：置顶笔记 — NoteEntity 加 isPinned 字段，列表按 pinned desc，updated_at desc 排序
- ✨ **新增**：通知提醒 — NoteEntity 加 reminderTime（Long?），到点通过 ReminderWorker 发通知；AlarmManager.setExactAndAllowWhileIdle 触发，通知点击打开笔记
- ✨ **新增**：图片附件 — NoteImageEntity 一对多关联，图片存到 app internal storage，列表/编辑页缩略图渲染
- 🔖 **升级**：版本号 v1.0.0 → v1.1.0 (versionCode 10 → 11)

---

## v1.0.0 (2026-06-05)

- 🎉 **首发**：清笺 Android 笔记应用
- 📝 **核心功能**：笔记 CRUD（创建/查看/编辑/删除），标题 + 富文本正文（纯文本/Markdown 渲染），按时间倒序列表，本地搜索（按标题/正文匹配）
- 🏗️ **技术栈**：Kotlin 1.8.22 + Jetpack Compose（BOM 2023.08.00）+ Room 2.5 + Navigation Compose + Timber + WorkManager + Coil
- 💾 **数据**：全部本地存储（SQLite），无云同步，无账号登录，无网络请求（除未来可能的更新检查）
- 🔐 **权限**：通知（Android 13+），摄像头/相册（图片附件），麦克风（语音输入，v1.14+），存储（SAF，无需 broad storage 权限）

---
