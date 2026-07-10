package com.sharedshoppinglists.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedshoppinglists.app.data.local.AppDatabase
import com.sharedshoppinglists.app.data.local.ListExportHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lists = remember {
        try { AppDatabase.getInstance(context).shoppingListDao().getAllSync() } catch (_: Exception) { emptyList() }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Exportar Listas", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
    }) { padding ->
        if (lists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\uD83D\uDCE4", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No hay listas para exportar", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "Compartir tus listas como texto o archivo CSV.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(lists) { list ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(list.emoji.ifBlank { "\uD83D\uDED2" }, fontSize = 24.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(list.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        val products = AppDatabase.getInstance(context).productDao().getByListIdSync(list.id)
                                        val exportProducts = products.map { p ->
                                            ListExportHelper.ExportProduct(p.name, p.emoji.ifBlank { p.categoryEmoji }, p.quantity, p.unit, p.categoryName, p.isPurchased)
                                        }
                                        ListExportHelper.exportAsText(context, list.name, exportProducts)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Share, "Texto", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Texto")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val products = AppDatabase.getInstance(context).productDao().getByListIdSync(list.id)
                                        val exportProducts = products.map { p ->
                                            ListExportHelper.ExportProduct(p.name, p.emoji.ifBlank { p.categoryEmoji }, p.quantity, p.unit, p.categoryName, p.isPurchased)
                                        }
                                        ListExportHelper.exportAsCsv(context, list.name, exportProducts)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("CSV")
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}