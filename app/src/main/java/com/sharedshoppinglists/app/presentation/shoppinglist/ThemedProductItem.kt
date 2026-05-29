package com.sharedshoppinglists.app.presentation.shoppinglist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedshoppinglists.app.domain.model.Product
import com.sharedshoppinglists.app.presentation.theme.LocalAppDesignStyle

/**
 * Dispatches to the correct product item layout based on the current design style.
 */
@Composable
fun ThemedProductItem(
    product: Product,
    onTogglePurchased: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val style = LocalAppDesignStyle.current
    when (style.layoutMode) {
        "grid" -> GridProductItem(product, onTogglePurchased, onEdit, onDelete, onLongPress)
        "minimal" -> MinimalProductItem(product, onTogglePurchased, onEdit, onDelete, onLongPress)
        "colorful" -> ColorfulProductItem(product, onTogglePurchased, onEdit, onDelete, onLongPress)
        "compact" -> CompactProductItem(product, onTogglePurchased, onEdit, onDelete, onLongPress)
        "modern" -> ModernProductItem(product, onTogglePurchased, onEdit, onDelete, onLongPress)
        else -> ClassicProductItem(product, onTogglePurchased, onEdit, onDelete, onLongPress)
    }
}

// ─── CLASSIC: Checkbox + name + edit/delete buttons (current design) ─────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClassicProductItem(product: Product, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onLongPress: () -> Unit) {
    val dim = if (product.isPurchased) 0.5f else 1f
    val emoji = product.emoji.ifBlank { product.categoryEmoji }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongPress),
        colors = if (product.isPurchased) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) else CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = product.isPurchased, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text("$emoji ${product.name}", style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (product.isPurchased) TextDecoration.LineThrough else TextDecoration.None),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim))
                Text("${product.quantity} ${product.unit}${if (product.preferredBrand.isNotBlank()) " · ${product.preferredBrand}" else ""}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim))
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

// ─── GRID: Bring!-style chips with big emoji, tap to toggle ──────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridProductItem(product: Product, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onLongPress: () -> Unit) {
    val emoji = product.emoji.ifBlank { product.categoryEmoji }
    val bgColor by animateColorAsState(
        if (product.isPurchased) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.surfaceVariant, label = "bg"
    )
    Surface(
        modifier = Modifier.combinedClickable(onClick = onToggle, onLongClick = onLongPress),
        shape = RoundedCornerShape(16.dp), color = bgColor, tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 32.sp)
            Spacer(Modifier.height(4.dp))
            Text(product.name, style = MaterialTheme.typography.labelMedium, maxLines = 2,
                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
                textDecoration = if (product.isPurchased) TextDecoration.LineThrough else TextDecoration.None)
            Text("${product.quantity.toInt()} ${product.unit}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (product.isPurchased) {
                Icon(Icons.Default.Check, "Comprado", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── MINIMAL: Ultra clean, no borders, subtle divider ────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinimalProductItem(product: Product, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onLongPress: () -> Unit) {
    val dim = if (product.isPurchased) 0.4f else 1f
    val emoji = product.emoji.ifBlank { product.categoryEmoji }
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onToggle, onLongClick = onLongPress).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 20.sp, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(product.name, style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (product.isPurchased) TextDecoration.LineThrough else TextDecoration.None,
                fontWeight = FontWeight.Normal), color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim))
            if (product.preferredBrand.isNotBlank()) {
                Text(product.preferredBrand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim))
            }
        }
        Text("${product.quantity.toInt()} ${product.unit}", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim))
        if (product.isPurchased) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Check, "Comprado", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

// ─── COLORFUL: Gradient cards with big emoji, progress feel ──────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColorfulProductItem(product: Product, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onLongPress: () -> Unit) {
    val emoji = product.emoji.ifBlank { product.categoryEmoji }
    val colors = if (product.isPurchased) {
        listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
    } else {
        listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
    }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onToggle, onLongClick = onLongPress),
        shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(Modifier.background(Brush.horizontalGradient(colors)).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 24.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(product.name, style = MaterialTheme.typography.titleSmall.copy(
                        textDecoration = if (product.isPurchased) TextDecoration.LineThrough else TextDecoration.None),
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${product.quantity.toInt()} ${product.unit}", style = MaterialTheme.typography.labelSmall)
                        if (product.preferredBrand.isNotBlank()) {
                            Text("· ${product.preferredBrand}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                if (product.isPurchased) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, "Comprado", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// ─── COMPACT: Maximum density, small items, everything visible ───────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactProductItem(product: Product, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onLongPress: () -> Unit) {
    val dim = if (product.isPurchased) 0.5f else 1f
    val emoji = product.emoji.ifBlank { product.categoryEmoji }
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onToggle, onLongClick = onLongPress)
            .background(if (product.isPurchased) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = product.isPurchased, onCheckedChange = { onToggle() }, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(emoji, fontSize = 14.sp)
        Spacer(Modifier.width(4.dp))
        Text(product.name, style = MaterialTheme.typography.bodySmall.copy(
            textDecoration = if (product.isPurchased) TextDecoration.LineThrough else TextDecoration.None),
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim))
        Text("${product.quantity.toInt()}${product.unit.take(2)}", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim))
        if (product.preferredBrand.isNotBlank()) {
            Spacer(Modifier.width(2.dp))
            Text(product.preferredBrand.take(8), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = dim), maxLines = 1)
        }
    }
}

// ─── MODERN: Clean white design with product images and circular checkboxes ──
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModernProductItem(product: Product, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onLongPress: () -> Unit) {
    val dim = if (product.isPurchased) 0.5f else 1f
    val emoji = product.emoji.ifBlank { product.categoryEmoji }

    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onToggle, onLongClick = onLongPress)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular checkbox
        Surface(
            onClick = onToggle,
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = if (product.isPurchased) MaterialTheme.colorScheme.primary else Color.Transparent,
            border = BorderStroke(
                2.dp,
                if (product.isPurchased) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            if (product.isPurchased) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, "Comprado", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Product image/emoji
        ProductImage(productName = product.name, emoji = emoji, size = 40.dp)

        Spacer(Modifier.width(12.dp))

        // Product info
        Column(Modifier.weight(1f)) {
            Text(
                product.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (product.isPurchased) TextDecoration.LineThrough else TextDecoration.None
                ),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (product.preferredBrand.isNotBlank()) {
                Text(product.preferredBrand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim * 0.7f))
            }
        }

        // Quantity badge
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Text(
                "${product.quantity.toInt()}${product.unit.take(3).lowercase()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
