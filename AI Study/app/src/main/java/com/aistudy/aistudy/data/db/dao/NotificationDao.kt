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
