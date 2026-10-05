package com.aistudy.aistudy.data.repository

import com.aistudy.aistudy.data.db.dao.QuizItemDao
import com.aistudy.aistudy.data.db.dao.QuestionAnswerDao
import com.aistudy.aistudy.data.db.dao.UserBadgeDao
import com.aistudy.aistudy.data.db.dao.UserDao
import com.aistudy.aistudy.data.db.dao.XpEventDao
import com.aistudy.aistudy.domain.model.entity.User
import com.aistudy.aistudy.domain.model.entity.UserBadge
import com.aistudy.aistudy.domain.model.entity.XpEvent
import com.aistudy.aistudy.domain.model.enums.BadgeCode
import com.aistudy.aistudy.domain.model.enums.XpReason
import com.aistudy.aistudy.domain.rules.BadgeCatalog
import com.aistudy.aistudy.domain.rules.XpRules
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * FR9 - XP, level, chuoi ngay, huyen.
 *
 * Khong gia tri nao duoc hardcode: XP lay tu [XpRules.xpFor], cong thuc level
 * lay tu [XpRules.levelFor], dieu kien huyen lay tu [BadgeCatalog.evaluate].
 * Repository chi lo ghi vao DB.
 */
class GamificationRepositoryImpl(
    private val userDao: UserDao,
    private val xpEventDao: XpEventDao,
    private val userBadgeDao: UserBadgeDao,
    private val questionAnswerDao: QuestionAnswerDao,
    private val quizItemDao: QuizItemDao
) : GamificationRepository {

    override fun observeUser(userId: Long): Flow<User?> = userDao.observeById(userId)

    override fun observeBadges(userId: Long): Flow<List<UserBadge>> = userBadgeDao.observeByUser(userId)

    override fun observeXpEvents(userId: Long): Flow<List<XpEvent>> = xpEventDao.observeByUser(userId)

    override suspend fun recordActivity(
        userId: Long,
        reason: XpReason,
        nowEpochDay: Long
    ): GamificationResult {
        val user = userDao.findById(userId)
            ?: throw IllegalStateException("Khong tim thay user $userId")

        val today = LocalDate.ofEpochDay(nowEpochDay)
        val dayStart = today.atStartOfDay(clock).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(clock).toInstant().toEpochMilli()

        // Idempotent trong ngay: mot ly do chi cong mot lan mot ngay. Bo qua
        // buoc nay thi bam nut "Hoi AI" lien tuc se cua doi XP.
        val alreadyAwarded = xpEventDao.existsForReasonBetween(userId, reason, dayStart, dayEnd)
        if (alreadyAwarded) {
            return GamificationResult(
                xpGained = 0,
                totalXp = user.totalXp,
                newLevel = user.level,
                leveledUp = false,
                newBadges = awardBadges(user, nowEpochDay)
            )
        }

        val gained = XpRules.xpFor(reason)
        val totalXp = user.totalXp + gained
        val newLevel = XpRules.levelFor(totalXp)

        xpEventDao.insert(
            XpEvent(
                userId = userId,
                reason = reason,
                xpAwarded = gained,
                createdAt = dayStart + 1
            )
        )
        userDao.addXp(userId, gained, newLevel)

        val streak = XpRules.nextStreak(user.currentStreakDays, user.lastQuestionDate, nowEpochDay)
        userDao.touchStreak(userId, streak, nowEpochDay)

        val updated = userDao.findById(userId) ?: user
        return GamificationResult(
            xpGained = gained,
            totalXp = totalXp,
            newLevel = newLevel,
            leveledUp = newLevel > user.level,
            newBadges = awardBadges(updated, nowEpochDay)
        )
    }

    override suspend fun xpSince(userId: Long, sinceMillis: Long): Int =
        xpEventDao.sumSince(userId, sinceMillis)

    /**
     * TC-GAM-09: cot tien gian trong `users` phai bang tong cong trong
     * `xp_events`. Dung khi database bi sua tay hoac khi can sua lai du lieu
     * sau mot lan migration.
     */
    override suspend fun recalculate(userId: Long) {
        val totalXp = xpEventDao.sumByUser(userId)
        val level = XpRules.levelFor(totalXp)
        userDao.addXp(userId, totalXp - userDao.findById(userId)?.totalXp.orZero(), level)
    }

    /**
     * [BadgeCatalog.evaluate] tra ve danh sach huyen DU DAU dat du dieu kien, chu
     * phai danh sach dang co. Voi vay phai loc lai bang `user_badges` truoc khi
     * ghi - neu khong, moi lan hoat dong la ghi lai toan bo hang duyet.
     */
    private suspend fun awardBadges(user: User, nowEpochDay: Long): List<BadgeCode> {
        val earned = BadgeCatalog.evaluate(
            user = user,
            totalQuestions = questionAnswerDao.countByUser(user.id),
            distinctSubjects = questionAnswerDao.distinctSubjectCount(user.id),
            quizAnswered = quizItemDao.answeredCount(user.id),
            quizAccuracy = quizItemDao.accuracy(user.id),
            askedBeforeNoon = LocalTime.now(clock).hour < EARLY_BIRD_HOUR
        )

        val fresh = earned.filterNot { userBadgeDao.exists(user.id, it) }
        fresh.forEach { code ->
            userBadgeDao.insert(
                UserBadge(
                    userId = user.id,
                    badgeCode = code,
                    earnedAt = LocalDate.ofEpochDay(nowEpochDay)
                        .atTime(LocalTime.NOON)
                        .atZone(clock)
                        .toInstant()
                        .toEpochMilli()
                )
            )
        }
        return fresh
    }

    private companion object {
        /** Dot moc "chim non som" trong `BadgeCatalog`. */
        const val EARLY_BIRD_HOUR = 7

        val clock: ZoneId = ZoneId.systemDefault()

        fun Int?.orZero(): Int = this ?: 0
    }
}

/** Epoch day -> millis, dung chung cho moi noi can moc ngay theo gio dia phuong. */
internal fun LocalDate.startOfDayMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()

internal fun nowMillis(): Long = Instant.now().toEpochMilli()
