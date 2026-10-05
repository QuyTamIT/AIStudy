package com.aistudy.aistudy.ui.screens.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aistudy.aistudy.R
import com.aistudy.aistudy.domain.model.enums.QuestionType
import com.aistudy.aistudy.ui.common.EmptyState
import com.aistudy.aistudy.ui.common.ErrorBanner
import com.aistudy.aistudy.ui.label
import com.aistudy.aistudy.ui.practice.PracticeUiState
import com.aistudy.aistudy.ui.practice.PracticeViewModel
import com.aistudy.aistudy.ui.practice.answerOptions
import com.aistudy.aistudy.ui.root.AppDependencies

@Composable
fun PracticeScreen(
    deps: AppDependencies,
    modifier: Modifier = Modifier
) {
    val viewModel: PracticeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PracticeViewModel(
                    quizRepository = deps.quizRepository,
                    questionRepository = deps.questionRepository,
                    gamificationRepository = deps.gamificationRepository
                )
            }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sessionUserId by deps.authRepository.sessionUserId
        .collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(sessionUserId) {
        sessionUserId?.let(viewModel::load)
    }

    PracticeContent(
        state = state,
        onGenerate = { sessionUserId?.let { viewModel.generatePractice(it) } },
        onSourceSelected = viewModel::selectSource,
        onCountSelected = viewModel::setCount,
        onAnswerChange = viewModel::setUserAnswer,
        onSubmit = viewModel::submitAnswer,
        onNext = viewModel::nextQuestion,
        onRestart = viewModel::restart,
        modifier = modifier
    )
}

@Composable
private fun PracticeContent(
    state: PracticeUiState,
    onGenerate: () -> Unit,
    onSourceSelected: (Long?) -> Unit,
    onCountSelected: (Int) -> Unit,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.practice_generating),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.hasError -> ErrorBanner(
                message = state.errorMessage?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.common_error_generic),
                onRetry = onGenerate
            )

            state.isFinished -> ResultState(state = state, onRestart = onRestart)

            state.currentItem != null -> QuestionState(
                state = state,
                onAnswerChange = onAnswerChange,
                onSubmit = onSubmit,
                onNext = onNext
            )

            else -> GenerateState(
                state = state,
                onGenerate = onGenerate,
                onSourceSelected = onSourceSelected,
                onCountSelected = onCountSelected
            )
        }
    }
}

@Composable
private fun ResultState(state: PracticeUiState, onRestart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.practice_result_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(
                        R.string.practice_result_score,
                        state.correctCount,
                        state.progressTotal
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.practice_result_accuracy, state.accuracy),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.practice_result_xp, state.xpGained),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRestart) {
            Text(stringResource(R.string.practice_result_done))
        }
    }
}

@Composable
private fun QuestionState(
    state: PracticeUiState,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit
) {
    val item = state.currentItem ?: return
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.practice_progress, state.progressIndex, state.progressTotal),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = {
                state.progressIndex.toFloat() / state.progressTotal.coerceAtLeast(1).toFloat()
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = item.questionType.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(text = item.prompt, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(16.dp))

        val options = item.answerOptions
        if (item.questionType == QuestionType.MULTIPLE_CHOICE && options.isNotEmpty()) {
            options.forEach { (index, optionText) ->
                val selected = state.userAnswer.trim().equals(optionText, ignoreCase = true)
                Card(
                    onClick = { if (!state.submitted) onAnswerChange(optionText) },
                    enabled = !state.submitted,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${stringResource(OPTION_LETTER_RES[index])}. $optionText",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = state.userAnswer,
                onValueChange = onAnswerChange,
                enabled = !state.submitted,
                label = { Text(stringResource(R.string.practice_your_answer)) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(16.dp))

        if (!state.submitted) {
            Button(
                onClick = onSubmit,
                enabled = state.userAnswer.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.practice_submit_answer))
            }
            if (state.userAnswer.isBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.practice_answer_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            FeedbackCard(
                isCorrect = state.userAnswer.trim().equals(item.correctAnswer, ignoreCase = true),
                correctAnswer = item.correctAnswer,
                explanation = item.explanation
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.practice_next))
            }
        }
    }
}

@Composable
private fun FeedbackCard(
    isCorrect: Boolean,
    correctAnswer: String,
    explanation: String?
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCorrect) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(
                    if (isCorrect) R.string.practice_correct else R.string.practice_incorrect
                ),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.practice_correct_answer, correctAnswer),
                style = MaterialTheme.typography.bodyMedium
            )
            if (!explanation.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.practice_explanation),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(text = explanation, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenerateState(
    state: PracticeUiState,
    onGenerate: () -> Unit,
    onSourceSelected: (Long?) -> Unit,
    onCountSelected: (Int) -> Unit
) {
    if (state.sources.isEmpty()) {
        EmptyState(
            titleRes = R.string.practice_empty_title,
            bodyRes = R.string.practice_pick_source_empty
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.practice_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.practice_generate_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SourcePicker(state = state, onSourceSelected = onSourceSelected)

                Spacer(Modifier.height(16.dp))

                CountPicker(state = state, onCountSelected = onCountSelected)
            }
        }

        Spacer(Modifier.height(28.dp))

        Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.practice_generate))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcePicker(
    state: PracticeUiState,
    onSourceSelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.sources.firstOrNull { it.id == state.sourceId }
    val placeholder = stringResource(R.string.practice_pick_source)

    Column {
        Text(
            text = stringResource(R.string.practice_pick_source),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selected?.questionText?.take(MAX_SOURCE_LABEL) ?: placeholder,
                    maxLines = 1
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(placeholder, maxLines = 1) },
                    onClick = {
                        onSourceSelected(null)
                        expanded = false
                    }
                )
                state.sources.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(source.questionText.take(MAX_SOURCE_LABEL), maxLines = 1) },
                        onClick = {
                            onSourceSelected(source.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CountPicker(state: PracticeUiState, onCountSelected: (Int) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.practice_count_label),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            COUNT_OPTIONS.forEach { option ->
                val selected = option == state.count
                OutlinedButton(onClick = { onCountSelected(option) }) {
                    Text(
                        text = option.toString(),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

private val OPTION_LETTER_RES = listOf(
    R.string.practice_option_a,
    R.string.practice_option_b,
    R.string.practice_option_c,
    R.string.practice_option_d
)

private const val MAX_SOURCE_LABEL = 60
private val COUNT_OPTIONS = listOf(3, 5, 10)