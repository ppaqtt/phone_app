    val entries: List<Entry> = listOf(
        Entry(
            version = "v1.20.0",
            date = "2026-06-16",
            items = listOf(
                "安全：NDK Native 层 PIN 哈希 — SHA-256 加盐哈希逻辑从 Kotlin/JVM 移到 C/C++ (OpenSSL), " +
                    "盐值以异或混淆存储 (编译后不以明文出现在 .rodata); " +
                    "NativeSecurity JNI 桥接类 + CMake 构建配置; " +
                    "native 库加载失败时自动回退到 JVM 实现",
                "安全：运行时安全检测 — SecurityChecker 工具类, 启动时后台检测 Root/调试器/模拟器/Hook 框架/签名重打包; " +
                    "MainActivity.onCreate 集成检测, 检测到威胁时记录日志 (不强制退出, 避免影响正常用户)",
                "安全：APK 签名校验 — 对比运行时签名 SHA1 与预期指纹, 防止重打包; " +
                    "PackageSignatureReader 新增 getSha1Hex() 便捷函数",
                "安全：ProGuard 规则补充 — 保留 NativeSecurity JNI 类 + SecurityChecker 数据类",
                "安全：NDK 构建配置 — externalNativeBuild cmake + ndkVersion 25.2.9519653, " +
                    "ABI 过滤仅保留 armeabi-v7a/arm64-v8a",
                "升级：版本号 v1.19.0 → v1.20.0 (versionCode 29 → 30)"
            )
        ),
        Entry(
            version = "v1.19.0",
            date = "2026-06-15",
            items = listOf(
                "新增：应用锁生物识别解锁 — 支持指纹/人脸 (2D/3D) 解锁, 自动检测设备能力; " +
                    "设置页新增「指纹/人脸解锁」开关, 显示设备支持状态 (可用/未录入/不支持/不可用); " +
                    "解锁页新增指纹图标按钮, 点击唤起 BiometricPrompt; " +
                    "生物识别失败时自动回退到 PIN 输入, PIN 始终作为兜底方案",
                "新增：BiometricHelper 工具类 — 封装 BiometricManager.canAuthenticate (BIOMETRIC_WEAK) " +
                    "检测 + BiometricPrompt 认证流程, 支持 onSuccess/onError/onCancel 回调",
                "升级：版本号 v1.18.0 → v1.19.0 (versionCode 28 → 29); " +
                    "新增 androidx.biometric:biometric:1.2.0-alpha05 依赖"
            )
        ),