# Checklist

## REQ-01 启动权限引导页
- [x] PermissionIntroPrefs.kt 单例, `introShown` 默认 false
- [x] PermissionIntroScreen.kt Composable 存在, 含 TopAppBar + 权限列表 + 2 按钮
- [x] 「同意并继续」调用 `RequestMultiplePermissions` 一次性申请
- [x] 「稍后再说」写入 `introShown = true` 跳过下次
- [x] 二次启动不再展示 (SharedPreferences 持久化)
- [x] 权限清单与 Manifest 声明一致 (7 项, 按 SDK 版本动态过滤)

## REQ-02 通知权限
- [x] 启动流程中 `rememberNotificationPermissionRequest` 仍保留
- [x] 引导页展示期间不触发通知权限弹窗 (LaunchedEffect 依赖 showPermissionIntro)

## REQ-03 Markdown 解析
- [x] 引入 `org.commonmark:commonmark:0.21.0` + `commonmark-ext-gfm-tables:0.21.0`
- [x] LegalDocumentRenderer.kt 输出 `List<RenderedBlock>` + `InlineNode` 层级
- [x] AboutLegalScreen.kt 删除简化解析, 改用新解析器
- [x] 表格解析使用 TableBlock → TableHead/TableBody → TableRow → TableCell
- [x] 链接用 `ClickableText` + `StringAnnotation` + `LocalUriHandler` 可点击跳浏览器
- [x] 标题三级字号 22/18/16 sp + 加粗
- [x] 列表 BulletList/OrderedList 支持
- [x] 行内代码 (Monospace + 浅灰底) / 粗体 / 斜体正确
- [x] 引用块左侧竖线 + 浅色文字
- [x] 代码块等宽字体 + 浅灰底色 + 圆角

## REQ-04 主页左滑
- [x] SwipeableNoteRow 存在 (第 470-526 行)
- [x] NoteActionsRow 存在 (第 568-620 行)
- [x] LazyColumn 中每个笔记用 SwipeableNoteRow 包裹 (第 334 行)
- [x] 左滑 35% 阈值后弹出 6 动作菜单 (置顶/标签/删除/移动/重要度/分享)
- [x] 6 个动作均正确接上 viewModel
- [x] 卡片关闭后回弹到原位 (animateTo(0f))

## REQ-MOD-01 build.gradle.kts
- [x] commonmark 0.21.0 依赖已添加
- [x] commonmark-ext-gfm-tables 0.21.0 依赖已添加
- [x] 语法正确无错误

## REQ-MOD-02 AndroidManifest
- [x] CAMERA 声明
- [x] RECORD_AUDIO 声明
- [x] READ_MEDIA_IMAGES 声明
- [x] READ_MEDIA_VIDEO 声明
- [x] READ_MEDIA_AUDIO 声明
- [x] POST_NOTIFICATIONS 声明
- [x] READ_EXTERNAL_STORAGE maxSdkVersion=32

## 部署验证 (需用户在设备上验证)
- [ ] 旧 APK 完全卸载后重新 `Run` / `installDebug`
- [ ] 首次启动展示权限引导页
- [ ] 7 项权限均能正常申请
- [ ] 二次启动不再展示引导页
- [ ] 设置 → 关于 → 隐私政策, 表格 / 链接可点击
- [ ] 主页左滑笔记卡片弹出动作菜单
