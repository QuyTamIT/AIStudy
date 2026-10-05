package com.aistudy.aistudy.ui.root

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.rememberNavController
import com.aistudy.aistudy.ui.common.LoadingBox
import com.aistudy.aistudy.ui.onboarding.OnboardingScreen
import com.aistudy.aistudy.ui.shell.AppNavHost
import com.aistudy.aistudy.ui.shell.MainShell
import com.aistudy.aistudy.ui.shell.Routes

/**
 * Cong vao cua app - quyet dinh man hinh nao hien thi.
 *
 * Khong dung Navigation cho lop nay: day la quyet dinh *co session hay khong*,
 * khong phai dieu huong trong mot tab. Dung NavHost o day se lam phim back cua
 * he lui qua cac man hinh da duoc quyet dinh xong.
 *
 * `Profile.onboardedAt == null` la tin hieu duy nhat cho biet chua onboarding.
 */
@Composable
fun AppRoot(deps: AppDependencies) {
    val gateViewModel: AuthGateViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AuthGateViewModel(deps.authRepository, deps.profileRepository) }
        }
    )
    val gate by gateViewModel.state.collectAsStateWithLifecycle()

    when (gate) {
        // Mot nhanh de tranh nhay man hinh chua quyet dinh tren tung thay doi
        // state cua Room.
        AuthGate.Loading -> LoadingBox()
        AuthGate.SignedOut -> SignedOutFlow(deps)
        AuthGate.NeedsOnboarding -> OnboardingFlow(deps)
        AuthGate.Ready -> MainShell(deps)
    }
}

/**
 * Welcome -> Login/Register la mot luoi con rieng. `popBackStack(WELCOME,
 * inclusive = true)` sau khi dang nhap xoa het luoi, nho do phim back khong quay
 * lai man hinh dang nhap tren mot app da co session.
 */
@Composable
private fun SignedOutFlow(deps: AppDependencies) {
    AppNavHost(
        navController = rememberNavController(),
        deps = deps,
        startDestination = Routes.WELCOME,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun OnboardingFlow(deps: AppDependencies) {
    OnboardingScreen(
        deps = deps,
        onBack = null,
        editMode = false
    )
}