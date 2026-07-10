package com.sharedshoppinglists.app.presentation.manualcomparator

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharedshoppinglists.app.domain.model.MedioPago

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PaymentMethodsScreen(viewModel: PaymentMethodsViewModel, onBack: () -> Unit) {
    val medios by viewModel.medios.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()

    val filtered = if (query.isBlank()) medios else medios.filter { it.nombreDisplay.contains(query, ignoreCase = true) }
    val bancos = filtered.filter { it.tipo == "banco" }
    val billeteras = filtered.filter { it.tipo == "billetera_digital" }
    val clubes = filtered.filter { it.tipo == "club_beneficios" }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Medios de Pago", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
    }) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Text(
                        "Selecciona tus bancos, billeteras y clubes para ver descuentos disponibles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${selectedIds.size} seleccionados",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Buscar...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                }
                if (bancos.isNotEmpty()) {
                    item { SectionTitle("\uD83C\uDFE6 Bancos", bancos.size, bancos.count { selectedIds.contains(it.id) }) }
                    item { MediosGrid(bancos, selectedIds) { viewModel.toggleMedio(it) } }
                    item { Spacer(Modifier.height(12.dp)) }
                }
                if (billeteras.isNotEmpty()) {
                    item { SectionTitle("\uD83D\uDCF1 Billeteras", billeteras.size, billeteras.count { selectedIds.contains(it.id) }) }
                    item { MediosGrid(billeteras, selectedIds) { viewModel.toggleMedio(it) } }
                    item { Spacer(Modifier.height(12.dp)) }
                }
                if (clubes.isNotEmpty()) {
                    item { SectionTitle("\uD83C\uDFAF Clubes", clubes.size, clubes.count { selectedIds.contains(it.id) }) }
                    item { MediosGrid(clubes, selectedIds) { viewModel.toggleMedio(it) } }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, total: Int, selected: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("$selected/$total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediosGrid(medios: List<MedioPago>, selectedIds: Set<Int>, onToggle: (Int) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        medios.forEach { medio ->
            MedioChip(medio, selectedIds.contains(medio.id)) { onToggle(medio.id) }
        }
    }
}

@Composable
private fun MedioChip(medio: MedioPago, isSelected: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onToggle,
        modifier = Modifier.width(100.dp).height(52.dp),
        shape = shape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
               else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null
    ) {
        Box(Modifier.fillMaxSize().padding(6.dp)) {
            Text(
                medio.nombreDisplay.replace("Banco ", ""),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.align(Alignment.Center)
            )
            if (isSelected) {
                Icon(
                    Icons.Default.Check, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp).align(Alignment.TopEnd)
                )
            }
        }
    }
}