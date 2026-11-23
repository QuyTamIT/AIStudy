package com.aistudy.aistudy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLocaleTest {

    @Test
    fun defaultLanguageIsEnglish() {
        assertEquals("en", AIStudyApp.DEFAULT_LANGUAGE_TAG)
        assertFalse(AIStudyApp.isVietnameseLocaleTag("", ""))
        assertFalse(AIStudyApp.isVietnameseLocaleTag(null, null))
    }

    @Test
    fun detectsVietnameseFromApplicationOrConfigurationLocales() {
        assertTrue(AIStudyApp.isVietnameseLocaleTag("vi", "en-US"))
        assertTrue(AIStudyApp.isVietnameseLocaleTag("vi-VN", "en-US"))
        assertTrue(AIStudyApp.isVietnameseLocaleTag("", "vi-VN,en-US"))
        assertFalse(AIStudyApp.isVietnameseLocaleTag("en", "vi-VN"))
        assertFalse(AIStudyApp.isVietnameseLocaleTag("", "en-US,vi-VN"))
        assertFalse(AIStudyApp.isVietnameseLocaleTag("vie", ""))
    }
}
