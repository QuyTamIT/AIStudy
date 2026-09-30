package com.aistudy.aistudy.domain.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aistudy.aistudy.domain.model.enums.XpReason

/**
 * Bảng `xp_events` — sổ cái XP của FR9.
 *
 * Vì sao có cả bảng này lẫn cột `users.totalXp`: xp_events là **nguồn sự thật**
 * (giải thích được vì sao lên level, tính được chuỗi ngày, thống kê theo tuần cho
 * FR10), còn totalXp là dữ liệu dẫn xuất để dashboard khỏi SUM() mỗi lần mở.
 * TC-GAM-09 kiểm tra bất biến giữa hai nguồn này.
 *
 * @see docs/PART1_M1_SOFTWARE_DESIGN.md mục 4.5
 */
@Entity(
    tableName = "xp_events",
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
data class XpEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "userId")
    val userId: Long,

    @ColumnInfo(name = "reason")
    val reason: XpReason,

    /** Giá trị lấy từ XpRules.xpFor(reason), không hardcode ở nơi gọi. */
    @ColumnInfo(name = "xpAwarded")
    val xpAwarded: Int,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long
)
