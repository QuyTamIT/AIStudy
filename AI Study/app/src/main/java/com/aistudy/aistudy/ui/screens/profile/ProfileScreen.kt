package com.aistudy.aistudy.ui.screens.profile

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.aistudy.aistudy.R
import com.aistudy.aistudy.ui.common.ConfirmDialog
import com.aistudy.aistudy.ui.common.EmptyState
import com.aistudy.aistudy.ui.common.LoadingBox
import com.aistudy.aistudy.ui.profile.ProfileUiState
import com.aistudy.aistudy.ui.profile.ProfileViewModel
import com.aistudy.aistudy.ui.root.AppDependencies
import com.aistudy.aistudy.ui.label
import androidx.compose.ui.res.stringResource

@Composable
fun ProfileScreen(
    deps: AppDependencies,
    onEditPreferences: () -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: ProfileViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ProfileViewModel(
                    authRepository = deps.authRepository,
                    profileRepository = deps.profileRepository,
                    gamificationRepository = deps.gamificationRepository
                )
            }
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()

    val sessionUserId by deps.authRepository.sessionUserId.collectAsStateWithLifecycle(initialValue = null)
    androidx.compose.runtime.LaunchedEffect(sessionUserId) {
        sessionUserId?.let { vm.load(it) }
    }

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ProfileContent(
        state = state,
        onEditPreferences = onEditPreferences,
        onOpenNotifications = onOpenNotifications,
        onLogoutRequested = { showLogoutConfirm = true },
        onDeleteRequested = { showDeleteConfirm = true },
        modifier = modifier
    )

    if (showLogoutConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.auth_logout_confirm_title),
            message = stringResource(R.string.auth_logout_confirm_body),
            confirmLabelRes = R.string.auth_logout,
            onConfirm = {
                showLogoutConfirm = false
                vm.logout()
            },
            onDismiss = { showLogoutConfirm = false }
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.privacy_delete_confirm_title),
            message = stringResource(R.string.privacy_delete_confirm_body),
            confirmLabelRes = R.string.common_delete,
            onConfirm = {
                showDeleteConfirm = false
                vm.deleteAccount()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onEditPreferences: () -> Unit,
    onOpenNotifications: () -> Unit,
    onLogoutRequested: () -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingBox()
            state.hasError -> {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    com.aistudy.aistudy.ui.common.ErrorBanner(
                        message = state.errorMessage?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.common_error_generic),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onEditPreferences) {
                        Text(stringResource(R.string.profile_edit_preferences))
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp)
                ) {
                    Text(
                        text = state.displayName,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.profile_level_label) + ": " + state.level,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.gamification_xp_total, state.totalXp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.gamification_streak, state.streak),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Column {
                            Button(onClick = onEditPreferences) {
                                Text(stringResource(R.string.profile_edit_preferences))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.gamification_badges_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.badges.isNotEmpty()) {
                        state.badges.forEach { badge ->
                            Text(
                                text = badge.badgeCode.label(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.gamification_badges_empty),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = onOpenNotifications) {
                            Text(stringResource(R.string.profile_open_notifications))
                        }
                        Button(onClick = onLogoutRequested) {
                            Text(stringResource(R.string.auth_logout))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.privacy_section_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.privacy_local_only_title),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.privacy_local_only_body),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onDeleteRequested) {
                        Text(stringResource(R.string.auth_delete_account))
                    }
                }
            }
        }
    }
}

