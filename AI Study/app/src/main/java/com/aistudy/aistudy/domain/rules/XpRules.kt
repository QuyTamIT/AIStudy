package com.aistudy.aistudy.domain.rules

import com.aistudy.aistudy.domain.model.enums.XpReason

/**
 * Quy tắc tính XP và level — FR9.
 *
 * Cố ý là `object` thuần Kotlin, **không** import androidx.room hay android.
 * Nhờ vậy toàn bộ đường cong level test được bằng JUnit thuần trong vài mili giây
 * (TC-GAM-02, TC-GAM-03) thay vì phải dựng emulator.
 */
object XpRules {

    const val XP_ASK_QUESTION = 5
    const val XP_REVIEW_ANSWER = 2
    const val XP_COMPLETE_QUIZ = 10
    const val XP_QUIZ_BONUS = 5

    /** Đọc câu trả lời từ ngần này trở lên mới tính là "đã ôn" — tránh cộng XP khi lướt qua. */
    const val REVIEW_SECONDS_THRESHOLD = 10

    const val MAX_LEVEL = 10

    /**
     * XP cần để đạt [level]. Chuỗi ngưỡng: 0, 100, 250, 450, 700, 1000, 1350, 1750, 2200, 2700.
     * Bậc nhảy tăng dần 100, 150, 200, ... nên càng lên cao càng phải cố gắng hơn.
     */
    fun totalXpToReach(level: Int): Int {
        if (level <= 1) return 0
        val n = (level - 1).coerceAtMost(MAX_LEVEL - 1)
        return 100 * n + 50 * n * (n - 1) / 2
    }

    /**
     * Level cao nhất mà [totalXp] đạt tới.
     *
     * XP âm là dữ liệu hỏng; trả về 1 thay vì ném lỗi để dashboard vẫn hiển thị được
     * (TC-GAM-03).
     */
    fun levelFor(totalXp: Int): Int {
        if (totalXp <= 0) return 1
        var level = 1
        while (level < MAX_LEVEL && totalXp >= totalXpToReach(level + 1)) {
            level++
        }
        return level
    }

    fun xpFor(reason: XpReason): Int = when (reason) {
        XpReason.ASK_QUESTION -> XP_ASK_QUESTION
        XpReason.REVIEW_ANSWER -> XP_REVIEW_ANSWER
        XpReason.COMPLETE_QUIZ -> XP_COMPLETE_QUIZ
        XpReason.QUIZ_BONUS -> XP_QUIZ_BONUS
        XpReason.DAILY_STREAK -> XP_ASK_QUESTION
    }

    /** XP còn thiếu để lên level kế tiếp; 0 khi đã max. */
    fun xpToNextLevel(totalXp: Int): Int {
        val next = levelFor(totalXp) + 1
        if (next > MAX_LEVEL) return 0
        return (totalXpToReach(next) - totalXp).coerceAtLeast(0)
    }

    /**
     * Chuỗi ngày liên tiếp sau khi hỏi câu mới vào [todayEpochDay].
     *
     * Cùng ngày thì giữ nguyên; hôm qua thì +1; còn lại thì tính lại từ 1.
     */
    fun nextStreak(currentStreak: Int, lastEpochDay: Long?, todayEpochDay: Long): Int = when {
        lastEpochDay == null -> 1
        todayEpochDay == lastEpochDay -> currentStreak.coerceAtLeast(1)
        todayEpochDay == lastEpochDay + 1 -> currentStreak + 1
        else -> 1
    }
}
