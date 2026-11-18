package com.aistudy.aistudy.profile

import com.aistudy.aistudy.database.QuestionAnswer

data class StudentProfileData(
    val userId: Long,
    val fullName: String,
    val email: String,
    val levelNumber: Int,
    val levelTitle: String,
    val xp: Int,
    val streak: Int,
    val avatarId: String,
    val avatarEmoji: String,
    val gradeLevel: String,
    val learningStyle: String,
    val favoriteSubjects: List<String>,
    val xpProgressPercent: Int,
    val xpToNextLevel: Int,
    val nextLevelThreshold: Int,
    val totalQuestions: Int,
    val totalStarred: Int,
    val totalReviewSeconds: Int,
    val quizAccuracy: Int,
    val subjectCounts: Map<String, Int>,
    val recentQuestions: List<QuestionAnswer>,
    val isCloudSynced: Boolean = true
)
