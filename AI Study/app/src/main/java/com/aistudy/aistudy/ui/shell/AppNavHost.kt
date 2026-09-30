package com.aistudy.aistudy.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.aistudy.aistudy.ui.screens.ask.AskScreen
import com.aistudy.aistudy.ui.screens.history.HistoryScreen
import com.aistudy.aistudy.ui.screens.practice.PracticeScreen
import com.aistudy.aistudy.ui.screens.profile.ProfileScreen
import com.aistudy.aistudy.ui.screens.progress.ProgressScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.ASK,
        modifier = modifier
    ) {
        composable(Routes.ASK) { AskScreen() }
        composable(Routes.HISTORY) { HistoryScreen() }
        composable(Routes.PRACTICE) { PracticeScreen() }
        composable(Routes.PROGRESS) { ProgressScreen() }
        composable(Routes.PROFILE) { ProfileScreen() }
    }
}
