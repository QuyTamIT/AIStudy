package com.aistudy.aistudy.ui.notifications

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.aistudy.aistudy.ui.common.EmptyState
import com.aistudy.aistudy.ui.common.LoadingBox
import com.aistudy.aistudy.ui.notifications.NotificationsUiState
import com.aistudy.aistudy.ui.notifications.NotificationsViewModel
import com.aistudy.aistudy.ui.root.AppDependencies
import com.aistudy.aistudy.ui.label
import androidx.compose.ui.res.stringResource

@Composable
fun NotificationsScreen(
    deps: AppDependencies,
    onBack: () -> Unit,
    onOpenDeepLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: NotificationsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { NotificationsViewModel(deps.notificationRepository) }
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()

    val sessionUserId by deps.authRepository.sessionUserId.collectAsStateWithLifecycle(initialValue = null)
    androidx.compose.runtime.LaunchedEffect(sessionUserId) {
        sessionUserId?.let { vm.load(it) }
    }

    NotificationsContent(
        state = state,
        onBack = onBack,
        onMarkAllRead = { sessionUserId?.let { vm.markAllRead(it) } },
        onOpenNotification = { notification ->
            notification.deepLink?.let { onOpenDeepLink(it) }
            vm.markRead(notification.id)
        },
        modifier = modifier
    )
}

@Composable
private fun NotificationsContent(
    state: NotificationsUiState,
    onBack: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenNotification: (com.aistudy.aistudy.domain.model.entity.Notification) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingBox()
            state.hasError -> {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    com.aistudy.aistudy.ui.common.ErrorBanner(
                        message = state.errorMessage ?: "",
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onBack) {
                        Text(stringResource(R.string.notifications_back))
                    }
                }
            }
            state.notifications.isEmpty() -> EmptyState(
                titleRes = R.string.notifications_empty_title,
                bodyRes = R.string.notifications_empty_body,
                modifier = Modifier.fillMaxSize(),
                action = {
                    Button(onClick = onBack) {
                        Text(stringResource(R.string.notifications_back))
                    }
                }
            )
            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.notifications_unread_count, state.unreadCount),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = onMarkAllRead) {
                            Text(stringResource(R.string.notifications_mark_all_read))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    state.notifications.forEach { notification ->
                        val isUnread = !notification.isRead
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = notification.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = notification.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = notification.type.label(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(onClick = { onOpenNotification(notification) }) {
                                Text(stringResource(R.string.notifications_open))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.notifications_back))
                    }
                }
            }
        }
    }
}

