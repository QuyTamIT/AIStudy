package com.aistudy.aistudy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aistudy.aistudy.domain.model.entity.Notification
import kotlinx.coroutines.flow.Flow

/** Truy cập bảng `notifications` — FR10. */
@Dao
interface NotificationDao {

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeByUser(userId: Long): Flow<List<Notification>>

    @Query("SELECT * FROM notifications WHERE userId = :userId AND isRead = 0 ORDER BY createdAt DESC")
    fun observeUnread(userId: Long): Flow<List<Notification>>

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    fun observeUnreadCount(userId: Long): Flow<Int>

    /**
     * Ban suspend cua [observeUnreadCount] cho [ProgressRepository] - mot
     * [ProgressSummary] la mot anh chup mot lan, khong phai dong bo theo luong.
     */
    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    suspend fun unreadCountNow(userId: Long): Int

    /**
     * FR10 - bao cao tom tat trong ngay co da sinh chua, dung lam dieu kien
     * idempotent cho `postDailySummaryIfDue`. Khoang thoi gian do nguoi goi truy
     * ve, khong hardcode trong SQL de giu cho test.
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM notifications
            WHERE userId = :userId
              AND type = 'DAILY_SUMMARY'
              AND createdAt >= :sinceMillis
              AND createdAt < :untilMillis
        )
        """
    )
    suspend fun hasDailySummaryBetween(userId: Long, sinceMillis: Long, untilMillis: Long): Boolean

    @Query("SELECT * FROM notifications WHERE userId = :userId AND isRead = 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestUnread(userId: Long): Notification?

    @Insert
    suspend fun insert(notification: Notification): Long

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllRead(userId: Long)

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId")
    suspend fun countByUser(userId: Long): Int

    /** N3 GDPR. */
    @Query("DELETE FROM notifications WHERE userId = :userId")
    suspend fun deleteByUser(userId: Long)
}
