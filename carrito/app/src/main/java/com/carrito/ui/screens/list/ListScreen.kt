package com.carrito.ui.screens.list

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.carrito.data.db.*
import com.carrito.ui.theme.Mint
import com.carrito.ui.theme.Peach
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ListVM @Inject constructor(
    saved: SavedStateHandle,
    private val listDao: ShoppingListDao,
    private val itemDao: ItemDao,
    private val catalogDao: CatalogDao
) : ViewModel() {
    private val listId: String = saved["id"] ?: ""

    val list = flow { emit(listDao.getById(listId)) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val items = itemDao.getByList(listId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = catalogDao.getCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val frequent = catalogDao.getFrequent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun catalogByCategory(cat: String) = catalogDao.getByCategory(cat)

    fun addItem(name: String, emoji: String, category: String) {
        viewModelScope.launch {
            itemDao.upsert(ItemEntity(UUID.randomUUID().toString(), listId, name, emoji, category = category))
            catalogDao.incrementUsage(name)
            listDao.getById(listId)?.let { listDao.upsert(it.copy(updatedAt = System.currentTimeMillis())) }
        }
    }

    fun toggleItem(id: String, inCart: Boolean) = viewModelScope.launch { itemDao.setInCart(id, inCart) }
    fun removeItem(id: String) = viewModelScope.launch { itemDao.deleteById(id) }
    fun uncheckAll() = viewModelScope.launch { itemDao.uncheckAll(listId) }

    fun addCustom(name: String) {
        viewModelScope.launch {
            val item = ItemEntity(UUID.randomUUID().toString(), listId, name, "📦", category = "Otros")
            itemDao.upsert(item)
            catalogDao.insertIfAbsent(CatalogItem(name, "📦", "Otros"))
            listDao.getById(listId)?.let { listDao.upsert(it.copy(updatedAt = System.currentTimeMillis())) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(listId: String, onBack: () -> Unit, vm: ListVM = hiltViewModel()) {
    val list by vm.list.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val frequent by vm.frequent.collectAsStateWithLifecycle()

    var selectedCat by rememberSaveable { mutableStateOf("Frecuentes") }
    var customText by rememberSaveable { mutableStateOf("") }
    var showCart by rememberSaveable { mutableStateOf(false) }

    val pending = items.filter { !it.inCart }
    val inCart = items.filter { it.inCart }
    val allCats = listOf("Frecuentes") + categories

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(list?.name ?: "", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Text("←", fontSize = 20.sp) } },
                actions = {
                    // Cart badge
                    BadgedBox(badge = { if (pending.isNotEmpty()) Badge { Text("${pending.size}") } }) {
                        IconButton(onClick = { showCart = !showCart }) { Text(if (showCart) "📋" else "🛒", fontSize = 20.sp) }
                    }
                    IconButton(onClick = { vm.uncheckAll() }) { Text("↩️", fontSize = 18.sp) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (showCart) {
                // Show current list items
                CartView(pending = pending, inCart = inCart, onToggle = { id, v -> vm.toggleItem(id, v) }, onRemove = { vm.removeItem(it) })
            } else {
                // Category tabs
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allCats) { cat ->
                        val selected = cat == selectedCat
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCat = cat },
                            label = { Text(cat, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Mint,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Custom add field
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = { Text("Agregar producto...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = {
                        if (customText.isNotBlank()) {
                            IconButton(onClick = { vm.addCustom(customText.trim()); customText = "" }) {
                                Text("➕", fontSize = 18.sp)
                            }
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))

                // Product grid. Collect unconditionally with a Flow remembered per
                // category (no re-subscription/flicker, and no conditional composable call).
                val catalogFlow = remember(selectedCat) { vm.catalogByCategory(selectedCat) }
                val byCategory by catalogFlow.collectAsStateWithLifecycle(emptyList())
                val catalogItems = if (selectedCat == "Frecuentes") frequent else byCategory

                val addedNames = items.map { it.name }.toSet()

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(catalogItems, key = { it.name }) { item ->
                        val isAdded = item.name in addedNames
                        ProductTile(
                            name = item.name,
                            emoji = item.emoji,
                            isAdded = isAdded,
                            onClick = {
                                if (!isAdded) vm.addItem(item.name, item.emoji, item.category)
                                else {
                                    // Remove from list
                                    items.find { it.name == item.name }?.let { vm.removeItem(it.id) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductTile(name: String, emoji: String, isAdded: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isAdded) Mint.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isAdded) androidx.compose.foundation.BorderStroke(2.dp, Mint) else null,
        modifier = Modifier.aspectRatio(1f)
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(emoji, fontSize = 32.sp)
            Spacer(Modifier.height(4.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (isAdded) Mint else MaterialTheme.colorScheme.onSurface)
            if (isAdded) {
                Spacer(Modifier.height(2.dp))
                Text("✓", fontSize = 12.sp, color = Mint, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CartView(pending: List<ItemEntity>, inCart: List<ItemEntity>, onToggle: (String, Boolean) -> Unit, onRemove: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (pending.isNotEmpty()) {
            item(span = { GridItemSpan(1) }) {
                Text("Por comprar (${pending.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
            }
            items(pending, key = { it.id }) { item ->
                CartItemRow(item, onToggle = { onToggle(item.id, true) }, onRemove = { onRemove(item.id) })
            }
        }
        if (inCart.isNotEmpty()) {
            item(span = { GridItemSpan(1) }) {
                Text("En el carrito (${inCart.size})", style = MaterialTheme.typography.titleMedium, color = Mint, modifier = Modifier.padding(vertical = 8.dp))
            }
            items(inCart, key = { "c_${it.id}" }) { item ->
                CartItemRow(item, onToggle = { onToggle(item.id, false) }, onRemove = { onRemove(item.id) })
            }
        }
    }
}

@Composable
private fun CartItemRow(item: ItemEntity, onToggle: () -> Unit, onRemove: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = if (item.inCart) Mint.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        tonalElevation = if (item.inCart) 0.dp else 1.dp
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.emoji.ifBlank { "📦" }, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), fontWeight = if (item.inCart) FontWeight.Normal else FontWeight.Medium)
            if (item.quantity > 1) {
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("×${item.quantity}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            if (item.inCart) Text("✓", fontSize = 18.sp, color = Mint, fontWeight = FontWeight.Bold)
        }
    }
}
