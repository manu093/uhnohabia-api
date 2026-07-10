package com.sharedshoppinglists.app.presentation.manualcomparator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

val DAYS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábados", "Domingo")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListPrepScreen(viewModel: ListPrepViewModel, listId: String, onBack: () -> Unit) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val selections by viewModel.selections.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isOptimizing by viewModel.isOptimizing.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()

    LaunchedEffect(listId) { viewModel.loadList(listId) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (result != null) "Resultado de Optimización" else "Preparar Optimización") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } })
    }) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            result != null -> OptimizationResultContent(result!!, selectedDay, viewModel.options.collectAsStateWithLifecycle().value, viewModel.selections.collectAsStateWithLifecycle().value, viewModel.bestDayResult.collectAsStateWithLifecycle().value, Modifier.padding(padding), onBack,
                onDayChange = { viewModel.switchResultDay(it) },
                onChainChange = { viewModel.switchResultChain(it) })
            isOptimizing -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Calculando mejor opción...")
                }
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text("Elegí la marca de cada producto y el día de compra.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Día de compra:", style = MaterialTheme.typography.labelMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            DAYS.forEach { day ->
                                FilterChip(selected = selectedDay == day, onClick = { viewModel.setDay(day) },
                                    label = { Text(day.take(2), style = MaterialTheme.typography.labelSmall) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    itemsIndexed(products) { _, product ->
                        val opts = options[product.name] ?: emptyList()
                        val sel = selections[product.name]
                        ProductOptionCard(product.name, product.quantity, product.preferredBrand, opts, sel,
                            onSelect = { viewModel.selectOption(product.name, it) })
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.optimize() }, Modifier.fillMaxWidth(), enabled = products.isNotEmpty()) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Optimizar compra")
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductOptionCard(name: String, qty: Double, preferredBrand: String, opts: List<com.sharedshoppinglists.app.domain.model.OpcionProducto>, selected: Int?,
    onSelect: (Int?) -> Unit) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("$name (x${qty.toInt()})${if (preferredBrand.isNotBlank()) " · $preferredBrand" else ""}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (opts.isEmpty()) {
                Text("Sin precio disponible - busca otra marca", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                val selectedOpt = if (selected != null && selected < opts.size) opts[selected] else null
                val displayText = selectedOpt?.let { "${it.marca} - ${it.nombre}" } ?: "Cualquier marca (más barato)"
                val priceRange = if (selectedOpt != null) {
                    val prices = selectedOpt.preciosPorCadena.map { it.precio }
                    "${prices.min().toInt()} - ${prices.max().toInt()}"
                } else ""
                OutlinedTextField(value = displayText, onValueChange = {}, readOnly = true,
                    modifier = Modifier.fillMaxWidth().clickable { showDialog = true },
                    textStyle = MaterialTheme.typography.bodySmall,
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.clickable { showDialog = true }) })
                if (priceRange.isNotBlank()) {
                    Text(priceRange, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
    if (showDialog && opts.isNotEmpty()) {
        ProductOptionDialog(opts = opts, selected = selected, onSelect = { onSelect(it); showDialog = false }, onDismiss = { showDialog = false })
    }
}

@Composable
private fun ProductOptionDialog(opts: List<com.sharedshoppinglists.app.domain.model.OpcionProducto>, selected: Int?, onSelect: (Int?) -> Unit, onDismiss: () -> Unit) {
    var filterText by rememberSaveable { mutableStateOf("") }
    val filteredOpts = if (filterText.isBlank()) opts else {
        val words = filterText.lowercase().split(" ")
        opts.filter { opt -> words.all { w -> opt.nombre.lowercase().contains(w) || opt.marca.lowercase().contains(w) } }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Elegir producto", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(value = filterText, onValueChange = { filterText = it },
                    placeholder = { Text("Buscar marca o producto...") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected == null, onClick = { onSelect(null) })
                            Text("Cualquier marca (más barato)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    items(filteredOpts) { opt ->
                        val origIndex = opts.indexOf(opt)
                        val prices = opt.preciosPorCadena.map { it.precio }
                        val cadenas = opt.preciosPorCadena.map { it.cadena }
                        val exclusiveLabel = if (cadenas.size == 1) "Solo en ${cadenas[0]}" else "${cadenas.size} cadenas"
                        Row(Modifier.fillMaxWidth().clickable { onSelect(origIndex) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected == origIndex, onClick = { onSelect(origIndex) })
                            Column(Modifier.padding(start = 4.dp)) {
                                Text("${opt.marca} - ${opt.nombre}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${prices.min().toInt()} - ${prices.max().toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                    Text(exclusiveLabel, style = MaterialTheme.typography.labelSmall,
                                        color = if (cadenas.size == 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable
fun OptimizationResultContent(result: com.sharedshoppinglists.app.domain.model.OptimizationResult, selectedDay: String, options: Map<String, List<com.sharedshoppinglists.app.domain.model.OpcionProducto>> = emptyMap(), selections: Map<String, Int?> = emptyMap(),
    bestDayResults: Map<String, com.sharedshoppinglists.app.domain.model.OptimizationResult?>, modifier: Modifier, onBack: () -> Unit,
    onDayChange: (String) -> Unit = {},
    onChainChange: (String) -> Unit = {}) {
    val bestDay = bestDayResults.entries
        .filter { it.value != null && it.value!!.totalFinal > 0 }
        .minByOrNull { it.value!!.totalFinal }

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("📅 Día seleccionado: $selectedDay", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            if (bestDay != null && bestDay.key != selectedDay && bestDay.value!!.totalFinal < result.totalFinal) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("💡 Mejor día para comprar: ${bestDay.key}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("En ${bestDay.value!!.cadenaRecomendada ?: "?"}: ${String.format("%.0f", bestDay.value!!.totalFinal)} (ahorrás ${String.format("%.0f", result.totalFinal - bestDay.value!!.totalFinal)} más)",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (bestDayResults.isNotEmpty()) {
                Text("📊 Costo por día:", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    bestDayResults.entries.sortedBy { it.value?.totalFinal ?: Double.MAX_VALUE }.forEach { (day, dayResult) ->
                        val isBest = dayResult != null && dayResult == bestDay?.value
                        val isSelected = day == selectedDay
                        FilterChip(selected = isSelected, onClick = { onDayChange(day) },
                            label = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(day.take(2), style = MaterialTheme.typography.labelSmall)
                                Text(String.format("%.0f", dayResult?.totalFinal ?: 0.0), style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal)
                            }})
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("🏆 Mejor opción: ${result.cadenaRecomendada ?: "N/A"}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Total original", style = MaterialTheme.typography.labelSmall)
                            Text(String.format("%.0f", result.totalOriginal), style = MaterialTheme.typography.titleMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total final", style = MaterialTheme.typography.labelSmall)
                            Text(String.format("%.0f", result.totalFinal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("💰 Ahorrás ${String.format("%.0f", result.ahorroTotal)} (${String.format("%.1f", result.ahorroPorcentaje)}%)",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        item { Text("💳 Distribución de pagos:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        // EXCLUSIVITY CONFLICT DETECTION
        val recChain = result.cadenaRecomendada ?: ""
        val conflicts = mutableListOf<Triple<String, String, String>>()
        if (recChain.isNotBlank()) {
            for ((pName, optList) in options) {
                val sIdx = selections[pName]
                if (sIdx != null && sIdx < optList.size) {
                    val sOpt = optList[sIdx]
                    val chs = sOpt.preciosPorCadena.map { it.cadena }
                    if (chs.isNotEmpty() && chs.none { it.equals(recChain, ignoreCase = true) }) {
                        conflicts.add(Triple(pName, sOpt.marca, chs.joinToString(", ")))
                    }
                }
            }
        }
        if (conflicts.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Atencion: productos no disponibles en " + recChain, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(6.dp))
                        conflicts.forEach { (n, b, c) ->
                            Text("- " + n + " (" + b + ") -> Solo en: " + c, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onBack) { Text("Volver a cambiar marcas") }
                    }
                }
            }
        }
        result.distribucionPagos.forEach { pago ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${pago.medioPago}${if (pago.tarjeta != null) " (${pago.tarjeta})" else ""}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            if (pago.descuentoPct > 0) {
                                Text("${pago.descuentoPct.toInt()}% dto.${if (pago.topeAplicado) " (tope alcanzado)" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(String.format("%.0f", pago.monto), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (pago.ahorro > 0) Text("-${String.format("%.0f", pago.ahorro)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }
        // Exclusivity warnings - products that are only in a different chain
        if (result.productosFaltantes.isNotEmpty() && result.cadenaRecomendada != null) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Productos no disponibles en ${result.cadenaRecomendada}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(4.dp))
                        Text("Estos productos no se encontraron en la cadena recomendada. Podes cambiar la marca en la pantalla anterior o ir a otra cadena.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(8.dp))
                        result.productosFaltantes.forEach { prod ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text("  - $prod", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onBack) { Text("Volver a cambiar marcas") }
                    }
                }
            }
        }
        if (result.productosSeleccionados.isNotEmpty()) {
            item { Text("🛒 Productos seleccionados:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            result.productosSeleccionados.forEach { prod ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(prod.nombre, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 2)
                                Text("${prod.marca} · x${prod.cantidad}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(String.format("%.0f", prod.precio), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        if (result.productosFaltantes.isNotEmpty()) {
            item {
                Text("⚠️ Productos sin precio:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                result.productosFaltantes.forEach { Text("  • $it - proba con otra marca o cadena", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (result.rankingCadenas.size > 1) {
        // Accumulated savings
        item {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            var totalSaved by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(0.0) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val db = com.sharedshoppinglists.app.data.local.AppDatabase.getInstance(ctx)
                        val monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                        totalSaved = db.savingsDao().getTotalSavingsSince(monthAgo)
                    } catch (_: Exception) {}
                }
            }
            if (totalSaved > 0) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Este mes ahorraste", style = MaterialTheme.typography.bodyMedium)
                        Text("$" + String.format("%.0f", totalSaved), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
            item { Text("📊 Ranking de cadenas:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            result.rankingCadenas.forEachIndexed { i, r ->
                item {
                    Card(Modifier.fillMaxWidth().clickable { onChainChange(r.cadena) }, colors = if (r.cadena == result.cadenaRecomendada) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("#${i + 1} ${r.cadena}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(String.format("%.0f", r.totalFinal), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (r.ahorro > 0) Text("Ahorro: ${String.format("%.0f", r.ahorro)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}