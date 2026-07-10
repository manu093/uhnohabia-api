package com.sharedshoppinglists.app.presentation.manualcomparator

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharedshoppinglists.app.data.local.PaymentMethodsStore
import com.sharedshoppinglists.app.data.remote.SepaCatalogClient
import com.sharedshoppinglists.app.domain.model.MedioPago
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class PaymentMethodsViewModel @Inject constructor(
    private val catalogClient: SepaCatalogClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _medios = MutableStateFlow<List<MedioPago>>(emptyList())
    val medios: StateFlow<List<MedioPago>> = _medios.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedIds: StateFlow<Set<Int>> = _selectedIds.asStateFlow()

    private val _cardSelections = MutableStateFlow<Map<Int, List<String>>>(emptyMap())
    val cardSelections: StateFlow<Map<Int, List<String>>> = _cardSelections.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    private val uid get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    init {
        _selectedIds.value = PaymentMethodsStore.getSelectedIds(context)
        _cardSelections.value = PaymentMethodsStore.getCardSelections(context)
        loadMedios()
        loadFromFirestore()
    }

    fun loadMedios() {
        viewModelScope.launch {
            _isLoading.value = true
            _medios.value = try { catalogClient.getMediosPago() } catch (_: Exception) { emptyList() }
            _isLoading.value = false
        }
    }

    fun toggleMedio(id: Int) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedIds.value = current
        PaymentMethodsStore.saveSelectedIds(context, current)
        syncToFirestore()
    }

    fun setCardSelection(medioId: Int, tarjetas: List<String>) {
        val current = _cardSelections.value.toMutableMap()
        current[medioId] = tarjetas
        _cardSelections.value = current
        PaymentMethodsStore.saveCardSelections(context, current)
        syncToFirestore()
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    private fun syncToFirestore() {
        val userId = uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("userSettings").document(userId).set(
                    mapOf(
                        "selectedMediosPago" to _selectedIds.value.toList(),
                        "cardSelections" to _cardSelections.value.mapKeys { it.key.toString() },
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ), com.google.firebase.firestore.SetOptions.merge()
                )
            } catch (_: Exception) { }
        }
    }

    private fun loadFromFirestore() {
        val userId = uid ?: return
        viewModelScope.launch {
            try {
                val doc = firestore.collection("userSettings").document(userId).get().await()
                if (doc.exists()) {
                    val ids = (doc.get("selectedMediosPago") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet()
                    if (ids != null && ids.isNotEmpty()) {
                        _selectedIds.value = ids
                        PaymentMethodsStore.saveSelectedIds(context, ids)
                    }
                }
            } catch (_: Exception) { }
        }
    }
}
