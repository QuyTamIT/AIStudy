package com.aistudy.aistudy.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aistudy.aistudy.R
import com.aistudy.aistudy.data.repository.AuthRepository
import com.aistudy.aistudy.data.repository.ProfileRepository
import com.aistudy.aistudy.domain.model.enums.EducationLevel
import com.aistudy.aistudy.domain.model.enums.ExplanationStyle
import com.aistudy.aistudy.domain.model.enums.Subject
import com.aistudy.aistudy.ui.common.ErrorBanner
import com.aistudy.aistudy.ui.common.SubjectChipRow
import com.aistudy.aistudy.ui.label
import com.aistudy.aistudy.ui.root.AppDependencies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val currentStep: Int = 1,
    val selectedLevel: EducationLevel = EducationLevel.HIGH,
    val selectedSubjects: Set<Subject> = setOf(Subject.MATHEMATICS),
    val selectedStyle: ExplanationStyle = ExplanationStyle.STEP_BY_STEP,
    val isLoading: Boolean = false,
    val errorMessageRes: Int? = null
)

class OnboardingViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val editMode: Boolean
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        loadExistingProfile()
    }

    private fun loadExistingProfile() {
        val userId = authRepository.sessionUserId.value ?: return
        viewModelScope.launch {
            val profile = profileRepository.getProfile(userId)
            if (profile != null) {
                _state.update { s ->
                    s.copy(
                        selectedLevel = profile.educationLevel,
                        selectedSubjects = if (profile.subjectCodes.isNotEmpty()) profile.subjectCodes else setOf(Subject.MATHEMATICS),
                        selectedStyle = profile.preferredStyle
                    )
                }
            }
        }
    }

    fun selectLevel(level: EducationLevel) {
        _state.update { it.copy(selectedLevel = level) }
    }

    fun toggleSubject(subject: Subject) {
        _state.update { s ->
            val updated = s.selectedSubjects.toMutableSet()
            if (updated.contains(subject)) {
                updated.remove(subject)
            } else {
                updated.add(subject)
            }
            s.copy(selectedSubjects = updated, errorMessageRes = null)
        }
    }

    fun selectStyle(style: ExplanationStyle) {
        _state.update { it.copy(selectedStyle = style) }
    }

    fun nextStep(onFinish: () -> Unit) {
        val s = _state.value
        when (s.currentStep) {
            1 -> {
                _state.update { it.copy(currentStep = 2) }
            }
            2 -> {
                if (s.selectedSubjects.isEmpty()) {
                    _state.update { it.copy(errorMessageRes = R.string.onboarding_subjects_error) }
                } else {
                    _state.update { it.copy(currentStep = 3, errorMessageRes = null) }
                }
            }
            3 -> {
                submitOnboarding(onFinish)
            }
        }
    }

    fun previousStep() {
        val s = _state.value
        if (s.currentStep > 1) {
            _state.update { it.copy(currentStep = s.currentStep - 1, errorMessageRes = null) }
        }
    }

    private fun submitOnboarding(onFinish: () -> Unit) {
        val userId = authRepository.sessionUserId.value ?: return
        val s = _state.value
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            runCatching {
                profileRepository.completeOnboarding(
                    userId = userId,
                    level = s.selectedLevel,
                    subjects = s.selectedSubjects,
                    style = s.selectedStyle
                )
            }.onSuccess {
                _state.update { st -> st.copy(isLoading = false) }
                onFinish()
            }.onFailure {
                _state.update { st -> st.copy(isLoading = false) }
                onFinish()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    deps: AppDependencies,
    onBack: (() -> Unit)?,
    editMode: Boolean,
    modifier: Modifier = Modifier
) {
    val vm: OnboardingViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                OnboardingViewModel(
                    profileRepository = deps.profileRepository,
                    authRepository = deps.authRepository,
                    editMode = editMode
                )
            }
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            R.string.onboarding_step_of,
                            state.currentStep,
                            3
                        )
                    )
                },
                navigationIcon = {
                    if (state.currentStep > 1 || onBack != null) {
                        IconButton(onClick = {
                            if (state.currentStep > 1) {
                                vm.previousStep()
                            } else {
                                onBack?.invoke()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                when (state.currentStep) {
                    1 -> LevelStepContent(
                        selected = state.selectedLevel,
                        onSelect = vm::selectLevel
                    )
                    2 -> SubjectStepContent(
                        selected = state.selectedSubjects,
                        onToggle = vm::toggleSubject,
                        errorMessageRes = state.errorMessageRes
                    )
                    3 -> StyleStepContent(
                        selected = state.selectedStyle,
                        onSelect = vm::selectStyle
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.currentStep > 1 || onBack != null) {
                    OutlinedButton(
                        onClick = {
                            if (state.currentStep > 1) {
                                vm.previousStep()
                            } else {
                                onBack?.invoke()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text(stringResource(R.string.common_back))
                    }
                }

                Button(
                    onClick = { vm.nextStep(onFinish = { onBack?.invoke() }) },
                    enabled = !state.isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(
                            text = if (state.currentStep == 3) {
                                if (editMode) stringResource(R.string.common_save) else stringResource(R.string.common_done)
                            } else {
                                stringResource(R.string.common_next)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelStepContent(
    selected: EducationLevel,
    onSelect: (EducationLevel) -> Unit
) {
    Text(
        text = stringResource(R.string.onboarding_title_level),
        style = MaterialTheme.typography.titleLarge
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_subtitle_level),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(24.dp))

    EducationLevel.values().forEach { level ->
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (selected == level) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable { onSelect(level) }
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (selected == level),
                    onClick = { onSelect(level) }
                )
                Text(
                    text = level.label(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SubjectStepContent(
    selected: Set<Subject>,
    onToggle: (Subject) -> Unit,
    errorMessageRes: Int?
) {
    Text(
        text = stringResource(R.string.onboarding_title_subjects),
        style = MaterialTheme.typography.titleLarge
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_subtitle_subjects),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))

    if (errorMessageRes != null) {
        ErrorBanner(
            message = stringResource(errorMessageRes),
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    SubjectChipRow(
        subjects = Subject.values().toList(),
        selected = selected,
        onToggle = onToggle
    )

    Spacer(modifier = Modifier.height(16.dp))

    Subject.values().forEach { subject ->
        val isSelected = subject in selected
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onToggle(subject) }
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subject.label(),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun StyleStepContent(
    selected: ExplanationStyle,
    onSelect: (ExplanationStyle) -> Unit
) {
    Text(
        text = stringResource(R.string.onboarding_title_style),
        style = MaterialTheme.typography.titleLarge
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_subtitle_style),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(24.dp))

    ExplanationStyle.values().forEach { style ->
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (selected == style) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable { onSelect(style) }
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (selected == style),
                    onClick = { onSelect(style) }
                )
                Text(
                    text = style.label(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}