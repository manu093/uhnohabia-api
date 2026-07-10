package com.sharedshoppinglists.app.presentation.manualcomparator

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharedshoppinglists.app.data.remote.PromoBancaria
import com.sharedshoppinglists.app.data.remote.SepaProductInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceCatalogScreen(viewModel: PriceCatalogViewModel, onBack: () -> Unit) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val cadenas by viewModel.cadenas.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val filterCadena by viewModel.filterCadena.collectAsStateWithLifecycle()
    val promos by viewModel.promos.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activePromoIds = remember { getActivePromoIds(context) }
    val activePromos = promos.filter { activePromoIds.contains(it.id.toString()) }

    var query by rememberSaveable { mutableStateOf("") }
    var marcaFilter by rememberSaveable { mutableStateOf("") }
    var cadenaExpanded by rememberSaveable { mutableStateOf(false) }
    var cadenaDisplay by rememberSaveable { mutableStateOf("Todos") }

    // Auto-search with debounce
    LaunchedEffect(query, marcaFilter, filterCadena) {
        kotlinx.coroutines.delay(400)
        viewModel.searchProducts(query, marcaFilter.ifBlank { null })
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Catálogo de Precios") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = query, onValueChange = { query = it },
                label = { Text("Buscar producto") }, placeholder = { Text("Ej: leche entera, arroz, yerba") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = marcaFilter, onValueChange = { marcaFilter = it },
                    label = { Text("Marca") }, placeholder = { Text("Ej: La Serenísima") },
                    singleLine = true, modifier = Modifier.weight(1f))
                ExposedDropdownMenuBox(expanded = cadenaExpanded, onExpandedChange = { cadenaExpanded = it }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(value = cadenaDisplay, onValueChange = {}, readOnly = true,
                        label = { Text("Cadena") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cadenaExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(expanded = cadenaExpanded, onDismissRequest = { cadenaExpanded = false }) {
                        DropdownMenuItem(text = { Text("Todos") }, onClick = { cadenaDisplay = "Todos"; viewModel.setFilterCadena(null); cadenaExpanded = false })
                        cadenas.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { cadenaDisplay = c; viewModel.setFilterCadena(c); cadenaExpanded = false })
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            status?.let { s ->
                Text("${s.totalProducts} productos de ${s.totalCadenas} cadenas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                products.isEmpty() && query.length >= 2 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No se encontraron productos.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                products.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Buscá un producto para comparar precios entre\nDIA, Jumbo, Disco y Carrefour.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Text("${products.size} resultados · ordenados por precio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(products, key = { it.id }) { p ->
                            val productPromos = activePromos.filter { it.cadena.equals(p.cadena, ignoreCase = true) }
                            ProductPriceCard(p, p == products.first(), productPromos)
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}


@Composable
private fun ProductPriceCard(product: SepaProductInfo, isCheapest: Boolean, promos: List<PromoBancaria>) {
    val bg = if (isCheapest) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val hasDiscount = product.precioLista > product.precio && product.precioLista > 0
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(if (isCheapest) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = bg)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(product.nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(product.marca, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (product.presentacion.isNotBlank()) Text("· ${product.presentacion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(product.cadena, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.tertiary)
                        if (isCheapest) Text("⭐ Más barato", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (hasDiscount) {
                        Text("$${String.format("%.0f", product.precioLista)}", style = MaterialTheme.typography.bodySmall,
                            textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("$${String.format("%.0f", product.precio)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = if (isCheapest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (hasDiscount) {
                        val pct = ((1 - product.precio / product.precioLista) * 100).toInt()
                        Text("-$pct%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            // Show applicable bank promos
            if (promos.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                promos.forEach { promo ->
                    val finalPrice = product.precio * (1 - promo.descuentoPct / 100)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🏦 ${promo.banco} ${promo.tarjeta}${if (promo.diaSemana.isNotBlank()) " (${promo.diaSemana})" else ""}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        Text("$${String.format("%.0f", finalPrice)} (-${promo.descuentoPct.toInt()}%)",
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}
