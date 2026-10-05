package com.aistudy.aistudy.data.security

/**
 * N1 - PBKDF2-HMAC-SHA256 de luu mat khau.
 *
 * Dat muc cao hon mac dinh Android de cham `PBKDF2WithHmacSHA256` la con nghe
 * "fast" khong con khuyen nghi nua. Salt 16 byte sinh rieng cho tung user.
 */
object PasswordHasher {

    const val ITERATIONS = 120_000
    const val SALT_BYTES = 16
    const val KEY_LENGTH_BITS = 256

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    data class Hashed(val hash: String, val salt: String)

    fun hash(password: String, salt: java.security.SecureRandom = java.security.SecureRandom()): Hashed {
        val saltBytes = ByteArray(SALT_BYTES).also { salt.nextBytes(it) }
        val hashBytes = derive(password, saltBytes)
        return Hashed(
            hash = hashBytes.toBase64(),
            salt = saltBytes.toBase64()
        )
    }

    /**
     * So sanh thoi gian hang khong de khong lo thong tin qua thoi gian do.
     *
     * Dung `MessageDigest.isEqual` thay vi `==` vi `==` tu dung lai va mat
     * thoi gian theo so ky tu khop truoc - day la dang timing attack kinh dien.
     */
    fun verify(password: String, hash: String, salt: String): Boolean {
        val expected = runCatching { hash.fromBase64() }.getOrNull() ?: return false
        val saltBytes = runCatching { salt.fromBase64() }.getOrNull() ?: return false
        val actual = derive(password, saltBytes)
        return java.security.MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = javax.crypto.SecretKeyFactory.getInstance(ALGORITHM)
        val key = spec.generateSecret(javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS))
        return key.encoded
    }
}

private fun ByteArray.toBase64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

private fun String.fromBase64(): ByteArray = android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
