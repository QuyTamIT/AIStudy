package com.aistudy.aistudy.data.db

import androidx.room.TypeConverter
import com.aistudy.aistudy.domain.model.enums.Subject

/**
 * Chuyển đổi kiểu cho Room.
 *
 * Room 2.7 đã tự xử lý enum, nên các cột enum khác không cần converter.
 * Chỉ `Set<Subject>` trong bảng `profiles` là kiểu Room không hiểu.
 *
 * Định dạng lưu: `MATHEMATICS|SCIENCE` — dùng dấu `|` vì tên enum không chứa ký tự này.
 * Set không có thứ tự nên chuỗi lưu có thể khác thứ tự giữa các lần ghi; điều đó vô hại
 * vì cột này không bao giờ được index hay so sánh bằng `=`.
 */
class Converters {

    @TypeConverter
    fun fromSubjectSet(subjects: Set<Subject>?): String =
        subjects.orEmpty().joinToString(SEPARATOR) { it.name }

    @TypeConverter
    fun toSubjectSet(value: String?): Set<Subject> =
        value.orEmpty()
            .split(SEPARATOR)
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { raw ->
                // Bỏ qua giá trị lạ thay vì ném exception, để dữ liệu cũ hỏng một ô
                // không làm sập cả màn hình.
                runCatching { Subject.valueOf(raw) }.getOrNull()
            }
            .toSet()

    private companion object {
        const val SEPARATOR = "|"
    }
}
