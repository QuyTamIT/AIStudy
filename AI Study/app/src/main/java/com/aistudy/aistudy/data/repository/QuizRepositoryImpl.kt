package com.aistudy.aistudy.data.repository

import com.aistudy.aistudy.data.db.dao.QuestionAnswerDao
import com.aistudy.aistudy.data.db.dao.QuizItemDao
import com.aistudy.aistudy.domain.ai.AiClient
import com.aistudy.aistudy.domain.model.entity.QuestionAnswer
import com.aistudy.aistudy.domain.model.entity.QuizItem
import com.aistudy.aistudy.domain.model.enums.EducationLevel
import com.aistudy.aistudy.domain.model.enums.ExplanationStyle
import com.aistudy.aistudy.domain.model.enums.Subject
import com.aistudy.aistudy.domain.model.enums.XpReason
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * FR7 - sinh cau hoi luyen tap va cham diem tuc thoi.
 *
 * [generateFromQuestion] la noi duy nhat tao them cau moi; cac con lao dung
 * lai nhung cau da sinh truoc do de khong goi AI lai khong can thiet.
 */
class QuizRepositoryImpl(
    private val quizItemDao: QuizItemDao,
    private val questionAnswerDao: QuestionAnswerDao,
    private val profileRepository: ProfileRepository,
    private val gamificationRepository: GamificationRepository,
    private val aiClient: AiClient
) : QuizRepository {

    override fun observeAll(userId: Long): Flow<List<QuizItem>> = quizItemDao.observeByUser(userId)

    override fun observeUnanswered(userId: Long): Flow<List<QuizItem>> =
        quizItemDao.observeUnanswered(userId)

    override fun observeBySubject(userId: Long, subject: Subject): Flow<List<QuizItem>> =
        quizItemDao.observeBySubject(userId, subject)

    override suspend fun generateFromQuestion(
        userId: Long,
        sourceQuestionId: Long?,
        count: Int
    ): Result<List<Long>> = runCatching {
        val source = sourceQuestionId?.let { questionAnswerDao.findById(it) }
            ?: questionAnswerDao.recentByUser(userId, 1).firstOrNull()
            ?: throw IllegalStateException("Chua co cau hoi nao de sinh cau luyen tap")

        val profile = profileRepository.getProfile(userId)
        val drafts = aiClient.generateQuiz(
            source = source,
            count = count,
            level = profile?.educationLevel ?: EducationLevel.HIGH,
            style = profile?.preferredStyle ?: ExplanationStyle.STEP_BY_STEP
        ).getOrThrow()

        if (drafts.isEmpty()) throw IllegalStateException("AI khong sinh duoc cau hoi nao")

        val now = nowMillis()
        quizItemDao.insertAll(
            drafts.map { draft ->
                QuizItem(
                    userId = userId,
                    sourceQuestionId = source.id,
                    subjectCode = source.subjectCode,
                    questionType = draft.questionType,
                    prompt = draft.prompt,
                    optionA = draft.optionA,
                    optionB = draft.optionB,
                    optionC = draft.optionC,
                    optionD = draft.optionD,
                    correctAnswer = draft.correctAnswer,
                    explanation = draft.explanation,
                    createdAt = now
                )
            }
        )
    }

    override suspend fun answer(id: Long, isCorrect: Boolean): Result<Unit> = runCatching {
        quizItemDao.recordAttempt(id, isCorrect, nowMillis())

        val item = quizItemDao.findById(id)
            ?: throw IllegalArgumentException("Khong tim thay cau luyen tap $id")

        // XP chay theo so CAU, khong theo so LAN lam - quac nhieu thi XP vo
        // nghia. `recordActivity` tu lo idempotent trong ngay.
        gamificationRepository.recordActivity(
            userId = item.userId,
            reason = XpReason.COMPLETE_QUIZ,
            nowEpochDay = LocalDate.now().toEpochDay()
        )
        if (isCorrect) {
            gamificationRepository.recordActivity(
                userId = item.userId,
                reason = XpReason.QUIZ_BONUS,
                nowEpochDay = LocalDate.now().toEpochDay()
            )
        }
    }

    override suspend fun accuracy(userId: Long): Double = quizItemDao.accuracy(userId)

    override suspend fun answeredCount(userId: Long): Int = quizItemDao.answeredCount(userId)

    override suspend fun totalCount(userId: Long): Int = quizItemDao.countByUser(userId)
}
