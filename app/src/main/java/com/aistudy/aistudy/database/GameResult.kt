package com.aistudy.aistudy.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_results")
data class GameResult(
    @PrimaryKey val roundId: String,
    val userId: Long,
    val subject: String,
    val timed: Boolean,
    val score: Int,
    val correctCount: Int,
    val attempted: Int,
    val bestCombo: Int,
    val xpAwarded: Int,
    val completedAt: Long
)
