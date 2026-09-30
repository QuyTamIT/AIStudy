package com.aistudy.aistudy.ui.shell

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.aistudy.aistudy.R

data class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

object Routes {
    const val ARG_QUESTION_ID = "questionId"

    const val ASK = "ask"
    const val HISTORY = "history"
    const val PRACTICE = "practice"
    const val PROGRESS = "progress"
    const val PROFILE = "profile"

    const val QUESTION_DETAIL = "question/{$ARG_QUESTION_ID}"

    fun questionDetail(id: Long): String = "question/$id"
}

val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(Routes.ASK, R.string.nav_ask, Icons.Filled.Home),
    TopLevelDestination(Routes.HISTORY, R.string.nav_history, Icons.Filled.List),
    TopLevelDestination(Routes.PRACTICE, R.string.nav_practice, Icons.Filled.Star),
    TopLevelDestination(Routes.PROGRESS, R.string.nav_progress, Icons.Filled.Refresh),
    TopLevelDestination(Routes.PROFILE, R.string.nav_profile, Icons.Filled.Person)
)
