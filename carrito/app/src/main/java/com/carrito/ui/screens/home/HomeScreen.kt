package com.carrito.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.carrito.data.db.*
import com.carrito.ui.theme.Mint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeVM @Inject constructor(private val dao: ShoppingListDao, private val itemDao: ItemDao) : ViewModel() {
    val lists = dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun create(name: String, emoji: String) = viewModelScope.launch { dao.upsert(ShoppingListEntity(UUID.randomUUID().toString(), name, emoji)) }
    fun delete(id: String) = viewModelScope.launch { dao.deleteById(id) }
    fun pending(listId: String) = itemDao.pendingCount(listId)
    fun total(listId: String) = itemDao.totalCount(listId)
}

@Composable
fun HomeScreen(vm: HomeVM = hiltViewModel(), onOpenList: (String) -> Unit) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    var showNew by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }, containerColor = Mint, contentColor = Color.White, shape = CircleShape, modifier = Modifier.size(64.dp)) {
                Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 100.dp)) {
            item {
                Column(Modifier.padding(24.dp).padding(top = 40.dp)) {
                    Text("Carrito", style = MaterialTheme.typography.headlineLarge, color = Mint)
                    Text("Tus listas de compras", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (lists.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🛒", fontSize = 72.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Creá tu primera lista", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(lists, key = { it.id }) { list ->
                // remember keyed by id so the Flow is not re-created on every recomposition
                val pendFlow = remember(list.id) { vm.pending(list.id) }
                val totFlow = remember(list.id) { vm.total(list.id) }
                val pend by pendFlow.collectAsStateWithLifecycle(0)
                val tot by totFlow.collectAsStateWithLifecycle(0)
                Surface(
                    onClick = { onOpenList(list.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(list.color).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Text(list.emoji, fontSize = 26.sp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(list.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (tot > 0) Text("$pend pendiente${if (pend != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else Text("Sin productos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                        }
                        if (tot > 0) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Mint.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                Text("$tot", style = MaterialTheme.typography.labelLarge, color = Mint)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNew) NewListDialog(onDismiss = { showNew = false }, onCreate = { n, e -> vm.create(n, e); showNew = false })
}

@Composable
private fun NewListDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("🛒") }
    val options = listOf("🛒", "🏠", "🍽️", "🎉", "💊", "🐶", "✈️", "🎁", "🧹", "💪")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nueva lista", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { e ->
                        Surface(onClick = { emoji = e }, shape = RoundedCornerShape(10.dp), color = if (e == emoji) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                            Text(e, fontSize = 22.sp, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim(), emoji) }, enabled = name.isNotBlank()) { Text("Crear", color = Mint) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
