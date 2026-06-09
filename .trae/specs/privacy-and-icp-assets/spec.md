# 隐私政策 + 使用条款 + 备案元数据 Spec

## Why
清笺笔记 APP 上架 / ICP 备案需要：
1. **隐私政策**和**使用条款**两份文本 (必须公开在 APP 内 + APP 外官网/商店描述里)
2. **APK 备案元数据**: 包名 (applicationId), 签名 SHA1, 签名 MD5 (用于备案系统「应用签名」字段)

当前 APP 内「设置 → 关于」没有这两个入口, 也无法获取 APK 签名信息 (运行时读 PackageInfo 即可, 不需要 keystore 文件)。

## What Changes
- 新增 [privacy_policy.md](file:///workspace/app/src/main/res/raw/privacy_policy.md) (Markdown, 隐私政策)
- 新增 [terms_of_service.md](file:///workspace/app/src/main/res/raw/terms_of_service.md) (Markdown, 使用条款)
- 新增 [AboutLegalScreen.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/AboutLegalScreen.kt) (Markdown 渲染页, 滚动 + 标题)
- 新增 [PackageSignatureReader.kt](file:///workspace/app/src/main/java/com/example/notes/util/PackageSignatureReader.kt) (运行时读 SHA1/MD5)
- 修改 [SettingsScreen.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/SettingsScreen.kt): 「关于」区块加 3 个入口
  - 「隐私政策」 → 跳 AboutLegalScreen(privacy_policy.md)
  - 「使用条款」 → 跳 AboutLegalScreen(terms_of_service.md)
  - 「应用备案信息」 → 弹 Dialog 显示 包名 / 版本 / SHA1 / MD5 / 隐私政策 URL
- 修改 [AndroidManifest.xml](file:///workspace/app/src/main/AndroidManifest.xml): 注册隐私政策 Activity (供外部浏览器调起, 走 ACTION_VIEW + file:// 或 https://)

## Impact
- Affected specs: 关于页面, 备案合规
- Affected code:
  - 新增 2 份 Markdown 资源
  - 新增 AboutLegalScreen.kt (Compose Markdown 渲染)
  - 新增 PackageSignatureReader.kt (PackageManager 读 signature)
  - SettingsScreen.kt 加 3 个入口

## ADDED Requirements

### Requirement: REQ-01 隐私政策文档
「隐私政策」 Markdown 文本, 详细列出:
- 收集哪些信息 (笔记标题/正文/图片/音频) 及用途
- 第三方 SDK 清单 (OkHttp, Retrofit, Gson, Coil, Hilt, Lottie, Timber, Accompanist)
- 权限申请说明 (相机/麦克风/相册/通知/提醒)
- 第三方服务 (GitHub Releases API, 腾讯文档)
- 数据存储位置 (本地 Room/SQLite, 不上传服务器)
- 注销账号 / 删除数据方式
- 联系方式 (邮箱)
- 生效日期: 2026-06-09

#### Scenario: APP 内查看
- **WHEN** 用户点击「设置 → 关于 → 隐私政策」
- **THEN** 打开全屏滚动页, 渲染 privacy_policy.md
- **AND** 顶部有返回按钮, 标题「隐私政策」

#### Scenario: 外部浏览器调起
- **WHEN** 用户从外部浏览器点击 `app://privacy` 或 `https://qbb.ppaqtt/privacy` 链接
- **THEN** 调起 APP 并打开隐私政策页

### Requirement: REQ-02 使用条款文档
「使用条款」 Markdown 文本, 列出:
- 服务内容 (笔记记录/图片/音频/涂鸦)
- 用户行为规范 (不发布违法/侵权内容)
- 知识产权 (APP 代码 MIT / 笔记内容归用户)
- 免责声明
- 条款变更
- 联系方式
- 生效日期: 2026-06-09

#### Scenario: APP 内查看
- **WHEN** 用户点击「设置 → 关于 → 使用条款」
- **THEN** 打开全屏滚动页, 渲染 terms_of_service.md

### Requirement: REQ-03 备案元数据展示
「应用备案信息」 Dialog 展示以下字段, 全部运行时从 PackageManager 读出, 无需硬编码:

| 字段 | 来源 | 用途 |
|---|---|---|
| 应用包名 (applicationId) | `packageName` | 备案「应用包名」 |
| 版本名 (versionName) | `PackageInfo.versionName` | 备案「应用版本」 |
| 版本号 (versionCode) | `PackageInfo.versionCode` | 备案「版本号」 |
| 签名 SHA1 | `Signature` → MessageDigest("SHA1") | 备案「应用签名 SHA1」 |
| 签名 MD5 | `Signature` → MessageDigest("MD5") | 备案「应用签名 MD5」 |
| 隐私政策 URL | `https://gitee.com/ppaqtt/qing-jian-privacy` (占位, 用户需替换为实际部署 URL) | 备案「隐私政策地址」 |

#### Scenario: 用户查看
- **WHEN** 用户点击「设置 → 关于 → 应用备案信息」
- **THEN** 弹出 Dialog, 显示上述 6 个字段
- **AND** 每个字段右侧有「复制」按钮 (ClipboardManager)

#### Scenario: 签名计算正确
- **WHEN** APK 签名 (debug keystore 或 release keystore) 已知
- **THEN** 运行时计算的 SHA1/MD5 与 keystore 工具 `keytool -printcert` 结果一致

### Requirement: REQ-04 关于页面加 3 入口
在 [SettingsScreen.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/SettingsScreen.kt) 的「关于」区域, 已有的「更新日志」**下方**加 3 个 Text 入口 (带右箭头), 跳对应页/弹 Dialog。

#### Scenario: 入口可见
- **WHEN** 用户进入设置 → 关于
- **THEN** 看到 3 个新入口: 「隐私政策」「使用条款」「应用备案信息」

## MODIFIED Requirements

无 (本次纯新增, 不改任何已有功能行为)。

## REMOVED Requirements

无。
