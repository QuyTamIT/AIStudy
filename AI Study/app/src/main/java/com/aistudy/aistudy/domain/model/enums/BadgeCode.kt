package com.aistudy.aistudy.domain.model.enums

/**
 * Mã huy hiệu — FR9.
 * Enum này là *khoá* trong bảng user_badges, nên tên hằng KHÔNG được đổi sau khi
 * đã phát hành. Điều kiện đạt huy hiệu nằm ở BadgeCatalog.
 */
enum class BadgeCode {
    FIRST_QUESTION,
    STREAK_7,
    SUBJECT_EXPLORER,
    QUIZ_MASTER,
    EARLY_BIRD
}
