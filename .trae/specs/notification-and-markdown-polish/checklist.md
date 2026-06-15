# Checklist

## REQ-01 启动权限引导页
- [ ] PermissionIntroPrefs.kt 单例, `introShown` 默认 false
- [ ] PermissionIntroScreen.kt Composable 存在, 含 TopAppBar + 5 项权限 + 2 按钮
- [ ] 「同意并继续」调用 `RequestMultiplePermissions` 一次性申请 5 项
- [ ] 「稍后再说」写入 `introShown = true` 跳过下次
- [ ] 二次启动不再展示 (验证 SharedPreferences 持久化)
- [ ] 5 项权限清单与 Manifest 声明完全一致

## REQ-02 通知权限
- [ ] 启动流程中 `rememberNotificationPermissionRequest` 仍保留
- [ ] 引导页「稍后」后通知权限仍可在 `LaunchedEffect` 中拉起

## REQ-03 Markdown 解析
- [ ] 引入 `org.commonmark:commonmark:0.21.0` + `commonmark-ext-gfm-tables`
- [ ] LegalDocumentRenderer.kt 输出 `List<RenderedBlock>`
- [ ] AboutLegalScreen.kt 删除简化解析, 改用新解析器
- [ ] 隐私政策中含表格的段落正确渲染为等宽对齐表格
- [ ] 链接 (e.g. 联系方式邮箱 / 备案 URL) 可点击跳浏览器
- [ ] 标题三级字号符合 22/18/16 sp 规范
- [ ] 列表嵌套层级缩进正常
- [ ] 行内代码 / 粗体 / 斜体正确
- [ ] 引用块左侧带竖线

## REQ-04 主页右滑
- [ ] SwipeableNoteRow 仍存在 (git blame 验证)
- [ ] 右滑 35% 阈值后弹出 5 动作菜单
- [ ] 5 个动作均能正确修改笔记 (置顶/标签/删除/移动/重要度)
- [ ] 卡片关闭后回弹到原位

## REQ-MOD-01 build.gradle.kts
- [ ] commonmark 0.21.0 依赖生效
- [ ] `gradle :app:dependencies` 输出含 commonmark
- [ ] APK 体积增量 < 300KB

## REQ-MOD-02 AndroidManifest
- [ ] CAMERA 声明
- [ ] RECORD_AUDIO 声明
- [ ] READ_MEDIA_IMAGES 声明
- [ ] READ_MEDIA_VIDEO 声明
- [ ] READ_MEDIA_AUDIO 声明
- [ ] POST_NOTIFICATIONS 声明
- [ ] READ_EXTERNAL_STORAGE maxSdkVersion=32

## 部署验证
- [ ] 旧 APK 完全卸载后重新 `Run` / `installDebug`
- [ ] 首次启动展示权限引导页 (需用户截图)
- [ ] 5 项权限均能正常申请
- [ ] 二次启动不再展示引导页
- [ ] 设置 → 关于 → 隐私政策, 表格 / 链接可点击
- [ ] 主页右滑笔记卡片弹出 5 动作菜单
