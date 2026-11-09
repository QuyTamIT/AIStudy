package com.aistudy.aistudy.ask

import com.aistudy.aistudy.answer.AnswerBodyRenderer
import com.aistudy.aistudy.answer.AnswerContent
import com.aistudy.aistudy.answer.ResponseMode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VietnameseUnicodeTest {

    // 1. Full Vietnamese alphabet with all diacritics and tones
    @Test
    fun testVietnameseAlphabetAndTonesPreserved() {
        val vietnameseVowels = "aàáảãạ ăằắẳẵặ âầấẩẫậ eèéẻẽẹ êềếểễệ iìíỉĩị oòóỏõọ ôồốổỗộ ơờớởỡợ uùúủũụ ưừứửữự yỳýỷỹỵ đĐ"
        val uppercaseVowels = "AÀÁẢÃẠ ĂẰẮẲẴẶ ÂẦẤẨẪẬ EÈÉẺẼẸ ÊỀẾỂỄỆ IÌÍỈĨỊ OÒÓỎÕỌ ÔỒỐỔỖỘ ƠỜỚỞỠỢ UÙÚỦŨỤ ƯỪỨỬỮỰ YỲÝỶỸỴ Đ"

        // Ensure UTF-8 byte conversion and string length match exact characters
        val bytes = vietnameseVowels.toByteArray(Charsets.UTF_8)
        val decoded = String(bytes, Charsets.UTF_8)
        assertEquals(vietnameseVowels, decoded)

        val upperBytes = uppercaseVowels.toByteArray(Charsets.UTF_8)
        val upperDecoded = String(upperBytes, Charsets.UTF_8)
        assertEquals(uppercaseVowels, upperDecoded)
    }

    // 2. Math & Special Characters combined with Vietnamese
    @Test
    fun testVietnameseQuestionWithMathAndSpecialCharacters() {
        val question = "Cho hàm số f(x) = x³ - 3x² + 2. Hãy tìm cực trị, tính đạo hàm f'(x) và vẽ bảng biến thiên khi x ∈ [0, 3]. Tính diện tích S = ∫ f(x)dx từ 0 đến 2."

        // JSONObject.quote properly escapes quotes and backslashes while preserving Unicode characters
        val quoted = JSONObject.quote(question)
        assertTrue(quoted.startsWith("\""))
        assertTrue(quoted.endsWith("\""))
        assertTrue(quoted.contains("hàm số"))
        assertTrue(quoted.contains("cực trị"))
        assertTrue(quoted.contains("x³ - 3x² + 2"))
        assertTrue(quoted.contains("x ∈ [0, 3]"))
        assertTrue(quoted.contains("∫"))
    }

    // 3. Multi-line Vietnamese question with Gboard Telex formatting
    @Test
    fun testMultiLineVietnameseQuestionPreserved() {
        val multilineQuestion = """
            Giải bài toán sau:
            Một người đi xe đạp từ A đến B với vận tốc 15 km/h.
            Sau đó đi tiếp từ B đến C với vận tốc 12 km/h.
            Biết tổng quãng đường là 54 km và tổng thời gian là 4 giờ.
            Tính độ dài mỗi quãng đường AB và BC?
        """.trimIndent()

        val prompt = AskFragment.buildPrompt(multilineQuestion, "uni", "detailed")
        assertTrue("Prompt contains user question", prompt.contains("Một người đi xe đạp"))
        assertTrue("Prompt contains diacritics", prompt.contains("quãng đường"))
        assertTrue("Prompt contains multi-line text", prompt.contains("Tính độ dài mỗi quãng đường"))
    }

    // 4. Verify Gemini Prompt has the Vietnamese Language Contract
    @Test
    fun testPromptContainsVietnameseLanguageContract() {
        val sampleQuestion = "Đa hình trong Kotlin là gì?"
        val prompt = AskFragment.buildPrompt(sampleQuestion, "high", "normal")

        assertTrue("Prompt includes Language Contract", prompt.contains("LANGUAGE AND LOCALIZATION CONTRACT"))
        assertTrue("Prompt mandates responding in Vietnamese", prompt.contains("Vietnamese (Tiếng Việt)"))
        assertTrue("Prompt prohibits forcing English", prompt.contains("Never force or default to English"))
    }

    // 5. Math Step-by-Step in Vietnamese
    @Test
    fun testVietnameseMathResponseParsing() {
        val rawJson = """
            {
                "subject": "math",
                "difficulty": "easy",
                "responseType": "step_by_step",
                "answerType": "solution",
                "finalAnswerRedundant": false,
                "steps": [
                    {
                        "topicId": "",
                        "title": "Trừ 5 ở cả hai vế",
                        "body": "Lấy 15 trừ đi 5 để cô lập biểu thức chứa x: 2x = 15 - 5.",
                        "expr": "2x = 10"
                    },
                    {
                        "topicId": "",
                        "title": "Chia cả hai vế cho 2",
                        "body": "Chia 10 cho 2 để tìm nghiệm x.",
                        "expr": "x = 10 / 2 = 5"
                    }
                ],
                "finalAnswer": "x = 5"
            }
        """.trimIndent()

        val qa = AskFragment.parseAnswer(rawJson)
        assertEquals("math", qa.subject)
        assertEquals("x = 5", qa.finalAnswer)

        val content = AnswerContent.read(qa.steps)
        assertEquals(ResponseMode.STEP_BY_STEP, content.responseMode)
        assertEquals(2, content.steps.size)
        assertEquals("Trừ 5 ở cả hai vế", content.steps[0].title)
        assertEquals("Lấy 15 trừ đi 5 để cô lập biểu thức chứa x: 2x = 15 - 5.", content.steps[0].body)
        assertEquals("Chia cả hai vế cho 2", content.steps[1].title)
    }

    // 6. Direct Conceptual Explanation in Vietnamese
    @Test
    fun testVietnameseDirectExplanationParsing() {
        val rawJson = """
            {
                "subject": "code",
                "difficulty": "medium",
                "responseType": "direct",
                "answerType": "explanation",
                "finalAnswerRedundant": false,
                "steps": [
                    {
                        "topicId": "",
                        "title": "Khái niệm đa hình trong Kotlin",
                        "body": "Đa hình (Polymorphism) là khả năng đối tượng thuộc các lớp con khác nhau phản hồi cùng một phương thức theo cách riêng của chúng thông qua lớp cha hoặc giao diện chung.",
                        "expr": ""
                    }
                ],
                "finalAnswer": "Đa hình giúp mã nguồn linh hoạt, dễ mở rộng và giảm phụ thuộc trực tiếp vào các lớp cụ thể."
            }
        """.trimIndent()

        val qa = AskFragment.parseAnswer(rawJson)
        assertEquals("code", qa.subject)
        assertTrue(qa.finalAnswer!!.contains("Đa hình"))

        val content = AnswerContent.read(qa.steps)
        assertEquals(ResponseMode.DIRECT, content.responseMode)
        assertEquals("Khái niệm đa hình trong Kotlin", content.steps.single().title)
        assertTrue(content.steps.single().body.contains("khả năng đối tượng"))
    }

    // 7. Creative Writing (Poem) in Vietnamese
    @Test
    fun testVietnameseCreativePoemParsing() {
        val poemBody = "Thu về nhuộm thắm sắc phong lan,\\nGió thổi heo may rải ngập tràn.\\n\\nSương giăng lối nhỏ bờ sông vắng,\\nMột thoáng bình yên giữa nhân gian."
        val rawJson = """
            {
                "subject": "other",
                "difficulty": "easy",
                "responseType": "creative",
                "answerType": "explanation",
                "finalAnswerRedundant": true,
                "steps": [
                    {
                        "topicId": "",
                        "title": "Bình yên mùa thu",
                        "body": "$poemBody",
                        "expr": ""
                    }
                ],
                "finalAnswer": "Chủ đề: Nét đẹp thanh bình và lắng đọng của mùa thu."
            }
        """.trimIndent()

        val qa = AskFragment.parseAnswer(rawJson)
        val content = AnswerContent.read(qa.steps)
        assertEquals(ResponseMode.CREATIVE, content.responseMode)
        assertEquals("Bình yên mùa thu", content.steps.single().title)
        assertTrue(content.steps.single().body.contains("Thu về nhuộm thắm"))
    }

    // 8. Clarification in Vietnamese
    @Test
    fun testVietnameseClarificationParsing() {
        val rawJson = """
            {
                "subject": "other",
                "difficulty": "easy",
                "responseType": "clarification",
                "answerType": "explanation",
                "finalAnswerRedundant": false,
                "steps": [
                    {
                        "topicId": "",
                        "title": "Cần thêm thông tin",
                        "body": "Bài hát 'Bèo dạt mây trôi' có bản dân ca Quan họ Bắc Ninh và nhiều bản phối hiện đại. Bạn muốn tìm hiểu lời bài hát gốc hay bản phối của nghệ sĩ cụ thể nào?",
                        "expr": ""
                    }
                ],
                "finalAnswer": "Vui lòng cho biết bạn muốn tìm hiểu bản dân ca gốc hay bản thể hiện của nghệ sĩ nào."
            }
        """.trimIndent()

        val qa = AskFragment.parseAnswer(rawJson)
        val content = AnswerContent.read(qa.steps)
        assertEquals(ResponseMode.CLARIFICATION, content.responseMode)
        assertTrue(content.steps.single().body.contains("Bèo dạt mây trôi"))
    }

    // 9. Limitation / Copyright in Vietnamese
    @Test
    fun testVietnameseLimitationParsing() {
        val rawJson = """
            {
                "subject": "other",
                "difficulty": "medium",
                "responseType": "limitation",
                "answerType": "explanation",
                "finalAnswerRedundant": false,
                "steps": [
                    {
                        "topicId": "",
                        "title": "Thông báo bản quyền tác phẩm",
                        "body": "AIStudy không thể cung cấp toàn bộ lời bài hát do chính sách tôn trọng quyền tác giả. Thay vào đó, bài hát phản ánh nỗi nhớ quê hương da diết với giai điệu mộc mạc và sâu lắng.",
                        "expr": ""
                    }
                ],
                "finalAnswer": "Toàn bộ lời bài hát được bảo vệ bản quyền; đã cung cấp tóm tắt ý nghĩa và hoàn cảnh sáng tác."
            }
        """.trimIndent()

        val qa = AskFragment.parseAnswer(rawJson)
        val content = AnswerContent.read(qa.steps)
        assertEquals(ResponseMode.LIMITATION, content.responseMode)
        assertTrue(content.steps.single().body.contains("tôn trọng quyền tác giả"))
    }

    // 10. Sanitizer preserves Vietnamese characters
    @Test
    fun testSanitizeProsePreservesVietnameseAccents() {
        val textWithNewlines = "Dòng thứ nhất: Tiếng Việt có dấu (ă, â, đ, ê, ô, ơ, ư).\\nDòng thứ hai: Công thức \$a \\neq b\$ và \$\\nabla f(x)\$.\\r\\nDòng thứ ba: Mã `val x = 10`."
        val sanitized = AnswerBodyRenderer.sanitizeProse(textWithNewlines)

        assertTrue("Vietnamese text preserved", sanitized.contains("Tiếng Việt có dấu (ă, â, đ, ê, ô, ơ, ư)"))
        assertTrue("First line unescaped", sanitized.contains("Dòng thứ nhất:"))
        assertTrue("Second line unescaped", sanitized.contains("\nDòng thứ hai:"))
        assertTrue("Third line unescaped", sanitized.contains("\nDòng thứ ba:"))
        assertTrue("LaTeX preserved", sanitized.contains("\\neq"))
        assertTrue("LaTeX preserved", sanitized.contains("\\nabla"))
        assertTrue("Inline code preserved", sanitized.contains("`val x = 10`"))
    }
}
