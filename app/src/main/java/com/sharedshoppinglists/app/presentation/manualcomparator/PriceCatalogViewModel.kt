package com.sharedshoppinglists.app.presentation.manualcomparator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharedshoppinglists.app.data.remote.CatalogStatus
import com.sharedshoppinglists.app.data.remote.SepaCatalogClient
import com.sharedshoppinglists.app.data.remote.SepaProductInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PriceCatalogViewModel @Inject constructor(
    private val catalogClient: SepaCatalogClient
) : ViewModel() {

    private val _products = MutableStateFlow<List<SepaProductInfo>>(emptyList())
    val products: StateFlow<List<SepaProductInfo>> = _products.asStateFlow()

    private val _cadenas = MutableStateFlow<List<String>>(emptyList())
    val cadenas: StateFlow<List<String>> = _cadenas.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _status = MutableStateFlow<CatalogStatus?>(null)
    val status: StateFlow<CatalogStatus?> = _status.asStateFlow()

    private val _filterCadena = MutableStateFlow<String?>(null)
    val filterCadena: StateFlow<String?> = _filterCadena.asStateFlow()

    private val _promos = MutableStateFlow<List<com.sharedshoppinglists.app.data.remote.PromoBancaria>>(emptyList())
    val promos: StateFlow<List<com.sharedshoppinglists.app.data.remote.PromoBancaria>> = _promos.asStateFlow()

    init {
        viewModelScope.launch {
            _cadenas.value = try { catalogClient.getCadenas() } catch (_: Exception) { emptyList() }
            _status.value = try { catalogClient.getStatus() } catch (_: Exception) { null }
            _promos.value = try { catalogClient.getPromos() } catch (_: Exception) { emptyList() }
        }
    }

    fun searchProducts(query: String, marca: String? = null) {
        if (query.length < 2) { _products.value = emptyList(); return }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _products.value = catalogClient.searchProducts(query, marca, _filterCadena.value)
            } catch (_: Exception) {
                _products.value = emptyList()
                _error.value = "No se pudieron cargar los precios. Revis\u00e1 tu conexi\u00f3n."
            }
            _isLoading.value = false
        }
    }

    fun setFilterCadena(cadena: String?) { _filterCadena.value = cadena }
}
