package com.aistudy.aistudy.data.repository

import com.aistudy.aistudy.domain.model.entity.User
import com.aistudy.aistudy.domain.model.entity.UserBadge
import com.aistudy.aistudy.domain.model.entity.XpEvent
import com.aistudy.aistudy.domain.model.enums.BadgeCode
import com.aistudy.aistudy.domain.model.enums.XpReason
import kotlinx.coroutines.flow.Flow

/**
 * FR9 - XP, level, streak, huyen.
 *
 * Quy tac tinh XP nam o `domain/rules/XpRules.kt` va dieu kien huyen o
 * `BadgeCatalog.kt` - repository chi lo phan ghi vao DB. Khong hardcode gia tri
 * XP o day.
 */
interface GamificationRepository {

    fun observeUser(userId: Long): Flow<User?>

    fun observeBadges(userId: Long): Flow<List<UserBadge>>

    fun observeXpEvents(userId: Long): Flow<List<XpEvent>>

    /**
     * Cap nhat streak theo ngay hien tai roi ghi XP. Idempotent trong mot ngay:
     * goi lai van khong cong them XP cho cung mot `XpReason` trong cung ngay.
     */
    suspend fun recordActivity(userId: Long, reason: XpReason, nowEpochDay: Long): GamificationResult

    suspend fun xpSince(userId: Long, sinceMillis: Long): Int

    suspend fun recalculate(userId: Long)
}

data class GamificationResult(
    val xpGained: Int = 0,
    val totalXp: Int = 0,
    val newLevel: Int = 1,
    val leveledUp: Boolean = false,
    val newBadges: List<BadgeCode> = emptyList()
)
