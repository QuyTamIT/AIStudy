package com.aistudy.aistudy.domain.ai

import com.aistudy.aistudy.domain.model.entity.QuestionAnswer
import com.aistudy.aistudy.domain.model.enums.Difficulty
import com.aistudy.aistudy.domain.model.enums.EducationLevel
import com.aistudy.aistudy.domain.model.enums.ExplanationStyle
import com.aistudy.aistudy.domain.model.enums.QuestionType
import com.aistudy.aistudy.domain.model.enums.Subject

/** Do kho AI tu nhan dien - FR4. */
data class AiRequest(
    val questionText: String,
    val imagePath: String? = null,
    /**
     * Anh da giai ma san. `imagePath` tro vao file AES-GCM trong `filesDir` nen
     * khong the gui thang cho API - repository phai giai ma qua `ImageStore`
     * roi day bytes vao day. Null khi cau hoi khong kem anh.
     */
    val imageBytes: ByteArray? = null,
    val subject: Subject = Subject.MATHEMATICS,
    val level: EducationLevel = EducationLevel.HIGH,
    val style: ExplanationStyle = ExplanationStyle.STEP_BY_STEP
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AiRequest) return false
        return questionText == other.questionText &&
            imagePath == other.imagePath &&
            imageBytes.contentEqualsNullable(other.imageBytes) &&
            subject == other.subject &&
            level == other.level &&
            style == other.style
    }

    override fun hashCode(): Int {
        var result = questionText.hashCode()
        result = 31 * result + (imagePath?.hashCode() ?: 0)
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + subject.hashCode()
        result = 31 * result + level.hashCode()
        result = 31 * result + style.hashCode()
        return result
    }
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
    this == null && other == null -> true
    this == null || other == null -> false
    else -> contentEquals(other)
}

/**
 * Ket qua tra loi tu AI - FR4 + FR5.
 *
 * Moi truong phu giu mot phan cua FR5 nen `QuestionRepository.improve()` goi lai
 * AI cho tung truong mot, khong bat buoc AI tra het mot the.
 */
data class AiAnswer(
    val answerText: String,
    val subject: Subject? = null,
    val difficulty: Difficulty? = null,
    val simplifiedExplanation: String? = null,
    val alternativeApproach: String? = null,
    val keyConcepts: String? = null,
    val commonMistakes: String? = null,
    val followUpQuestions: String? = null,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0
)

/** Cau hoi luyen tap AI sinh ra - FR7, chua luu vao DB. */
data class QuizDraft(
    val questionType: QuestionType,
    val prompt: String,
    val correctAnswer: String,
    val optionA: String? = null,
    val optionB: String? = null,
    val optionC: String? = null,
    val optionD: String? = null,
    val explanation: String? = null
)

/**
 * Cong goai AI. Implementations: `GeminiAiClient` (that) + `FakeAiClient` (offline).
 *
 * Khong dung Retrofit/OkHttp theo quyet dinh D-5 trong design doc - chi
 * `HttpURLConnection` + `org.json` de build khong can mang va khong xung dot
 * version voi AGP 9.3.3.
 */
interface AiClient {

    /** false khi chua co API key - UI can hien thi thong bao thay vi goi mang. */
    val isConfigured: Boolean

    suspend fun answer(request: AiRequest): Result<AiAnswer>

    /** Sinh [count] cau luyen tap tu mot cau hoi da luu. */
    suspend fun generateQuiz(
        source: QuestionAnswer,
        count: Int,
        level: EducationLevel,
        style: ExplanationStyle
    ): Result<List<QuizDraft>>
}
