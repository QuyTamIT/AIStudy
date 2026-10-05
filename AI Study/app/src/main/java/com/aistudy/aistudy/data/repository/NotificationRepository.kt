package com.aistudy.aistudy.data.repository

import com.aistudy.aistudy.domain.model.entity.Notification
import com.aistudy.aistudy.domain.model.enums.NotificationType
import kotlinx.coroutines.flow.Flow

/**
 * FR10 - thong bao.
 *
 * App local-first, khong co backend nen khong co FCM. Thong bao sinh o trong
 * app (sau khi hoi cau, sau khi cham diem) va doc qua [observeAll] cua chinh
 * user dang dang nhap. Muc tieu FR10 la "he thong thong bao cho nguoi dung",
 * khong phai "day thong bao khi app dang dong".
 */
interface NotificationRepository {

    fun observeAll(userId: Long): Flow<List<Notification>>

    fun observeUnreadCount(userId: Long): Flow<Int>

    /** [deepLink] luu route nhu `"question/42"` de bam mo dung man hinh. */
    suspend fun notify(
        userId: Long,
        type: NotificationType,
        title: String,
        body: String,
        deepLink: String? = null
    ): Long

    suspend fun markRead(id: Long)

    suspend fun markAllRead(userId: Long)

    /** FR10 - tom tat cuoi ngay, goi tu [ReminderScheduler] khi app mo. */
    suspend fun postDailySummaryIfDue(userId: Long, nowMillis: Long)
}
