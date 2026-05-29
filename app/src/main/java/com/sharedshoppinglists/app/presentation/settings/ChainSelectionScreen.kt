package com.sharedshoppinglists.app.presentation.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChainSelectionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var allChains by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedChains by remember { mutableStateOf(getSelectedChains(context)) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://colonial-albertine-pepin-5207cd9b.koyeb.app/catalog/cadenas")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 10000
                val json = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val arr = org.json.JSONArray(json)
                allChains = (0 until arr.length()).map { arr.getString(it) }.sorted()
            } catch (_: Exception) {
                allChains = listOf("Carrefour", "Changomas", "Coto", "DIA", "Diarco", "Disco", "Jumbo", "Makro", "Maxiconsumo", "Vea", "Vital")
            }
            loading = false
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Mis Cadenas", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Text("\u2B05\uFE0F", fontSize = 18.sp) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
    }) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Text(
                        "Selecciona donde compras habitualmente para optimizar precios.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "${selectedChains.size} cadenas seleccionadas",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                items(allChains) { chain ->
                    val isSelected = chain in selectedChains
                    Surface(
                        onClick = {
                            selectedChains = if (isSelected) selectedChains - chain else selectedChains + chain
                            saveSelectedChains(context, selectedChains)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                               else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("\uD83C\uDFEA", fontSize = 20.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                chain,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, "Seleccionado", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

private fun getSelectedChains(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("chain_prefs", Context.MODE_PRIVATE)
    return prefs.getStringSet("selected_chains", null) ?: setOf("Carrefour", "Coto", "Jumbo", "Disco", "DIA", "Changomas")
}

private fun saveSelectedChains(context: Context, chains: Set<String>) {
    context.getSharedPreferences("chain_prefs", Context.MODE_PRIVATE).edit()
        .putStringSet("selected_chains", chains).apply()
}