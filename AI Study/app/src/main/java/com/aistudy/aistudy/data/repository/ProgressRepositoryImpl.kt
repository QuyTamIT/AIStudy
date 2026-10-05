package com.aistudy.aistudy.data.repository

import com.aistudy.aistudy.data.db.dao.NotificationDao
import com.aistudy.aistudy.data.db.dao.QuestionAnswerDao
import com.aistudy.aistudy.data.db.dao.QuizItemDao
import com.aistudy.aistudy.data.db.dao.XpEventDao

/**
 * FR8 - so lieu cho dashboard Tien do.
 *
 * Gom san mot lan thoi vi man hinh chi can mot anh chup: du lieu tinh theo yeu
 * cau la mot lan quet nhieu cau COUNT/SUM, dong bo la hop ly hon, va tranh viet
 * lai moi Flow moi khi mot bang trong bao dong.
 */
class ProgressRepositoryImpl(
    private val questionAnswerDao: QuestionAnswerDao,
    private val quizItemDao: QuizItemDao,
    private val xpEventDao: XpEventDao,
    private val notificationDao: NotificationDao
) : ProgressRepository {

    override suspend fun summary(userId: Long): ProgressSummary {
        val bySubject = questionAnswerDao.countBySubject(userId)
            .associate { it.subjectCode to it.total }

        return ProgressSummary(
            totalQuestions = questionAnswerDao.countByUser(userId),
            questionsBySubject = bySubject,
            totalReviewSeconds = questionAnswerDao.totalReviewSeconds(userId),
            distinctSubjects = questionAnswerDao.distinctSubjectCount(userId),
            quizTotal = quizItemDao.countByUser(userId),
            quizAnswered = quizItemDao.answeredCount(userId),
            quizAccuracy = quizItemDao.accuracy(userId),
            xpTotal = xpEventDao.sumByUser(userId),
            xpThisWeek = xpEventDao.sumSince(userId, weekStartMillis()),
            unreadNotifications = notificationDao.unreadCountNow(userId)
        )
    }

    private fun weekStartMillis(): Long =
        nowMillis() - SEVEN_DAYS_MILLIS

    private companion object {
        const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
