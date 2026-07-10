package com.sharedshoppinglists.app.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sharedshoppinglists.app.data.local.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SearchResult(val productName: String, val listName: String, val listId: String, val emoji: String, val isPurchased: Boolean)

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(private val db: AppDatabase) : ViewModel() {
    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results

    fun search(query: String) {
        if (query.length < 2) { _results.value = emptyList(); return }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
            val lists = db.shoppingListDao().getAllSync()
            val allResults = mutableListOf<SearchResult>()
            for (list in lists) {
                val products = db.productDao().getByListIdSync(list.id)
                products.filter { it.name.contains(query, ignoreCase = true) }.forEach { p ->
                    allResults.add(SearchResult(p.name, list.name, list.id, p.emoji.ifBlank { p.categoryEmoji }, p.isPurchased))
                }
            }
            _results.value = allResults
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(onBack: () -> Unit, onListClick: (String) -> Unit) {
    val vm: GlobalSearchViewModel = hiltViewModel()
    val results by vm.results.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Buscar en todas las listas") })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it; vm.search(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar producto...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true, shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(12.dp))
            if (results.isEmpty() && query.length >= 2) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Sin resultados", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(results) { r ->
                    Card(Modifier.fillMaxWidth().clickable { onListClick(r.listId) }, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.emoji, fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.productName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("En: ${r.listName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (r.isPurchased) Text("Comprado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}