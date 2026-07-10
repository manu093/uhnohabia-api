package com.sharedshoppinglists.app.presentation.manualcomparator

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharedshoppinglists.app.data.local.PaymentMethodsStore
import com.sharedshoppinglists.app.data.remote.SepaCatalogClient
import com.sharedshoppinglists.app.domain.model.OpcionProducto
import com.sharedshoppinglists.app.domain.model.OptimizarRequest
import com.sharedshoppinglists.app.domain.model.OptimizationResult
import com.sharedshoppinglists.app.domain.model.Product
import com.sharedshoppinglists.app.domain.model.ProductoOptimizar
import com.sharedshoppinglists.app.domain.repository.ShoppingListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class ListPrepViewModel @Inject constructor(
    private val shoppingListRepository: ShoppingListRepository,
    private val catalogClient: SepaCatalogClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    // Map: product name -> list of options from catalog
    private val _options = MutableStateFlow<Map<String, List<OpcionProducto>>>(emptyMap())
    val options: StateFlow<Map<String, List<OpcionProducto>>> = _options.asStateFlow()

    // Map: product name -> selected option index (null = cualquier marca)
    private val _selections = MutableStateFlow<Map<String, Int?>>(emptyMap())
    val selections: StateFlow<Map<String, Int?>> = _selections.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _result = MutableStateFlow<OptimizationResult?>(null)
    val result: StateFlow<OptimizationResult?> = _result.asStateFlow()

    private val _bestDayResult = MutableStateFlow<Map<String, OptimizationResult?>>(emptyMap())
    val bestDayResult: StateFlow<Map<String, OptimizationResult?>> = _bestDayResult.asStateFlow()

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing.asStateFlow()

    private val _selectedDay = MutableStateFlow(getCurrentDayName())
    val selectedDay: StateFlow<String> = _selectedDay.asStateFlow()

    fun loadList(listId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val prods = shoppingListRepository.getProducts(listId).first().filter { !it.isPurchased }
            _products.value = prods
            // Search options for each product
            val opts = mutableMapOf<String, List<OpcionProducto>>()
            val sels = mutableMapOf<String, Int?>()
            for (p in prods) {
                try {
                    val results = catalogClient.buscarOpciones(p.name)
                    opts[p.name] = results
                    // If product has preferred brand, try to pre-select matching option
                    if (p.preferredBrand.isNotBlank() && results.isNotEmpty()) {
                        val brandLower = p.preferredBrand.lowercase()
                        // Try to match brand + unit size (e.g., "1 litro" -> "1l", "1 lt", "1000ml")
                        val unitLower = p.unit.lowercase().trim()
                        val qty = p.quantity
                        val sizeHint = buildSizeHint(qty, unitLower)
                        // First try: brand + size match
                        var matchIdx = results.indexOfFirst { opt ->
                            opt.marca.lowercase().contains(brandLower) && sizeHint.any { hint -> opt.nombre.lowercase().contains(hint) }
                        }
                        // Fallback: brand only
                        if (matchIdx < 0) {
                            matchIdx = results.indexOfFirst { it.marca.lowercase().contains(brandLower) }
                        }
                        sels[p.name] = if (matchIdx >= 0) matchIdx else null
                    } else {
                        sels[p.name] = null // default: cualquier marca (más barato)
                    }
                } catch (_: Exception) {
                    opts[p.name] = emptyList()
                    sels[p.name] = null
                }
            }
            _options.value = opts
            _selections.value = sels
            _isLoading.value = false
        }
    }

    fun selectOption(productName: String, index: Int?) {
        _selections.value = _selections.value.toMutableMap().apply { put(productName, index) }
    }

    fun setDay(day: String) { _selectedDay.value = day }

    fun switchResultDay(day: String) {
        val dayResults = _bestDayResult.value
        val dayResult = dayResults[day]
        if (dayResult != null) {
            _selectedDay.value = day
            _result.value = dayResult
        }
    }

    fun switchResultChain(cadena: String) {
        val current = _result.value ?: return
        val chainData = current.rankingCadenas.find { it.cadena == cadena } ?: return
        _result.value = OptimizationResult(
            cadenaRecomendada = chainData.cadena,
            totalOriginal = chainData.totalOriginal,
            totalFinal = chainData.totalFinal,
            ahorroTotal = chainData.ahorro,
            ahorroPorcentaje = chainData.ahorroPorcentaje,
            distribucionPagos = chainData.distribucionPagos,
            productosFaltantes = chainData.productosFaltantes,
            productosSeleccionados = chainData.productosSeleccionados,
            rankingCadenas = current.rankingCadenas
        )
    }

    fun optimize() {
        viewModelScope.launch {
            _isOptimizing.value = true
            val selectedMedios = PaymentMethodsStore.getSelectedIds(context).toList()
            val chainPrefs = context.getSharedPreferences("chain_prefs", android.content.Context.MODE_PRIVATE)
            val selectedChains = chainPrefs.getStringSet("selected_chains", null)?.toList() ?: emptyList()
            android.util.Log.d("OPTIMIZE", "selectedChains=$selectedChains")
            val cardSels = PaymentMethodsStore.getCardSelections(context)
            val productos = _products.value.map { prod ->
                val sel = _selections.value[prod.name]
                val opts = _options.value[prod.name] ?: emptyList()
                if (sel != null && sel < opts.size) {
                    val opt = opts[sel]
                    val firstPrice = opt.preciosPorCadena.firstOrNull()
                    ProductoOptimizar(productoId = firstPrice?.productoId, nombre = opt.nombre, cantidad = prod.quantity.toInt().coerceAtLeast(1), unidad = prod.unit)
                } else {
                    val sn = if (prod.preferredBrand.isNotBlank()) "${prod.name} ${prod.preferredBrand}" else prod.name
                    ProductoOptimizar(productoId = "cualquier_marca", nombre = sn, cantidad = prod.quantity.toInt().coerceAtLeast(1), unidad = prod.unit)
                }
            }
            val request = OptimizarRequest(
                productos = productos,
                mediosPagoIds = selectedMedios,
                tarjetasSeleccionadas = cardSels.mapKeys { it.key.toString() },
                diaSemana = _selectedDay.value,
                cadenas = selectedChains
            )
            _result.value = try { catalogClient.optimizar(request) } catch (_: Exception) { null }

            // Calculate for all days to find the best day
            val allDays = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábados", "Domingo")
            val dayResults = mutableMapOf<String, OptimizationResult?>()
            for (day in allDays) {
                if (day == _selectedDay.value) { dayResults[day] = _result.value; continue }
                val dayReq = OptimizarRequest(productos = productos, mediosPagoIds = selectedMedios,
                    tarjetasSeleccionadas = cardSels.mapKeys { it.key.toString() }, diaSemana = day, cadenas = selectedChains)
                dayResults[day] = try { catalogClient.optimizar(dayReq) } catch (_: Exception) { null }
            }
            _bestDayResult.value = dayResults

            // Record savings
            val savings = _result.value?.ahorroTotal ?: 0.0
            if (savings > 0) {
                try {
                    val db = com.sharedshoppinglists.app.data.local.AppDatabase.getInstance(context)
                    db.savingsDao().insert(com.sharedshoppinglists.app.data.local.entity.SavingsEntity(
                        amount = savings, chain = _result.value?.cadenaRecomendada ?: "",
                        listName = "", recordedAt = System.currentTimeMillis()
                    ))
                } catch (_: Exception) {}
            }
            _isOptimizing.value = false
        }
    }

    private fun getCurrentDayName(): String {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Lunes"; Calendar.TUESDAY -> "Martes"
            Calendar.WEDNESDAY -> "Miércoles"; Calendar.THURSDAY -> "Jueves"
            Calendar.FRIDAY -> "Viernes"; Calendar.SATURDAY -> "Sábados"
            Calendar.SUNDAY -> "Domingo"; else -> ""
        }
    }

    private fun buildSizeHint(qty: Double, unit: String): List<String> {
        val hints = mutableListOf<String>()
        val qInt = qty.toInt()
        when {
            unit in listOf("litro", "litros", "lt", "l", "lts") -> {
                hints.addAll(listOf("${qInt}l", "${qInt} l", "${qInt}lt", "${qInt} lt", "${qInt} litro"))
                if (qInt == 1) hints.addAll(listOf("1000ml", "1000 ml", "1000cc", "1000 cc"))
            }
            unit in listOf("kg", "kilo", "kilos") -> {
                hints.addAll(listOf("${qInt}kg", "${qInt} kg", "${qInt} kilo"))
                if (qInt == 1) hints.addAll(listOf("1000g", "1000 g", "1000gr"))
            }
            unit in listOf("ml", "cc") -> {
                hints.addAll(listOf("${qInt}ml", "${qInt} ml", "${qInt}cc", "${qInt} cc"))
            }
            unit in listOf("g", "gr", "grs", "gramos") -> {
                hints.addAll(listOf("${qInt}g", "${qInt} g", "${qInt}gr", "${qInt} gr"))
            }
        }
        return hints
    }
}