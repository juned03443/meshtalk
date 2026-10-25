package com.campus.meshtalk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.campus.meshtalk.data.local.entity.ConnectionRequestEntity
import com.campus.meshtalk.data.local.entity.ContactEntity
import com.campus.meshtalk.data.local.entity.RequestDirection
import com.campus.meshtalk.data.repository.ContactRepository
import com.campus.meshtalk.data.repository.SendRequestResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactsUiState(
    val isLoading: Boolean = false,
    val meshIdInput: String = "",
    val formattedMeshId: String = "",
    val validationError: String? = null,
    val isValidFormat: Boolean = false
)

sealed class ContactsEvent {
    data class ShowSnackbar(val message: String) : ContactsEvent()
    data object RequestSent : ContactsEvent()
    data object RequestAccepted : ContactsEvent()
    data object AutoAccepted : ContactsEvent()
}

class ContactsViewModel(
    private val repository: ContactRepository,
    val myMeshId: String,
    private val meshRouter: com.campus.meshtalk.mesh.MeshRouter? = null,
    private val nearbyPeopleRepo: com.campus.meshtalk.data.repository.NearbyPeopleRepository? = null
) : ViewModel() {

    val nearbyPeople: StateFlow<List<com.campus.meshtalk.data.repository.NearbyPerson>> =
        nearbyPeopleRepo?.nearbyPeople ?: MutableStateFlow(emptyList())

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ContactsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ContactsEvent> = _events.asSharedFlow()

    val contacts: StateFlow<List<ContactEntity>> = repository.getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomingRequests: StateFlow<List<ConnectionRequestEntity>> =
        repository.getIncomingPendingRequests()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outgoingRequests: StateFlow<List<ConnectionRequestEntity>> =
        repository.getOutgoingPendingRequests()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomingBadgeCount: StateFlow<Int> = repository.getIncomingPendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Auto-format raw input to MT-XXXX-XXXX pattern.
     * Strips non-alphanumerics, uppercases, inserts dashes.
     */
    fun onMeshIdInputChanged(raw: String) {
        // Strip everything except alphanumerics
        val stripped = raw.uppercase().replace(Regex("[^A-Z0-9]"), "")

        // Remove "MT" prefix if user typed it (we'll add it back)
        val digits = if (stripped.startsWith("MT")) stripped.removePrefix("MT") else stripped

        // Limit to 8 characters max (the two groups of 4)
        val limited = digits.take(8)

        // Build formatted string
        val formatted = buildString {
            append("MT-")
            append(limited.take(4))
            if (limited.length > 4) {
                append("-")
                append(limited.substring(4))
            }
        }

        val isComplete = limited.length == 8
        val validationError = when {
            limited.isEmpty() -> null
            !isComplete -> "Complete the ID: MT-XXXX-XXXX"
            else -> null
        }

        _uiState.value = _uiState.value.copy(
            meshIdInput = raw,
            formattedMeshId = formatted,
            isValidFormat = isComplete,
            validationError = validationError
        )
    }

    fun sendRequest() {
        val targetId = _uiState.value.formattedMeshId
        if (!_uiState.value.isValidFormat) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            when (val result = repository.sendConnectionRequest(targetId, meshRouter)) {
                is SendRequestResult.Success -> {
                    _events.emit(ContactsEvent.RequestSent)
                    _events.emit(ContactsEvent.ShowSnackbar("Connection request sent to $targetId"))
                    // Clear input
                    _uiState.value = ContactsUiState()
                }
                is SendRequestResult.OwnId -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        validationError = "You cannot add your own ID"
                    )
                }
                is SendRequestResult.AlreadyContact -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        validationError = "This user is already in your contacts"
                    )
                }
                is SendRequestResult.AlreadyPending -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        validationError = "A pending request already exists for this ID"
                    )
                }
                is SendRequestResult.AutoAccepted -> {
                    _events.emit(ContactsEvent.AutoAccepted)
                    _events.emit(ContactsEvent.ShowSnackbar("Mutual request detected — contact added!"))
                    _uiState.value = ContactsUiState()
                }
                is SendRequestResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        validationError = result.message
                    )
                }
            }
        }
    }

    fun sendRequestToNearbyPerson(person: com.campus.meshtalk.data.repository.NearbyPerson) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = nearbyPeopleRepo?.sendRequestToPerson(person) ?: repository.sendConnectionRequest(person.meshId)) {
                is SendRequestResult.Success -> {
                    _events.emit(ContactsEvent.RequestSent)
                    _events.emit(ContactsEvent.ShowSnackbar("Connection request sent to ${person.displayName}"))
                    _uiState.value = ContactsUiState()
                }
                is SendRequestResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, validationError = result.message)
                    _events.emit(ContactsEvent.ShowSnackbar(result.message))
                }
                is SendRequestResult.AlreadyContact -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.emit(ContactsEvent.ShowSnackbar("${person.displayName} is already a contact"))
                }
                is SendRequestResult.AlreadyPending -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.emit(ContactsEvent.ShowSnackbar("Request already pending for ${person.displayName}"))
                }
                is SendRequestResult.AutoAccepted -> {
                    _events.emit(ContactsEvent.AutoAccepted)
                    _events.emit(ContactsEvent.ShowSnackbar("Mutual request detected — contact added!"))
                    _uiState.value = ContactsUiState()
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    fun acceptRequest(requestId: String) {
        viewModelScope.launch {
            val success = repository.acceptRequest(requestId)
            if (success) {
                _events.emit(ContactsEvent.RequestAccepted)
                _events.emit(ContactsEvent.ShowSnackbar("Contact added!"))
            }
        }
    }

    fun deleteRequest(requestId: String) {
        viewModelScope.launch {
            repository.declineRequest(requestId)
            _events.emit(ContactsEvent.ShowSnackbar("Request declined"))
        }
    }

    fun cancelRequest(requestId: String) {
        viewModelScope.launch {
            repository.cancelRequest(requestId)
            _events.emit(ContactsEvent.ShowSnackbar("Request cancelled"))
        }
    }

    class Factory(
        private val repository: ContactRepository,
        private val myMeshId: String,
        private val meshRouter: com.campus.meshtalk.mesh.MeshRouter? = null,
        private val nearbyPeopleRepo: com.campus.meshtalk.data.repository.NearbyPeopleRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ContactsViewModel(repository, myMeshId, meshRouter, nearbyPeopleRepo) as T
        }
    }
}
