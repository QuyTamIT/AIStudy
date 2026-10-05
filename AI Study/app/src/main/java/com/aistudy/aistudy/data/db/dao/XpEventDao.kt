package com.aistudy.aistudy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aistudy.aistudy.domain.model.entity.XpEvent
import com.aistudy.aistudy.domain.model.enums.XpReason
import kotlinx.coroutines.flow.Flow

/** Truy cập bảng `xp_events` — FR9. */
@Dao
interface XpEventDao {

    @Query("SELECT * FROM xp_events WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeByUser(userId: Long): Flow<List<XpEvent>>

    @Insert
    suspend fun insert(event: XpEvent): Long

    @Query("SELECT COALESCE(SUM(xpAwarded), 0) FROM xp_events WHERE userId = :userId")
    suspend fun sumByUser(userId: Long): Int

    /** FR10 — thống kê XP theo tuần cho daily/weekly summary. */
    @Query("SELECT COALESCE(SUM(xpAwarded), 0) FROM xp_events WHERE userId = :userId AND createdAt >= :since")
    suspend fun sumSince(userId: Long, since: Long): Int

    /**
     * FR9 - idempotent theo ngay: mot [XpReason] chi duoc cong mot lan mot
     * ngay. Khoang `[since, until)` do repository truy ve tu
     * `LocalDate` cua thoi diem hien tai, nen khong hardcode gio trong SQL.
     * Khong co cach nay thi spam nut "Hoi AI" se cua doi XP.
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM xp_events
            WHERE userId = :userId
              AND reason = :reason
              AND createdAt >= :since
              AND createdAt < :until
        )
        """
    )
    suspend fun existsForReasonBetween(
        userId: Long,
        reason: XpReason,
        since: Long,
        until: Long
    ): Boolean

    /** N3 GDPR. */
    @Query("DELETE FROM xp_events WHERE userId = :userId")
    suspend fun deleteByUser(userId: Long)
}
