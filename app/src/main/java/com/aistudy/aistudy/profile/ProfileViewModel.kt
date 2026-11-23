package com.aistudy.aistudy.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aistudy.aistudy.R
import com.aistudy.aistudy.xp.UnlockCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val isLoading: Boolean = false,
    val profile: StudentProfileData? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isSaving: Boolean = false
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudentProfileRepository(application)
    private val appContext = application.applicationContext
    private val _uiState = MutableLiveData(ProfileUiState(isLoading = true))
    val uiState: LiveData<ProfileUiState> = _uiState

    fun loadProfile(userId: Long) {
        _uiState.value = _uiState.value?.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    repository.getProfile(userId)
                }
                _uiState.value = ProfileUiState(
                    isLoading = false,
                    profile = data,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = ProfileUiState(
                    isLoading = false,
                    profile = null,
                    errorMessage = e.localizedMessage ?: appContext.getString(R.string.profile_error_load)
                )
            }
        }
    }

    fun updateName(userId: Long, newName: String) {
        val current = _uiState.value ?: return
        _uiState.value = current.copy(isSaving = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.updateName(userId, newName)
            }
            if (result.isSuccess) {
                val updated = withContext(Dispatchers.IO) {
                    repository.getProfile(userId)
                }
                _uiState.value = current.copy(
                    isSaving = false,
                    profile = updated,
                    successMessage = appContext.getString(R.string.profile_name_saved_success)
                )
            } else {
                _uiState.value = current.copy(
                    isSaving = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: appContext.getString(R.string.profile_name_save_error)
                )
            }
        }
    }

    fun updateAvatar(userId: Long, avatarId: String) {
        val current = _uiState.value ?: return
        _uiState.value = current.copy(isSaving = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.updateAvatar(userId, avatarId)
            }
            if (result.isSuccess) {
                val updated = withContext(Dispatchers.IO) {
                    repository.getProfile(userId)
                }
                _uiState.value = current.copy(
                    isSaving = false,
                    profile = updated,
                    successMessage = if (UnlockCatalog.isCustomAvatar(avatarId)) {
                        appContext.getString(R.string.profile_avatar_device_saved)
                    } else {
                        appContext.getString(R.string.profile_avatar_saved)
                    }
                )
            } else {
                _uiState.value = current.copy(
                    isSaving = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: appContext.getString(R.string.profile_avatar_save_error)
                )
            }
        }
    }

    fun updatePreferences(
        userId: Long,
        gradeLevel: String,
        learningStyle: String,
        favoriteSubjects: List<String>
    ) {
        val current = _uiState.value ?: return
        _uiState.value = current.copy(isSaving = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.updatePreferences(userId, gradeLevel, learningStyle, favoriteSubjects)
            }
            if (result.isSuccess) {
                val updated = withContext(Dispatchers.IO) {
                    repository.getProfile(userId)
                }
                _uiState.value = current.copy(
                    isSaving = false,
                    profile = updated,
                    successMessage = appContext.getString(R.string.profile_preferences_saved)
                )
            } else {
                _uiState.value = current.copy(
                    isSaving = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: appContext.getString(R.string.profile_preferences_save_error)
                )
            }
        }
    }

    fun changePassword(
        userId: Long,
        oldPass: String,
        newPass: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.changePassword(userId, oldPass, newPass)
            }
            if (result.isSuccess) {
                onResult(true, appContext.getString(R.string.profile_password_changed))
            } else {
                val msg = result.exceptionOrNull()?.localizedMessage
                    ?: appContext.getString(R.string.profile_password_change_error)
                onResult(false, msg)
            }
        }
    }

    fun clearMessages() {
        val current = _uiState.value ?: return
        _uiState.value = current.copy(errorMessage = null, successMessage = null)
    }
}
