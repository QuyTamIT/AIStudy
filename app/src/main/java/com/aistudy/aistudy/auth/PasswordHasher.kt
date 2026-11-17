package com.aistudy.aistudy.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val PREFIX = "pbkdf2-sha256"
    private const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val HASH_BYTES = 32
    private val LEGACY_SHA256 = Regex("[0-9a-f]{64}")
    private val random = SecureRandom()

    fun hash(plainPassword: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val digest = derive(plainPassword, salt, ITERATIONS)
        return listOf(PREFIX, ITERATIONS.toString(), encode(salt), encode(digest)).joinToString("$")
    }

    fun matches(plainPassword: String, stored: String?): Boolean {
        if (stored == null) return false
        if (stored.startsWith("$PREFIX$")) {
            val parts = stored.split('$')
            if (parts.size != 4 || parts[1].toIntOrNull() != ITERATIONS) return false
            return try {
                val salt = Base64.getDecoder().decode(parts[2])
                val digest = Base64.getDecoder().decode(parts[3])
                salt.size == SALT_BYTES && digest.size == HASH_BYTES &&
                    MessageDigest.isEqual(derive(plainPassword, salt, ITERATIONS), digest)
            } catch (_: IllegalArgumentException) {
                false
            }
        }
        if (LEGACY_SHA256.matches(stored)) {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(plainPassword.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return MessageDigest.isEqual(digest.toByteArray(Charsets.US_ASCII), stored.toByteArray(Charsets.US_ASCII))
        }
        return MessageDigest.isEqual(
            plainPassword.toByteArray(Charsets.UTF_8), stored.toByteArray(Charsets.UTF_8)
        )
    }

    fun needsUpgrade(stored: String): Boolean = !stored.startsWith("$PREFIX$")

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().withoutPadding().encodeToString(bytes)
}
