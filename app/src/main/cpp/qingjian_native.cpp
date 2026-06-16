#include <jni.h>
#include <string>
#include <sstream>
#include <iomanip>
#include <cstdint>
#include <cstring>

namespace {

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

// ============================================================
// SHA-256 纯 C++ 实现 (不依赖 OpenSSL)
// ============================================================

#define SHA256_BLOCK_SIZE 64
#define SHA256_DIGEST_SIZE 32

constexpr uint32_t SHA256_K[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
    0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
    0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
    0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
    0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
    0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
    0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
};

inline uint32_t rotr32(uint32_t x, int n) {
    return (x >> n) | (x << (32 - n));
}

inline uint32_t ch(uint32_t x, uint32_t y, uint32_t z) {
    return (x & y) ^ (~x & z);
}

inline uint32_t maj(uint32_t x, uint32_t y, uint32_t z) {
    return (x & y) ^ (x & z) ^ (y & z);
}

inline uint32_t sigma0(uint32_t x) {
    return rotr32(x, 2) ^ rotr32(x, 13) ^ rotr32(x, 22);
}

inline uint32_t sigma1(uint32_t x) {
    return rotr32(x, 6) ^ rotr32(x, 11) ^ rotr32(x, 25);
}

inline uint32_t gamma0(uint32_t x) {
    return rotr32(x, 7) ^ rotr32(x, 18) ^ (x >> 3);
}

inline uint32_t gamma1(uint32_t x) {
    return rotr32(x, 17) ^ rotr32(x, 19) ^ (x >> 10);
}

void sha256_init(uint32_t state[8]) {
    state[0] = 0x6a09e667;
    state[1] = 0xbb67ae85;
    state[2] = 0x3c6ef372;
    state[3] = 0xa54ff53a;
    state[4] = 0x510e527f;
    state[5] = 0x9b05688c;
    state[6] = 0x1f83d9ab;
    state[7] = 0x5be0cd19;
}

void sha256_transform(uint32_t state[8], const uint8_t block[SHA256_BLOCK_SIZE]) {
    uint32_t w[64];
    for (int i = 0; i < 16; i++) {
        w[i] = (uint32_t)block[4*i] << 24 |
               (uint32_t)block[4*i+1] << 16 |
               (uint32_t)block[4*i+2] << 8 |
               (uint32_t)block[4*i+3];
    }
    for (int i = 16; i < 64; i++) {
        w[i] = gamma1(w[i-2]) + w[i-7] + gamma0(w[i-15]) + w[i-16];
    }

    uint32_t a = state[0], b = state[1], c = state[2], d = state[3];
    uint32_t e = state[4], f = state[5], g = state[6], h = state[7];

    for (int i = 0; i < 64; i++) {
        uint32_t t1 = h + sigma1(e) + ch(e, f, g) + SHA256_K[i] + w[i];
        uint32_t t2 = sigma0(a) + maj(a, b, c);
        h = g; g = f; f = e; e = d + t1;
        d = c; c = b; b = a; a = t1 + t2;
    }

    state[0] += a; state[1] += b; state[2] += c; state[3] += d;
    state[4] += e; state[5] += f; state[6] += g; state[7] += h;
}

void sha256_final(uint32_t state[8], uint8_t digest[SHA256_DIGEST_SIZE]) {
    for (int i = 0; i < 8; i++) {
        digest[4*i] = (state[i] >> 24) & 0xff;
        digest[4*i+1] = (state[i] >> 16) & 0xff;
        digest[4*i+2] = (state[i] >> 8) & 0xff;
        digest[4*i+3] = state[i] & 0xff;
    }
}

std::string sha256Hex(const std::string& input) {
    uint32_t state[8];
    sha256_init(state);

    uint64_t total_len = input.size();
    uint8_t block[SHA256_BLOCK_SIZE];
    size_t pos = 0;

    while (pos + SHA256_BLOCK_SIZE <= total_len) {
        memcpy(block, input.data() + pos, SHA256_BLOCK_SIZE);
        sha256_transform(state, block);
        pos += SHA256_BLOCK_SIZE;
    }

    size_t remaining = total_len - pos;
    memset(block, 0, SHA256_BLOCK_SIZE);
    memcpy(block, input.data() + pos, remaining);

    block[remaining] = 0x80;

    if (remaining >= 56) {
        sha256_transform(state, block);
        memset(block, 0, SHA256_BLOCK_SIZE);
    }

    uint64_t bits = total_len * 8;
    for (int i = 0; i < 8; i++) {
        block[56 + i] = (bits >> (56 - 8*i)) & 0xff;
    }
    sha256_transform(state, block);

    uint8_t digest[SHA256_DIGEST_SIZE];
    sha256_final(state, digest);

    std::ostringstream oss;
    for (int i = 0; i < SHA256_DIGEST_SIZE; i++) {
        oss << std::hex << std::setw(2) << std::setfill('0') << static_cast<int>(digest[i]);
    }
    return oss.str();
}

}

extern "C" {

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
    std::string combined = salt + pinStr;
    std::string hex = sha256Hex(combined);

    return env->NewStringUTF(hex.c_str());
}

}
