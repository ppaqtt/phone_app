# Tasks

- [x] Task 1: 隐私政策 Markdown 文档
  - [x] SubTask 1.1: 写 [privacy_policy.md](file:///workspace/app/src/main/res/raw/privacy_policy.md) (含 SDK 清单/权限/数据存储/联系方式/生效日期)
  - [x] SubTask 1.2: 写 [terms_of_service.md](file:///workspace/app/src/main/res/raw/terms_of_service.md) (含服务内容/用户行为/免责/联系方式/生效日期)

- [x] Task 2: PackageSignatureReader.kt
  - [x] SubTask 2.1: 写 `data class ApkSignature(sha1, md5)`
  - [x] SubTask 2.2: 写 `suspend fun readApkSignature(context: Context): ApkSignature?`, 用 `PackageManager.getPackageInfo(name, GET_SIGNING_CERTIFICATES)` (API 28+) 或 `GET_SIGNATURES` (旧版)
  - [x] SubTask 2.3: 兼容处理: API 28+ 走 SigningInfo, API < 28 走 Signature[]

- [x] Task 3: AboutLegalScreen.kt
  - [x] SubTask 3.1: 创建 Composable `AboutLegalScreen(title: String, rawResId: Int, onBack: () -> Unit)`
  - [x] SubTask 3.2: 顶部 TopAppBar 含返回按钮 + 标题
  - [x] SubTask 3.3: 用 `LazyColumn` + `Text(text = md, lineHeight = 24.sp)` 渲染整段 Markdown (无需完整 Markdown 库, 直接当纯文本即可, 也支持 ## / ** 等最简)
  - [x] SubTask 3.4: 加载本地 raw 资源, 用 `context.resources.openRawResource(rawResId).bufferedReader().readText()`

- [x] Task 4: SettingsScreen.kt 加 3 入口
  - [x] SubTask 4.1: 在「关于」标题下加 3 行 `Text("隐私政策")` / `Text("使用条款")` / `Text("应用备案信息")`, 带 `Icons.Filled.ChevronRight`
  - [x] SubTask 4.2: 隐私政策 / 使用条款 → 调用 `navController.navigate("about/privacy")` 等 (或本地 var 切换, 视当前导航结构)
  - [x] SubTask 4.3: 应用备案信息 → 弹 `AlertDialog`, 显示 6 个字段, 每行带「复制」按钮
  - [x] SubTask 4.4: 复制按钮用 `ClipboardManager.setPrimaryClip(ClipData.newPlainText(label, value))`

- [x] Task 5: AndroidManifest.xml 注册 DeepLink
  - [x] SubTask 5.1: 在 MainActivity 加 `<intent-filter>` 接收 `app://privacy` scheme
  - [x] SubTask 5.2: 在 MainActivity 加 `<intent-filter>` 接收 `https://qing-jian.ppaqtt.com/privacy` scheme
  - [x] SubTask 5.3: MainActivity 解析 intent.data, 用 `mutableStateOf<LegalPage?>(null)` 决定显示 AboutLegalScreen 还是主界面

- [x] Task 6: 编译与运行验证
  - [x] SubTask 6.1: Build → Make Project, 0 编译错误 0 warning
  - [x] SubTask 6.2: 跑 App 验证: 设置 → 关于 → 3 入口都能点开
  - [x] SubTask 6.3: 验证复制按钮: 复制 SHA1, 粘贴到 cmd 校验格式 (40 字符 hex)
  - [x] SubTask 6.4: 验证 DeepLink: `adb shell am start -W -a android.intent.action.VIEW -d "app://privacy"` 能跳到隐私政策页

# Task Dependencies
- Task 3 依赖 Task 1 (需要 raw markdown 资源)
- Task 4 依赖 Task 2, Task 3
- Task 5 依赖 Task 3
- Task 6 依赖 Task 1, Task 2, Task 3, Task 4, Task 5
