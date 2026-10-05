package com.aistudy.aistudy.data.repository

import android.content.Context
import com.aistudy.aistudy.data.db.dao.QuestionAnswerDao
import com.aistudy.aistudy.data.security.ImageStore
import com.aistudy.aistudy.domain.ai.AiAnswer
import com.aistudy.aistudy.domain.ai.AiClient
import com.aistudy.aistudy.domain.ai.AiRequest
import com.aistudy.aistudy.domain.model.entity.QuestionAnswer
import com.aistudy.aistudy.domain.model.enums.EducationLevel
import com.aistudy.aistudy.domain.model.enums.ExplanationStyle
import com.aistudy.aistudy.domain.model.enums.NotificationType
import com.aistudy.aistudy.domain.model.enums.Subject
import com.aistudy.aistudy.domain.model.enums.XpReason
import com.aistudy.aistudy.domain.rules.BadgeCatalog
import com.aistudy.aistudy.domain.rules.QuestionSimilarity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * FR3-FR6.
 *
 * Ba nhanh cua [askInternal] la phan co diem cua bai: hash trung, gan trung,
 * hay goi AI that. Brief yeu cau ro "Reduce repeated AI calls through caching"
 * va "Detect similar questions and reuse answers", nen quyet dinh tai dung
 * phai tach khoi I/O de test duoc bang JUnit thuan (xem [ReusePolicy]).
 */
class QuestionRepositoryImpl(
    private val questionAnswerDao: QuestionAnswerDao,
    private val notificationRepository: NotificationRepository,
    private val gamificationRepository: GamificationRepository,
    private val profileRepository: ProfileRepository,
    private val aiClient: AiClient,
    private val imageStore: ImageStore,
    private val appContext: Context
) : QuestionRepository {

    override fun observeHistory(userId: Long): Flow<List<QuestionAnswer>> =
        questionAnswerDao.observeByUser(userId)

    override fun observeBookmarked(userId: Long): Flow<List<QuestionAnswer>> =
        questionAnswerDao.observeBookmarked(userId)

    override suspend fun getQuestion(id: Long): QuestionAnswer? = questionAnswerDao.findById(id)

    override suspend fun search(userId: Long, keyword: String): List<QuestionAnswer> {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return questionAnswerDao.recentByUser(userId, SEARCH_FALLBACK_LIMIT)
        return questionAnswerDao.search(userId, trimmed)
    }

    override suspend fun bySubject(userId: Long, subject: Subject): List<QuestionAnswer> =
        questionAnswerDao.bySubject(userId, subject)

    override suspend fun ask(userId: Long, text: String): Result<QuestionAnswer> =
        askInternal(userId, text, null, null, null)

    override suspend fun askWithImage(
        userId: Long,
        text: String,
        imagePath: String?,
        imageIv: String?
    ): Result<QuestionAnswer> = askInternal(userId, text, imagePath, imageIv, null)

    override suspend fun improve(id: Long, kind: ImprovementKind): Result<String> = runCatching {
        val target = questionAnswerDao.findById(id)
            ?: throw IllegalArgumentException("Khong tim thay cau hoi $id")

        val profile = profileRepository.getProfile(target.userId)
        val answer = aiClient.answer(
            AiRequest(
                questionText = promptFor(kind, target),
                subject = target.subjectCode,
                level = profile?.educationLevel ?: EducationLevel.HIGH,
                style = profile?.preferredStyle ?: ExplanationStyle.STEP_BY_STEP
            )
        ).getOrThrow()

        val improved = answer.fieldFor(kind) ?: answer.answerText
        when (kind) {
            ImprovementKind.SIMPLIFIED_EXPLANATION ->
                questionAnswerDao.setSimplifiedExplanation(id, improved)

            ImprovementKind.ALTERNATIVE_APPROACH ->
                questionAnswerDao.setAlternativeApproach(id, improved)

            ImprovementKind.KEY_CONCEPTS -> questionAnswerDao.setKeyConcepts(id, improved)
            ImprovementKind.COMMON_MISTAKES -> questionAnswerDao.setCommonMistakes(id, improved)
            ImprovementKind.FOLLOW_UP_QUESTIONS -> questionAnswerDao.setFollowUpQuestions(id, improved)
        }
        improved
    }

    override suspend fun setBookmarked(id: Long, value: Boolean) =
        questionAnswerDao.setBookmarked(id, value)

    override suspend fun addReviewTime(id: Long, seconds: Int) {
        if (seconds <= 0) return
        questionAnswerDao.addReviewTime(id, seconds)
    }

    private suspend fun askInternal(
        userId: Long,
        text: String,
        imagePath: String?,
        imageIv: String?,
        overrideSubject: Subject?
    ): Result<QuestionAnswer> = runCatching {
        val trimmed = text.trim()
        if (!QuestionSimilarity.isValidQuestionText(trimmed) && imagePath == null) {
            throw IllegalArgumentException("Cau hoi qua ngan hoac qua dai")
        }

        val profile = profileRepository.ensureDefaultProfile(userId)
        val now = nowMillis()
        val hash = QuestionSimilarity.hashOf(trimmed)

        // 1. Cache trung chinh xac: hash cua van ban da chuan hoa.
        val exact = questionAnswerDao.findByHash(hash)
        // 2. Gan trung: Jaccard tren cua so cau moi nhat.
        val near = questionAnswerDao.recentByUser(userId, CANDIDATE_WINDOW)

        ReusePolicy.select(exact, near, trimmed)?.let { reusable ->
            val copyId = questionAnswerDao.insert(ReusePolicy.reuseAs(reusable, userId, now))
            return@runCatching ReusePolicy.reuseAs(reusable, userId, now).copy(id = copyId)
        }

        // 3. Truot: goi AI that.
        val imageBytes = imagePath?.let { imageStore.read(appContext, it, imageIv) }
        val subject = overrideSubject
            ?: subjectFromText(trimmed)
            ?: profile.subjectCodes.firstOrNull()
            ?: Subject.MATHEMATICS

        val answer = aiClient.answer(
            AiRequest(
                questionText = trimmed,
                imagePath = imagePath,
                imageBytes = imageBytes,
                subject = subject,
                level = profile.educationLevel,
                style = profile.preferredStyle
            )
        ).getOrThrow()

        val row = QuestionAnswer(
            userId = userId,
            subjectCode = answer.subject ?: subject,
            questionText = trimmed,
            questionImagePath = imagePath,
            questionImageIv = imageIv,
            questionHash = hash,
            servedFromCache = false,
            detectedDifficulty = answer.difficulty,
            answerText = answer.answerText,
            simplifiedExplanation = answer.simplifiedExplanation,
            alternativeApproach = answer.alternativeApproach,
            keyConcepts = answer.keyConcepts,
            commonMistakes = answer.commonMistakes,
            followUpQuestions = answer.followUpQuestions,
            inputTokens = answer.inputTokens,
            outputTokens = answer.outputTokens,
            createdAt = now
        )
        val id = questionAnswerDao.insert(row)

        // XP chi cong khi AI that chay. Tra loi tu cache khong ton tien nen
        // khong cong - nguoc lai nguoi dung farm XP bang cach spam.
        val result = gamificationRepository.recordActivity(
            userId = userId,
            reason = XpReason.ASK_QUESTION,
            nowEpochDay = LocalDate.now().toEpochDay()
        )
        if (result.newBadges.isNotEmpty()) {
            val names = result.newBadges.joinToString(", ") { BadgeCatalog.displayName(it) }
            notificationRepository.notify(
                userId = userId,
                type = NotificationType.MOTIVATIONAL,
                title = names,
                body = "Ban vua tra loi mot cau moi. Tiep tuc de giu chuoi ngay!",
                deepLink = DEEP_LINK_PROGRESS
            )
        }

        row.copy(id = id)
    }

    /**
     * Doan mon tu chuoi trong cau hoi, kiem truoc mon da chon trong profile.
     * Doan theo chuoi de tranh ban them mot ban do tu khoa - cau hoi ngan
     * kiem thu o `question_answers.subjectCode` bang cach khop chuoi.
     */
    private fun subjectFromText(text: String): Subject? {
        val normalised = QuestionSimilarity.normalise(text)
        return SUBJECT_KEYWORDS.entries
            .firstOrNull { (_, keywords) -> keywords.any(normalised::contains) }
            ?.key
    }

    private fun promptFor(kind: ImprovementKind, target: QuestionAnswer): String =
        IMPROVE_TEMPLATES.getValue(kind).replace(
            SOURCE_PLACEHOLDER,
            target.answerText.take(MAX_SOURCE_CHARS)
        )

    private companion object {
        /** 50 cau la nua so sanh, giong ghi chu trong `QuestionSimilarity`. */
        const val CANDIDATE_WINDOW = 50
        const val SEARCH_FALLBACK_LIMIT = 50
        const val MAX_SOURCE_CHARS = 1500
        const val SOURCE_PLACEHOLDER = "{SOURCE}"

        /**
         * `Notification.deepLink` luu route de UI `navController.navigate` truc
         * tiep. Gia tri phai khop `Routes.PROGRESS` o `ui/shell`, nhung data
         * layer khong duoc import ui - nen dat la hang so rieng va ghi chu
         * buoc phoi khop lai neu route doi.
         */
        const val DEEP_LINK_PROGRESS = "progress"

        val SUBJECT_KEYWORDS: Map<Subject, List<String>> = mapOf(
            Subject.MATHEMATICS to listOf("toan", "phuong trinh", "dao ham", "tich phan", "ma tran"),
            Subject.SCIENCE to listOf("sinh", "hoa", "vat ly", "khoa hoc", "nguyen tu", "huyen"),
            Subject.PROGRAMMING to listOf("lap trinh", "code", "thuat toan", "python", "java", "sql"),
            Subject.HISTORY to listOf("lich su", "cu", "thoi dai", "su kien"),
            Subject.LANGUAGE to listOf("ngu phap", "tu vung", "diem cau", "giao tiep")
        )

        val IMPROVE_TEMPLATES: Map<ImprovementKind, String> = mapOf(
            ImprovementKind.SIMPLIFIED_EXPLANATION to
                "Day la loi giai mot bai:\n{SOURCE}\n\n" +
                "Viet lai phan GIAI THICH RUT GON cua loi giai tren trong toi da 3 cau, " +
                "chi dung cach giai do, khong them bai moi.",
            ImprovementKind.ALTERNATIVE_APPROACH to
                "Day la loi giai mot bai:\n{SOURCE}\n\n" +
                "Trinh bay MOT CACH GIAI KHAC cho cung bai nay. Chi giai thich cach moi, khong lap lai cach cu.",
            ImprovementKind.KEY_CONCEPTS to
                "Day la loi giai mot bai:\n{SOURCE}\n\n" +
                "Liet ke cac KHAI NIEM CHIN can nho, phan cach bang dau cham, toi da 6 muc.",
            ImprovementKind.COMMON_MISTAKES to
                "Day la loi giai mot bai:\n{SOURCE}\n\n" +
                "Ngoi 3 LOI THUONG GAP khi lam bai nay va 1 ky tu do moi loai.",
            ImprovementKind.FOLLOW_UP_QUESTIONS to
                "Day la loi giai mot bai:\n{SOURCE}\n\n" +
                "Goi y 2 CAU HOI NEN HOI TIEP theo chu de nay, moi cau mot dong."
        )
    }
}

private fun AiAnswer.fieldFor(kind: ImprovementKind): String? = when (kind) {
    ImprovementKind.SIMPLIFIED_EXPLANATION -> simplifiedExplanation
    ImprovementKind.ALTERNATIVE_APPROACH -> alternativeApproach
    ImprovementKind.KEY_CONCEPTS -> keyConcepts
    ImprovementKind.COMMON_MISTAKES -> commonMistakes
    ImprovementKind.FOLLOW_UP_QUESTIONS -> followUpQuestions
}
