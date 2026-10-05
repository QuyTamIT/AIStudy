package com.aistudy.aistudy.data.repository

import com.aistudy.aistudy.domain.model.entity.QuestionAnswer
import com.aistudy.aistudy.domain.model.enums.Difficulty
import com.aistudy.aistudy.domain.model.enums.Subject
import com.aistudy.aistudy.domain.rules.QuestionSimilarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TC-ASK-02 / TC-ASK-03 / TC-ASK-04 trong test plan: hoi trung phai trung
 * cache, hoi gan giong phai trung, hoi khoac phai miss.
 *
 * Chi test [ReusePolicy] - ham thuan Kotlin. Khong dung Room, khong dung
 * emulator, nen chay duoc trong mili giay.
 */
class ReusePolicyTest {

    private fun row(
        id: Long,
        text: String,
        userId: Long = 1L,
        reviewSeconds: Int = 0,
        bookmarked: Boolean = false,
        servedFromCache: Boolean = false
    ) = QuestionAnswer(
        id = id,
        userId = userId,
        subjectCode = Subject.MATHEMATICS,
        questionText = text,
        questionHash = QuestionSimilarity.hashOf(text),
        servedFromCache = servedFromCache,
        detectedDifficulty = Difficulty.MEDIUM,
        answerText = "Loi giai cua cau $id",
        simplifiedExplanation = "Giai thich rut gon",
        alternativeApproach = "Cach khac",
        keyConcepts = "Khai niem",
        commonMistakes = "Loi sai",
        followUpQuestions = "Hoi tiep gi?",
        isBookmarked = bookmarked,
        reviewTimeSeconds = reviewSeconds,
        createdAt = 1_000L + id
    )

    @Test
    fun `hash trung chinh xac duoc uu tien hon gan trung`() {
        val exact = row(10L, "Tính đạo hàm của hàm số f(x) = x^2")
        val near = row(11L, "Tính đạo hàm hàm số f(x) bằng x^2")

        val chosen = ReusePolicy.select(
            exact = exact,
            candidates = listOf(near, exact),
            text = "tinh dao ham cua ham so f(x) = x^2"
        )

        assertNotNull(chosen)
        assertEquals(10L, chosen!!.id)
    }

    @Test
    fun `cau hoan toan giong nhung o hoa thuong khac nhau van trung`() {
        val exact = row(12L, "Tính tích phân hình bầu tròn bán kính R")
        val chosen = ReusePolicy.select(
            exact = exact,
            candidates = listOf(row(13L, "ban co khoe khong")),
            text = "TÍNH TÍCH PHÂN HÌNH BẦU TRÒN BÁN KÍNH R!!!"
        )
        assertEquals(12L, chosen?.id)
    }

    @Test
    fun `cau gan giong qua nguong 0_85 duoc dung lai`() {
        val source = row(20L, "Tính đạo hàm của hàm số f(x) = x^2")
        val text = "Tính đạo hàm hàm số f(x) bằng x^2"

        assertTrue("vi du phai vuot nguong", QuestionSimilarity.isSimilar(text, source.questionText))
        assertEquals(20L, ReusePolicy.select(null, listOf(source), text)?.id)
    }

    @Test
    fun `cau khoac hoan toan phai miss de goi AI that`() {
        val chosen = ReusePolicy.select(
            exact = null,
            candidates = listOf(
                row(30L, "Tính đạo hàm của hàm số f(x) = x^2"),
                row(31L, "Ai là người sáng lập nước Việt Nam Dân chủ Cộng hòa")
            ),
            text = "Giải phương trình bậc hai x^2 - 5x + 6 = 0"
        )
        assertNull(chosen)
    }

    @Test
    fun `danh sach rong thi khong tai dung gi`() {
        assertNull(ReusePolicy.select(null, emptyList(), "bat ky cau hoi nao"))
    }

    @Test
    fun `reuseAs tao row moi chu khong sua row goc`() {
        val source = row(
            id = 40L,
            text = "Tính đạo hàm của hàm số f(x) = x^2",
            userId = 7L,
            reviewSeconds = 300,
            bookmarked = true
        )
        val now = 9_999L

        val copy = ReusePolicy.reuseAs(source, userId = 99L, nowMillis = now)

        assertEquals("row moi phai co id 0 de Room tu sinh", 0L, copy.id)
        assertEquals("phai gan user moi", 99L, copy.userId)
        assertTrue(copy.servedFromCache)
        assertEquals("tro ve id ban goc de biet reuse tu dong", 40L, copy.matchedQuestionId)
        assertEquals(0, copy.reviewTimeSeconds)
        assertFalse("row moi chua doc nen khong bookmark", copy.isBookmarked)
        assertEquals(now, copy.createdAt)

        assertEquals("row goc phai bat bien", 7L, source.userId)
        assertEquals(300, source.reviewTimeSeconds)
        assertTrue(source.isBookmarked)
    }

    @Test
    fun `reuseAs giu nguyen phan FR5 de khong phai goi AI lai`() {
        val source = row(50L, "Tich phan x^2")
        val copy = ReusePolicy.reuseAs(source, userId = 3L, nowMillis = 1L)

        assertEquals(source.answerText, copy.answerText)
        assertEquals(source.simplifiedExplanation, copy.simplifiedExplanation)
        assertEquals(source.alternativeApproach, copy.alternativeApproach)
        assertEquals(source.keyConcepts, copy.keyConcepts)
        assertEquals(source.commonMistakes, copy.commonMistakes)
        assertEquals(source.followUpQuestions, copy.followUpQuestions)
    }
}
