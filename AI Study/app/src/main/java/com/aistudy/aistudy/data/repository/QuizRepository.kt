package com.aistudy.aistudy.data.repository

import com.aistudy.aistudy.domain.model.entity.QuizItem
import com.aistudy.aistudy.domain.model.enums.Subject
import kotlinx.coroutines.flow.Flow

/**
 * FR7 - sinh cau hoi luyen tap va cham diem tuc thoi.
 *
 * Chi [generateFromQuestion] tao them cau moi. Cac con lao `observeUnanswered`
 * dung lai nhung cau da sinh truoc do - tranh goi AI lai khong can thiet.
 */
interface QuizRepository {

    fun observeAll(userId: Long): Flow<List<QuizItem>>

    fun observeUnanswered(userId: Long): Flow<List<QuizItem>>

    fun observeBySubject(userId: Long, subject: Subject): Flow<List<QuizItem>>

    /** @return id cac row vua tao, rong neu AI that bai. */
    suspend fun generateFromQuestion(
        userId: Long,
        sourceQuestionId: Long?,
        count: Int
    ): Result<List<Long>>

    suspend fun answer(id: Long, isCorrect: Boolean): Result<Unit>

    suspend fun accuracy(userId: Long): Double

    suspend fun answeredCount(userId: Long): Int

    suspend fun totalCount(userId: Long): Int
}
