package com.sharedshoppinglists.app.presentation.shoppinglist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sharedshoppinglists.app.domain.model.CustomCategory
import com.sharedshoppinglists.app.domain.model.KnownProduct
import com.sharedshoppinglists.app.domain.model.Product

private val EMOJI_MAP = mapOf(
    "carne" to listOf("🥩", "🍖", "🥓"),
    "pollo" to listOf("🐔", "🍗"),
    "pescado" to listOf("🐟", "🐠", "🦐"),
    "leche" to listOf("🥛", "🧀", "🧈"),
    "queso" to listOf("🧀"),
    "huevo" to listOf("🥚"),
    "pan" to listOf("🍞", "🥖", "🥐"),
    "fruta" to listOf("🍎", "🍌", "🍊", "🍇", "🍓", "🍑", "🍐", "🥝"),
    "manzana" to listOf("🍎", "🍏"),
    "banana" to listOf("🍌"),
    "naranja" to listOf("🍊"),
    "uva" to listOf("🍇"),
    "frutilla" to listOf("🍓"),
    "durazno" to listOf("🍑"),
    "pera" to listOf("🍐"),
    "kiwi" to listOf("🥝"),
    "limon" to listOf("🍋"),
    "sandia" to listOf("🍉"),
    "melon" to listOf("🍈"),
    "cereza" to listOf("🍒"),
    "anana" to listOf("🍍"),
    "coco" to listOf("🥥"),
    "palta" to listOf("🥑"),
    "verdura" to listOf("🥬", "🥕", "🍅", "🥔", "🌽"),
    "tomate" to listOf("🍅"),
    "papa" to listOf("🥔"),
    "cebolla" to listOf("🧅"),
    "zanahoria" to listOf("🥕"),
    "lechuga" to listOf("🥬"),
    "arroz" to listOf("🍚"),
    "fideo" to listOf("🍝"),
    "pasta" to listOf("🍝"),
    "agua" to listOf("💧", "🥤"),
    "gaseosa" to listOf("🥤", "🧃"),
    "cerveza" to listOf("🍺"),
    "vino" to listOf("🍷"),
    "cafe" to listOf("☕"),
    "te" to listOf("🍵"),
    "jabon" to listOf("🧴"),
    "shampoo" to listOf("🧴"),
    "papel" to listOf("🧻"),
    "limpieza" to listOf("🧹", "🧽"),
    "chocolate" to listOf("🍫"),
    "galletita" to listOf("🍪"),
    "helado" to listOf("🍦", "🧊"),
    "aceite" to listOf("🫒"),
    "sal" to listOf("🧂"),
    "azucar" to listOf("🍬"),
    "manteca" to listOf("🧈"),
    "yogur" to listOf("🥛"),
)

private val ALL_EMOJIS = listOf(
    "🥩", "🥬", "🍎", "🍌", "🥛", "🧀", "🍞", "🥚", "🐔", "🐟",
    "🍝", "🍚", "🥫", "🧃", "🥤", "🍺", "🧴", "🧹", "🧻", "🧊",
    "🍫", "🍪", "🥜", "🫒", "🧈", "🧅", "🥕", "🍅", "🥔", "🌽"
)

private fun getEmojiSuggestions(productName: String): List<String> {
    if (productName.isBlank()) return ALL_EMOJIS
    val lower = productName.lowercase()
    val matched = EMOJI_MAP.entries
        .filter { (key, _) -> lower.contains(key) }
        .flatMap { it.value }
        .distinct()
    return if (matched.isNotEmpty()) matched + ALL_EMOJIS.filter { it !in matched }
    else ALL_EMOJIS
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditProductDialog(
    product: Product? = null,
    categories: List<CustomCategory> = emptyList(),
    suggestions: List<KnownProduct> = emptyList(),
    onSearchQuery: (String) -> Unit = {},
    onCreateCategory: (name: String, emoji: String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    onConfirm: (name: String, quantity: Double, unit: String, categoryId: String, categoryName: String, categoryEmoji: String, emoji: String, preferredBrand: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(product?.name ?: "") }
    var quantity by rememberSaveable { mutableStateOf(product?.quantity?.toString() ?: "1") }
    var unit by rememberSaveable { mutableStateOf(product?.unit ?: "Unidad") }
    var selectedCategoryId by rememberSaveable { mutableStateOf(product?.categoryId ?: "") }
    var emoji by rememberSaveable { mutableStateOf(product?.emoji ?: "") }
    var preferredBrand by rememberSaveable { mutableStateOf(product?.preferredBrand ?: "") }
    var showSuggestions by rememberSaveable { mutableStateOf(false) }
    var showNewCategoryDialog by rememberSaveable { mutableStateOf(false) }
    val isEdit = product != null

    // Trigger autocomplete search when name changes
    LaunchedEffect(name) {
        if (name.length >= 2 && !isEdit) {
            onSearchQuery(name)
            showSuggestions = true
        } else {
            showSuggestions = false
        }
    }

    // Auto-select newly created category
    LaunchedEffect(categories.size) {
        val last = categories.maxByOrNull { it.sortOrder }
        if (last != null && selectedCategoryId.isBlank()) {
            // Don't auto-select on first load
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Editar producto" else "Agregar producto") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nombre del producto") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                // Autocomplete suggestions
                if (showSuggestions && suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            suggestions.take(5).forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            name = suggestion.name
                                            emoji = suggestion.emoji
                                            unit = suggestion.defaultUnit
                                            selectedCategoryId = suggestion.categoryId
                                            showSuggestions = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (suggestion.emoji.isNotBlank()) {
                                        Text(suggestion.emoji)
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = suggestion.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = emoji, onValueChange = { emoji = it },
                    label = { Text("Emoji del producto") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                // Emoji suggestions - dynamic based on product name
                Spacer(modifier = Modifier.height(4.dp))
                Text("Emojis sugeridos", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(2.dp))
                val emojiSuggestions = getEmojiSuggestions(name)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    emojiSuggestions.take(15).forEach { e ->
                        Text(
                            text = e,
                            modifier = Modifier
                                .clickable { emoji = e }
                                .padding(4.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = preferredBrand, onValueChange = { preferredBrand = it },
                    label = { Text("Marca preferida (opcional)") },
                    placeholder = { Text("Ej: La Serenísima, Gallo") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = quantity, onValueChange = { v -> if (v.isEmpty() || v.toDoubleOrNull()?.let { it >= 0 } == true) quantity = v },
                    label = { Text("Cantidad") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Unidad", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                val unitOptions = listOf("Kg", "Unidad", "Gr", "Docena", "Litro", "Otro")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    unitOptions.forEach { opt ->
                        FilterChip(
                            selected = unit == opt,
                            onClick = { unit = opt },
                            label = { Text(opt) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Categoría", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text("${cat.emoji} ${cat.name}") }
                        )
                    }
                    // CHANGE 2: "+ Nueva categoría" chip
                    AssistChip(
                        onClick = { showNewCategoryDialog = true },
                        label = { Text("+ Nueva categoría") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val qty = (quantity.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.1)
                    val selectedCat = categories.find { it.id == selectedCategoryId }
                    val catName = selectedCat?.name ?: "Otros"
                    val catEmoji = selectedCat?.emoji ?: "📦"
                    onConfirm(name, qty, unit, selectedCategoryId, catName, catEmoji, emoji, preferredBrand.trim())
                },
                enabled = name.isNotBlank()
            ) { Text(if (isEdit) "Guardar" else "Agregar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )

    // CHANGE 2: New category inline dialog
    if (showNewCategoryDialog) {
        NewCategoryDialog(
            onDismiss = { showNewCategoryDialog = false },
            onConfirm = { catName, catEmoji ->
                onCreateCategory(catName, catEmoji)
                showNewCategoryDialog = false
                // Auto-select will happen when categories list updates
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, emoji: String) -> Unit
) {
    var catName by rememberSaveable { mutableStateOf("") }
    var catEmoji by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear nueva categoría") },
        text = {
            Column {
                OutlinedTextField(
                    value = catName,
                    onValueChange = { catName = it },
                    label = { Text("Nombre de la categoría") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = catEmoji,
                    onValueChange = { catEmoji = it },
                    label = { Text("Emoji") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Emojis sugeridos", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(2.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ALL_EMOJIS.forEach { e ->
                        Text(
                            text = e,
                            modifier = Modifier
                                .clickable { catEmoji = e }
                                .padding(4.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(catName, catEmoji) },
                enabled = catName.isNotBlank()
            ) { Text("Crear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
