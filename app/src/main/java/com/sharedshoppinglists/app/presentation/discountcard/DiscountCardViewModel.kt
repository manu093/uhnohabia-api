package com.sharedshoppinglists.app.presentation.discountcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharedshoppinglists.app.domain.model.DiscountCard
import com.sharedshoppinglists.app.domain.repository.AuthRepository
import com.sharedshoppinglists.app.domain.repository.DiscountCardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiscountCardViewModel @Inject constructor(
    private val discountCardRepository: DiscountCardRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _currentUserId = MutableStateFlow<String?>(null)

    val cards: StateFlow<List<DiscountCard>> = _currentUserId
        .flatMapLatest { userId ->
            if (userId != null) discountCardRepository.getCards(userId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.getCurrentUser().collect { user ->
                _currentUserId.value = user?.id
            }
        }
    }

    fun addCard(card: DiscountCard) {
        val userId = _currentUserId.value ?: return
        val cardWithUser = card.copy(userId = userId)
        viewModelScope.launch {
            discountCardRepository.addCard(cardWithUser)
                .onFailure { _error.value = it.message }
        }
    }

    fun updateCard(card: DiscountCard) {
        viewModelScope.launch {
            discountCardRepository.updateCard(card)
                .onFailure { _error.value = it.message }
        }
    }

    fun deleteCard(cardId: String) {
        viewModelScope.launch {
            discountCardRepository.deleteCard(cardId)
                .onFailure { _error.value = it.message }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
