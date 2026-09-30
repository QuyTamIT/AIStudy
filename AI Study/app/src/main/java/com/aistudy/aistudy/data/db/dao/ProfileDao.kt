package com.aistudy.aistudy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.aistudy.aistudy.domain.model.entity.Profile
import kotlinx.coroutines.flow.Flow

/** Truy cập bảng `profiles` — FR2 onboarding. */
@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles WHERE userId = :userId LIMIT 1")
    fun observeByUser(userId: Long): Flow<Profile?>

    @Query("SELECT * FROM profiles WHERE userId = :userId LIMIT 1")
    suspend fun findByUser(userId: Long): Profile?

    @Insert
    suspend fun insert(profile: Profile): Long

    @Update
    suspend fun update(profile: Profile)

    @Query("UPDATE profiles SET preferredStyle = :style WHERE userId = :userId")
    suspend fun updateStyle(userId: Long, style: com.aistudy.aistudy.domain.model.enums.ExplanationStyle)

    @Query("UPDATE profiles SET onboardedAt = :now WHERE userId = :userId")
    suspend fun markOnboarded(userId: Long, now: Long)
}
