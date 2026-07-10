package com.sharedshoppinglists.app.presentation.shoppinglist

import android.os.Build
import android.os.VibrationEffect
import android.os.VibratorManager
import android.os.Vibrator
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingModeScreen(viewModel: ShoppingListViewModel, listId: String, listName: String, onBack: () -> Unit) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(listId) { viewModel.selectList(listId) }

    val pending = products.filter { !it.isPurchased }
    val done = products.filter { it.isPurchased }
    val grouped = pending.groupBy { it.categoryName.ifBlank { "Otros" } }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(listName, fontWeight = FontWeight.Bold)
                    Text("Faltan ${pending.size} de ${products.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = { IconButton(onClick = { viewModel.clearSelection(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = if (pending.isEmpty()) Color(0xFF4CAF50).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
        )
    }) { padding ->
        if (pending.isEmpty() && done.isNotEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Listo!", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Compraste todo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Progress bar
                item {
                    if (products.isNotEmpty()) {
                        val progress = done.size.toFloat() / products.size
                        Column(Modifier.padding(vertical = 8.dp)) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF4CAF50), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("${done.size}/${products.size} comprados", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                grouped.forEach { (category, categoryProducts) ->
                    val catEmoji = categoryProducts.firstOrNull()?.categoryEmoji ?: ""
                    item(key = "cat_$category") {
                        Text("$catEmoji $category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    items(categoryProducts, key = { "shop_${it.id}" }) { product ->
                        BigCheckItem(product = product, onToggle = {
                            haptic(context)
                            viewModel.toggleProductPurchased(product.id, true)
                        })
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun BigCheckItem(product: com.sharedshoppinglists.app.domain.model.Product, onToggle: () -> Unit) {
    val emoji = product.emoji.ifBlank { product.categoryEmoji }
    Card(Modifier.fillMaxWidth().clickable { onToggle() }, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("${product.quantity.toInt()} ${product.unit}${if (product.preferredBrand.isNotBlank()) " - ${product.preferredBrand}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text("", fontSize = 20.sp)
            }
        }
    }
}

private fun haptic(context: android.content.Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } catch (_: Exception) {}
}