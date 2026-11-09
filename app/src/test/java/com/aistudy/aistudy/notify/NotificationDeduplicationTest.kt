package com.aistudy.aistudy.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationDeduplicationTest {

    @Test
    fun testUserNamespacedKeysAreDistinct() {
        val userA = 101L
        val userB = 202L
        val prefix = "last_day_"
        val type = "bookmark"

        val keyA = "${prefix}${userA}_$type"
        val keyB = "${prefix}${userB}_$type"

        assertNotEquals(keyA, keyB)
        assertEquals("last_day_101_bookmark", keyA)
        assertEquals("last_day_202_bookmark", keyB)
    }

    @Test
    fun testNotificationTypesPerUser() {
        val user = 55L
        val prefix = "last_day_"
        val types = listOf("bookmark", "mistakes", "motivational", "summary_daily", "summary_weekly")

        val keys = types.map { "${prefix}${user}_$it" }
        assertEquals(5, keys.toSet().size)
    }
}

