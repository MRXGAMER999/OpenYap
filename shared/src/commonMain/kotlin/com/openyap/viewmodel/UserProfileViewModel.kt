package com.openyap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openyap.model.UserProfile
import com.openyap.repository.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val profile: UserProfile = UserProfile(),
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
)

sealed interface UserProfileEvent {
    data class UpdateName(val name: String) : UserProfileEvent
    data class UpdateEmail(val email: String) : UserProfileEvent
    data class UpdatePhone(val phone: String) : UserProfileEvent
    data class UpdateJobTitle(val jobTitle: String) : UserProfileEvent
    data class UpdateCompany(val company: String) : UserProfileEvent
    data object Save : UserProfileEvent
    data object DismissSaveMessage : UserProfileEvent
}

class UserProfileViewModel(
    private val userProfileRepository: UserProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UserProfileUiState())
    val state: StateFlow<UserProfileUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val profile = userProfileRepository.loadProfile()
            _state.update { it.copy(profile = profile, isSaving = false, saveMessage = null) }
        }
    }

    fun onEvent(event: UserProfileEvent) {
        when (event) {
            is UserProfileEvent.UpdateName -> _state.update { it.copy(profile = it.profile.copy(name = event.name)) }
            is UserProfileEvent.UpdateEmail -> _state.update {
                it.copy(
                    profile = it.profile.copy(
                        email = event.email
                    )
                )
            }

            is UserProfileEvent.UpdatePhone -> _state.update {
                it.copy(
                    profile = it.profile.copy(
                        phone = event.phone
                    )
                )
            }

            is UserProfileEvent.UpdateJobTitle -> _state.update {
                it.copy(
                    profile = it.profile.copy(
                        jobTitle = event.jobTitle
                    )
                )
            }

            is UserProfileEvent.UpdateCompany -> _state.update {
                it.copy(
                    profile = it.profile.copy(
                        company = event.company
                    )
                )
            }

            is UserProfileEvent.Save -> saveProfile()
            is UserProfileEvent.DismissSaveMessage -> _state.update { it.copy(saveMessage = null) }
        }
    }

    private fun saveProfile() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            userProfileRepository.saveProfile(_state.value.profile)
            _state.update { it.copy(isSaving = false, saveMessage = "Profile saved") }
        }
    }
}
