package com.sharedshoppinglists.app.presentation.manualcomparator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharedshoppinglists.app.domain.model.AffinityProgram
import com.sharedshoppinglists.app.domain.model.ComparisonResult
import com.sharedshoppinglists.app.domain.model.ManualPrice
import com.sharedshoppinglists.app.domain.model.MySupermarket
import com.sharedshoppinglists.app.domain.repository.AuthRepository
import com.sharedshoppinglists.app.domain.repository.ManualPriceComparatorRepository
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
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ManualComparatorViewModel @Inject constructor(
    private val repository: ManualPriceComparatorRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _comparisonResults = MutableStateFlow<List<ComparisonResult>>(emptyList())
    val comparisonResults: StateFlow<List<ComparisonResult>> = _comparisonResults.asStateFlow()

    private val _isCalculating = MutableStateFlow(false)
    val isCalculating: StateFlow<Boolean> = _isCalculating.asStateFlow()

    private val _priceHistory = MutableStateFlow<List<ManualPrice>>(emptyList())
    val priceHistory: StateFlow<List<ManualPrice>> = _priceHistory.asStateFlow()

    private val _productNameSuggestions = MutableStateFlow<List<String>>(emptyList())
    val productNameSuggestions: StateFlow<List<String>> = _productNameSuggestions.asStateFlow()

    val supermarkets: StateFlow<List<MySupermarket>> = _currentUserId
        .flatMapLatest { uid ->
            if (uid != null) repository.getSupermarkets(uid) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPrices: StateFlow<List<ManualPrice>> = _currentUserId
        .flatMapLatest { uid ->
            if (uid != null) repository.getAllPrices(uid) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val affinityPrograms: StateFlow<List<AffinityProgram>> = _currentUserId
        .flatMapLatest { uid ->
            if (uid != null) repository.getAffinityPrograms(uid) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSupermarketId = MutableStateFlow<String?>(null)

    val pricesForSupermarket: StateFlow<List<ManualPrice>> = _selectedSupermarketId
        .flatMapLatest { sid ->
            if (sid != null) repository.getPricesBySupermarket(sid) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            authRepository.getCurrentUser().collect { user ->
                _currentUserId.value = user?.id
            }
        }
    }

    fun selectSupermarket(id: String) { _selectedSupermarketId.value = id }

    // --- Supermarkets ---
    fun addSupermarket(name: String, address: String) {
        val uid = _currentUserId.value ?: return
        viewModelScope.launch {
            repository.addSupermarket(
                MySupermarket(UUID.randomUUID().toString(), uid, name.trim(), address.trim())
            ).onFailure { _error.value = it.message }
        }
    }

    fun updateSupermarket(supermarket: MySupermarket) {
        viewModelScope.launch {
            repository.updateSupermarket(supermarket)
                .onFailure { _error.value = it.message }
        }
    }

    fun deleteSupermarket(id: String) {
        viewModelScope.launch {
            repository.deleteSupermarket(id)
                .onFailure { _error.value = it.message }
        }
    }

    // --- Prices ---
    fun addPrice(supermarketId: String, productName: String, price: Double) {
        val uid = _currentUserId.value ?: return
        viewModelScope.launch {
            repository.addPrice(
                ManualPrice(
                    UUID.randomUUID().toString(), uid, supermarketId,
                    productName.trim(), price, Instant.now()
                )
            ).onFailure { _error.value = it.message }
        }
    }

    fun updatePrice(price: ManualPrice) {
        viewModelScope.launch {
            repository.updatePrice(price.copy(updatedAt = Instant.now()))
                .onFailure { _error.value = it.message }
        }
    }

    fun deletePrice(id: String) {
        viewModelScope.launch {
            repository.deletePrice(id)
                .onFailure { _error.value = it.message }
        }
    }

    // --- Affinity programs ---
    fun addAffinityProgram(program: AffinityProgram) {
        val uid = _currentUserId.value ?: return
        viewModelScope.launch {
            repository.addAffinityProgram(program.copy(userId = uid))
                .onFailure { _error.value = it.message }
        }
    }

    fun updateAffinityProgram(program: AffinityProgram) {
        viewModelScope.launch {
            repository.updateAffinityProgram(program)
                .onFailure { _error.value = it.message }
        }
    }

    fun deleteAffinityProgram(id: String) {
        viewModelScope.launch {
            repository.deleteAffinityProgram(id)
                .onFailure { _error.value = it.message }
        }
    }

    // --- Comparison ---
    fun calculateComparison(listId: String) {
        val uid = _currentUserId.value ?: return
        viewModelScope.launch {
            _isCalculating.value = true
            repository.calculateBestSupermarket(uid, listId)
                .onSuccess { _comparisonResults.value = it }
                .onFailure { _error.value = it.message }
            _isCalculating.value = false
        }
    }

    // --- Price history ---
    fun loadPriceHistory(productName: String) {
        val uid = _currentUserId.value ?: return
        viewModelScope.launch {
            _priceHistory.value = repository.getPriceHistory(uid, productName)
        }
    }

    fun clearPriceHistory() { _priceHistory.value = emptyList() }

    // --- Product name suggestions ---
    fun loadProductSuggestions() {
        val uid = _currentUserId.value ?: return
        viewModelScope.launch {
            _productNameSuggestions.value = repository.getDistinctProductNames(uid)
        }
    }

    fun clearError() { _error.value = null }
}
