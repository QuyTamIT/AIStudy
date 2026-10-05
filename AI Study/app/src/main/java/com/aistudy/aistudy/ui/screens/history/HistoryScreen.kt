package com.aistudy.aistudy.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.aistudy.aistudy.R
import com.aistudy.aistudy.domain.model.entity.QuestionAnswer
import com.aistudy.aistudy.ui.common.EmptyState
import com.aistudy.aistudy.ui.common.LoadingBox
import com.aistudy.aistudy.ui.history.HistoryUiState
import com.aistudy.aistudy.ui.history.HistoryViewModel
import androidx.compose.ui.res.stringResource
import com.aistudy.aistudy.ui.root.AppDependencies
import com.aistudy.aistudy.ui.label

@Composable
fun HistoryScreen(
    deps: AppDependencies,
    onOpenQuestion: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HistoryViewModel(deps.questionRepository) }
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()

    val sessionUserId by deps.authRepository.sessionUserId.collectAsStateWithLifecycle(initialValue = null)
    androidx.compose.runtime.LaunchedEffect(sessionUserId) {
        sessionUserId?.let { vm.load(it) }
    }

    HistoryContent(
        state = state,
        onOpenQuestion = onOpenQuestion,
        onSearch = vm::setSearch,
        onToggleFilter = vm::setFilterBookmarked,
        onToggleGroup = vm::setGroupBySubject,
        onToggleBookmark = { id, value -> vm.toggleBookmark(id, value) },
        modifier = modifier
    )
}

@Composable
private fun HistoryContent(
    state: HistoryUiState,
    onOpenQuestion: (Long) -> Unit,
    onSearch: (String) -> Unit,
    onToggleFilter: (Boolean) -> Unit,
    onToggleGroup: (Boolean) -> Unit,
    onToggleBookmark: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Search field
        OutlinedTextField(
            value = state.searchKeyword,
            onValueChange = onSearch,
            label = { Text(stringResource(R.string.history_search_hint)) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Filter chips
        Row(modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = !state.filterBookmarked,
                onClick = { onToggleFilter(false) },
                label = { Text(stringResource(R.string.history_filter_all)) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = state.filterBookmarked,
                onClick = { onToggleFilter(true) },
                label = { Text(stringResource(R.string.history_filter_bookmarked)) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Group toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.groupBySubject,
                onClick = { onToggleGroup(!state.groupBySubject) },
                label = { Text(stringResource(R.string.history_group_by_subject)) }
            )
            if (state.items.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.history_result_count, state.items.size),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            state.isLoading -> LoadingBox(modifier = Modifier.fillMaxSize())
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
            state.items.isEmpty() -> {
                val emptyTitle = if (state.filterBookmarked) R.string.history_empty_bookmark_title else R.string.history_empty_title
                val emptyBody = if (state.filterBookmarked) R.string.history_empty_bookmark_body else (if (state.searchKeyword.isNotEmpty()) R.string.history_empty_search_body else R.string.history_empty_body)
                EmptyState(
                    titleRes = emptyTitle,
                    bodyRes = emptyBody,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val list = if (state.groupBySubject) {
                    state.items.groupBy { it.subjectCode }.map { (subject, items) ->
                        SubjectGroup(subject, items)
                    }
                } else {
                    state.items.map { item -> SubjectGroup(null, listOf(item)) }
                }
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    list.forEach { group ->
                        if (group.subject != null) {
                            Text(
                                text = group.subject.label(),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        group.items.forEach { item ->
                            HistoryRow(
                                item = item,
                                onOpenQuestion = { onOpenQuestion(item.id) },
                                onToggleBookmark = { value -> onToggleBookmark(item.id, value) }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

private data class SubjectGroup(
    val subject: com.aistudy.aistudy.domain.model.enums.Subject?,
    val items: List<QuestionAnswer>
)

@Composable
private fun HistoryRow(
    item: QuestionAnswer,
    onOpenQuestion: () -> Unit,
    onToggleBookmark: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.questionText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.subjectCode.label(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            androidx.compose.material3.TextButton(onClick = { onToggleBookmark(!item.isBookmarked) }) {
                Text(
                    text = if (item.isBookmarked) stringResource(R.string.history_bookmark_remove) else stringResource(R.string.history_bookmark_add),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

