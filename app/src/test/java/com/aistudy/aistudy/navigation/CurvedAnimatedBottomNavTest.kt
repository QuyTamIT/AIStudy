package com.aistudy.aistudy.navigation

import com.aistudy.aistudy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurvedAnimatedBottomNavTest {

    @Test
    fun testTabDefinitionsCountAndIds() {
        val tabs = CurvedAnimatedBottomNav.TABS
        assertEquals("Must contain exactly 5 navigation tabs", 5, tabs.size)

        assertEquals(R.id.nav_home, tabs[0].id)
        assertEquals("Home", tabs[0].label)
        assertTrue(tabs[0].iconRes != 0)

        assertEquals(R.id.nav_ask, tabs[1].id)
        assertEquals("Ask", tabs[1].label)
        assertTrue(tabs[1].iconRes != 0)

        assertEquals(R.id.nav_playground, tabs[2].id)
        assertEquals("Playground", tabs[2].label)
        assertTrue(tabs[2].iconRes != 0)

        assertEquals(R.id.nav_history, tabs[3].id)
        assertEquals("History", tabs[3].label)
        assertTrue(tabs[3].iconRes != 0)

        assertEquals(R.id.nav_progress, tabs[4].id)
        assertEquals("Progress", tabs[4].label)
        assertTrue(tabs[4].iconRes != 0)
    }

    @Test
    fun testTabIdsAreDistinct() {
        val ids = CurvedAnimatedBottomNav.TABS.map { it.id }
        assertEquals(5, ids.distinct().size)
    }
}

