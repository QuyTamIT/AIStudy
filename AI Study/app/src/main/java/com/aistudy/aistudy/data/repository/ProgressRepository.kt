package com.aistudy.aistudy.data.repository

import com.aistudy.aistudy.domain.model.enums.Subject

/**
 * FR8 - so lieu cho dashboard Tien do.
 *
 * Gom san mot lan thoi vi man hinh chi can mot snapshot, khong can Flow: du lieu
 * tinh theo yeu cau la mot lan quet bang nhieu cau `COUNT`/`SUM`, dong bo la
 * hop ly hon, va tranh viet lai Flow moi khi mot bang trong bao dong.
 */
interface ProgressRepository {

    suspend fun summary(userId: Long): ProgressSummary
}

data class ProgressSummary(
    val totalQuestions: Int = 0,
    val questionsBySubject: Map<Subject, Int> = emptyMap(),
    val totalReviewSeconds: Long = 0L,
    val distinctSubjects: Int = 0,
    val quizTotal: Int = 0,
    val quizAnswered: Int = 0,
    val quizAccuracy: Double = 0.0,
    val xpTotal: Int = 0,
    val xpThisWeek: Int = 0,
    val unreadNotifications: Int = 0
) {
    /** Quiz chua lam - dung cho copy "con 3 cau de luyen tap". */
    val quizRemaining: Int get() = (quizTotal - quizAnswered).coerceAtLeast(0)

    val reviewMinutes: Long get() = totalReviewSeconds / 60
}
