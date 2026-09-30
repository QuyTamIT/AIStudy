package com.aistudy.aistudy.domain.model.enums

/**
 * Danh mục môn — 5 môn đúng như brief, không cần bảng riêng (thiết kế D-11).
 * Lưu trong DB dạng String nên đổi vị trí/thứ tự enum không phá dữ liệu cũ.
 */
enum class Subject {
    MATHEMATICS,
    SCIENCE,
    PROGRAMMING,
    HISTORY,
    LANGUAGE
}
