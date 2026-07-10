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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.sharedshoppinglists.app.domain.model.AffinityProgram
import com.sharedshoppinglists.app.domain.model.MySupermarket
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AffinityProgramsScreen(
    viewModel: ManualComparatorViewModel,
    onBack: () -> Unit
) {
    val programs by viewModel.affinityPrograms.collectAsStateWithLifecycle()
    val supermarkets by viewModel.supermarkets.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Programas de Afinidad") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Agregar programa")
            }
        }
    ) { padding ->
        if (programs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No tenés programas de afinidad.\nTocá + para agregar uno.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(programs, key = { it.id }) { program ->
                    val superName = supermarkets.find { it.id == program.supermarketId }?.name ?: program.supermarketId
                    AffinityProgramItem(
                        program = program,
                        supermarketName = superName,
                        onDelete = { viewModel.deleteAffinityProgram(program.id) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddAffinityProgramDialog(
            supermarkets = supermarkets,
            onDismiss = { showAddDialog = false },
            onConfirm = { program ->
                viewModel.addAffinityProgram(program)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AffinityProgramItem(
    program: AffinityProgram,
    supermarketName: String,
    onDelete: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(program.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "En: $supermarketName — ${program.discountPercentage}% dto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                if (program.validFrom != null || program.validUntil != null) {
                    val from = program.validFrom?.let { dateFormatter.format(it) } ?: "—"
                    val until = program.validUntil?.let { dateFormatter.format(it) } ?: "—"
                    Text("Vigencia: $from a $until", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAffinityProgramDialog(
    supermarkets: List<MySupermarket>,
    onDismiss: () -> Unit,
    onConfirm: (AffinityProgram) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var selectedSupermarketId by rememberSaveable { mutableStateOf("") }
    var selectedSupermarketName by rememberSaveable { mutableStateOf("") }
    var discountText by rememberSaveable { mutableStateOf("") }
    var validFromText by rememberSaveable { mutableStateOf("") }
    var validUntilText by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo programa de afinidad") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nombre del programa") },
                    placeholder = { Text("Ej: Club Día, Comunidad Coto") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedSupermarketName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Supermercado") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        supermarkets.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.name) },
                                onClick = {
                                    selectedSupermarketId = s.id
                                    selectedSupermarketName = s.name
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = discountText, onValueChange = { discountText = it },
                    label = { Text("% Descuento") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Vigencia (opcional, AAAA-MM-DD)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = validFromText, onValueChange = { validFromText = it },
                        label = { Text("Desde") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = validUntilText, onValueChange = { validUntilText = it },
                        label = { Text("Hasta") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        AffinityProgram(
                            id = UUID.randomUUID().toString(),
                            userId = "",
                            name = name.trim(),
                            supermarketId = selectedSupermarketId,
                            discountPercentage = discountText.toDoubleOrNull() ?: 0.0,
                            validFrom = parseLocalDate(validFromText),
                            validUntil = parseLocalDate(validUntilText)
                        )
                    )
                },
                enabled = name.isNotBlank() && selectedSupermarketId.isNotBlank() && discountText.toDoubleOrNull() != null
            ) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun parseLocalDate(text: String): LocalDate? {
    return try {
        if (text.isBlank()) null else LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: Exception) { null }
}
