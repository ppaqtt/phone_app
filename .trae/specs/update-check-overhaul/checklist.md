# Checklist

## REQ-01 真实网络请求
- [x] AppUpdateChecker.fetchLatestRelease() 调 GitHub API
- [x] 请求 URL: `https://api.github.com/repos/ppaqtt/phone_app/releases/latest`
- [x] Header: `Accept: application/vnd.github+json`, `User-Agent: QingJian-Android/{version}`
- [x] 解析 `tag_name` / `name` / `body` / `html_url` / `published_at`
- [x] 失败 → `FALLBACK_LATEST_VERSION = "1.0.3"` + `errorMessage`

## REQ-02 版本号升级
- [x] `app/build.gradle.kts` `versionCode = 3`
- [x] `app/build.gradle.kts` `versionName = "1.0.3"`
- [x] `BuildConfig.VERSION_NAME` 在 SettingsScreen 显示

## REQ-03 弹窗内容
- [x] UpdateAvailableDialog: 标题 + 当前版本 + 最新版本 + 发布时间 + 更新内容
- [x] 「立即更新」跳浏览器 (Intent.ACTION_VIEW)
- [x] 「稍后」关闭
- [x] NoUpdateDialog: 「已是最新」+ 错误提示

## REQ-04 release notes 清理
- [x] 移除 markdown `#` 标题前缀
- [x] 移除代码块 ``` ```
- [x] `[text](url)` 简化为 `text`
- [x] 超过 220dp 可滚动 (`heightIn(max=220.dp) + verticalScroll`)
- [x] 清理后为空 → 占位文本

## 提交
- [x] git commit 包含 AppUpdateChecker.kt / UpdateDialog.kt / SettingsScreen.kt / build.gradle.kts
- [ ] git push 到 trae/solo-agent-30qsRV

## 部署验证
- [ ] Clean + Rebuild + Run
- [ ] 设备设置里看到 v1.0.3
- [ ] 点击「检查更新」, 看到请求真实发出 (Logcat)
- [ ] 模拟网络正常, 显示「已是最新」+ 真实 latest tag
- [ ] 模拟网络断开, 显示「已是最新」+ 错误提示
