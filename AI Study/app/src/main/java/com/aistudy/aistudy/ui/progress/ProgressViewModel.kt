package com.aistudy.aistudy.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudy.aistudy.data.repository.ProgressRepository
import com.aistudy.aistudy.data.repository.ProgressSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** FR8 - Tien do */

data class ProgressUiState(
    val isLoading: Boolean = true,
    val summary: ProgressSummary? = null,
    val errorMessage: String? = null,
    val hasError: Boolean = false
)

class ProgressViewModel(
    private val progressRepository: ProgressRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    fun load(userId: Long) {
        viewModelScope.launch {
            _state.value = ProgressUiState(isLoading = true)
            try {
                val summary = progressRepository.summary(userId)
                _state.value = ProgressUiState(
                    isLoading = false,
                    summary = summary
                )
            } catch (e: Exception) {
                _state.value = ProgressUiState(
                    isLoading = false,
                    hasError = true,
                    errorMessage = e.message
                )
            }
        }
    }
}
