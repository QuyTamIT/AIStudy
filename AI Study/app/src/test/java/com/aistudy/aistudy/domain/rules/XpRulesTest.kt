package com.aistudy.aistudy.domain.rules

import com.aistudy.aistudy.domain.model.enums.XpReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm thử đường cong XP/level — FR9.
 * Ánh xạ test plan: TC-GAM-02, TC-GAM-03.
 *
 * Chạy bằng JUnit thuần, không cần emulator — đây là bằng chứng cho lập luận
 * "tách tầng domain khỏi Android" trong design document.
 */
class XpRulesTest {

    @Test
    fun `totalXpToReach trả ngưỡng đúng ở từng level`() {
        assertEquals(0, XpRules.totalXpToReach(1))
        assertEquals(100, XpRules.totalXpToReach(2))
        assertEquals(250, XpRules.totalXpToReach(3))
        assertEquals(450, XpRules.totalXpToReach(4))
        assertEquals(700, XpRules.totalXpToReach(5))
        assertEquals(1000, XpRules.totalXpToReach(6))
        assertEquals(1350, XpRules.totalXpToReach(7))
        assertEquals(1750, XpRules.totalXpToReach(8))
        assertEquals(2200, XpRules.totalXpToReach(9))
        assertEquals(2700, XpRules.totalXpToReach(10))
    }

    /** Biên: ngay dưới ngưỡng vẫn ở level cũ, đúng ngưỡng thì lên. */
    @Test
    fun `levelFor xử lý đúng các mốc biên`() {
        assertEquals(1, XpRules.levelFor(0))
        assertEquals(1, XpRules.levelFor(99))
        assertEquals(2, XpRules.levelFor(100))
        assertEquals(2, XpRules.levelFor(249))
        assertEquals(3, XpRules.levelFor(250))
        assertEquals(9, XpRules.levelFor(2699))
        assertEquals(10, XpRules.levelFor(2700))
    }

    /** XP âm là dữ liệu hỏng — phải trả về hợp lệ chứ không ném lỗi. */
    @Test
    fun `levelFor trả 1 khi totalXp am`() {
        assertEquals(1, XpRules.levelFor(-1))
        assertEquals(1, XpRules.levelFor(-9999))
    }

    @Test
    fun `levelFor khong vuot qua MAX_LEVEL`() {
        assertEquals(XpRules.MAX_LEVEL, XpRules.levelFor(100_000))
        assertEquals(XpRules.MAX_LEVEL, XpRules.levelFor(Int.MAX_VALUE))
    }

    @Test
    fun `totalXpToReach tra 0 cho level duoi 1`() {
        assertEquals(0, XpRules.totalXpToReach(0))
        assertEquals(0, XpRules.totalXpToReach(-5))
    }

    @Test
    fun `xpFor tra dung gia tri theo tung ly do`() {
        assertEquals(5, XpRules.xpFor(XpReason.ASK_QUESTION))
        assertEquals(2, XpRules.xpFor(XpReason.REVIEW_ANSWER))
        assertEquals(10, XpRules.xpFor(XpReason.COMPLETE_QUIZ))
        assertEquals(5, XpRules.xpFor(XpReason.QUIZ_BONUS))
        assertEquals(5, XpRules.xpFor(XpReason.DAILY_STREAK))
    }

    /**
     * xpToNextLevel nghĩa là XP còn thiếu để lên level KẾ TIẾP so với level hiện tại.
     * Ở 100 XP người dùng đã lên level 2, nên còn 150 XP nữa mới lên level 3.
     */
    @Test
    fun `xpToNextLevel tinh dung khoang cach con thieu`() {
        assertEquals(100, XpRules.xpToNextLevel(0))
        assertEquals(50, XpRules.xpToNextLevel(50))
        assertEquals(1, XpRules.xpToNextLevel(99))
        assertEquals(150, XpRules.xpToNextLevel(100))
        assertEquals(50, XpRules.xpToNextLevel(200))
    }

    @Test
    fun `xpToNextLevel bang 0 khi da len level toi da`() {
        assertEquals(0, XpRules.xpToNextLevel(5000))
    }

    @Test
    fun `nextStreak bat dau tu 1 khi chua co lan nao hoi cau`() {
        assertEquals(1, XpRules.nextStreak(currentStreak = 0, lastEpochDay = null, todayEpochDay = 20_000))
    }

    @Test
    fun `nextStreak giu nguyen khi hoi nhieu cau trong cung ngay`() {
        assertEquals(3, XpRules.nextStreak(currentStreak = 3, lastEpochDay = 20_000, todayEpochDay = 20_000))
    }

    @Test
    fun `nextStreak cong them 1 khi hoi tiep ngay hom sau`() {
        assertEquals(4, XpRules.nextStreak(currentStreak = 3, lastEpochDay = 20_000, todayEpochDay = 20_001))
    }

    /** Mất một ngày thì chuỗi đứt, phải tính lại từ đầu. */
    @Test
    fun `nextStreak reset ve 1 khi bo qua mot ngay`() {
        assertEquals(1, XpRules.nextStreak(currentStreak = 6, lastEpochDay = 20_000, todayEpochDay = 20_002))
    }

    @Test
    fun `levelFor va xpToNextLevel luon nhat quan`() {
        listOf(0, 50, 99, 100, 249, 250, 700, 1000, 2699, 2700).forEach { xp ->
            val level = XpRules.levelFor(xp)
            assertTrue(
                "xp=$xp phai lon bang nguong cua level $level",
                xp >= XpRules.totalXpToReach(level)
            )
            val next = level + 1
            if (next <= XpRules.MAX_LEVEL) {
                assertTrue(
                    "xp=$xp phai nho nguong cua level $next",
                    xp < XpRules.totalXpToReach(next)
                )
            }
        }
    }
}
