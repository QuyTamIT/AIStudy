package com.aistudy.aistudy.domain.ai

import com.aistudy.aistudy.domain.model.entity.QuestionAnswer
import com.aistudy.aistudy.domain.model.enums.Difficulty
import com.aistudy.aistudy.domain.model.enums.EducationLevel
import com.aistudy.aistudy.domain.model.enums.ExplanationStyle
import com.aistudy.aistudy.domain.model.enums.QuestionType
import com.aistudy.aistudy.domain.model.enums.Subject
import kotlinx.coroutines.delay

/**
 * FR4 + FR7 - AI client chay offline khi chua co API key.
 *
 * Day KHONG phai stub de lam cho nhanh. App la bai mon hoc, nguoi dung se
 * demo truc tiep tren may khong co key, va mot app ma moi man hinh loi
 * "chua cau hinh" thi khong demo duoc. Noi dung sinh ra phai du shape de UI
 * ve dung, va phai danh dau `isConfigured = false` de UI hien thi canh bao
 * trung thuc.
 */
class FakeAiClient(
    private val latencyMillis: Long = 600L
) : AiClient {

    override val isConfigured: Boolean = false

    override suspend fun answer(request: AiRequest): Result<AiAnswer> {
        delay(latencyMillis)
        val topic = request.questionText.trim().ifBlank { "bai tap" }
        val level = when (request.level) {
            EducationLevel.MIDDLE -> "ngay thuoc chuong trinh THCS"
            EducationLevel.HIGH -> "vung kien thuc THPT"
            EducationLevel.UNIVERSITY -> "muc do dai hoc co so"
        }
        val styleHint = when (request.style) {
            ExplanationStyle.SHORT -> "Tra loi gọn, 3-5 dong."
            ExplanationStyle.DETAILED -> "Giai thich day du, kem vi du so."
            ExplanationStyle.STEP_BY_STEP -> "Tach thanh cac buoc tuong minh theo thu tu."
        }

        val answer = buildString {
            appendLine("### Loi giai")
            appendLine()
            appendLine(
                "Ve cau hoi \"$topic\", ta dung phuong phap sau, phu hop $level. $styleHint"
            )
            appendLine()
            appendLine("**Buoc 1.** Doc ky de bai va xac dinh du lieu da cho.")
            appendLine("**Buoc 2.** Chon dinh nghia hoac cong thuc dung voi chu de.")
            appendLine("**Buoc 3.** Thay the va theo doi tung buoc de tim loi sai neu co.")
            appendLine("**Buoc 4.** Kiem tra lai ket qua co khop voi dieu kien dau bai khong.")
        }

        return Result.success(
            AiAnswer(
                answerText = answer,
                subject = request.subject,
                difficulty = Difficulty.MEDIUM,
                simplifiedExplanation = "Dien giai ngan gon: bai nay dung mot quy tac co ban, " +
                    "chi can ap dung dung thu tu va kiem tra lai du lieu dau vao.",
                alternativeApproach = "Cach khac: dung hinh ve de minh hoa truc quan, " +
                    "thuong nhanh hon cach tinh truc tiep khi so lieu lon.",
                keyConcepts = "Dinh nghia co ban; Tien le; Dieu kien keo theo; Ngoai le canh bao",
                commonMistakes = "Hay nham dau vao dau ra; bo qua truong hop bien = 0; " +
                    "lam tron qua nhieu lan giua chung se sai so.",
                followUpQuestions = "Bai nay co bai tap nao tuong tu khong? " +
                    "Dieu kien nao lam doi dap an?",
                inputTokens = estimateTokens(request.questionText) + 180,
                outputTokens = estimateTokens(answer) + 120
            )
        )
    }

    override suspend fun generateQuiz(
        source: QuestionAnswer,
        count: Int,
        level: EducationLevel,
        style: ExplanationStyle
    ): Result<List<QuizDraft>> {
        delay(latencyMillis)
        val stem = source.questionText.trim().take(90).ifBlank { "Noi dung bai da hoc" }
        val subject = source.subjectCode

        val drafts = (1..count.coerceIn(1, 10)).map { index ->
            when (index % 3) {
                1 -> QuizDraft(
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    prompt = "Nghiem nào sau day la dap an dung cho: $stem?",
                    correctAnswer = "A",
                    optionA = "Ket qua dung voi dieu kien cua bai toan",
                    optionB = "Ket qua dung nhung thieu dieu kien can thiet",
                    optionC = "Ket qua sai hoan toan",
                    optionD = "Khong du du lieu de ket luan",
                    explanation = "Dap an A vi chi nhan gia tri thoa man day du dieu kien."
                )

                2 -> QuizDraft(
                    questionType = QuestionType.SHORT_ANSWER,
                    prompt = "Trinh bay ngan gon buoc giai quyet trung cho: $stem",
                    correctAnswer = "Xac dinh dieu kien - ap dung quy tac - kiem tra ket qua",
                    explanation = "Ba buoc nay du de giai quyet nhom cau hoi tinh toan co ban."
                )

                else -> QuizDraft(
                    questionType = QuestionType.FILL_BLANK,
                    prompt = "Cho nhanh hon, phuong phap nay dua tren $subject va yeu cau gi?",
                    correctAnswer = "dieu kien",
                    explanation = "Dap an la 'dieu kien' - moi quy tac deu xuat phat tu dieu kien."
                )
            }
        }

        return Result.success(drafts)
    }

    /**
     * Uoc luong so token de dong bang `usageMetadata` cua Gemini. Sai so vai chuc
     * token khong anh huong gi, va no giu cho man hinh "dung bao nhieu token"
     * hien du lieu khong rong khi o che do mo phong.
     */
    private fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)
}
