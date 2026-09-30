package com.aistudy.aistudy.domain.rules

import com.aistudy.aistudy.domain.model.entity.User
import com.aistudy.aistudy.domain.model.enums.BadgeCode
import com.aistudy.aistudy.domain.model.enums.EducationLevel
import com.aistudy.aistudy.domain.model.enums.ExplanationStyle
import com.aistudy.aistudy.domain.model.enums.Subject
import com.aistudy.aistudy.domain.model.entity.Profile
import com.aistudy.aistudy.domain.model.entity.XpEvent

/**
 * Hồ sơ mặc định tạo lúc đăng ký — FR2.
 *
 * `onboardedAt = null` là tín hiệu cho AuthGate biết phải chuyển tới màn
 * Onboarding, nhờ đó không cần truy vấn thêm để hỏi "đã onboarding chưa".
 */
object ProfileDefaults {

    val DEFAULT_STYLE = ExplanationStyle.STEP_BY_STEP

    val DEFAULT_LEVEL = EducationLevel.HIGH

    val DEFAULT_SUBJECTS: Set<Subject> = setOf(Subject.MATHEMATICS, Subject.SCIENCE)

    fun forNewUser(userId: Long): Profile = Profile(
        userId = userId,
        educationLevel = DEFAULT_LEVEL,
        preferredStyle = DEFAULT_STYLE,
        subjectCodes = DEFAULT_SUBJECTS,
        onboardedAt = null
    )
}
