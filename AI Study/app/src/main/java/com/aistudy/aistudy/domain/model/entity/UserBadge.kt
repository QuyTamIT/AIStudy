package com.aistudy.aistudy.domain.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aistudy.aistudy.domain.model.enums.BadgeCode

/**
 * Bảng `user_badges` — FR9 achievement badges.
 *
 * Cố ý KHÔNG có bảng `badges`: danh mục huy hiệu là hằng số của nhà phát triển
 * trong `object BadgeCatalog`, không phải dữ liệu người dùng.
 *
 * Khoá chính tổ hợp (userId, badgeCode) chặn nhận trùng một huy hiệu.
 *
 * @see docs/PART1_M1_SOFTWARE_DESIGN.md mục 4.6
 */
@Entity(
    tableName = "user_badges",
    primaryKeys = ["userId", "badgeCode"],
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class UserBadge(
    @ColumnInfo(name = "userId")
    val userId: Long,

    @ColumnInfo(name = "badgeCode")
    val badgeCode: BadgeCode,

    @ColumnInfo(name = "earnedAt")
    val earnedAt: Long
)
