package com.aistudy.aistudy.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(user: User): Long

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    fun findByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun getById(userId: Long): User?

    @Query("UPDATE users SET password = :newPassword WHERE id = :userId AND password = :oldPassword")
    fun replacePasswordIfUnchanged(userId: Long, oldPassword: String, newPassword: String): Int
}
