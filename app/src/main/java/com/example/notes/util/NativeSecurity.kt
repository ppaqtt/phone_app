package com.example.notes.util

/**
 * F20: Native 安全工具 — JNI 桥接层。
 *
 * 将 SHA-256 加盐哈希逻辑移到 C/C++ (qingjian_native.cpp),
 * 反编译 .so 比 dex 难得多, 且盐值以异或混淆存储, strings 命令看不到明文。
 *
 * 使用方式:
 *   val hash = NativeSecurity.hashPin("123456")
 *
 * 编译要求:
 *   - NDK r25+ (Android Studio SDK Manager 安装)
 *   - app/build.gradle.kts 中配置 externalNativeBuild { cmake { path = ... } }
 *   - NDK 自带 OpenSSL (openssl/sha.h)
 */
object NativeSecurity {

    init {
        System.loadLibrary("qingjian_native")
    }

    /**
     * 对 PIN 进行 SHA-256 加盐哈希。
     * 盐值在 native 层以异或混淆存储, 运行时还原。
     *
     * @param pin 用户输入的 PIN (4-8 位数字)
     * @return 64 位十六进制 SHA-256 哈希字符串
     */
    external fun hashPin(pin: String): String
}
