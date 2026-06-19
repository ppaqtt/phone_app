package com.example.notes.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 进阶功能: 单篇笔记加密工具。
 *
 * 算法: AES-128-CBC + PKCS5Padding
 * - 密钥派生: PBKDF2WithHmacSHA1, 10000 iterations, 128 bit key
 * - 盐值: 16 字节随机, 每次加密独立, 与密文一起存
 * - IV: 16 字节随机, 放在密文前 (ciphertext = IV || encrypted)
 *
 * 存储格式 (Base64):  "BASE64(IV + CIPHERTEXT)"
 *
 * 安全说明:
 * - 加密密钥由用户密码派生, 不存储
 * - 用户忘记密码 = 笔记永久不可读 (设计取舍)
 * - salt 与 IV 都随机生成, 即使两篇笔记密码相同, 密文也不同
 */
object NoteEncryptor {

    private const val ITERATIONS = 10_000
    private const val KEY_LENGTH = 128
    private const val SALT_LEN = 16
    private const val IV_LEN = 16
    private const val ALGORITHM = "AES"
    private const val TRANSFORM = "AES/CBC/PKCS5Padding"
    private const val SECRET = "PBKDF2WithHmacSHA1"

    /** 加密内容, 返回包含 salt + 密文 的 Base64 字符串 */
    fun encrypt(plain: String, password: String): String {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, javax.crypto.spec.IvParameterSpec(iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // 打包: salt + iv + encrypted
        val combined = ByteArray(SALT_LEN + IV_LEN + encrypted.size)
        System.arraycopy(salt, 0, combined, 0, SALT_LEN)
        System.arraycopy(iv, 0, combined, SALT_LEN, IV_LEN)
        System.arraycopy(encrypted, 0, combined, SALT_LEN + IV_LEN, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** 解密内容, 失败抛异常 */
    fun decrypt(packed: String, password: String): String {
        val combined = Base64.decode(packed, Base64.NO_WRAP)
        require(combined.size > SALT_LEN + IV_LEN) { "加密数据长度无效" }
        val salt = combined.copyOfRange(0, SALT_LEN)
        val iv = combined.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
        val encrypted = combined.copyOfRange(SALT_LEN + IV_LEN, combined.size)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, javax.crypto.spec.IvParameterSpec(iv))
        val plain = cipher.doFinal(encrypted)
        return String(plain, Charsets.UTF_8)
    }

    /** 仅派生 salt (用于存盘后校验密码) */
    fun newSalt(): String {
        val salt = ByteArray(SALT_LEN)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(SECRET)
        val raw = factory.generateSecret(spec).encoded
        return SecretKeySpec(raw, ALGORITHM)
    }
}
