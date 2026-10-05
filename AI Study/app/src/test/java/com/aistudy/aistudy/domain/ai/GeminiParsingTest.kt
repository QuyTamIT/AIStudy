package com.aistudy.aistudy.domain.ai

import com.aistudy.aistudy.domain.model.enums.Difficulty
import com.aistudy.aistudy.domain.model.enums.EducationLevel
import com.aistudy.aistudy.domain.model.enums.QuestionType
import com.aistudy.aistudy.domain.model.enums.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parse JSON cua Gemini la noi de sinh loi vi no phu thuoc vao chuoi model
 * tra ve. Cac ca day khoa cac thay doi nho cua prompt.
 */
class GeminiParsingTest {

    private val client = GeminiAiClient(apiKey = "test-key")

    private val fullAnswerJson = """
        {
          "answerText": "Dap an la 3.",
          "difficulty": "HARD",
          "subject": "SCIENCE",
          "simplifiedExplanation": "Rat gon.",
          "alternativeApproach": "Dung hinh ve.",
          "keyConcepts": "Luc; Mo bien",
          "commonMistakes": "Quen dau am",
          "followUpQuestions": "Tinh nang?; Bai tiep?"
        }
    """.trimIndent()

    @Test
    fun `doc duoc JSON boc trong fences`() {
        val answer = client.parseAnswer("```json\n$fullAnswerJson\n```", Subject.MATHEMATICS)

        assertEquals("Dap an la 3.", answer.answerText)
        assertEquals(Difficulty.HARD, answer.difficulty)
        assertEquals(Subject.SCIENCE, answer.subject)
        assertEquals("Rat gon.", answer.simplifiedExplanation)
        assertEquals("Dung hinh ve.", answer.alternativeApproach)
        assertEquals("Luc; Mo bien", answer.keyConcepts)
        assertEquals("Quen dau am", answer.commonMistakes)
        assertEquals("Tinh nang?; Bai tiep?", answer.followUpQuestions)
    }

    @Test
    fun `doc duoc JSON khong co fences va co text bao quanh`() {
        val answer = client.parseAnswer(
            "Day la loi giai cua ban:\n$fullAnswerJson\n\nHet.",
            Subject.MATHEMATICS
        )
        assertEquals("Dap an la 3.", answer.answerText)
    }

    @Test
    fun `thieu answerText phai nem Malformed chu khong tra rong`() {
        val error = runCatching {
            client.parseAnswer("""{"difficulty":"EASY"}""", Subject.MATHEMATICS)
        }.exceptionOrNull()

        assertTrue("phai la AiException.Malformed", error is AiException.Malformed)
    }

    @Test
    fun `enum sai gia tri tra null thay vi crash`() {
        val answer = client.parseAnswer(
            """{"answerText":"x","difficulty":"KHONG_TON_TAI","subject":"TOAN_HOC"}""",
            Subject.PROGRAMMING
        )

        assertNull(answer.difficulty)
        assertEquals("subject la optional nen phai dung gia tri fallback", Subject.PROGRAMMING, answer.subject)
    }

    @Test
    fun `subject hop le duoc ghi de fallback`() {
        val answer = client.parseAnswer(
            """{"answerText":"x","subject":"HISTORY"}""",
            Subject.PROGRAMMING
        )
        assertEquals(Subject.HISTORY, answer.subject)
    }

    @Test
    fun `khong co JSON nao trong text phai nem Malformed`() {
        val error = runCatching {
            client.parseAnswer("Xin loi, toi khong the tra loi cau nay.", Subject.MATHEMATICS)
        }.exceptionOrNull()

        assertTrue(error is AiException.Malformed)
    }

    @Test
    fun `parseQuizDrafts doc duoc object boc mang trong questions`() {
        val drafts = client.parseQuizDrafts(
            """
            ```json
            {"questions":[
              {"questionType":"MULTIPLE_CHOICE","prompt":"2+2=?","correctAnswer":"A",
               "optionA":"4","optionB":"5","optionC":"6","optionD":"7","explanation":"Cong don"},
              {"questionType":"FILL_BLANK","prompt":"Dat vao cho trong","correctAnswer":"luc"}
            ]}
            ```
            """.trimIndent()
        )

        assertEquals(2, drafts.size)
        assertEquals(QuestionType.MULTIPLE_CHOICE, drafts[0].questionType)
        assertEquals("A", drafts[0].correctAnswer)
        assertEquals("4", drafts[0].optionA)
        assertEquals("7", drafts[0].optionD)
        assertNull("SHORT_ANSWER khong co option", drafts[1].optionA)
        assertEquals(QuestionType.FILL_BLANK, drafts[1].questionType)
    }

    @Test
    fun `parseQuizDrafts doc duoc mang thang`() {
        val drafts = client.parseQuizDrafts(
            """[{"questionType":"SHORT_ANSWER","prompt":"Giai thich","correctAnswer":"Vi co x"}]"""
        )
        assertEquals(1, drafts.size)
        assertEquals(QuestionType.SHORT_ANSWER, drafts[0].questionType)
    }

    @Test
    fun `mang rong phai nem Malformed thay vi tra list rong`() {
        val error = runCatching { client.parseQuizDrafts("""{"questions":[]}""") }.exceptionOrNull()
        assertTrue(error is AiException.Malformed)
    }

    @Test
    fun `cau thieu correctAnswer phai nem Malformed`() {
        val error = runCatching {
            client.parseQuizDrafts("""[{"questionType":"SHORT_ANSWER","prompt":"p"}]""")
        }.exceptionOrNull()
        assertTrue(error is AiException.Malformed)
    }

    @Test
    fun `questionType la khoa deu thi fallback sang SHORT_ANSWER`() {
        val drafts = client.parseQuizDrafts(
            """[{"questionType":"TU_KHON_BIET","prompt":"p","correctAnswer":"c"}]"""
        )
        assertEquals(QuestionType.SHORT_ANSWER, drafts[0].questionType)
    }

    @Test
    fun `isConfigured true khi co api key`() {
        assertTrue(GeminiAiClient(apiKey = "abc").isConfigured)
    }

    @Test
    fun `extractJson uu tien noi dung trong fences`() {
        val extracted = extractJson("```json\n{\"a\":1}\n```")
        assertEquals("""{"a":1}""", extracted)
    }

    @Test
    fun `AiRequest so sanh theo noi dung anh, khong theo dia chi mang`() {
        val a = AiRequest(
            questionText = "q",
            imagePath = "p",
            imageBytes = byteArrayOf(1, 2, 3),
            level = EducationLevel.HIGH
        )
        val b = a.copy(imageBytes = byteArrayOf(1, 2, 3))
        val c = a.copy(imageBytes = byteArrayOf(3, 2, 1))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue("anh khac thi phai khac request", a != c)
    }
}
