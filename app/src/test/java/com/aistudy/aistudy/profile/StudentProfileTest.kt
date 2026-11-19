package com.aistudy.aistudy.profile

import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.xp.UnlockCatalog
import com.aistudy.aistudy.xp.XpRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StudentProfileTest {

    @Test
    fun userOwnershipEnforcesStrictDataIsolation() {
        // Same user ID must succeed
        FirebaseStudentSync.validateUserOwnership(100L, 100L)

        // Cross-user ID access must throw SecurityException
        try {
            FirebaseStudentSync.validateUserOwnership(100L, 200L)
            fail("Expected SecurityException on cross-user profile access")
        } catch (_: SecurityException) {
            // Success
        }

        // Invalid session ID must throw SecurityException
        try {
            FirebaseStudentSync.validateUserOwnership(0L, 0L)
            fail("Expected SecurityException on unauthenticated profile access")
        } catch (_: SecurityException) {
            // Success
        }
    }

    @Test
    fun firestorePayloadContainsAllRequiredFields() {
        val profile = StudentProfileData(
            userId = 42L,
            fullName = "Lê Văn Hùng",
            email = "hung.le@gmail.com",
            levelNumber = 3,
            levelTitle = "Học giả",
            xp = 350,
            streak = 7,
            avatarId = UnlockCatalog.AVATAR_CAT,
            avatarEmoji = "🐱",
            gradeLevel = ProfileDefaults.LEVEL_HIGH,
            learningStyle = ProfileDefaults.STYLE_STEPWISE,
            favoriteSubjects = listOf("math", "code"),
            xpProgressPercent = 40,
            xpToNextLevel = 150,
            nextLevelThreshold = 500,
            totalQuestions = 15,
            totalStarred = 5,
            totalReviewSeconds = 1800,
            quizAccuracy = 90,
            subjectCounts = mapOf("math" to 10, "code" to 5),
            recentQuestions = emptyList()
        )

        assertFalse("Local profile should not be marked as cloud-synced", profile.isCloudSynced)
        val payload = FirebaseStudentSync.createFirestorePayload(profile)

        assertEquals("42", payload["studentId"])
        assertEquals("Lê Văn Hùng", payload["fullName"])
        assertEquals("hung.le@gmail.com", payload["email"])
        assertEquals(3, payload["level"])
        assertEquals(350, payload["xp"])
        assertEquals(7, payload["streak"])
        assertEquals("cat", payload["avatarId"])
        assertEquals(15, payload["totalQuestions"])
        assertEquals(90, payload["quizAccuracy"])
        assertTrue((payload["updatedAt"] as Long) > 0L)
    }

    @Test
    fun avatarUnlockProgressionFollowsStudentLevel() {
        // Level 1: Only Fox
        assertTrue(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_FOX, 1))
        assertFalse(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_OWL, 1))
        assertFalse(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_CAT, 1))
        assertFalse(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_ROCKET, 1))

        // Level 2: Fox and Owl
        assertTrue(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_FOX, 2))
        assertTrue(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_OWL, 2))
        assertFalse(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_CAT, 2))

        // Level 3: Fox, Owl, Cat
        assertTrue(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_CAT, 3))
        assertFalse(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_ROCKET, 3))

        // Level 4: All unlocked
        assertTrue(UnlockCatalog.isAvatarUnlocked(UnlockCatalog.AVATAR_ROCKET, 4))
    }

    @Test
    fun realDataCalculatesCorrectProgressAndLevel() {
        val level1Xp = 50
        assertEquals(1, XpRules.levelFromXp(level1Xp))
        assertEquals(100, XpRules.nextLevelThreshold(level1Xp))
        assertEquals(50, XpRules.progressPercentToNext(level1Xp))
        assertEquals(50, XpRules.xpToNextLevel(level1Xp))

        val level2Xp = 150
        assertEquals(2, XpRules.levelFromXp(level2Xp))
        assertEquals(250, XpRules.nextLevelThreshold(level2Xp))
        assertEquals(100, XpRules.xpToNextLevel(level2Xp))
    }

    @Test
    fun customAvatarIsAlwaysUnlockedAndPreserved() {
        val customAvatarUri = "custom:/data/user/0/com.aistudy.aistudy/files/avatars/avatar_42.jpg"
        assertTrue(UnlockCatalog.isCustomAvatar(customAvatarUri))
        assertTrue(UnlockCatalog.isCustomAvatar("/storage/emulated/0/DCIM/profile.png"))
        assertFalse(UnlockCatalog.isCustomAvatar(UnlockCatalog.AVATAR_FOX))
        assertFalse(UnlockCatalog.isCustomAvatar(null))
        assertFalse(UnlockCatalog.isCustomAvatar(""))

        // Custom avatar is always unlocked even at level 1
        assertTrue(UnlockCatalog.isAvatarUnlocked(customAvatarUri, 1))
        assertEquals(1, UnlockCatalog.requiredLevelForAvatar(customAvatarUri))
        assertEquals("👤", UnlockCatalog.emojiForAvatar(customAvatarUri))
        assertEquals(customAvatarUri, UnlockCatalog.normalizeAvatarId(customAvatarUri))
    }
}
