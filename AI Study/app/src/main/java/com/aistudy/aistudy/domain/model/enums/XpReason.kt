package com.aistudy.aistudy.domain.model.enums

/** Lý do phát XP — FR9. Giá trị XP lấy từ XpRules.xpFor(), không hardcode ở nơi gọi. */
enum class XpReason {
    ASK_QUESTION,
    REVIEW_ANSWER,
    COMPLETE_QUIZ,
    QUIZ_BONUS,
    DAILY_STREAK
}
