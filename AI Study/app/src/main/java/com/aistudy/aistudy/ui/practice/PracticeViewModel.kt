package com.aistudy.aistudy.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudy.aistudy.data.repository.GamificationRepository
import com.aistudy.aistudy.data.repository.QuestionRepository
import com.aistudy.aistudy.data.repository.QuizRepository
import com.aistudy.aistudy.domain.model.entity.QuestionAnswer
import com.aistudy.aistudy.domain.model.entity.QuizItem
import com.aistudy.aistudy.domain.model.enums.QuestionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** FR7 - Luyen tap. */

data class PracticeUiState(
    val isLoading: Boolean = false,
    val unanswered: List<QuizItem> = emptyList(),
    val currentItem: QuizItem? = null,
    val userAnswer: String = "",
    val submitted: Boolean = false,
    val progressIndex: Int = 0,
    val progressTotal: Int = 0,
    val correctCount: Int = 0,
    val isFinished: Boolean = false,
    val xpGained: Int = 0,
    val errorMessage: String? = null,
    val hasError: Boolean = false,
    val sources: List<QuestionAnswer> = emptyList(),
    val sourceId: Long? = null,
    val count: Int = DEFAULT_COUNT
) {
    val answeredCount: Int get() = progressIndex

    val accuracy: Int
        get() = if (progressIndex == 0) 0 else correctCount * 100 / progressIndex

    companion object {
        const val DEFAULT_COUNT = 5
    }
}

class PracticeViewModel(
    private val quizRepository: QuizRepository,
    private val questionRepository: QuestionRepository,
    private val gamificationRepository: GamificationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PracticeUiState())
    val state: StateFlow<PracticeUiState> = _state.asStateFlow()

    /**
     * `QuizRepository.answer()` cong XP ben trong roi bo qua `GamificationResult`,
     * nen UI khong doc duoc XP tu ham do. Lay `users.totalXp` luc bat dau phien
     * lam chu, hien thi hieu - khong tinh XP trong UI.
     */
    private var xpBaseline: Int = 0

    private var latestTotalXp: Int = 0

    fun load(userId: Long) {
        observeItems(userId)
        observeSources(userId)
        observeXp(userId)
    }

    private fun observeItems(userId: Long) {
        viewModelScope.launch {
            try {
                quizRepository.observeUnanswered(userId).collect { list ->
                    val current = _state.value
                    if (!current.isFinished && current.currentItem == null && list.isNotEmpty()) {
                        // Bat dau phien moi. startSession tu ghi _state nen phai
                        // return ngay, khong ghi de bang snapshot cu ben duoi.
                        startSession(list)
                    } else {
                        _state.value = current.copy(
                            isLoading = false,
                            // Giua phien luyen tap thi unanswered do nextQuestion
                            // quan ly - khong de emission tu DB ghi de lam lech.
                            unanswered = if (current.currentItem == null && !current.isFinished) {
                                list
                            } else {
                                current.unanswered
                            },
                            hasError = false,
                            errorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                reportError(e.message)
            }
        }
    }

    private fun observeSources(userId: Long) {
        viewModelScope.launch {
            try {
                questionRepository.observeHistory(userId).collect { history ->
                    _state.value = _state.value.copy(sources = history)
                }
            } catch (e: Exception) {
                reportError(e.message)
            }
        }
    }

    private fun observeXp(userId: Long) {
        viewModelScope.launch {
            try {
                gamificationRepository.observeUser(userId).collect { user ->
                    latestTotalXp = user?.totalXp ?: 0
                    _state.value = _state.value.copy(
                        xpGained = (latestTotalXp - xpBaseline).coerceAtLeast(0)
                    )
                }
            } catch (e: Exception) {
                reportError(e.message)
            }
        }
    }

    private fun startSession(items: List<QuizItem>) {
        xpBaseline = latestTotalXp
        _state.value = _state.value.copy(
            isLoading = false,
            hasError = false,
            errorMessage = null,
            currentItem = items.first(),
            unanswered = items.drop(1),
            userAnswer = "",
            submitted = false,
            correctCount = 0,
            progressIndex = 1,
            progressTotal = items.size,
            isFinished = false
        )
    }

    fun selectSource(id: Long?) {
        _state.value = _state.value.copy(sourceId = id)
    }

    fun setCount(count: Int) {
        _state.value = _state.value.copy(count = count.coerceIn(MIN_COUNT, MAX_COUNT))
    }

    fun setUserAnswer(text: String) {
        if (_state.value.submitted) return
        _state.value = _state.value.copy(userAnswer = text)
    }

    fun generatePractice(userId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, hasError = false, errorMessage = null)
            try {
                val result = quizRepository.generateFromQuestion(
                    userId,
                    _state.value.sourceId,
                    _state.value.count
                )
                if (result.isFailure) {
                    reportError(result.exceptionOrNull()?.message)
                    return@launch
                }
                // Phien moi se duoc observeItems dung len khi DB phat danh
                // sach - o day chi tat co loading de khong ket o spinner.
                _state.value = _state.value.copy(isLoading = false)
            } catch (e: Exception) {
                reportError(e.message)
            }
        }
    }

    fun submitAnswer() {
        val state = _state.value
        val item = state.currentItem ?: return
        if (state.submitted) return
        val answer = state.userAnswer.trim()
        if (answer.isEmpty()) return

        viewModelScope.launch {
            val isCorrect = checkAnswer(item, answer)
            try {
                quizRepository.answer(item.id, isCorrect)
            } catch (e: Exception) {
                reportError(e.message)
                return@launch
            }
            // Cau da cham khong con nam trong `observeUnanswered`, nhung van can
            // hien thi feedback cho nguoi dung truocc khi sang cau sau.
            _state.value = _state.value.copy(
                submitted = true,
                correctCount = _state.value.correctCount + if (isCorrect) 1 else 0
            )
        }
    }

    fun nextQuestion() {
        val state = _state.value
        val remaining = state.unanswered
        val next = remaining.firstOrNull()
        if (next == null) {
            _state.value = state.copy(
                currentItem = null,
                isFinished = true,
                submitted = false,
                userAnswer = ""
            )
        } else {
            _state.value = state.copy(
                currentItem = next,
                unanswered = remaining.drop(1),
                userAnswer = "",
                submitted = false,
                progressIndex = state.progressIndex + 1
            )
        }
    }

    fun restart() {
        _state.value = _state.value.copy(
            currentItem = null,
            isFinished = false,
            submitted = false,
            userAnswer = "",
            correctCount = 0,
            progressIndex = 0,
            progressTotal = 0
        )
    }

    private fun checkAnswer(item: QuizItem, answer: String): Boolean =
        answer.equals(item.correctAnswer, ignoreCase = true)

    private fun reportError(message: String?) {
        _state.value = _state.value.copy(
            isLoading = false,
            hasError = true,
            errorMessage = message
        )
    }

    private companion object {
        const val MIN_COUNT = 1
        const val MAX_COUNT = 10
    }
}

/** Chi so 0..3 de man hinh doi sang `practice_option_a..d`, khong hardcode "A". */
internal val QuizItem.answerOptions: List<Pair<Int, String>>
    get() = listOf(optionA, optionB, optionC, optionD)
        .mapIndexedNotNull { index, text -> text?.let { index to it } }