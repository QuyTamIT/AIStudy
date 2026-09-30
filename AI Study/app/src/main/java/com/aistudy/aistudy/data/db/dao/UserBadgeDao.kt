package com.aistudy.aistudy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aistudy.aistudy.domain.model.entity.UserBadge
import com.aistudy.aistudy.domain.model.enums.BadgeCode
import kotlinx.coroutines.flow.Flow

/** Truy cập bảng `user_badges` — FR9. */
@Dao
interface UserBadgeDao {

    @Query("SELECT * FROM user_badges WHERE userId = :userId ORDER BY earnedAt DESC")
    fun observeByUser(userId: Long): Flow<List<UserBadge>>

    @Query("SELECT * FROM user_badges WHERE userId = :userId AND badgeCode = :code LIMIT 1")
    suspend fun find(userId: Long, code: BadgeCode): UserBadge?

    @Query("SELECT EXISTS(SELECT 1 FROM user_badges WHERE userId = :userId AND badgeCode = :code)")
    suspend fun exists(userId: Long, code: BadgeCode): Boolean

    @Query("SELECT COUNT(*) FROM user_badges WHERE userId = :userId")
    suspend fun countByUser(userId: Long): Int

    @Insert
    suspend fun insert(badge: UserBadge)

    /** N3 GDPR. */
    @Query("DELETE FROM user_badges WHERE userId = :userId")
    suspend fun deleteByUser(userId: Long)
}
