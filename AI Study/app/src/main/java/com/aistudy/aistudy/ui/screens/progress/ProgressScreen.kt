package com.aistudy.aistudy.ui.screens.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.aistudy.aistudy.R
import com.aistudy.aistudy.domain.model.enums.Subject
import com.aistudy.aistudy.ui.common.EmptyState
import com.aistudy.aistudy.ui.common.LoadingBox
import com.aistudy.aistudy.ui.progress.ProgressUiState
import com.aistudy.aistudy.ui.progress.ProgressViewModel
import com.aistudy.aistudy.ui.root.AppDependencies
import com.aistudy.aistudy.ui.label
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun ProgressScreen(
    deps: AppDependencies,
    modifier: Modifier = Modifier
) {
    val vm: ProgressViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ProgressViewModel(deps.progressRepository) }
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()

    // Load on first composition; observe session for userId
    val sessionUserId by deps.authRepository.sessionUserId.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(sessionUserId) {
        sessionUserId?.let { vm.load(it) }
    }

    ProgressContent(state = state, modifier = modifier)
}

@Composable
private fun ProgressContent(
    state: ProgressUiState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingBox()
            state.hasError -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    com.aistudy.aistudy.ui.common.ErrorBanner(
                        message = state.errorMessage?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.common_error_generic),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }
            state.summary != null -> {
                val summary = state.summary
                val total = summary.totalQuestions
                if (total == 0) {
                    EmptyState(
                        titleRes = R.string.progress_empty_title,
                        bodyRes = R.string.progress_empty_body,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        // Card: questions total
                        TextRow(
                            label = R.string.progress_questions_total,
                            value = total.toString()
                        )

                        // Review time
                        val reviewMin = summary.reviewMinutes
                        TextRow(
                            label = R.string.progress_review_time,
                            value = stringResource(R.string.progress_review_time_value, reviewMin)
                        )

                        // Quiz accuracy
                        val accuracy = (summary.quizAccuracy * 100).toInt()
                        TextRow(
                            label = R.string.progress_quiz_accuracy,
                            value = stringResource(R.string.progress_quiz_accuracy_value, accuracy)
                        )

                        // Quiz progress
                        val answered = summary.quizAnswered
                        val totalQuiz = summary.quizTotal
                        TextRow(
                            label = R.string.progress_quiz_progress,
                            value = stringResource(R.string.progress_quiz_progress, answered, totalQuiz)
                        )

                        // XP week
                        val xpWeek = summary.xpThisWeek
                        TextRow(
                            label = R.string.progress_xp_week,
                            value = stringResource(R.string.progress_xp_week_value, xpWeek)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Breakdown by subject
                        if (summary.questionsBySubject.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                androidx.compose.material3.Text(
                                    text = stringResource(R.string.progress_by_subject),
                                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                summary.questionsBySubject.forEach { (subject, count) ->
                                    SubjectRow(subject = subject, count = count)
                                }
                            }
                        } else {
                            EmptyState(
                                titleRes = R.string.progress_by_subject_empty,
                                bodyRes = R.string.progress_by_subject_empty,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            else -> EmptyState(
                titleRes = R.string.progress_empty_title,
                bodyRes = R.string.progress_empty_body,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun TextRow(
    @androidx.annotation.StringRes label: Int,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Text(
            text = stringResource(label),
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
        androidx.compose.material3.Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SubjectRow(
    subject: Subject,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Text(
            text = subject.label(),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Bar visualization: small box with width proportional to count
            val maxCount = 100 // arbitrary scale for visualization
            val ratio = (count.toFloat() / maxCount).coerceAtMost(1f)
            Box(
                modifier = Modifier
                    .width((48.dp * ratio).coerceAtLeast(8.dp))
                    .height(8.dp)
                    .padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp, end = 2.dp)
                            .background(
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
            androidx.compose.material3.Text(
                text = stringResource(R.string.progress_subject_count, count),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }
    }
}

