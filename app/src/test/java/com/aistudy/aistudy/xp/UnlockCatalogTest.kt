package com.aistudy.aistudy.xp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockCatalogTest {

    @Test
    fun testAvatarUnlocks() {
        // Fox Lv 1, Owl Lv 2, Cat Lv 3, Rocket Lv 4
        assertEquals(1, UnlockCatalog.requiredLevelForAvatar(UnlockCatalog.AVATAR_FOX))
        assertEquals(2, UnlockCatalog.requiredLevelForAvatar(UnlockCatalog.AVATAR_OWL))
        assertEquals(3, UnlockCatalog.requiredLevelForAvatar(UnlockCatalog.AVATAR_CAT))
        assertEquals(4, UnlockCatalog.requiredLevelForAvatar(UnlockCatalog.AVATAR_ROCKET))
        assertEquals(-1, UnlockCatalog.requiredLevelForAvatar("unknown"))

        assertTrue(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_FOX, 1))
        assertFalse(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_OWL, 1))
        assertTrue(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_OWL, 2))
        assertFalse(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_ROCKET, 3))
        assertTrue(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_ROCKET, 4))
    }

    @Test
    fun testThemeUnlocks() {
        // Default Lv 1, Night Lv 2, Pastel Lv 3
        assertEquals(1, UnlockCatalog.requiredLevelForTheme(UnlockCatalog.THEME_DEFAULT))
        assertEquals(2, UnlockCatalog.requiredLevelForTheme(UnlockCatalog.THEME_NIGHT))
        assertEquals(3, UnlockCatalog.requiredLevelForTheme(UnlockCatalog.THEME_PASTEL))
        assertEquals(-1, UnlockCatalog.requiredLevelForTheme("unknown"))

        assertTrue(UnlockCatalog.isThemeUnlocked(UnlockCatalog.THEME_DEFAULT, 1))
        assertFalse(UnlockCatalog.isThemeUnlocked(UnlockCatalog.THEME_NIGHT, 1))
        assertTrue(UnlockCatalog.isThemeUnlocked(UnlockCatalog.THEME_NIGHT, 2))
        assertFalse(UnlockCatalog.isThemeUnlocked(UnlockCatalog.THEME_PASTEL, 2))
        assertTrue(UnlockCatalog.isThemeUnlocked(UnlockCatalog.THEME_PASTEL, 3))
    }

    @Test
    fun testNormalize() {
        assertEquals(UnlockCatalog.AVATAR_FOX, UnlockCatalog.normalizeAvatarId(null))
        assertEquals(UnlockCatalog.AVATAR_FOX, UnlockCatalog.normalizeAvatarId(""))
        assertEquals(UnlockCatalog.AVATAR_FOX, UnlockCatalog.normalizeAvatarId("invalid"))
        assertEquals(UnlockCatalog.AVATAR_CAT, UnlockCatalog.normalizeAvatarId(UnlockCatalog.AVATAR_CAT))

        assertEquals(UnlockCatalog.THEME_DEFAULT, UnlockCatalog.normalizeThemeId(null))
        assertEquals(UnlockCatalog.THEME_DEFAULT, UnlockCatalog.normalizeThemeId(""))
        assertEquals(UnlockCatalog.THEME_DEFAULT, UnlockCatalog.normalizeThemeId("invalid"))
        assertEquals(UnlockCatalog.THEME_PASTEL, UnlockCatalog.normalizeThemeId(UnlockCatalog.THEME_PASTEL))
    }

    @Test
    fun testEmojiAndLabels() {
        assertEquals("🦊", UnlockCatalog.emojiForAvatar(UnlockCatalog.AVATAR_FOX))
        assertEquals("🦉", UnlockCatalog.emojiForAvatar(UnlockCatalog.AVATAR_OWL))
        assertEquals("🐱", UnlockCatalog.emojiForAvatar(UnlockCatalog.AVATAR_CAT))
        assertEquals("🚀", UnlockCatalog.emojiForAvatar(UnlockCatalog.AVATAR_ROCKET))

        assertEquals("Default", UnlockCatalog.labelForTheme(UnlockCatalog.THEME_DEFAULT))
        assertEquals("Night", UnlockCatalog.labelForTheme(UnlockCatalog.THEME_NIGHT))
        assertEquals("Pastel", UnlockCatalog.labelForTheme(UnlockCatalog.THEME_PASTEL))
    }
}

