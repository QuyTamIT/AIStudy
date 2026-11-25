package com.aistudy.aistudy.profile

import android.content.Context
import com.aistudy.aistudy.auth.PasswordHasher
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.ProfileDefaults
import com.aistudy.aistudy.xp.UnlockCatalog
import com.aistudy.aistudy.xp.XpRules

class StudentProfileRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    private val userDao = database.userDao()
    private val profileDao = database.profileDao()
    private val qaDao = database.questionAnswerDao()
    private val quizDao = database.quizSessionDao()

    fun getProfile(currentUserId: Long): StudentProfileData {
        FirebaseStudentSync.validateUserOwnership(currentUserId, currentUserId)

        val user = userDao.getById(currentUserId)
            ?: throw IllegalStateException("Không tìm thấy thông tin người dùng với ID $currentUserId")
        val profile = ProfileDefaults.ensureExists(profileDao, currentUserId)

        val levelNumber = XpRules.levelFromXp(profile.xp)
        val levelTitle = "Học giả"
        val xpProgress = XpRules.progressPercentToNext(profile.xp)
        val xpToNext = XpRules.xpToNextLevel(profile.xp)
        val nextThreshold = XpRules.nextLevelThreshold(profile.xp)

        val avatarId = UnlockCatalog.normalizeAvatarId(profile.avatarId)
        val avatarEmoji = UnlockCatalog.emojiForAvatar(avatarId)

        val totalQuestions = qaDao.countByUserId(currentUserId)
        val totalStarred = qaDao.countStarredByUserId(currentUserId)
        val totalReviewSeconds = qaDao.sumReviewedSecondsByUserId(currentUserId)
        val recentQuestions = qaDao.getAllByUserId(currentUserId).take(5)

        val subjectCounts = mapOf(
            ProfileDefaults.SUBJECT_MATH to qaDao.countBySubject(currentUserId, ProfileDefaults.SUBJECT_MATH),
            ProfileDefaults.SUBJECT_SCIENCE to qaDao.countBySubject(currentUserId, ProfileDefaults.SUBJECT_SCIENCE),
            ProfileDefaults.SUBJECT_CODE to qaDao.countBySubject(currentUserId, ProfileDefaults.SUBJECT_CODE),
            ProfileDefaults.SUBJECT_HISTORY to qaDao.countBySubject(currentUserId, ProfileDefaults.SUBJECT_HISTORY),
            ProfileDefaults.SUBJECT_LANGUAGE to qaDao.countBySubject(currentUserId, ProfileDefaults.SUBJECT_LANGUAGE)
        )

        // Real Quiz Accuracy
        val quizSessions = quizDao.getAllByUserId(currentUserId).filter { it.completed }
        val totalQuizQuestions = quizSessions.sumOf { it.total }
        val totalQuizScore = quizSessions.sumOf { it.score }
        val quizAccuracy = if (totalQuizQuestions > 0) {
            (totalQuizScore * 100 / totalQuizQuestions).coerceIn(0, 100)
        } else {
            0
        }

        val favSubjects = profile.subjects.orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return StudentProfileData(
            userId = currentUserId,
            fullName = user.fullName.orEmpty().ifBlank { "Học sinh AIStudy" },
            email = user.email.orEmpty(),
            levelNumber = levelNumber,
            levelTitle = levelTitle,
            xp = profile.xp,
            streak = profile.streak,
            avatarId = avatarId,
            avatarEmoji = avatarEmoji,
            gradeLevel = profile.level ?: ProfileDefaults.LEVEL_HIGH,
            learningStyle = profile.style ?: ProfileDefaults.STYLE_STEPWISE,
            favoriteSubjects = favSubjects,
            xpProgressPercent = xpProgress,
            xpToNextLevel = xpToNext,
            nextLevelThreshold = nextThreshold,
            totalQuestions = totalQuestions,
            totalStarred = totalStarred,
            totalReviewSeconds = totalReviewSeconds,
            quizAccuracy = quizAccuracy,
            subjectCounts = subjectCounts,
            recentQuestions = recentQuestions,
            isCloudSynced = true
        )
    }

    fun updateName(currentUserId: Long, newName: String): Result<Unit> {
        FirebaseStudentSync.validateUserOwnership(currentUserId, currentUserId)
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Họ và tên không được để trống"))
        }
        val affected = userDao.updateFullName(currentUserId, trimmed)
        return if (affected > 0) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Không thể cập nhật họ và tên"))
        }
    }

    fun updateAvatar(currentUserId: Long, avatarId: String): Result<Unit> {
        FirebaseStudentSync.validateUserOwnership(currentUserId, currentUserId)
        val profile = ProfileDefaults.ensureExists(profileDao, currentUserId)
        val currentLevel = XpRules.levelFromXp(profile.xp)

        if (!UnlockCatalog.isAvatarUnlocked(avatarId, currentLevel)) {
            val req = UnlockCatalog.requiredLevelForAvatar(avatarId)
            return Result.failure(IllegalStateException("Linh vật này yêu cầu đạt Cấp $req để mở khóa"))
        }

        profile.avatarId = avatarId
        val affected = profileDao.update(profile)
        return if (affected > 0) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Không thể cập nhật ảnh đại diện"))
        }
    }

    fun updatePreferences(
        currentUserId: Long,
        gradeLevel: String,
        learningStyle: String,
        favoriteSubjects: List<String>
    ): Result<Unit> {
        FirebaseStudentSync.validateUserOwnership(currentUserId, currentUserId)
        val profile = ProfileDefaults.ensureExists(profileDao, currentUserId)

        profile.level = gradeLevel
        profile.style = learningStyle
        profile.subjects = favoriteSubjects.joinToString(",")

        val affected = profileDao.update(profile)
        return if (affected > 0) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Không thể lưu tùy chọn học tập"))
        }
    }

    fun changePassword(
        currentUserId: Long,
        oldPass: String,
        newPass: String
    ): Result<Unit> {
        FirebaseStudentSync.validateUserOwnership(currentUserId, currentUserId)
        if (newPass.length < 6) {
            return Result.failure(IllegalArgumentException("Mật khẩu mới phải có ít nhất 6 ký tự"))
        }

        val user = userDao.getById(currentUserId)
            ?: return Result.failure(IllegalStateException("Không tìm thấy người dùng"))

        val stored = user.password ?: ""
        if (!PasswordHasher.matches(oldPass, stored)) {
            return Result.failure(IllegalArgumentException("Mật khẩu hiện tại không chính xác"))
        }

        val newHashed = PasswordHasher.hash(newPass)
        val affected = userDao.replacePasswordIfUnchanged(currentUserId, stored, newHashed)
        return if (affected > 0) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Không thể đổi mật khẩu, vui lòng thử lại sau"))
        }
    }

    fun syncFirebase(currentUserId: Long): FirebaseStudentSync.SyncResult {
        FirebaseStudentSync.validateUserOwnership(currentUserId, currentUserId)
        val data = getProfile(currentUserId)
        return FirebaseStudentSync.sync(context, currentUserId, data)
    }
}
