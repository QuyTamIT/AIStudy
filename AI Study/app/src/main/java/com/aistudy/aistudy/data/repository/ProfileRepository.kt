package com.aistudy.aistudy.data.repository

import com.aistudy.aistudy.domain.model.entity.Profile
import com.aistudy.aistudy.domain.model.enums.EducationLevel
import com.aistudy.aistudy.domain.model.enums.ExplanationStyle
import com.aistudy.aistudy.domain.model.enums.Subject
import kotlinx.coroutines.flow.Flow

/**
 * FR2 - onboarding: cap hoc, mon quan tam, phong cach giai thich.
 *
 * `onboardedAt == null` la tin hieu duy nhat AuthGate dung de chuyen sang man
 * onboarding - khong can mot ham `hasOnboarded()` rieng.
 */
interface ProfileRepository {

    fun observeProfile(userId: Long): Flow<Profile?>

    suspend fun getProfile(userId: Long): Profile?

    /** Tao hoac tra ve profile mac dinh neu chua co. Idempotent. */
    suspend fun ensureDefaultProfile(userId: Long): Profile

    /** Gan `onboardedAt`, ket thuc onboarding. */
    suspend fun completeOnboarding(
        userId: Long,
        level: EducationLevel,
        subjects: Set<Subject>,
        style: ExplanationStyle
    ): Profile

    suspend fun updateLevel(userId: Long, level: EducationLevel)

    suspend fun updateSubjects(userId: Long, subjects: Set<Subject>)

    suspend fun updateStyle(userId: Long, style: ExplanationStyle)
}
