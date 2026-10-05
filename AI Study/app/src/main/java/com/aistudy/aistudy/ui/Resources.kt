package com.aistudy.aistudy.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aistudy.aistudy.R
import com.aistudy.aistudy.data.repository.ImprovementKind
import com.aistudy.aistudy.domain.model.enums.BadgeCode
import com.aistudy.aistudy.domain.model.enums.Difficulty
import com.aistudy.aistudy.domain.model.enums.EducationLevel
import com.aistudy.aistudy.domain.model.enums.ExplanationStyle
import com.aistudy.aistudy.domain.model.enums.NotificationType
import com.aistudy.aistudy.domain.model.enums.QuestionType
import com.aistudy.aistudy.domain.model.enums.Subject
import com.aistudy.aistudy.domain.model.enums.XpReason

/**
 * Mot cho doi enum -> string resource.
 *
 * Ten enum la tieng Anh vi no nam trong DB, con nhan hien thi la tieng Viet.
 * Gom o mot cho de khong man hinh nao tu hardcode mot tu Viet.
 */
@get:StringRes
val Subject.labelRes: Int
    get() = when (this) {
        Subject.MATHEMATICS -> R.string.subject_mathematics
        Subject.SCIENCE -> R.string.subject_science
        Subject.PROGRAMMING -> R.string.subject_programming
        Subject.HISTORY -> R.string.subject_history
        Subject.LANGUAGE -> R.string.subject_language
    }

@get:StringRes
val EducationLevel.labelRes: Int
    get() = when (this) {
        EducationLevel.MIDDLE -> R.string.education_level_middle
        EducationLevel.HIGH -> R.string.education_level_high
        EducationLevel.UNIVERSITY -> R.string.education_level_university
    }

@get:StringRes
val ExplanationStyle.labelRes: Int
    get() = when (this) {
        ExplanationStyle.SHORT -> R.string.explanation_style_short
        ExplanationStyle.DETAILED -> R.string.explanation_style_detailed
        ExplanationStyle.STEP_BY_STEP -> R.string.explanation_style_step_by_step
    }

@get:StringRes
val NotificationType.labelRes: Int
    get() = when (this) {
        NotificationType.REVIEW_REMINDER -> R.string.notification_type_review_reminder
        NotificationType.MISTAKE_ALERT -> R.string.notification_type_mistake_alert
        NotificationType.MOTIVATIONAL -> R.string.notification_type_motivational
        NotificationType.DAILY_SUMMARY -> R.string.notification_type_daily_summary
    }

@get:StringRes
val Difficulty.labelRes: Int
    get() = when (this) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.MEDIUM -> R.string.difficulty_medium
        Difficulty.HARD -> R.string.difficulty_hard
    }

@get:StringRes
val QuestionType.labelRes: Int
    get() = when (this) {
        QuestionType.MULTIPLE_CHOICE -> R.string.practice_type_multiple_choice
        QuestionType.SHORT_ANSWER -> R.string.practice_type_short_answer
        QuestionType.FILL_BLANK -> R.string.practice_type_fill_blank
    }

@get:StringRes
val XpReason.labelRes: Int
    get() = when (this) {
        XpReason.ASK_QUESTION -> R.string.xp_reason_ask_question
        XpReason.REVIEW_ANSWER -> R.string.xp_reason_review_answer
        XpReason.COMPLETE_QUIZ -> R.string.xp_reason_complete_quiz
        XpReason.QUIZ_BONUS -> R.string.xp_reason_quiz_bonus
        XpReason.DAILY_STREAK -> R.string.xp_reason_daily_streak
    }

@get:StringRes
val BadgeCode.labelRes: Int
    get() = when (this) {
        BadgeCode.FIRST_QUESTION -> R.string.badge_first_question
        BadgeCode.STREAK_7 -> R.string.badge_streak_7
        BadgeCode.SUBJECT_EXPLORER -> R.string.badge_subject_explorer
        BadgeCode.QUIZ_MASTER -> R.string.badge_quiz_master
        BadgeCode.EARLY_BIRD -> R.string.badge_early_bird
    }

@get:StringRes
val ImprovementKind.labelRes: Int
    get() = when (this) {
        ImprovementKind.SIMPLIFIED_EXPLANATION -> R.string.improve_simplified
        ImprovementKind.ALTERNATIVE_APPROACH -> R.string.improve_alternative
        ImprovementKind.KEY_CONCEPTS -> R.string.improve_key_concepts
        ImprovementKind.COMMON_MISTAKES -> R.string.improve_common_mistakes
        ImprovementKind.FOLLOW_UP_QUESTIONS -> R.string.improve_follow_ups
    }

@Composable
fun Subject.label(): String = stringResource(labelRes)

@Composable
fun EducationLevel.label(): String = stringResource(labelRes)

@Composable
fun ExplanationStyle.label(): String = stringResource(labelRes)

@Composable
fun NotificationType.label(): String = stringResource(labelRes)

@Composable
fun Difficulty.label(): String = stringResource(labelRes)

@Composable
fun QuestionType.label(): String = stringResource(labelRes)

@Composable
fun XpReason.label(): String = stringResource(labelRes)

@Composable
fun BadgeCode.label(): String = stringResource(labelRes)

@Composable
fun ImprovementKind.label(): String = stringResource(labelRes)

/**
 * `subjectCode` trong DB la String, nhung enum co the bi bo sung vao DB cu.
 * Parse an toan de UI khong crash khi gap gia tri la.
 */
fun subjectFromCode(code: String): Subject? =
    runCatching { Subject.valueOf(code.trim().uppercase()) }.getOrNull()