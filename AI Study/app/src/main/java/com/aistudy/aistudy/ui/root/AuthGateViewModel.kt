package com.aistudy.aistudy.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudy.aistudy.data.repository.AuthRepository
import com.aistudy.aistudy.data.repository.ProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class AuthGate {
    Loading,
    SignedOut,
    NeedsOnboarding,
    Ready
}

class AuthGateViewModel(
    authRepository: AuthRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AuthGate> = authRepository.sessionUserId
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(AuthGate.SignedOut)
            } else {
                profileRepository.observeProfile(userId)
                    .map { profile -> if (profile?.onboardedAt == null) AuthGate.NeedsOnboarding else AuthGate.Ready }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AuthGate.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}