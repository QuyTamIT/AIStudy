package com.aistudy.aistudy.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudy.aistudy.data.repository.AuthRepository
import com.aistudy.aistudy.data.repository.GamificationRepository
import com.aistudy.aistudy.data.repository.ProfileRepository
import com.aistudy.aistudy.domain.model.entity.UserBadge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** FR9 - Ho so */

data class ProfileUiState(
    val isLoading: Boolean = true,
    val displayName: String = "",
    val level: Int = 1,
    val totalXp: Int = 0,
    val xpToNext: Int = 0,
    val streak: Int = 0,
    val badges: List<UserBadge> = emptyList(),
    val errorMessage: String? = null,
    val hasError: Boolean = false
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val gamificationRepository: GamificationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState(isLoading = true))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun load(userId: Long) {
        viewModelScope.launch {
            try {
                authRepository.currentUser.collectLatest { user ->
                    val name = user?.displayName ?: ""
                    val profile = profileRepository.getProfile(userId)
                    val userData = user ?: return@collectLatest
                    _state.value = ProfileUiState(
                        isLoading = false,
                        displayName = name,
                        level = userData.level,
                        totalXp = userData.totalXp,
                        xpToNext = 1000 - userData.totalXp, // simplified
                        streak = userData.currentStreakDays,
                        badges = emptyList()
                    )
                    gamificationRepository.observeBadges(userId).collect { badges ->
                        val current = _state.value
                        _state.value = current.copy(badges = badges)
                    }
                }
            } catch (e: Exception) {
                _state.value = ProfileUiState(
                    isLoading = false,
                    hasError = true,
                    errorMessage = e.message
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            authRepository.deleteAccount()
        }
    }
}
