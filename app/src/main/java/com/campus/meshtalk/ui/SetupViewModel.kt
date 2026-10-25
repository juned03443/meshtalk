package com.campus.meshtalk.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.campus.meshtalk.data.repository.IdentityRepository
import com.campus.meshtalk.data.repository.ProfileRepository
import com.campus.meshtalk.data.repository.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.KeyPair

enum class SetupRoute { IDENTITY, PROFILE }

data class SetupUiState(
    val route: SetupRoute = SetupRoute.IDENTITY,
    val meshTalkId: String = "",
    val displayName: String = "",
    val statusText: String = "",
    val avatarPath: String? = null,
    val nameError: String? = null
)

class SetupViewModel(
    private val keyPair: KeyPair,
    private val identityRepository: IdentityRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val id = identityRepository.getOrCreateId(keyPair)
            _uiState.value = _uiState.value.copy(meshTalkId = id)
            profileRepository.profile.collect { profile ->
                if (profile != null) _uiState.value = _uiState.value.copy(
                    meshTalkId = profile.id, displayName = profile.displayName,
                    statusText = profile.statusText, avatarPath = profile.avatarPath
                )
            }
        }
    }

    fun continueToProfile() { _uiState.value = _uiState.value.copy(route = SetupRoute.PROFILE) }
    fun updateName(value: String) { if (value.length <= 30) _uiState.value = _uiState.value.copy(displayName = value, nameError = null) }
    fun updateStatus(value: String) { if (value.length <= 80) _uiState.value = _uiState.value.copy(statusText = value) }
    fun updateAvatar(path: String?) { _uiState.value = _uiState.value.copy(avatarPath = path) }

    fun finish(onFinished: () -> Unit) {
        val name = _uiState.value.displayName.trim()
        if (name.isBlank()) { _uiState.value = _uiState.value.copy(nameError = "Display name is required"); return }
        viewModelScope.launch {
            profileRepository.save(UserProfile(_uiState.value.meshTalkId, name, _uiState.value.avatarPath, _uiState.value.statusText.trim(), System.currentTimeMillis()))
            onFinished()
        }
    }

    class Factory(
        private val context: Context,
        private val keyPair: KeyPair
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SetupViewModel(
            keyPair,
            IdentityRepository(context.applicationContext),
            ProfileRepository(context.applicationContext)
        ) as T
    }
}
