package com.aistudy.aistudy.domain.rules

import com.aistudy.aistudy.domain.model.entity.User
import com.aistudy.aistudy.domain.model.enums.BadgeCode
import com.aistudy.aistudy.domain.model.enums.XpReason

/**
 * Điều kiện trao huy hiệu — FR9.
 *
 * Hàm [evaluate] trả về **danh sách huy hiệu mới đạt**, không phải toàn bộ.
 * GamificationRepository chịu trách nhiệm lọc lại theo `user_badges` trước khi
 * ghi, nên việc thêm huy hiệu mới không phá dữ liệu cũ.
 */
object BadgeCatalog {

    const val FIRST_QUESTION_THRESHOLD = 1
    const val STREAK_7_THRESHOLD = 7
    const val SUBJECT_EXPLORER_THRESHOLD = 5
    const val QUIZ_MASTER_ITEM_COUNT = 50
    const val QUIZ_MASTER_ACCURACY = 0.8

    fun displayName(code: BadgeCode): String = when (code) {
        BadgeCode.FIRST_QUESTION -> "Câu hỏi đầu tiên"
        BadgeCode.STREAK_7 -> "Học 7 ngày liên tiếp"
        BadgeCode.SUBJECT_EXPLORER -> "Thám hiểm 5 môn"
        BadgeCode.QUIZ_MASTER -> "Bậc thầy luyện tập"
        BadgeCode.EARLY_BIRD -> "Chim non sớm"
    }

    fun description(code: BadgeCode): String = when (code) {
        BadgeCode.FIRST_QUESTION -> "Đặt câu hỏi đầu tiên của bạn"
        BadgeCode.STREAK_7 -> "Hỏi câu trong 7 ngày liên tiếp"
        BadgeCode.SUBJECT_EXPLORER -> "Hỏi câu ở 5 môn khác nhau"
        BadgeCode.QUIZ_MASTER -> "Trả lời 50 câu luyện tập với tỉ lệ đúng từ 80%"
        BadgeCode.EARLY_BIRD -> "Hỏi câu trước 7 giờ sáng"
    }

    /**
     * @param stats là kết quả tổng hợp, xem ProgressSummary (sẽ có ở Phase 10).
     *              Hiện dùng các tham số rời để giữ hàm này thuần Kotlin, test được ngay.
     */
    fun evaluate(
        user: User,
        totalQuestions: Int,
        distinctSubjects: Int,
        quizAnswered: Int,
        quizAccuracy: Double,
        askedBeforeNoon: Boolean
    ): List<BadgeCode> = buildList {
        if (totalQuestions >= FIRST_QUESTION_THRESHOLD) add(BadgeCode.FIRST_QUESTION)
        if (user.currentStreakDays >= STREAK_7_THRESHOLD) add(BadgeCode.STREAK_7)
        if (distinctSubjects >= SUBJECT_EXPLORER_THRESHOLD) add(BadgeCode.SUBJECT_EXPLORER)
        if (quizAnswered >= QUIZ_MASTER_ITEM_COUNT && quizAccuracy >= QUIZ_MASTER_ACCURACY) {
            add(BadgeCode.QUIZ_MASTER)
        }
        if (askedBeforeNoon && totalQuestions > 0) add(BadgeCode.EARLY_BIRD)
    }
}
