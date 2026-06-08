# 检查更新改造 Spec

## Why
清笺笔记 App 当前的版本检查功能完全是假的：`LATEST_VERSION` 硬编码 `1.0.1`、无网络请求、模拟 `delay(800)`、弹窗没有下载链接。需要对接 GitHub Releases API 真实查更新，并同步 `versionName` 到 `1.0.3`。

## What Changes
- `app/build.gradle.kts` 升 `versionName = "1.0.3"`, `versionCode = 3`
- `AppUpdateChecker.kt` 重写：
  - `suspend fetchLatestRelease()` → 调 `https://api.github.com/repos/ppaqtt/phone_app/releases/latest`
  - 解析 `tag_name / name / body / html_url / published_at`
  - `suspend checkForUpdate()` → 优先远端, 失败回退 `FALLBACK_LATEST_VERSION`
  - 返回 `UpdateCheckResult(currentVersion, latestVersion, releaseInfo, hasUpdate, errorMessage)`
- `UpdateDialog.kt` 重写：
  - `UpdateAvailableDialog` 显示「当前版本 / 最新版本 / 发布时间 / release notes 摘要」+ 跳浏览器
  - `NoUpdateDialog` 显示「已是最新」+ 错误提示
  - 旧 `UpdateDialog` 移除
- `SettingsScreen.kt`：
  - 改用 `checkForUpdate()`, 移除 `delay(800)` 模拟
  - `LATEST_VERSION` → `FALLBACK_LATEST_VERSION`
  - `showUpdateDialog` 用 `UpdateAvailableDialog`, `showNoUpdateTip` 用 `NoUpdateDialog`
  - 删 `AlertDialog`/`TextButton` import

## Impact
- Affected code:
  - [app/build.gradle.kts](file:///workspace/app/build.gradle.kts)
  - [AppUpdateChecker.kt](file:///workspace/app/src/main/java/com/example/notes/util/AppUpdateChecker.kt)
  - [UpdateDialog.kt](file:///workspace/app/src/main/java/com/example/notes/util/UpdateDialog.kt)
  - [SettingsScreen.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/SettingsScreen.kt)
- Affected specs: 应用版本检查
- 权限: INTERNET (已有)

## ADDED Requirements

### Requirement: REQ-01 真实网络请求
点击「检查更新」时调 GitHub Releases API。

#### Scenario: 网络可用
- **WHEN** 用户点击「检查更新」且网络正常
- **THEN** 调 `https://api.github.com/repos/ppaqtt/phone_app/releases/latest`
- **AND** 解析 `tag_name` / `name` / `body` / `html_url` / `published_at`
- **AND** 用 `tag_name` 与 `BuildConfig.VERSION_NAME` 比较

#### Scenario: 网络不可用
- **WHEN** GitHub API 请求失败 (超时/HTTP 错误/解析失败)
- **THEN** 走 `FALLBACK_LATEST_VERSION = "1.0.3"`
- **AND** `errorMessage = "网络异常, 已使用本地版本对比"`
- **AND** 弹窗底部用红色文字显示错误信息

### Requirement: REQ-02 版本号升级
`versionName` 必须与最新发版一致。

#### Scenario: 编译 debug APK
- **WHEN** 用户编译 debug build
- **THEN** `BuildConfig.VERSION_NAME = "1.0.3"`
- **AND** `versionCode = 3`
- **AND** 设备设置 → 应用信息中显示「1.0.3-debug」

### Requirement: REQ-03 弹窗内容增强
发现新版本时显示更详细的信息。

#### Scenario: UpdateAvailableDialog 布局
- **WHEN** 有新版本
- **THEN** 弹窗标题: 「发现新版本 v{latest}」
- **AND** 弹窗正文: 「当前版本 v{current}」/「最新版本 v{latest}」/「发布于 YYYY-MM-DD」/「更新内容」+ release notes 摘要 (滚动)
- **AND** 「立即更新」按钮: 用 `Intent.ACTION_VIEW` 跳 `htmlUrl` (GitHub Releases 页)
- **AND** 「稍后」按钮: 关闭弹窗

#### Scenario: NoUpdateDialog
- **WHEN** 无新版本
- **THEN** 弹窗显示「当前版本 v{current} 已是最新。」
- **AND** 如有网络错误, 附加小字提示
- **AND** 「好的」按钮关闭

### Requirement: REQ-04 release notes 清理
GitHub release notes 是 markdown, 弹窗只展示可读文本。

#### Scenario: 渲染 release notes
- **WHEN** 弹窗显示更新内容
- **THEN** 移除 markdown 标题前缀 (`#`)
- **AND** 移除代码块 ``` ```
- **AND** 把 `[text](url)` 简化为 `text`
- **AND** 超过 220dp 时可滚动
- **AND** 若清理后为空, 用占位文本「本版本修复了若干问题, 优化了使用体验。」

## MODIFIED Requirements
无

## REMOVED Requirements

### Requirement: REQ-REM-01 模拟 delay
**Reason**: 之前用 `delay(800)` 模拟网络请求, 现已用真实 OkHttp 请求代替
**Migration**: 删除 `kotlinx.coroutines.delay` import 和 `delay(800)` 调用

### Requirement: REQ-REM-02 旧 UpdateDialog
**Reason**: 旧 Dialog 不可定制 release notes, 没有跳转链接
**Migration**: 拆为 `UpdateAvailableDialog` 和 `NoUpdateDialog`
