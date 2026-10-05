package com.aistudy.aistudy.data.repository

import com.aistudy.aistudy.data.db.dao.ProfileDao
import com.aistudy.aistudy.domain.model.entity.Profile
import com.aistudy.aistudy.domain.model.enums.EducationLevel
import com.aistudy.aistudy.domain.model.enums.ExplanationStyle
import com.aistudy.aistudy.domain.model.enums.Subject
import com.aistudy.aistudy.domain.rules.ProfileDefaults
import kotlinx.coroutines.flow.Flow

/**
 * FR2 - onboarding.
 *
 * [ensureDefaultProfile] phai idempotent: `AuthRepositoryImpl.register` va
 * `MainShell` deu co the goi, va `profiles.userId` la UNIQUE index nen goi
 * hai lan se khac nhau `IllegalStateException` tu Room.
 */
class ProfileRepositoryImpl(
    private val profileDao: ProfileDao
) : ProfileRepository {

    override fun observeProfile(userId: Long): Flow<Profile?> = profileDao.observeByUser(userId)

    override suspend fun getProfile(userId: Long): Profile? = profileDao.findByUser(userId)

    override suspend fun ensureDefaultProfile(userId: Long): Profile {
        profileDao.findByUser(userId)?.let { return it }

        val defaults = ProfileDefaults.forNewUser(userId)
        return runCatching {
            profileDao.insert(defaults)
            defaults
        }.getOrElse {
            //insert that bai o day nghia la co rac thu tu giua hai coroutine
            profileDao.findByUser(userId) ?: throw it
        }
    }

    override suspend fun completeOnboarding(
        userId: Long,
        level: EducationLevel,
        subjects: Set<Subject>,
        style: ExplanationStyle
    ): Profile {
        val current = ensureDefaultProfile(userId)
        val updated = current.copy(
            educationLevel = level,
            preferredStyle = style,
            subjectCodes = subjects,
            onboardedAt = System.currentTimeMillis()
        )
        profileDao.update(updated)
        return updated
    }

    override suspend fun updateLevel(userId: Long, level: EducationLevel) {
        val current = ensureDefaultProfile(userId)
        profileDao.update(current.copy(educationLevel = level))
    }

    override suspend fun updateSubjects(userId: Long, subjects: Set<Subject>) {
        val current = ensureDefaultProfile(userId)
        profileDao.update(current.copy(subjectCodes = subjects))
    }

    override suspend fun updateStyle(userId: Long, style: ExplanationStyle) {
        profileDao.updateStyle(userId, style)
    }
}
