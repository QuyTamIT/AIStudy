package com.aistudy.aistudy.data.db

import com.aistudy.aistudy.domain.model.enums.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm thử bộ chuyển đổi kiểu cho Room.
 * Không cần emulator vì Converters là lớp thuần Kotlin.
 */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `round trip giu nguyen bo mon`() {
        val subjects = setOf(Subject.MATHEMATICS, Subject.PROGRAMMING, Subject.HISTORY)
        val restored = converters.toSubjectSet(converters.fromSubjectSet(subjects))
        assertEquals(subjects, restored)
    }

    @Test
    fun `round trip voi bo rong`() {
        assertEquals(emptySet<Subject>(), converters.toSubjectSet(converters.fromSubjectSet(emptySet())))
    }

    @Test
    fun `round trip voi mot phan tu`() {
        val single = setOf(Subject.SCIENCE)
        assertEquals(single, converters.toSubjectSet(converters.fromSubjectSet(single)))
    }

    @Test
    fun `round trip lay het nam mon trong brief`() {
        val all = Subject.entries.toSet()
        assertEquals(5, all.size)
        assertEquals(all, converters.toSubjectSet(converters.fromSubjectSet(all)))
    }

    /** Set không có thứ tự nên chuỗi lưu có thể đảo thứ tự; đọc lại phải ra đúng tập. */
    @Test
    fun `thu tu trong chuoi luu khong quan trong`() {
        val forward = converters.fromSubjectSet(setOf(Subject.MATHEMATICS, Subject.SCIENCE))
        val backward = converters.fromSubjectSet(setOf(Subject.SCIENCE, Subject.MATHEMATICS))
        assertEquals(converters.toSubjectSet(forward), converters.toSubjectSet(backward))
    }

    @Test
    fun `gia tri la trong bi bo qua thay vi lam sap man hinh`() {
        val restored = converters.toSubjectSet("MATHEMATICS|NOT_A_SUBJECT|SCIENCE")
        assertEquals(setOf(Subject.MATHEMATICS, Subject.SCIENCE), restored)
    }

    @Test
    fun `chuoi null hoac rong tra ve bo rong`() {
        assertTrue(converters.toSubjectSet(null).isEmpty())
        assertTrue(converters.toSubjectSet("").isEmpty())
        assertEquals("", converters.fromSubjectSet(null))
    }

    @Test
    fun `co khoang trang thua van doc dung duoc`() {
        assertEquals(
            setOf(Subject.MATHEMATICS, Subject.SCIENCE),
            converters.toSubjectSet(" MATHEMATICS | SCIENCE ")
        )
    }
}
