# Tasks

- [x] Task 1: 升级版本号
  - [x] 1.1 `app/build.gradle.kts` `versionCode = 3`, `versionName = "1.0.3"`

- [x] Task 2: 重写 `AppUpdateChecker.kt`
  - [x] 2.1 `suspend fetchLatestRelease()` 调 GitHub API
  - [x] 2.2 解析 JSON: `tag_name` / `name` / `body` / `html_url` / `published_at`
  - [x] 2.3 `ReleaseInfo` data class
  - [x] 2.4 `suspend checkForUpdate()` 返回 `UpdateCheckResult`
  - [x] 2.5 失败回退 `FALLBACK_LATEST_VERSION = "1.0.3"`
  - [x] 2.6 OkHttp 8s 超时
  - [x] 2.7 `parseVersion` 支持 `v` 前缀

- [x] Task 3: 重写 `UpdateDialog.kt`
  - [x] 3.1 `UpdateAvailableDialog` Composable
  - [x] 3.2 显示「当前/最新/发布时间/更新内容」
  - [x] 3.3 「立即更新」跳 `Intent.ACTION_VIEW` 浏览器
  - [x] 3.4 「稍后」关闭
  - [x] 3.5 `NoUpdateDialog` Composable
  - [x] 3.6 release notes 清理 markdown (`#` / ``` ``` / `[](url)`)
  - [x] 3.7 release notes 超过 220dp 可滚动

- [x] Task 4: 改 `SettingsScreen.kt`
  - [x] 4.1 `LATEST_VERSION` → `FALLBACK_LATEST_VERSION`
  - [x] 4.2 `delay(800)` 模拟删除, 改用 `checkForUpdate()`
  - [x] 4.3 `showUpdateDialog` 用 `UpdateAvailableDialog`
  - [x] 4.4 `showNoUpdateTip` 用 `NoUpdateDialog`
  - [x] 4.5 删 `AlertDialog` / `TextButton` / `delay` import
  - [x] 4.6 changelog 加 3 条「检查更新改造」

- [x] Task 5: 提交推送

# Task Dependencies
- Task 1 独立 ✅
- Task 2 依赖 OkHttp (已有) ✅
- Task 3 依赖 Task 2 (需要 ReleaseInfo) ✅
- Task 4 依赖 Task 2 + 3 ✅
- Task 5 依赖所有 ✅
