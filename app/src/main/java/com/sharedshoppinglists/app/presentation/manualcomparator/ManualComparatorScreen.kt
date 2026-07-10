package com.sharedshoppinglists.app.presentation.manualcomparator

import android.content.Intent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharedshoppinglists.app.domain.model.ComparisonResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualComparatorScreen(
    viewModel: ManualComparatorViewModel,
    listId: String,
    onBack: () -> Unit
) {
    val results by viewModel.comparisonResults.collectAsStateWithLifecycle()
    val isCalculating by viewModel.isCalculating.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(listId) { viewModel.calculateComparison(listId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comparador de Precios") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (results.isNotEmpty()) {
                        IconButton(onClick = { shareResults(context, results) }) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir resultados")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            isCalculating -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Calculando mejor opción...")
                    }
                }
            }
            error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Error: $error", color = MaterialTheme.colorScheme.error)
                }
            }
            results.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        "No hay datos suficientes para comparar.\nCargá precios en tus supermercados primero.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ranking de supermercados",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ordenados por costo total (menor a mayor)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    itemsIndexed(results) { index, result ->
                        ComparisonResultCard(index = index + 1, result = result, isBest = index == 0)
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ComparisonResultCard(index: Int, result: ComparisonResult, isBest: Boolean) {
    val containerColor = if (isBest) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBest) 4.dp else 2.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#$index",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.padding(start = 12.dp))
                    Column {
                        Text(result.supermarket.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (result.supermarket.address.isNotBlank()) {
                            Text(result.supermarket.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (isBest) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Mejor", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            PriceRow("Subtotal", result.subtotal)
            if (result.cardDiscount > 0) {
                PriceRow("Dto. tarjeta (${result.appliedCardName ?: ""})", -result.cardDiscount, isDiscount = true)
            }
            if (result.affinityDiscount > 0) {
                PriceRow("Dto. afinidad (${result.appliedAffinityName ?: ""})", -result.affinityDiscount, isDiscount = true)
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TOTAL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "$${String.format("%.2f", result.total)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            if (result.missingProducts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Text(
                        "Productos sin precio: ${result.missingProducts.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceRow(label: String, amount: Double, isDiscount: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            "$${String.format("%.2f", amount)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isDiscount) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun shareResults(context: android.content.Context, results: List<ComparisonResult>) {
    val text = buildString {
        appendLine("🛒 Comparador de Precios - Uh no había")
        appendLine("━━━━━━━━━━━━━━━━━━━━━━")
        results.forEachIndexed { index, r ->
            val medal = when (index) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "#${index + 1}" }
            appendLine()
            appendLine("$medal ${r.supermarket.name}")
            if (r.supermarket.address.isNotBlank()) appendLine("   📍 ${r.supermarket.address}")
            appendLine("   Subtotal: $${String.format("%.2f", r.subtotal)}")
            if (r.cardDiscount > 0) appendLine("   Dto. tarjeta (${r.appliedCardName}): -$${String.format("%.2f", r.cardDiscount)}")
            if (r.affinityDiscount > 0) appendLine("   Dto. afinidad (${r.appliedAffinityName}): -$${String.format("%.2f", r.affinityDiscount)}")
            appendLine("   💰 TOTAL: $${String.format("%.2f", r.total)}")
            if (r.missingProducts.isNotEmpty()) appendLine("   ⚠️ Sin precio: ${r.missingProducts.joinToString(", ")}")
        }
        if (results.size >= 2) {
            val savings = results.last().total - results.first().total
            appendLine()
            appendLine("💡 Ahorrás $${String.format("%.2f", savings)} comprando en ${results.first().supermarket.name}")
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir comparación"))
}
