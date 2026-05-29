package com.sharedshoppinglists.app.presentation.shoppinglist

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.sharedshoppinglists.app.domain.model.CustomCategory
import com.sharedshoppinglists.app.domain.model.Product
import com.sharedshoppinglists.app.presentation.shared.EditingIndicatorBadge
import com.sharedshoppinglists.app.presentation.shared.LastModifiedByText
import com.sharedshoppinglists.app.presentation.shared.ShareListDialog
import com.sharedshoppinglists.app.presentation.shared.SharedListViewModel

// Category colors for visual distinction
private val categoryColors = mapOf(
    "Carniceria" to Color(0xFFE53935), "Verduleria" to Color(0xFF43A047),
    "Limpieza" to Color(0xFF1E88E5), "Perfumeria" to Color(0xFFAB47BC),
    "Granja" to Color(0xFFFF8F00), "Bebidas" to Color(0xFF00ACC1),
    "Almacen" to Color(0xFF8D6E63), "Otros" to Color(0xFF78909C),
    "Panaderia" to Color(0xFFFFB300), "Lacteos" to Color(0xFF5C6BC0),
    "Congelados" to Color(0xFF26C6DA), "Frutas y Verduras" to Color(0xFF66BB6A)
)

private fun getCategoryColor(name: String): Color {
    return categoryColors.entries.find { name.contains(it.key, ignoreCase = true) }?.value ?: Color(0xFF78909C)
}

private fun hapticTick(context: android.content.Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
            v?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } catch (_: Exception) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListDetailScreen(
    viewModel: ShoppingListViewModel,
    sharedListViewModel: SharedListViewModel,
    listId: String, listName: String, isShared: Boolean,
    onBack: () -> Unit, onCopyLink: (String) -> Unit = {},
    onManualComparator: (String) -> Unit = {}, onOptimize: (String) -> Unit = {}, onShoppingMode: (String) -> Unit = {}
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val suggestions by viewModel.autocompleteSuggestions.collectAsStateWithLifecycle()
    val editingStatuses by sharedListViewModel.editingStatuses.collectAsStateWithLifecycle()
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
    var showAddProductDialog by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var productToEdit by rememberSaveable { mutableStateOf<Product?>(null) }
    var showShareDialog by rememberSaveable { mutableStateOf(false) }
    var productToMove by rememberSaveable { mutableStateOf<Product?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(listId) {
        viewModel.selectList(listId)
        if (isShared) sharedListViewModel.observeList(listId)
    }

    // Preload product images when products change
    LaunchedEffect(products) {
        if (products.isNotEmpty()) {
            preloadProductImages(context, products.map { it.name })
        }
    }

    // Progress
    val pending = products.filter { !it.isPurchased }
    val done = products.filter { it.isPurchased }
    val progress = if (products.isNotEmpty()) done.size.toFloat() / products.size else 0f

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Column {
                            Text(listName, fontWeight = FontWeight.Bold)
                            if (products.isNotEmpty()) {
                                Text("${done.size} de ${products.size} comprados", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection(); sharedListViewModel.clearObservation(); onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.uncheckAllProducts() }) { Icon(Icons.Default.Refresh, "Desmarcar todos") }
                        IconButton(onClick = { onShoppingMode(listId) }) { Icon(Icons.Default.ShoppingCart, "Modo super") }
                        IconButton(onClick = { onOptimize(listId) }) { Icon(Icons.Default.Star, "Optimizar") }
                        IconButton(onClick = { showImportDialog = true }) { Icon(Icons.Default.Add, "Importar") }
                        IconButton(onClick = { showShareDialog = true }) { Icon(Icons.Default.Share, "Compartir") }
                    }
                )
                // Progress bar
                if (products.isNotEmpty() && progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(2.dp)),
                        color = if (progress >= 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                VoiceInputButton(onResult = { spokenText ->
                    viewModel.addProduct(name = spokenText, quantity = 1.0, unit = "Unidad",
                        categoryId = "", categoryName = "Otros", categoryEmoji = "\uD83D\uDCE6", emoji = "", preferredBrand = "")
                })
                FloatingActionButton(
                    onClick = { showAddProductDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, "Agregar producto")
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.selectList(listId)
                refreshScope.launch {
                    kotlinx.coroutines.delay(1500)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (products.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text("\uD83D\uDED2", fontSize = 56.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Lista vacia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("Toca + para agregar un producto", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Spacer(Modifier.height(24.dp))
                        // Frequent products suggestions
                        val frequent by viewModel.getFrequentProducts().collectAsStateWithLifecycle(initialValue = emptyList())
                        if (frequent.isNotEmpty()) {
                            Text("Productos frecuentes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(10.dp))
                            frequent.take(6).forEach { kp ->
                                Surface(
                                    onClick = { viewModel.addProduct(kp.name, 1.0, kp.defaultUnit, kp.categoryId, "", "", kp.emoji, "") },
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(kp.emoji.ifBlank { "\uD83D\uDCE6" }, fontSize = 22.sp)
                                        Spacer(Modifier.width(12.dp))
                                        Text(kp.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Spacer(Modifier.weight(1f))
                                        Icon(Icons.Default.Add, "Agregar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val groupedPending = pending.groupBy { it.categoryName.ifBlank { "Otros" } }
                var collapsedCategories by rememberSaveable { mutableStateOf(setOf<String>()) }
                var doneExpanded by rememberSaveable { mutableStateOf(false) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    groupedPending.forEach { (categoryName, categoryProducts) ->
                        val catEmoji = categoryProducts.firstOrNull()?.categoryEmoji ?: ""
                        val catColor = getCategoryColor(categoryName)
                        val isExpanded = categoryName !in collapsedCategories

                        // Category header
                        item(key = "header_$categoryName") {
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    collapsedCategories = if (isExpanded) collapsedCategories + categoryName else collapsedCategories - categoryName
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = catColor.copy(alpha = 0.08f)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(catEmoji, fontSize = 18.sp)
                                        Text(categoryName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = catColor)
                                        Surface(shape = RoundedCornerShape(10.dp), color = catColor.copy(alpha = 0.15f)) {
                                            Text("${categoryProducts.size}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = catColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                        }
                                    }
                                    Icon(if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Toggle", tint = catColor, modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        if (isExpanded) {
                            items(categoryProducts, key = { it.id }) { product ->
                                SwipeToDismissProduct(
                                    product = product,
                                    onToggle = { hapticTick(context); viewModel.toggleProductPurchased(product.id, !product.isPurchased) },
                                    onEdit = { productToEdit = product },
                                    onDelete = { hapticTick(context); viewModel.removeProduct(product.id) },
                                    onLongPress = { productToMove = product }
                                )
                            }
                        }
                    }

                    // Completed section
                    if (done.isNotEmpty()) {
                        item(key = "header_done") {
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { doneExpanded = !doneExpanded },
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF4CAF50).copy(alpha = 0.08f)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("\u2705", fontSize = 18.sp)
                                        Text("Completados", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF4CAF50))
                                        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF4CAF50).copy(alpha = 0.15f)) {
                                            Text("${done.size}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                        }
                                    }
                                    Icon(if (doneExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Toggle", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        if (doneExpanded) {
                            items(done, key = { "done_${it.id}" }) { product ->
                                SwipeToDismissProduct(
                                    product = product,
                                    onToggle = { hapticTick(context); viewModel.toggleProductPurchased(product.id, !product.isPurchased) },
                                    onEdit = { productToEdit = product },
                                    onDelete = { hapticTick(context); viewModel.removeProduct(product.id) },
                                    onLongPress = { productToMove = product }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    // Dialogs
    if (showImportDialog) {
        ImportListDialog(onDismiss = { showImportDialog = false }, onImport = { names -> viewModel.importProducts(names); showImportDialog = false })
    }
    if (showAddProductDialog) {
        AddEditProductDialog(categories = categories, suggestions = suggestions,
            onSearchQuery = { viewModel.searchProducts(it) },
            onCreateCategory = { n, e -> viewModel.createCategory(n, e) },
            onDismiss = { showAddProductDialog = false; viewModel.clearSuggestions() },
            onConfirm = { name, qty, unit, catId, catName, catEmoji, emoji, brand ->
                viewModel.addProduct(name, qty, unit, catId, catName, catEmoji, emoji, brand)
                showAddProductDialog = false; viewModel.clearSuggestions()
            })
    }
    productToEdit?.let { product ->
        AddEditProductDialog(product = product, categories = categories,
            onCreateCategory = { n, e -> viewModel.createCategory(n, e) },
            onDismiss = { productToEdit = null },
            onConfirm = { name, qty, unit, catId, catName, catEmoji, emoji, brand ->
                viewModel.updateProduct(product.copy(name = name, quantity = qty, unit = unit, categoryId = catId, categoryName = catName, categoryEmoji = catEmoji, emoji = emoji, preferredBrand = brand))
                productToEdit = null
            })
    }
    if (showShareDialog) {
        ShareListDialog(listId = listId, viewModel = sharedListViewModel, onDismiss = { showShareDialog = false }, onCopyLink = onCopyLink)
    }
    productToMove?.let { product ->
        MoveToCategorySheet(product = product, categories = categories, onDismiss = { productToMove = null },
            onMove = { cat -> viewModel.updateProduct(product.copy(categoryId = cat.id, categoryName = cat.name, categoryEmoji = cat.emoji)); productToMove = null })
    }
    val duplicateProduct by viewModel.duplicateProduct.collectAsStateWithLifecycle()
    duplicateProduct?.let { existing ->
        AlertDialog(onDismissRequest = { viewModel.dismissDuplicate() },
            title = { Text("Producto duplicado") },
            text = { Text("Ya tenes \"${existing.name}\" (${existing.quantity} ${existing.unit}) en la lista. Queres sumar la cantidad?") },
            confirmButton = { TextButton(onClick = { viewModel.confirmAddDuplicate(merge = true) }) { Text("Sumar cantidad") } },
            dismissButton = { TextButton(onClick = { viewModel.confirmAddDuplicate(merge = false) }) { Text("Agregar aparte") } })
    }
}

// Swipe to dismiss wrapper
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissProduct(
    product: Product, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onLongPress: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.StartToEnd -> { onToggle(); true }
                else -> false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFE53935).copy(alpha = 0.2f)
                    else -> Color.Transparent
                }, label = "bg"
            )
            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled || dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                val icon = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Check
                    SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                    else -> null
                }
                val align = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                }
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(color).padding(horizontal = 20.dp), contentAlignment = align) {
                    icon?.let { Icon(it, null, tint = MaterialTheme.colorScheme.onSurface) }
                }
            }
        },
        enableDismissFromStartToEnd = !product.isPurchased,
        enableDismissFromEndToStart = true
    ) {
        ThemedProductItem(product = product, onTogglePurchased = onToggle, onEdit = onEdit, onDelete = onDelete, onLongPress = onLongPress)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveToCategorySheet(product: Product, categories: List<CustomCategory>, onDismiss: () -> Unit, onMove: (CustomCategory) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Mover \"${product.name}\" a:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 16.dp))
            categories.forEach { category ->
                val isCurrent = category.id == product.categoryId
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    onClick = { if (!isCurrent) onMove(category) }
                ) {
                    Row(
                        Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(category.emoji, fontSize = 22.sp)
                        Text(category.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal)
                        if (isCurrent) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.Check, "Actual", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}