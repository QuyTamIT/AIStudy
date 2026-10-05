package com.aistudy.aistudy.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudy.aistudy.data.repository.NotificationRepository
import com.aistudy.aistudy.domain.model.entity.Notification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** FR10 - Thong bao */

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val notifications: List<Notification> = emptyList(),
    val unreadCount: Int = 0,
    val errorMessage: String? = null,
    val hasError: Boolean = false
)

class NotificationsViewModel(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState(isLoading = true))
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    fun load(userId: Long) {
        viewModelScope.launch {
            _state.value = NotificationsUiState(isLoading = true)
            try {
                notificationRepository.observeAll(userId).collect { list ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        notifications = list,
                        unreadCount = list.count { !it.isRead },
                        errorMessage = null,
                        hasError = false
                    )
                }
            } catch (e: Exception) {
                _state.value = NotificationsUiState(
                    isLoading = false,
                    hasError = true,
                    errorMessage = e.message
                )
            }
        }
    }

    fun markAllRead(userId: Long) {
        viewModelScope.launch {
            notificationRepository.markAllRead(userId)
        }
    }

    fun markRead(id: Long) {
        viewModelScope.launch {
            notificationRepository.markRead(id)
        }
    }
}
