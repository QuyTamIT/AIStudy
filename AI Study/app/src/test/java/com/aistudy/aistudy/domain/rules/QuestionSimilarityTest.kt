package com.aistudy.aistudy.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm thử cơ chế AI Cost Management — phần lõi của FR3 và yêu cầu
 * "AI Cost Management" trong brief.
 *
 * Ba ca này là **TC-ASK-02 / TC-ASK-03 / TC-ASK-04** trong test plan:
 * hỏi trùng phải trúng cache, hỏi gần giống phải trúng, hỏi khác phải miss.
 */
class QuestionSimilarityTest {

    @Test
    fun `normalise ha chu thuong va bo dau cham`() {
        assertEquals("hello world", QuestionSimilarity.normalise("  Hello,  World!  "))
    }

    /** Khác nhau ở hoa thường và dấu câu vẫn phải ra cùng một hash. */
    @Test
    fun `hashOf giong nhau khi chuoi khac hoa thuong va dau cham`() {
        val a = QuestionSimilarity.hashOf("Tính đạo hàm của x^2?")
        val b = QuestionSimilarity.hashOf("tinh dao ham cua x 2")
        assertEquals(a, b)
    }

    @Test
    fun `hashOf khac nhau khi noi dung khac nhau`() {
        assertNotEquals(
            QuestionSimilarity.hashOf("Tính đạo hàm của x^2"),
            QuestionSimilarity.hashOf("Tích phân hình bầu tròn")
        )
    }

    @Test
    fun `hashOf la chuoi hex dai 64 ky tu`() {
        val hash = QuestionSimilarity.hashOf("xin chào")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in "0123456789abcdef" })
    }

    /**
     * Câu hỏi giống hệt nhau phải đạt 1.0.
     */
    @Test
    fun `jaccard bang 1 khi hai chuoi giong het`() {
        assertEquals(1.0, QuestionSimilarity.jaccard("giải phương trình bậc hai", "giải phương trình bậc hai"), 0.0001)
    }

    /**
     * Hệ số bao phủ chịu được câu hỏi có thêm từ chức năng, đây là lý do
     * [isSimilar] dùng nó thay vì chỉ dựa vào Jaccard.
     */
    @Test
    fun `containment cao hon jaccard khi mot ben them tu chuc nang`() {
        val a = "Tính đạo hàm của hàm số f(x) = x^2"
        val b = "Tính đạo hàm hàm số f(x) bằng x^2"
        assertTrue(QuestionSimilarity.jaccard(a, b) < QuestionSimilarity.SIMILARITY_THRESHOLD)
        assertTrue(QuestionSimilarity.containment(a, b) >= QuestionSimilarity.SIMILARITY_THRESHOLD)
    }

    /** Câu ngắn nằm trọn trong câu dài: chặn kép Jaccard phải loại. */
    @Test
    fun `isSimilar tu choi khi cau ngan nam tron trong cau dai`() {
        val short = "tính đạo hàm"
        val long = "cho tôi hỏi một câu rất dài về nhiều chủ đề " +
            "bao gồm lịch sử và ngôn ngữ cùng sinh học và địa lý"
        assertEquals(1.0, QuestionSimilarity.containment(short, long), 0.0001)
        assertTrue(QuestionSimilarity.jaccard(short, long) < QuestionSimilarity.MIN_JACCARD_GUARD)
        assertFalse(QuestionSimilarity.isSimilar(short, long))
    }

    @Test
    fun `jaccard bang 0 khi khong co tu chung nao`() {
        assertEquals(0.0, QuestionSimilarity.jaccard("đạo hàm", " photosynthesis "), 0.0001)
    }

    @Test
    fun `isSimilar nhan dien hai cach hoi cung mot bai`() {
        val a = "Tính đạo hàm của hàm số f(x) = x^2"
        val b = "Tính đạo hàm hàm số f(x) bằng x^2"
        assertTrue("Hai cách hội này phải được coi là tương tự", QuestionSimilarity.isSimilar(a, b))
    }

    @Test
    fun `isSimilar tu choi hai bai hoan toan khac mon`() {
        val a = "Tính đạo hàm của hàm số f(x) = x^2"
        val b = "Ai là người sáng lập nước Việt Nam Dân chủ Cộng hòa"
        assertFalse(QuestionSimilarity.isSimilar(a, b))
    }

    /** Câu hỏi quá ngắn không đủ từ khoá — tránh khớp nhầm với câu khác. */
    @Test
    fun `chuoi rong duoc coi la giong nhung khong dung de gui di`() {
        assertEquals(0.5, QuestionSimilarity.jaccard("", ""), 0.0001)
        assertEquals(0.0, QuestionSimilarity.jaccard("abc", ""), 0.0001)
    }

    @Test
    fun `tu dung stop word khong anh huong den do khop`() {
        val a = "What is the derivative of a function"
        val b = "derivative of a function"
        assertTrue(QuestionSimilarity.isSimilar(a, b))
    }

    // ----- Biên độ dài câu hỏi: TC-ASK-07, TC-ASK-08, TC-ASK-09 -----

    @Test
    fun `isValidQuestionText cho qua o do dai bien duoi`() {
        assertTrue(QuestionSimilarity.isValidQuestionText("a"))
    }

    @Test
    fun `isValidQuestionText cho qua dung 2000 ky tu`() {
        assertTrue(QuestionSimilarity.isValidQuestionText("a".repeat(2000)))
    }

    @Test
    fun `isValidQuestionText chan 2001 ky tu`() {
        assertFalse(QuestionSimilarity.isValidQuestionText("a".repeat(2001)))
    }

    @Test
    fun `isValidQuestionText chan chuoi rong hoac chi co khoang trang`() {
        assertFalse(QuestionSimilarity.isValidQuestionText(""))
        assertFalse(QuestionSimilarity.isValidQuestionText("   "))
        assertFalse(QuestionSimilarity.isValidQuestionText("\n\t "))
    }

    @Test
    fun `tokenize bo tu 1 ky tu va tu dung`() {
        // "a" và "b" chỉ 1 ký tự nên bị bỏ; "the" là từ dừng cũng bị bỏ;
        // "o" (trong "toán") chỉ 1 ký tự nên bị bỏ.
        val tokens = QuestionSimilarity.tokenize("What is a b the of o 12")
        assertTrue("1 ký tự phải bị bỏ: a", "a" !in tokens)
        assertTrue("1 ký tự phải bị bị bỏ: b", "b" !in tokens)
        assertTrue("1 ký tự phải bị bỏ: o", "o" !in tokens)
        assertTrue("từ dừng tiếng Anh phải bị bỏ: what", "what" !in tokens)
        assertTrue("từ dừng tiếng Anh phải bị bỏ: the", "the" !in tokens)
        assertTrue("từ dừng tiếng Anh phải bị bỏ: is", "is" !in tokens)
        assertTrue("từ có 2 chữ số phải còn lại: 12", "12" in tokens)
    }

    /**
     * Bỏ dấu là bắt buộc cho người dùng Việt: gõ "tinh dao ham" phải ra cùng hash
     * với "Tính đạo hàm", nếu không cache sẽ trượt và tốn tiền gọi AI oan.
     */
    @Test
    fun `hashOf giong nhau khi mot ben co dau ben khong co dau`() {
        assertEquals(
            QuestionSimilarity.hashOf("Tính đạo hàm của x^2"),
            QuestionSimilarity.hashOf("Tinh dao ham cua x^2")
        )
    }

    @Test
    fun `stripDiacritics bo dau tieng Viet giu nguyen chu cai`() {
        assertEquals("tinh dao ham", QuestionSimilarity.stripDiacritics("Tính đạo hàm"))
        assertEquals("nguon gia", QuestionSimilarity.stripDiacritics("nguồn giả"))
        assertEquals("abcdef", QuestionSimilarity.stripDiacritics("abcdef"))
    }
}
