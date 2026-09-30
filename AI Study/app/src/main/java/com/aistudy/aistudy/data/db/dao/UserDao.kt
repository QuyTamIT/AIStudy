package com.aistudy.aistudy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.aistudy.aistudy.domain.model.entity.User
import kotlinx.coroutines.flow.Flow

/** Truy cập bảng `users` — FR1, FR9. */
@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeById(id: Long): Flow<User?>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun findById(id: Long): User?

    @Insert
    suspend fun insert(user: User): Long

    @Update
    suspend fun update(user: User)

    /**
     * Cộng XP và đồng bộ level trong cùng một câu lệnh.
     * Gọi từ GamificationRepository sau khi đã chèn XpEvent.
     */
    @Query("UPDATE users SET totalXp = totalXp + :delta, level = :newLevel WHERE id = :userId")
    suspend fun addXp(userId: Long, delta: Int, newLevel: Int)

    @Query("UPDATE users SET currentStreakDays = :days, lastQuestionDate = :today WHERE id = :userId")
    suspend fun touchStreak(userId: Long, days: Int, today: Long)

    @Query("UPDATE users SET lastLoginAt = :now WHERE id = :userId")
    suspend fun touchLogin(userId: Long, now: Long)

    @Query("UPDATE users SET twoFactorSecret = :secret WHERE id = :userId")
    suspend fun setTwoFactorSecret(userId: Long, secret: String?)

    @Query("UPDATE users SET displayName = :displayName WHERE id = :userId")
    suspend fun setDisplayName(userId: Long, displayName: String)
}
