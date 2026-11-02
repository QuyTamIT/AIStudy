package com.aistudy.aistudy.auth

import java.security.MessageDigest

/**
 * Educational password hashing utility using SHA-256.
 *
 * NOTE ON SECURITY LIMITATIONS:
 * Direct unsalted SHA-256 is fast and susceptible to rainbow table and brute-force attacks.
 * For production systems, use standard salted password-hashing algorithms such as Argon2id
 * or bcrypt with high work factors and per-user salts.
 */
object PasswordHasher {

    fun hash(plainPassword: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(plainPassword.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(plainPassword: String, stored: String?): Boolean {
        if (stored == null) {
            return false
        }
        // Backward compatibility for existing plaintext passwords created in prior phases
        if (plainPassword == stored) {
            return true
        }
        return hash(plainPassword) == stored
    }
}

