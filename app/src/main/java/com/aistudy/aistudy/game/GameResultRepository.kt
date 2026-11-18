package com.aistudy.aistudy.game

import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.GameResult
import com.aistudy.aistudy.xp.XpRules

class GameResultRepository(private val db: AppDatabase) {
    /** One transaction makes result persistence and XP award idempotent across recreation/retry. */
    fun save(result: GameResult): GameResult {
        var persisted = result
        db.runInTransaction {
            val dao = db.gameResultDao()
            val existing = dao.getByRoundId(result.roundId)
            if (existing != null) {
                require(existing.userId == result.userId)
                persisted = existing
                return@runInTransaction
            }
            val profileDao = db.profileDao()
            val profile = profileDao.getByUserId(result.userId)
            persisted = result.copy(xpAwarded = if (profile == null) 0 else result.correctCount * XpRules.QUIZ_CORRECT)
            check(dao.insert(persisted) != -1L)
            if (persisted.xpAwarded > 0) {
                XpRules.recordDailyActivity(profile, profileDao)
                XpRules.addXp(profile, profileDao, persisted.xpAwarded)
            }
        }
        return persisted
    }
}
