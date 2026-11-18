package com.aistudy.aistudy.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aistudy.aistudy.xp.UnlockCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val isLoading: Boolean = false,
    val profile: StudentProfileData? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isSaving: Boolean = false,
    val isSyncing: Boolean = false
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudentProfileRepository(application)
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
                    errorMessage = e.localizedMessage ?: "Không thể tải hồ sơ học sinh"
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
                    successMessage = "Cập nhật họ và tên thành công!"
                )
            } else {
                _uiState.value = current.copy(
                    isSaving = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Lỗi khi cập nhật họ và tên"
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
                        "Đã cập nhật ảnh đại diện từ thiết bị!"
                    } else {
                        "Đã đổi ảnh đại diện linh vật!"
                    }
                )
            } else {
                _uiState.value = current.copy(
                    isSaving = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Lỗi khi cập nhật ảnh đại diện"
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
                    successMessage = "Đã lưu tùy chọn học tập!"
                )
            } else {
                _uiState.value = current.copy(
                    isSaving = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Lỗi khi lưu tùy chọn"
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
                onResult(true, "Đổi mật khẩu thành công!")
            } else {
                val msg = result.exceptionOrNull()?.localizedMessage ?: "Không thể đổi mật khẩu"
                onResult(false, msg)
            }
        }
    }

    fun syncFirebase(userId: Long) {
        val current = _uiState.value ?: return
        _uiState.value = current.copy(isSyncing = true)
        viewModelScope.launch {
            val syncResult = withContext(Dispatchers.IO) {
                repository.syncFirebase(userId)
            }
            _uiState.value = current.copy(
                isSyncing = false,
                successMessage = if (syncResult.isSuccess) syncResult.message else null,
                errorMessage = if (!syncResult.isSuccess) syncResult.message else null
            )
        }
    }

    fun clearMessages() {
        val current = _uiState.value ?: return
        _uiState.value = current.copy(errorMessage = null, successMessage = null)
    }
}
