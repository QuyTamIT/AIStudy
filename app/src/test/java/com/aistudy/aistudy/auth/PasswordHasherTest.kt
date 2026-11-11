package com.aistudy.aistudy.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    @Test
    fun storedSha256DigestCannotBeUsedAsPassword() {
        val stored = PasswordHasher.hash("CorrectPassword123")

        assertFalse(PasswordHasher.matches(stored, stored))
        assertTrue(PasswordHasher.matches("CorrectPassword123", stored))
    }

    @Test
    fun legacySha256PasswordCanStillSignInButItsDigestCannot() {
        val legacyDigest = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"

        assertTrue(PasswordHasher.matches("password", legacyDigest))
        assertFalse(PasswordHasher.matches(legacyDigest, legacyDigest))
        assertFalse(PasswordHasher.matches("wrong-password", legacyDigest))
    }

    @Test
    fun legacyPlaintextCanStillSignIn() {
        assertTrue(PasswordHasher.matches("LegacyPass123", "LegacyPass123"))
        assertFalse(PasswordHasher.matches("OtherPassword", "LegacyPass123"))
    }

    @Test
    fun newPasswordHashesAreSaltedAndDoNotAcceptStoredValue() {
        val first = PasswordHasher.hash("CorrectPassword123")
        val second = PasswordHasher.hash("CorrectPassword123")

        assertTrue(first.startsWith("pbkdf2-sha256$"))
        assertFalse(first == second)
        assertTrue(PasswordHasher.matches("CorrectPassword123", first))
        assertFalse(PasswordHasher.matches(first, first))
        assertFalse(PasswordHasher.matches("IncorrectPassword", first))
        assertFalse(PasswordHasher.matches("CorrectPassword123", "pbkdf2-sha256" + '$' + "corrupt"))
    }

}
