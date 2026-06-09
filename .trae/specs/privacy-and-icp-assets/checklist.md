# Checklist

- [x] [privacy_policy.md](file:///workspace/app/src/main/res/raw/privacy_policy.md) 包含: SDK 清单 (OkHttp 4.11.0 / Retrofit 2.9.0 / Gson 2.10.1 / Coil 2.4.0 / Hilt 2.48 / Lottie 6.1.0 / Timber 5.0.1 / Accompanist 0.30.1) / 权限说明 / 数据存储 / 第三方服务 / 联系方式 / 生效日期
- [x] [terms_of_service.md](file:///workspace/app/src/main/res/raw/terms_of_service.md) 包含: 服务内容 / 用户行为 / 知识产权 / 免责 / 条款变更 / 联系方式 / 生效日期
- [x] [PackageSignatureReader.kt](file:///workspace/app/src/main/java/com/example/notes/util/PackageSignatureReader.kt) 兼容 API 24+ (API < 28 走 GET_SIGNATURES, API >= 28 走 GET_SIGNING_CERTIFICATES)
- [x] [AboutLegalScreen.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/AboutLegalScreen.kt) 渲染 raw 资源为可滚动页面, 顶部有返回按钮
- [x] [SettingsScreen.kt](file:///workspace/app/src/main/java/com/example/notes/ui/screens/SettingsScreen.kt) 关于区域加 3 入口: 隐私政策 / 使用条款 / 应用备案信息
- [x] 应用备案信息 Dialog 显示 6 字段: applicationId / versionName / versionCode / SHA1 / MD5 / 隐私政策 URL
- [x] 6 字段每行有「复制」按钮, 复制后能用剪贴板读到
- [x] AndroidManifest.xml MainActivity 注册 `app://privacy` 和 `https://qing-jian.ppaqtt.com/privacy` deep link
- [x] DeepLink 测试: `adb shell am start -W -a android.intent.action.VIEW -d "app://privacy"` 能跳隐私政策页
- [x] 运行时计算的 SHA1 与 `keytool -printcert -jarfile app-debug.apk` 输出一致
- [x] 运行时计算的 MD5 与 `keytool -printcert -jarfile app-debug.apk` 输出一致
- [x] Build → Make Project 通过, 0 编译错误 0 warning
- [x] versionName 仍为 1.0.4, versionCode = 4 保持
