package com.sharedshoppinglists.app.presentation.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharedshoppinglists.app.domain.model.EditingStatus
import com.sharedshoppinglists.app.domain.model.User
import com.sharedshoppinglists.app.domain.repository.AuthRepository
import com.sharedshoppinglists.app.domain.repository.SharedListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SharedListViewModel @Inject constructor(
    private val sharedListRepository: SharedListRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _activeListId = MutableStateFlow<String?>(null)

    /** Map of productId -> EditingStatus for the active list, excluding current user */
    val editingStatuses: StateFlow<Map<String, EditingStatus>> = _activeListId
        .flatMapLatest { listId ->
            if (listId != null) {
                sharedListRepository.observeEditingStatus(listId).map { statusMap ->
                    val userId = _currentUser.value?.id
                    statusMap.filter { (_, status) -> status.userId != userId && status.isEditing }
                }
            } else {
                flowOf(emptyMap())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _shareResult = MutableStateFlow<ShareResult>(ShareResult.Idle)
    val shareResult: StateFlow<ShareResult> = _shareResult.asStateFlow()

    private val _shareLink = MutableStateFlow<String?>(null)
    val shareLink: StateFlow<String?> = _shareLink.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.getCurrentUser().collect { user ->
                _currentUser.value = user
            }
        }
    }

    fun observeList(listId: String) {
        _activeListId.value = listId
    }

    fun clearObservation() {
        _activeListId.value = null
    }

    // --- Sharing ---

    fun shareByEmail(listId: String, email: String) {
        viewModelScope.launch {
            _shareResult.value = ShareResult.Loading
            sharedListRepository.shareList(listId, email)
                .onSuccess { _shareResult.value = ShareResult.Success }
                .onFailure { _shareResult.value = ShareResult.Error(it.message ?: "Error al compartir") }
        }
    }

    fun shareByLink(listId: String) {
        viewModelScope.launch {
            _shareResult.value = ShareResult.Loading
            sharedListRepository.shareListByLink(listId)
                .onSuccess { url ->
                    _shareLink.value = url
                    _shareResult.value = ShareResult.LinkGenerated(url)
                }
                .onFailure { _shareResult.value = ShareResult.Error(it.message ?: "Error al generar enlace") }
        }
    }

    fun resetShareResult() {
        _shareResult.value = ShareResult.Idle
        _shareLink.value = null
    }

    // --- Editing status ---

    fun setEditing(listId: String, productId: String, editing: Boolean) {
        val userId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            sharedListRepository.setEditingStatus(listId, productId, userId, editing)
        }
    }

    fun clearError() {
        _error.value = null
    }
}

sealed class ShareResult {
    data object Idle : ShareResult()
    data object Loading : ShareResult()
    data object Success : ShareResult()
    data class LinkGenerated(val url: String) : ShareResult()
    data class Error(val message: String) : ShareResult()
}
