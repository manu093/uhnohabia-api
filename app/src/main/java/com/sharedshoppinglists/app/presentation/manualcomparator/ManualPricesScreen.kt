package com.sharedshoppinglists.app.presentation.manualcomparator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharedshoppinglists.app.domain.model.ManualPrice
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualPricesScreen(
    viewModel: ManualComparatorViewModel,
    supermarketId: String,
    supermarketName: String,
    onBack: () -> Unit
) {
    val prices by viewModel.pricesForSupermarket.collectAsStateWithLifecycle()
    val priceHistory by viewModel.priceHistory.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showHistoryFor by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(supermarketId) {
        viewModel.selectSupermarket(supermarketId)
        viewModel.loadProductSuggestions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Precios en $supermarketName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Agregar precio")
            }
        }
    ) { padding ->
        if (prices.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No hay precios cargados.\nTocá + para agregar uno.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Spacer(Modifier.height(8.dp)) }
                items(prices, key = { it.id }) { price ->
                    EditablePriceItem(price,
                        onUpdate = { viewModel.updatePrice(price.copy(price = it)) },
                        onDelete = { viewModel.deletePrice(price.id) },
                        onShowHistory = { showHistoryFor = price.productName; viewModel.loadPriceHistory(price.productName) })
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddPriceDialog(viewModel, onDismiss = { showAddDialog = false },
            onConfirm = { name, price -> viewModel.addPrice(supermarketId, name, price); showAddDialog = false })
    }

    showHistoryFor?.let { name ->
        PriceHistoryDialog(name, priceHistory, viewModel.supermarkets.value,
            onDismiss = { showHistoryFor = null; viewModel.clearPriceHistory() })
    }
}

@Composable
private fun EditablePriceItem(price: ManualPrice, onUpdate: (Double) -> Unit, onDelete: () -> Unit, onShowHistory: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var editPrice by rememberSaveable { mutableStateOf(price.price.toString()) }
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable { onShowHistory() }) {
                Text(price.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Actualizado: ${fmt.format(price.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Tocá para ver historial", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (isEditing) {
                OutlinedTextField(value = editPrice, onValueChange = { editPrice = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.width(100.dp))
                IconButton(onClick = { editPrice.toDoubleOrNull()?.let { onUpdate(it) }; isEditing = false }) {
                    Icon(Icons.Default.Check, contentDescription = "Guardar", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { isEditing = false; editPrice = price.price.toString() }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancelar")
                }
            } else {
                Text("$${String.format("%.2f", price.price)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp))
                IconButton(onClick = { isEditing = true; editPrice = price.price.toString() }) { Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPriceDialog(viewModel: ManualComparatorViewModel, onDismiss: () -> Unit, onConfirm: (String, Double) -> Unit) {
    var productName by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }
    val suggestions by viewModel.productNameSuggestions.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val filtered = suggestions.filter { it.contains(productName, ignoreCase = true) && productName.length >= 2 }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Cargar precio") },
        text = {
            Column {
                ExposedDropdownMenuBox(expanded = expanded && filtered.isNotEmpty(), onExpandedChange = { expanded = it }) {
                    OutlinedTextField(value = productName, onValueChange = { productName = it; expanded = true },
                        label = { Text("Producto") }, placeholder = { Text("Ej: Leche La Serenísima 1L") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(expanded = expanded && filtered.isNotEmpty(), onDismissRequest = { expanded = false }) {
                        filtered.take(5).forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { productName = s; expanded = false }) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = priceText, onValueChange = { priceText = it },
                    label = { Text("Precio") }, placeholder = { Text("Ej: 1250.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { priceText.toDoubleOrNull()?.let { onConfirm(productName, it) } },
            enabled = productName.isNotBlank() && priceText.toDoubleOrNull() != null) { Text("Agregar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun PriceHistoryDialog(productName: String, history: List<ManualPrice>,
    supermarkets: List<com.sharedshoppinglists.app.domain.model.MySupermarket>, onDismiss: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())
    val supermarketMap = supermarkets.associateBy { it.id }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Historial: $productName") },
        text = {
            if (history.isEmpty()) Text("No hay historial.")
            else LazyColumn(Modifier.fillMaxWidth().height(300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(history.sortedByDescending { it.updatedAt }) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(supermarketMap[entry.supermarketId]?.name ?: entry.supermarketId, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(fmt.format(entry.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("$${String.format("%.2f", entry.price)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}
