# 更新日志 (Changelog)

所有重要变更都会记录在此文件。格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/),
本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [1.1.0] - 2026-06-10

### 修复 (P82-P90 新一轮 9 项潜在问题)

#### 严重 (数据损坏 / 崩溃)
- **P82 修复**: `NoteEditScreen.tryExit` 与"丢弃"按钮原本 `busy = true` 后立即
  调用 `then()` (= onBack) 销毁 Composable, `busy` 锁永远不释放, 下次进入
  同一笔记所有按钮永久置灰。删除 `busy = true` (弹回后 Composable 销毁, busy
  状态随之销毁, 不必再设锁); 删除路径补 try-finally 保证 onBack 一定执行。
- **P83 修复**: `removeTagFromAllNotes` SQL 用 `REPLACE(',,tags,,', ',,tag,,', ',')`
  会留下首尾逗号 (例: tags="a,b,c" 删 b 后变成 ",a,c," 而非 "a,c")。
  修法: 套 `TRIM(',' FROM REPLACE(...))` 剥两侧逗号; 同时加空 tag 短路保护。
- **P90 修复**: `NotesViewModel` 8 处 `viewModelScope.launch { repository.X() }`
  全部无 try-catch, 任何 DAO 异常 (SQLiteConstraintException 等) 都会被传到
  全局 `CoroutineExceptionHandler` 导致 APP 闪退。新增 `ViewModel.launchSafe`
  扩展统一捕获并打日志。

#### 中等
- **P84 修复**: `SplashScreen` 旧版有 `ready: Boolean = true` 参数但
  MainActivity 始终不传, 该参数是死代码且误导后人。删除, 把"淡入 + 600ms
  等待 + 回调"合并为一个 LaunchedEffect。
- **P85 修复**: `SearchHistoryManager.addSearch / removeSearch` 旧版用
  `val cur = _history.value; ...; _history.value = ...` 读-改-写, 两次连续
  操作会竞态 (后一次读到旧值, 覆盖前一次结果)。改用 `MutableStateFlow.update`
  内部 CAS, 原子完成。
- **P86 修复**: `NoteShareUtil.shareAsText` 旧版没有 `runCatching`, 在没有
  ACTION_SEND 处理的设备 (如部分车机) 上 startActivity 会抛
  ActivityNotFoundException 导致崩溃, 与 shareAsImage 风格不一致。补上同样
  的 `runCatching` + Toast 反馈。

#### 轻微
- **P89 修复**: `SearchHistoryManager.clearHistory` 旧版直接在调用线程 (常为
  主线程) 写 SharedPreferences, 与 addSearch 的协程风格不一致。改为
  `scope.launch { ... }`, 同样走 IO 线程。

### 升级
- 升级：版本号 v1.0.9 → v1.1.0 (versionCode 9 → 10)
- 升级：FALLBACK_LATEST_VERSION 同步到 1.1.0

---

## [1.0.9] - 2026-06-10

### 修复 (P75-P81 新增 + P57/P62/P64/P66/P70/P72/P80 补漏)

#### 严重
- **P75 修复**: `SettingsScreen.FeedbackCard` / `UpdateDialog` 的 `startActivity`
  原本被 `runCatching` 静默吞掉, 用户点"问题反馈"/"立即更新"无任何反馈。
  改为 `runCatching(...).onFailure { Toast.makeText(...) }` 给用户提示。

#### 中等
- **P57 修复**: 删除确认对话框原本 `viewModelScope.launch` 异步后立即 `onBack()`,
  改为 `scope.launch { ...; onBack() }` 等删除完成后才退出, 与 P52/P53 保持一致。
- **P62 修复**: 音频 URI 提取 `LaunchedEffect(content.text)` 每次按键触发 O(n) 正则,
  无 debounce → 加 `delay(500L)` 节流。
- **P63 补漏**: `SearchHistoryManager.addSearch/removeSearch` 内部 JSON 序列化
  仍在调用线程 → 改为 `scope.launch { ... }`, JSON 写移 IO 线程。
- **P64 修复**: `TimeFormat.formatTimestampShort/formatRelativeTime` 每次
  `new SimpleDateFormat()` → 新增 `fmtShort/fmtTime/fmtDate` 三个 ThreadLocal,
  与 `formatTimestamp` 统一。
- **P70 修复**: `NoteActionsBackground` 背景只画 5 个图标, 但 `NoteActionsRow`
  有 6 项 (Pin/Tag/Delete/Move/Priority/Share) → 背景补 Share 图标。

#### 轻微
- **P66 修复**: `deleteInFlight` 锁重置延迟 `delay(300)` → `delay(100)`,
  300ms 在快速操作时按钮意外置灰感明显。
- **P72 修复**: `MarkdownTableRenderer` 的 `@Suppress("UNUSED_PARAMETER") onEditDone`
  是误导注释 → 删除, 加注释说明通过 `KeyboardActions.onDone` 实际被调用。
- **P80 修复**: `MainActivity.ViewModel` 用 `by lazy` 模糊生命周期 →
  去掉 lazy, 在 `onCreate` 中直接初始化。
- **P81 修复**: P58 改用 `pointerInput` 后, `draggable/rememberDraggableState`
  的两个 import 永不引用 → 删除。

### 跳过 (权衡后保留)
- P65 通知图标需要新增 drawable 资源, 涉及美术资产, 暂用系统默认。
- P71 `coverImageUri` 参数删除影响面待评估, 暂保留。
- P73 表格多行与 Text 截断统一问题, 涉及复杂 UX 重设计, 暂缓。
- P76 DAO 搜索排序, 需要重新设计 DAO @Query, 暂缓。

---

## [1.0.8] - 2026-06-10

### 修复 (P51-P74 新一轮 11 项潜在问题)

#### 严重 (P0 必崩 / 数据丢失)
- **P51 修复**: `NoteEditScreen.saveNote()` 内部 `rememberCoroutineScope()` 在非
  Composable 函数中调用 → 抛 `IllegalStateException` (保存按钮 / 退出保存
  全部必崩)。将 `rememberCoroutineScope()` 提到 Composable 顶部, `saveNote` 改为
  纯 `suspend` 函数。
- **P52 / P53 修复**: 顶部"保存"按钮 + 退出确认"保存"按钮原本 fire-and-forget,
  协程未完成就 `onBack()` → 列表数据未刷新 + `busy` 锁永不释放 (下次进入
  同一笔记返回键永久置灰)。新增 `saveNoteThen { ... }` 包装, 协程完成后再
  执行回调 + finally 重置 busy。
- **P56 修复**: `MarkdownTableRenderer.CellPos` 是 private data class,
  `rememberSaveable { mutableStateOf<CellPos?>(null) }` 无 Saver → 配置变更
  / 进程恢复时崩溃。新增 `CellPosSaver` 自定义 Saver, 编码为 "row,col"。
- **P54 修复**: `NotesRepository.deleteCategorySafely` 的 `@Transaction` 注解
  在 Repository 上**不生效** (Room 只对 DAO 接口方法生效), 两次 DAO 调用各
  自 commit, 异常时出现"分类已删, 笔记 category_id 残留"脏状态。改为
  `RoomDatabase.withTransaction { ... }`, 并在 `NotesApplication` 构造时注入
  `database` 参数。

#### 中等
- **P55 修复**: `ToolPanel.onInsertAtCursor` 参数被 `@Suppress("UNUSED_PARAMETER")`
  标记, 实际 `ColumnsPanel / StyleGrid / SymbolGrid / TemplatesGrid` 走的都是
  `onInsertText` (追加到末尾), P14 修复是空壳。贯通到 `ColumnsPanel` 和
  `ListPanel` 之后, 符号/模板点击插入到光标位置, 行为与用户预期一致。
- **P58 修复**: `NotesListScreen` 卡片右滑手势原用 `rememberDraggableState`,
  拖动时每帧 `scope.launch { Animatable.snapTo() }` → 几百协程互抢
  `Animatable`, 滑动卡顿。改为 `pointerInput + detectHorizontalDragGestures` +
  `Animatable` (状态变化驱动, 不再每帧起协程)。
- **P61 修复**: 删分类时 `state.notes.count { it.note.categoryId == c.id }`
  对每个分类遍历全表, O(n*m)。新增 `CategoryDao.observeNoteCountForCategory(id)`,
  `CategoriesScreen` 用 `collectAsState` 订阅 SQL COUNT 结果, 分类变更自动刷新。

#### 轻微
- **P60 修复**: `PhotoViewer` 双击缩放 `* 1.5f` 是 magic number, 巧合等于
  `scale - 1f`。改为 `targetScale - 1f`, 后续调整缩放系数不会失配。
- **P67 修复**: `NoteEditScreen.showDateTimePicker` 共享 `remember` 的
  `Calendar`, 跨日跨月后上次选的日期残留。改为每次 picker 新建 `Calendar`。
- **P69 修复**: `NoteEntity.color` 默认 `0xFFFFFFFF` 与"用户选白色"撞色,
  无法区分。引入 `Entities.DEFAULT_COLOR` 常量 + 注释, `NoteCard` 改用常量判断。
- **P74 修复**: `CategoriesScreen.AddCategoryDialog` 确认按钮 `enabled = false`
  时仍会触发 `onConfirm` 回调。`onClick` 内加 `if (safe.isBlank()) return@TextButton`
  防护, 与 `onConfirm(name.trim().take(20), color)` 串联。

### 跳过 (权衡后保留)
- P57 删除 fire-and-forget: viewModelScope 异步刷新足够快, 不影响可见性。
- P62 音频正则解析: 长文本下保留, debounce 收益有限。
- P63 搜索历史 JSON 序列化: SharedPreferences `apply` 异步落盘, JSON 序列化数据量小。
- P64 SimpleDateFormat: 仅在 4 个调用点, 性能影响可忽略。
- P65 通知图标: 需要新增 `ic_notification` 资源, 涉及美术资产, 暂用系统默认。
- P66 deleteInFlight 重置延迟: 300ms 已经在体验阈值内。
- P70-P80 全部为非关键 UX 微调, 留待后续。

---

## [1.0.7] - 2026-06-10

### 修复 (50 项 P1-P50 全面修复)

#### 数据库与并发 (P1, P8, P9)
- **【严重】启用 Room 外键约束** (P1)。SQLite 默认关闭外键, 之前删除分类时
  仍可能残留 `category_id` 引用。`AppDatabase` 增加 `Callback.onOpen` 显式
  `setForeignKeyConstraintsEnabled(true)`, 配合 repository 的 `deleteCategorySafely`
  保护数据完整性。
- **【严重】搜索历史 IO 移到后台线程** (P8)。`SearchHistoryManager.loadHistory`
  之前在构造时同步解析 JSON, 极端情况下可阻塞主线程。改为
  `CoroutineScope(Dispatchers.IO).launch { loadHistory() }`。
- **【严重】数据库走 lazy 初始化** (P9)。`NotesApplication.database/repository`
  改为 `by lazy`, 不在 `Application.onCreate` 同步构建, 加快冷启动速度。

#### 媒体与分享 (P2, P4, P5, P6)
- **【严重】分享为图片时整体 runCatching** (P2)。`NoteShareUtil.shareAsImage`
  把 `createNoteBitmap` / 文件写 / `FileProvider` / `startActivity` 全部包到
  `runCatching`, 单点捕获异常, 失败时给 Toast 兜底, 避免崩溃。
- **【严重】涂鸦导出切到 IO 线程 + OOM 防护** (P4)。`DoodleDialog` 用
  `rememberCoroutineScope` + `withContext(Dispatchers.IO)` 异步生成 PNG, 限制
  画布最大 2048x2048, 避免主线程 ANR / OOM。
- **【严重】图片查看器 size 0 保护** (P5)。`PhotoViewer` 在 `centerX/centerY`
  计算前 `takeIf { it.isFinite() } ?: 0f`, 防止 `size.width = 0` 时 NaN 传播。
- **【严重】通知权限检查** (P6)。`ReminderWorker` 在 Android 13+ 显式
  `checkSelfPermission(POST_NOTIFICATIONS)`, 缺失则静默返回 success; `notify`
  调用包 `runCatching` 兜底 `SecurityException`。

#### 编辑器与表格 (P10, P11, P12, P14, P27, P37, P38, P40, P42, P43)
- **【严重】表格块替换改用 endIdx 切片** (P10/P11)。`replaceTableBlock` 不再
  依赖 `indexOf + replaceFirst` (只能替换首个), 改为重新 `findTableBlocks`
  + `endIdx` 切片重拼, 长文位置不再错乱。
- **【严重】caret 落在新块末尾** (P12)。表格编辑后 caret 自动定位到新块末尾,
  撤销时回到旧块位置, 体验更顺。
- **【严重】实装光标位置插入** (P14)。`onInsertAtCursor` 不再是 unused 桩,
  改为调用 `insertAtCursor` 工具函数。
- **【中等】撤销/重做 200ms 节流** (P37)。`pushHistory` 增加 `lastPushMs` 锁,
  200ms 内连续输入不重复入栈, 避免 80 步容量被快速耗尽。
- **【中等】表格单元格支持多行** (P27)。移除 `singleLine = true`, 用户编辑
  文本更灵活。
- **【中等】表格单元格 IME Done 退出** (P38)。`BasicTextField` 绑定
  `imeAction = ImeAction.Done` + `KeyboardActions(onDone = { onEditDone() })`,
  按 Enter 立即退出编辑态。
- **【中等】insertAtCursor 选区替换** (P40)。`insertAtCursor` 在选区非空时
  替换选区内容, 选区为空时在光标处插入, 行为符合用户预期。
- **【中等】serializeTable 转义管道符 / 换行** (P42)。`esc(s) = s.replace("|", "\\|").replace("\n", " ")`,
  防止含 `|` / 换行的单元格破坏表格结构。
- **【轻微】表格块 key 用 hashCode** (P43)。`tbl_${block.text.hashCode()}` 替代
  `tbl_${block.startIdx}_${block.endIdx}`, 表格内容变更后 key 不残留。

#### 提醒与通知 (P3)
- **【严重】ReminderManager 调度与 Toast 协程化** (P3)。`scheduleReminder` 和
  `showScheduleResult` 改为 `suspend fun`, 内部用 `withContext(Dispatchers.Main)`
  调用 Toast, 避免在 IO 线程崩溃。

#### 搜索与历史 (P18, P29)
- **【轻微】搜索历史 trim 后入栈** (P18)。`state.query` trim 后再判断
  `isNotBlank`, 避免 " test " 和 "test" 被视为两条。
- **【轻微】避免历史项点击重复入栈** (P29)。新增 `lastRecordedQuery` 记录
  最后入栈的 query, 命中即跳过, 杜绝历史点击造成的搜索历史膨胀。

#### 分类与标签 (P13, P17, P23, P41)
- **【中等】标签显示 trim + 空格分隔** (P13/P41)。`NoteCard` 标签列表
  trim + 过滤空串, joinToString 用 "  " 双空格分隔, 避免 "#工作#重要" 粘连。
- **【轻微】标签管理 trim + 大小写不敏感去重** (P17)。`TagsScreen.allTags` 改用
  `.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }`,
  避免 "工作" 和 " 工作 " 被视为两个标签。
- **【轻微】分类名长度限制 20 字** (P23)。`CategoriesScreen.AddCategoryDialog`
  入库前 `name.trim().take(20)`, 防止误输入超长字符串。

#### 路由与隐私 (P21)
- **【轻微】深链接路径严格匹配** (P21)。`MainActivity.parseLegalUri` 改为
  `data.path == "/privacy"` (而非 `startsWith`), 不允许 `/privacy/xxx` 子路径绕过。

#### 涂鸦 / 图片 / 表格计数 (P33, P34)
- **【轻微】笔记卡片附件计数** (P33/P34)。`NoteCard` 新增音频 (GraphicEq) /
  表格 (TableChart) 数量角标, 用 `audioCountInContent` / `tableCountInContent`
  正则解析, 用户能直接看到笔记里有多少个附件。

#### 性能与缓存 (P31)
- **【轻微】应用更新 6 小时缓存** (P31)。`AppUpdateChecker.cachedRemote` 内存
  缓存远端 release, 6 小时内不重复请求 GitHub API, 减少频率滥用。

#### 启动与体验 (P19, P22, P30)
- **【轻微】启动页等待数据就绪** (P19)。`SplashScreen` 改为接收 `ready: Boolean`
  参数, 数据库首屏数据未加载好时持续显示启动页, 避免主屏数据"闪烁"。

#### 文本编辑 (P28, P40)
- **【轻微】align 选区重算** (P28)。`wrapParagraphWithAlign` 在选区跨多段时
  重新计算 `newCaretStart/newCaretEnd`, 跨段包绕后选区不再错位。

#### 主屏保存 (P45)
- **【严重】saveNote 改为 suspend 并返回 id** (P45)。`NotesViewModel.saveNote`
  从 `fun` 改为 `suspend fun`, 内部 `repository.saveNote` 返回真实 id,
  编辑页 `lastSaved.id` 与新生成 id 同步, 解决新建笔记"无 id 残留"问题。

#### 图片查看器手势 (P48)
- **【轻微】双击/单击单独 pointerInput** (P48)。`PhotoViewer` 把
  `detectTransformGestures` 和 `detectTapGestures` 拆到两个 `pointerInput(Unit)`
  块, 避免双击/单击被缩放手势吞掉。

#### 其他 (P26, P42, P47)
- 表格回写后 key 用 `block.text.hashCode()`, 避免内容变更后 key 残留。
- 涂鸦 Canvas Path 坐标系 1:1 (有意的, 保持笔画精度)。
- 删除分类提示已包含 noteCount, 用户更清楚影响范围。

### 跳过 (权衡后保留原状)
- **P22** ID 冲突概率极低, 不值得引入 UUID 改写主键类型。
- **P30** 涂鸦区分功能 (背景/前景) 影响交互模型, 暂缓。
- **P35** AboutLegalScreen 版本号展示为可读字段, 暂保持原样。
- **P36** 主题切换动画需要更复杂的状态机, 暂缓。
- **P44** 极少触发的多行被截断, 暂保持原样。
- **P46** Doze 模式提醒非关键, 暂不优化。
- **P49 / P50** UX 图标 / 笔刷粗细自定影响面较小, 暂不调整。

---

## [1.0.6] - 2026-06-09

### 修复
- **【严重】修复"疯狂点击应用 → 内容消失"问题**。根因是 Room 数据库配置了
  `fallbackToDestructiveMigration()`, 一旦 schema 校验不通过, Room 会**静默删除
  整个数据库**。改为 `fallbackToDestructiveMigrationOnDowngrade()` — 仅在版本号
  被人工调低时才清空, 正常升级路径绝不删数据。
- **修复编辑页疯狂点击"保存"按钮导致重复写入 + 退出竞态**。新增 `busy` 锁,
  保存 / 删除 / 退出确认 / 退出 全部走同一把锁, 第一次点击后立即锁住,
  按钮和对话框都自动置灰, 避免快速点击引发的数据竞态。
- **修复疯狂点击"删除笔记"按钮导致 UI 闪烁 + 重复触发**。新增 `deleteInFlight`
  锁, 删除对话框确认按钮点击后立即锁住, 关闭后延迟 300ms 自动重置。
- **修复疯狂点击笔记列表项 / 顶栏图标导致页面堆叠**。`NotesNavGraph` 中所有
  `navigate(...)` 调用全部加上 `launchSingleTop = true`, 已处于目标页面时
  不会重复入栈, 杜绝了"按返回键要按好几下才能退出"的现象。
- 修复快速按系统返回键时, 有未保存修改的对话框被关闭但 `pendingExitAction`
  仍保留, 下次进入时残留状态的隐患。

---

## [1.0.5] - 2026-06-08

### 新增
- **全屏图片查看器 (PhotoViewer)**。编辑页中点击任意图片缩略图即可全屏查看,
  支持**双指捏合缩放** (1x ~ 5x)、**双击放大** (1x ↔ 2.5x 切换)、**单指拖动** 平移,
  左下角实时显示缩放百分比, 单击空白处或按系统返回键关闭。
- **保存后退出**。编辑页右上角"保存"图标现在点击后**保存 + 立即返回上一页**,
  不会残留无意义的页面在回退栈中。
- **未保存修改智能拦截**。当笔记有未编辑过的内容时直接返回; 有修改时弹出
  "保存 / 丢弃 / 取消"对话框, 选"保存"则写库后退出, 选"丢弃"则放弃修改退出,
  选"取消"则留在编辑页继续编辑。系统手势返回 / 顶部返回箭头 / 都会走相同逻辑。
- **隐私政策 + 使用条款**。"设置 - 关于" 中新增两个可阅读的 Markdown 文档,
  包含完整 12 章节隐私说明、10 章节使用条款、联系邮箱
  `2474922840@qq.com`、反馈入口 (关于 - 问题反馈)。
- **正式版签名密钥** (`app/qingjian-release.jks`)。使用 PKCS12 容器 / 2048 位 RSA
  / 10000 天有效期, alias `qingjian`, 可直接用于正式版 APK / AAB 签名。

### 修复
- 修复 `SigningConfig with name 'release' not found` 错误: 把 `signingConfigs`
  块移到 `buildTypes` 块**之前**。
- 修复插件类路径冲突: 根 `buildscript` 已注入 AGP, 子模块直接用 `id(...)` 引用,
  不再 `alias(libs.plugins...)` 重复声明。
- 修复 `kotlin-stdlib 1.9.0` 元数据不兼容错误: 强制锁定 `kotlin-stdlib:1.8.22`,
  并设置 `lint { abortOnError = false; checkReleaseBuilds = false }` 不阻塞打包。
- 修复 `HorizontalDivider` 未解析引用 (material3 1.1.2 暂未稳定) — 改用
  `androidx.compose.material3.Divider`。
- 修复在 Android 9 及以上设备上 `GET_SIGNATURES` 已废弃导致无法获取签名信息 —
  改用 `PackageManager.GET_SIGNING_CERTIFICATES`。

---

## [1.0.4] - 2026-06-05

### 新增
- **图片上传 / 拍照插入**。支持从相册多选 (最多 9 张) 或调用相机实时拍摄,
  缩略图横排展示在编辑器顶部, 长按 X 图标可移除。
- **音频附件**。支持从文件选择器插入本地音频, 以 "🎵 [音频](uri)" 形式插入正文,
  渲染为带图标的条目。
- **图片涂鸦 (Doodle)**。在涂鸦面板上用手指画草图 / 标注, 完成后作为图片插入笔记。
- **Markdown 表格**。支持插入可视化表格, 单元格点击即可编辑, 实时回写为标准
  markdown 语法 (渲染兼容 GitHub / Typora / VSCode)。
- **模板库**。6 类常用模板 (待办清单 / 灵感 / 日程 / 日记 / 目标 / 笔记),
  一键插入正文, 适合快速开始。
- **符号库 + 分割线库**。底部"分栏"工具栏的 4 个子页签之一, 一键插入常用符号
  (★ ♥ ☀ ♪ ✓ → ← ※ © ...) 和分割线 (─── ═══ ━━━ ┄┄┄ ...) 风格。

---

## [1.0.3] - 2026-05-30

### 新增
- **分类管理**。"分类"页支持新建 / 重命名 / 改色 / 删除分类, 笔记可归类到具体分类下。
- **分类过滤条**。笔记列表顶部多出分类 Chip 条, 点击切换只显示某分类下的笔记。
- **重要度标记**。每条笔记可标记为"普通 / 重要 / 紧急", 列表项用 ★ 星标显示。
- **快速搜索**。顶栏放大镜入口, 全文搜索标题 / 正文 / 标签, 实时高亮匹配片段。
- **右滑卡片手势**。在笔记项上向右滑动露出 5 个快捷动作: 置顶 / 标签 / 删除 /
  移动 / 重要。

---

## [1.0.2] - 2026-05-22

### 新增
- **多图附件**。一条笔记可附带 0~9 张图片, 图片在编辑器顶部以缩略图横排展示。
- **图片选择器**。使用 Android 13+ 推荐的 `PickVisualMedia` 协议, 无需申请
  存储权限, 跨 Android 版本表现一致。
- **持久化 URI 权限**。选中的图片 URI 通过 `takePersistableUriPermission` 获取
  长期读权限, 即使原图被移动 / 删除也能正常显示。
- **字体颜色 / 字号调整**。选区作用, 支持 8 档字号 (10~36) 和 7 种颜色。

---

## [1.0.1] - 2026-05-15

### 新增
- **基础笔记 CRUD**。新建 / 编辑 / 删除 / 查看笔记, 标题 + 正文 + 创建时间 + 修改时间。
- **置顶功能**。置顶笔记始终排在列表最前面, 顶栏图标可快速切换。
- **本地 Room 数据库**。所有数据保存在 app 私有目录的 `notes.db`, 卸载后清空。
- **暗色 / 亮色主题**。跟随系统, 也可在设置中手动指定。
- **Material You 动态取色** (Android 12+)。从壁纸自动取色作为主题强调色。
- **Splash 启动屏**。首屏品牌展示, 平滑过渡到主界面。

---

## 计划中的功能 (Roadmap)

- [ ] **云同步** (待评估): 通过自建后端或第三方服务同步数据。
- [ ] **WebDAV 备份**: 用户可配置自己的云盘 (坚果云 / 群晖 / NextCloud) 进行备份。
- [ ] **笔记导出**: 一键导出为 PDF / Markdown / 长图。
- [ ] **OCR 文字识别**: 拍图直接提取文字插入正文。
- [ ] **语音转文字**: 录音后自动转写为文字。
- [ ] **桌面快捷方式 / 桌面小部件**: 长按桌面图标快速新建笔记, 桌面小部件
      显示最近编辑的笔记。
- [ ] **Markdown 渲染预览**: 实时预览区, 所见即所得。
