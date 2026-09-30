package com.aistudy.aistudy.domain.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Bảng `users` — FR1 (đăng ký/đăng nhập) + FR9 (XP, level, streak).
 *
 * @see docs/PART1_M1_SOFTWARE_DESIGN.md mục 4.1
 */
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["email"], unique = true),
        Index(value = ["displayName"])
    ]
)
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "email")
    val email: String,

    /** Base64 của PBKDF2-HMAC-SHA256. TUYỆT ĐỐI không lưu mật khẩu dạng rõ (thiết kế D-6). */
    @ColumnInfo(name = "passwordHash")
    val passwordHash: String,

    /** Base64 salt 16 byte, sinh riêng cho từng user (TC-AUTH-08). */
    @ColumnInfo(name = "passwordSalt")
    val passwordSalt: String,

    @ColumnInfo(name = "displayName")
    val displayName: String,

    /** Chỉ có giá trị khi bật 2FA. */
    @ColumnInfo(name = "twoFactorSecret")
    val twoFactorSecret: String? = null,

    /**
     * Dữ liệu dẫn xuất từ xp_events, để dashboard đọc 1 dòng thay vì SUM() hàng nghìn dòng
     * (NFR Performance). Nguồn sự thật vẫn là xp_events — TC-GAM-09 kiểm tra bất biến này.
     */
    @ColumnInfo(name = "totalXp", defaultValue = "0")
    val totalXp: Int = 0,

    @ColumnInfo(name = "level", defaultValue = "1")
    val level: Int = 1,

    @ColumnInfo(name = "currentStreakDays", defaultValue = "0")
    val currentStreakDays: Int = 0,

    /** Ngày (epoch day) hỏi câu cuối — cần để tính streak là ngày hôm nay hay hôm qua. */
    @ColumnInfo(name = "lastQuestionDate")
    val lastQuestionDate: Long? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long,

    @ColumnInfo(name = "lastLoginAt")
    val lastLoginAt: Long? = null
)
