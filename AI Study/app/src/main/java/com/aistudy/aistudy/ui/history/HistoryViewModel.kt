package com.aistudy.aistudy.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudy.aistudy.data.repository.QuestionRepository
import com.aistudy.aistudy.domain.model.entity.QuestionAnswer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** FR6 - Lich su */

data class HistoryUiState(
    val isLoading: Boolean = true,
    val allItems: List<QuestionAnswer> = emptyList(),
    val items: List<QuestionAnswer> = emptyList(),
    val searchKeyword: String = "",
    val filterBookmarked: Boolean = false,
    val groupBySubject: Boolean = false,
    val errorMessage: String? = null,
    val hasError: Boolean = false
)

class HistoryViewModel(
    private val questionRepository: QuestionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState(isLoading = true))
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private val searchKeyword = MutableStateFlow("")
    private val filterBookmarked = MutableStateFlow(false)
    private val groupBySubject = MutableStateFlow(false)
    private var currentUserId: Long? = null
    private var loadJob: Job? = null

    fun load(userId: Long) {
        // Man hinh co the goi lai sau moi recomposition - chi giu mot collector
        // duy nhat cho cung mot user, tranh loop load/ghi state lien tuc.
        if (userId == currentUserId && loadJob?.isActive == true) return
        currentUserId = userId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = HistoryUiState(isLoading = true)
            try {
                questionRepository.observeHistory(userId).collect { list ->
                    val result = applyFiltersToList(list, searchKeyword.value.trim(), filterBookmarked.value)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        allItems = list,
                        items = result,
                        searchKeyword = searchKeyword.value,
                        filterBookmarked = filterBookmarked.value,
                        groupBySubject = groupBySubject.value,
                        errorMessage = null,
                        hasError = false
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    hasError = true,
                    errorMessage = e.message
                )
            }
        }
    }

    fun setSearch(keyword: String) {
        searchKeyword.value = keyword
        refreshFilters()
    }

    fun setFilterBookmarked(value: Boolean) {
        filterBookmarked.value = value
        refreshFilters()
    }

    fun setGroupBySubject(value: Boolean) {
        groupBySubject.value = value
        refreshFilters()
    }

    fun toggleBookmark(id: Long, value: Boolean) {
        viewModelScope.launch {
            runCatching { questionRepository.setBookmarked(id, value) }
        }
    }

    private fun refreshFilters() {
        val current = _state.value
        val result = applyFiltersToList(current.allItems, searchKeyword.value.trim(), filterBookmarked.value)
        _state.value = current.copy(
            items = result,
            searchKeyword = searchKeyword.value,
            filterBookmarked = filterBookmarked.value,
            groupBySubject = groupBySubject.value
        )
    }

    private fun applyFiltersToList(
        list: List<QuestionAnswer>,
        keyword: String,
        bookmarkedOnly: Boolean
    ): List<QuestionAnswer> {
        var result = list
        if (bookmarkedOnly) {
            result = result.filter { it.isBookmarked }
        }
        if (keyword.isNotEmpty()) {
            result = result.filter {
                it.questionText.contains(keyword, ignoreCase = true) ||
                    it.answerText.contains(keyword, ignoreCase = true)
            }
        }
        return result
    }
}
