package com.aistudy.aistudy.profile

/** Student profile ownership checks and Firestore payload formatting. */
object FirebaseStudentSync {

    const val FIRESTORE_COLLECTION_STUDENTS = "students"

    /**
     * Enforces user isolation. Throws SecurityException if a request tries to mutate
     * another user's profile.
     */
    fun validateUserOwnership(currentUserId: Long, requestedUserId: Long) {
        if (currentUserId <= 0L || currentUserId != requestedUserId) {
            throw SecurityException("Truy cập bị từ chối: Người dùng chỉ có quyền truy cập hồ sơ của chính mình.")
        }
    }

    /**
     * Creates a Firestore document payload for the student profile.
     * Document Path: students/{userId}
     */
    fun createFirestorePayload(profile: StudentProfileData): Map<String, Any> {
        return mapOf(
            "studentId" to profile.userId.toString(),
            "fullName" to profile.fullName,
            "email" to profile.email,
            "level" to profile.levelNumber,
            "levelTitle" to profile.levelTitle,
            "xp" to profile.xp,
            "streak" to profile.streak,
            "avatarId" to profile.avatarId,
            "gradeLevel" to profile.gradeLevel,
            "learningStyle" to profile.learningStyle,
            "favoriteSubjects" to profile.favoriteSubjects,
            "totalQuestions" to profile.totalQuestions,
            "totalStarred" to profile.totalStarred,
            "totalReviewSeconds" to profile.totalReviewSeconds,
            "quizAccuracy" to profile.quizAccuracy,
            "updatedAt" to System.currentTimeMillis()
        )
    }
}
