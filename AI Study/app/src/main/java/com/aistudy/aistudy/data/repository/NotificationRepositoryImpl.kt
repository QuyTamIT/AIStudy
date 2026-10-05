package com.aistudy.aistudy.data.repository

import com.aistudy.aistudy.data.db.dao.NotificationDao
import com.aistudy.aistudy.data.db.dao.XpEventDao
import com.aistudy.aistudy.domain.model.entity.Notification
import com.aistudy.aistudy.domain.model.enums.NotificationType
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * FR10 - thong bao.
 *
 * App local-first, khong co backend nen khong the co FCM. Thong bao sinh o
 * trong app (sau khi hoi cau, sau khi mo huyen) va doc qua [observeAll] cua
 * chinh user dang dang nhap. Muc tieu FR10 la "he thong thong bao cho nguoi
 * dung", khong phai "day thong bao khi app dang dong".
 */
class NotificationRepositoryImpl(
    private val notificationDao: NotificationDao,
    private val xpEventDao: XpEventDao
) : NotificationRepository {

    override fun observeAll(userId: Long): Flow<List<Notification>> =
        notificationDao.observeByUser(userId)

    override fun observeUnreadCount(userId: Long): Flow<Int> =
        notificationDao.observeUnreadCount(userId)

    override suspend fun notify(
        userId: Long,
        type: NotificationType,
        title: String,
        body: String,
        deepLink: String?
    ): Long = notificationDao.insert(
        Notification(
            userId = userId,
            type = type,
            title = title,
            body = body,
            deepLink = deepLink,
            createdAt = nowMillis()
        )
    )

    override suspend fun markRead(id: Long) = notificationDao.markRead(id)

    override suspend fun markAllRead(userId: Long) = notificationDao.markAllRead(userId)

    /**
     * FR10 - tom tat cuoi ngay, goi tu `MainShell` khi app mo.
     *
     * Idempotent theo ngay: chi tao mot ban tom tat cho mot ngay, va chi khi
     * hom do that su co hoat dong. Khong co dieu kien "da co" thi moi lan mo
     * app la lai mot thong bao trung lap.
     */
    override suspend fun postDailySummaryIfDue(userId: Long, nowMillis: Long) {
        val zone = ZoneId.systemDefault()
        val dayStart = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

        if (notificationDao.hasDailySummaryBetween(userId, dayStart, nowMillis + 1)) return

        val xp = xpEventDao.sumSince(userId, dayStart)
        if (xp <= 0) return

        val unread = notificationDao.unreadCountNow(userId)
        val body = buildString {
            append("Hom nay ban hoc duoc $xp XP.")
            if (unread > 0) append(" Con $unread thong bao chua doc.")
        }

        notificationDao.insert(
            Notification(
                userId = userId,
                type = NotificationType.DAILY_SUMMARY,
                title = "Tóm tắt hoạt động hôm nay",
                body = body,
                deepLink = DEEP_LINK_PROGRESS,
                createdAt = nowMillis
            )
        )
    }

    private companion object {
        const val DEEP_LINK_PROGRESS = "progress"
    }
}
