package com.aistudy.aistudy.data.repository

import com.aistudy.aistudy.domain.model.entity.QuestionAnswer
import com.aistudy.aistudy.domain.model.enums.Subject
import kotlinx.coroutines.flow.Flow

/**
 * FR3-FR6 - hoi AI, tra loi, answer improvement, lich su.
 *
 * FR3 cung cap ca hai duong vao cau hoi: [ask] cho text va [askWithImage] cho
 * anh chup. Hai ham dung chung mot duong cache trong [askInternal] vi
 * `questionHash` chi tinh tren van ban.
 */
interface QuestionRepository {

    fun observeHistory(userId: Long): Flow<List<QuestionAnswer>>

    fun observeBookmarked(userId: Long): Flow<List<QuestionAnswer>>

    suspend fun getQuestion(id: Long): QuestionAnswer?

    suspend fun search(userId: Long, keyword: String): List<QuestionAnswer>

    suspend fun bySubject(userId: Long, subject: Subject): List<QuestionAnswer>

    suspend fun ask(userId: Long, text: String): Result<QuestionAnswer>

    /** [imagePath] la duong dan file da ma hoa trong `filesDir`. */
    suspend fun askWithImage(
        userId: Long,
        text: String,
        imagePath: String?,
        imageIv: String?
    ): Result<QuestionAnswer>

    /** FR5 - sinh rieng mot phan. Tra ve ban moi de UI hien thi ngay. */
    suspend fun improve(id: Long, kind: ImprovementKind): Result<String>

    suspend fun setBookmarked(id: Long, value: Boolean)

    suspend fun addReviewTime(id: Long, seconds: Int)
}

/** FR5 - 5 dang "hoc tro" ma brief yeu cau. */
enum class ImprovementKind {
    SIMPLIFIED_EXPLANATION,
    ALTERNATIVE_APPROACH,
    KEY_CONCEPTS,
    COMMON_MISTAKES,
    FOLLOW_UP_QUESTIONS
}
