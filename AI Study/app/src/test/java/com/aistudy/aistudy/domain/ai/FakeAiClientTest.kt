package com.aistudy.aistudy.domain.ai

import com.aistudy.aistudy.domain.model.entity.QuestionAnswer
import com.aistudy.aistudy.domain.model.enums.Difficulty
import com.aistudy.aistudy.domain.model.enums.EducationLevel
import com.aistudy.aistudy.domain.model.enums.ExplanationStyle
import com.aistudy.aistudy.domain.model.enums.QuestionType
import com.aistudy.aistudy.domain.model.enums.Subject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `FakeAiClient` la noi duy nhat khi app chay khong co API key, nen no phai
 * tra du cai giung cau truc that - UI ve duoc, khong co truong null khoe man
 * hinh, va dashboard "dung bao nhieu token" khong hien 0.
 */
class FakeAiClientTest {

    private val client = FakeAiClient(latencyMillis = 0L)

    private val request = AiRequest(
        questionText = "Tính đạo hàm của hàm số f(x) = x^2",
        subject = Subject.MATHEMATICS,
        level = EducationLevel.HIGH,
        style = ExplanationStyle.STEP_BY_STEP
    )

    private val source = QuestionAnswer(
        id = 1L,
        userId = 1L,
        subjectCode = Subject.MATHEMATICS,
        questionText = "Tính đạo hàm của hàm số f(x) = x^2",
        questionHash = "hash",
        answerText = "f'(x) = 2x",
        createdAt = 0L
    )

    @Test
    fun `isConfigured phai false de UI hien canh bao trung thuc`() {
        assertFalse(client.isConfigured)
    }

    @Test
    fun `answer tra day du cac truong FR4 va FR5`() = runTest {
        val answer = client.answer(request).getOrThrow()

        assertTrue("answerText phai khong rong", answer.answerText.isNotBlank())
        assertTrue("phai nho toi cau hoi", answer.answerText.contains("đạo hàm"))
        assertEquals(Subject.MATHEMATICS, answer.subject)
        assertEquals(Difficulty.MEDIUM, answer.difficulty)
        assertTrue(answer.simplifiedExplanation!!.isNotBlank())
        assertTrue(answer.alternativeApproach!!.isNotBlank())
        assertTrue(answer.keyConcepts!!.isNotBlank())
        assertTrue(answer.commonMistakes!!.isNotBlank())
        assertTrue(answer.followUpQuestions!!.isNotBlank())
    }

    @Test
    fun `token count phai khong bang 0 de dashboard co so lieu`() = runTest {
        val answer = client.answer(request).getOrThrow()
        assertTrue(answer.inputTokens > 0)
        assertTrue(answer.outputTokens > 0)
    }

    @Test
    fun `generateQuiz tra dung so cau yeu cau`() = runTest {
        val drafts = client.generateQuiz(source, count = 5, EducationLevel.HIGH, ExplanationStyle.SHORT)
            .getOrThrow()

        assertEquals(5, drafts.size)
        assertTrue(drafts.all { it.prompt.isNotBlank() && it.correctAnswer.isNotBlank() })
    }

    @Test
    fun `generateQuiz phai co it nhat mot MULTIPLE_CHOICE day du option A den D`() = runTest {
        val drafts = client.generateQuiz(source, count = 3, EducationLevel.HIGH, ExplanationStyle.SHORT)
            .getOrThrow()

        val choice = drafts.firstOrNull { it.questionType == QuestionType.MULTIPLE_CHOICE }
        assertTrue("can it nhat mot cau trac nghiem", choice != null)
        assertTrue(choice!!.optionA!!.isNotBlank())
        assertTrue(choice.optionB!!.isNotBlank())
        assertTrue(choice.optionC!!.isNotBlank())
        assertTrue(choice.optionD!!.isNotBlank())
    }

    @Test
    fun `generateQuiz xen ke ba kieu cau hoi khong dung mot mau`() = runTest {
        val drafts = client.generateQuiz(source, count = 6, EducationLevel.HIGH, ExplanationStyle.SHORT)
            .getOrThrow()

        assertEquals(2, drafts.count { it.questionType == QuestionType.MULTIPLE_CHOICE })
        assertEquals(2, drafts.count { it.questionType == QuestionType.SHORT_ANSWER })
        assertEquals(2, drafts.count { it.questionType == QuestionType.FILL_BLANK })
    }

    @Test
    fun `generateQuiz chan so qua lon de khong lam no dong app`() = runTest {
        val drafts = client.generateQuiz(source, count = 500, EducationLevel.HIGH, ExplanationStyle.SHORT)
            .getOrThrow()

        assertTrue("phai chan lai so cau thuc te", drafts.size <= 10)
    }

    @Test
    fun `answer khong nem loi khi cau hoi rong`() = runTest {
        val empty = client.answer(request.copy(questionText = "   ")).getOrThrow()
        assertTrue(empty.answerText.isNotBlank())
    }
}
