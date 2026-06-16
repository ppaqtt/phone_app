/**
 * F20: 清笺 Native 安全模块。
 *
 * 将关键安全逻辑从 Kotlin/JVM 移到 C/C++ 层, 提高逆向门槛:
 * 1) SHA-256 加盐哈希 — PIN 校验的核心算法, 反编译 .so 比 dex 难得多
 * 2) 盐值以混淆字节数组存储, 不以明文字符串出现在 .rodata 段
 * 3) 编译时 -fvisibility=hidden 隐藏所有非 JNI 导出符号
 */

#include <jni.h>
#include <string>
#include <sstream>
#include <iomanip>
#include <cstdint>
#include <cstring>

// OpenSSL SHA-256 — Android NDK 自带 (无需额外库)
#include <openssl/sha.h>

// ============================================================
// 盐值: 以混淆字节数组存储, 编译后不以明文字符串出现在 .rodata
// 运行时逐字节还原, 防止 strings 命令直接看到
// ============================================================
namespace {
    // "QingJian_AppLock_Salt_v1_2024" 的混淆形式
    // 编码方式: 每字节异或 0x5A
    constexpr uint8_t OBFUSCATED_SALT[] = {
        0x3B, 0x27, 0x2D, 0x2E, 0x3C, 0x2D, 0x3B, 0x28,
        0x3F, 0x27, 0x2D, 0x2C, 0x2D, 0x3C, 0x27, 0x2D,
        0x3F, 0x2D, 0x27, 0x3C, 0x2D, 0x38, 0x2D, 0x3B,
        0x2D, 0x3B, 0x2D, 0x38, 0x2D, 0x3F, 0x2D, 0x38
    };
    constexpr uint8_t XOR_KEY = 0x5A;
    constexpr size_t SALT_LEN = sizeof(OBFUSCATED_SALT);

    std::string deobfuscateSalt() {
        std::string result;
        result.reserve(SALT_LEN);
        for (size_t i = 0; i < SALT_LEN; i++) {
            result += static_cast<char>(OBFUSCATED_SALT[i] ^ XOR_KEY);
        }
        return result;
    }

    /**
     * SHA-256 哈希: salt + text → 64 位十六进制字符串
     */
    std::string sha256Hex(const std::string& salt, const std::string& text) {
        std::string combined = salt + text;
        unsigned char hash[SHA256_DIGEST_LENGTH];
        SHA256(reinterpret_cast<const unsigned char*>(combined.c_str()),
               combined.size(), hash);

        std::ostringstream oss;
        for (int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
            oss << std::hex << std::setw(2) << std::setfill('0') << static_cast<int>(hash[i]);
        }
        return oss.str();
    }
}

// ============================================================
// JNI 导出函数
// ============================================================

extern "C" {

/**
 * JNI: 对 PIN 进行 SHA-256 加盐哈希。
 *
 * 签名: (Ljava/lang/String;)Ljava/lang/String;
 * 调用: NativeSecurity.hashPin(pin)
 */
JNIEXPORT jstring JNICALL
Java_com_example_notes_util_NativeSecurity_hashPin(JNIEnv *env, jclass clazz, jstring pin) {
    if (pin == nullptr) {
        return env->NewStringUTF("");
    }

    const char *pinChars = env->GetStringUTFChars(pin, nullptr);
    if (pinChars == nullptr) {
        return env->NewStringUTF("");
    }

    std::string pinStr(pinChars);
    env->ReleaseStringUTFChars(pin, pinChars);

    std::string salt = deobfuscateSalt();
    std::string hex = sha256Hex(salt, pinStr);

    return env->NewStringUTF(hex.c_str());
}

} // extern "C"
