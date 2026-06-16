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

- 🔒 **安全**：NDK Native 层 PIN 哈希 — SHA-256 加盐哈希逻辑从 Kotlin/JVM 移到 C/C++ (OpenSSL), 盐值以异或混淆存储; NativeSecurity JNI 桥接类 + CMake 构建配置; native 库加载失败时自动回退到 JVM 实现
- 🔒 **安全**：运行时安全检测 — SecurityChecker 工具类, 启动时后台检测 Root/调试器/模拟器/Hook 框架/签名重打包; 检测到威胁时记录日志 (不强制退出)
- 🔒 **安全**：APK 签名校验 — 对比运行时签名 SHA1 与预期指纹, 防止重打包
- 🔒 **安全**：ProGuard 规则补充 — 保留 NativeSecurity JNI 类 + SecurityChecker 数据类
- 🔒 **安全**：NDK 构建配置 — externalNativeBuild cmake + ndkVersion 25.2.9519653, ABI 过滤仅保留 armeabi-v7a/arm64-v8a
- 🔖 **升级**：版本号 v1.19.0 → v1.20.0 (versionCode 29 → 30)

---

## v1.19.0 (2026-06-15)

- ✨ **新增**：应用锁生物识别解锁 — 支持指纹/人脸 (2D/3D) 解锁, 自动检测设备能力; 设置页新增「指纹/人脸解锁」开关, 显示设备支持状态 (可用/未录入/不支持/不可用); 解锁页新增指纹图标按钮, 点击唤起 BiometricPrompt; 生物识别失败时自动回退到 PIN 输入, PIN 始终作为兜底方案