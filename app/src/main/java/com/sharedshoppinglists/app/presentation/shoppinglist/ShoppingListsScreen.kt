package com.sharedshoppinglists.app.presentation.shoppinglist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharedshoppinglists.app.data.local.AppDatabase
import com.sharedshoppinglists.app.domain.model.PendingInvitation
import com.sharedshoppinglists.app.domain.model.ShoppingList
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListsScreen(
    viewModel: ShoppingListViewModel,
    onListClick: (String) -> Unit,
    onDiscountCardsClick: () -> Unit = {},
    onCategoryManagementClick: () -> Unit = {},
    onKnownProductsClick: () -> Unit = {},
    onMySupermarketsClick: () -> Unit = {},
    onAffinityProgramsClick: () -> Unit = {},
    onBarcodeScannerClick: () -> Unit = {},
    onPriceCatalogClick: () -> Unit = {},
    onMyBankPromosClick: () -> Unit = {},
    onPaymentMethodsClick: () -> Unit = {},
    onGlobalSearchClick: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val lists by viewModel.shoppingLists.collectAsStateWithLifecycle()
    val pendingInvitations by viewModel.pendingInvitations.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var listToDelete by rememberSaveable { mutableStateOf<ShoppingList?>(null) }
    var listToRename by rememberSaveable { mutableStateOf<ShoppingList?>(null) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var showDuplicateDialog by rememberSaveable { mutableStateOf(false) }
    var listToEditEmoji by rememberSaveable { mutableStateOf<ShoppingList?>(null) }
    var dbStatus by rememberSaveable { mutableStateOf("") }

    val greeting = remember {
        val hour = java.time.LocalTime.now().hour
        when {
            hour < 12 -> "Buenos dias \u2600\uFE0F"
            hour < 19 -> "Buenas tardes \uD83C\uDF24\uFE0F"
            else -> "Buenas noches \uD83C\uDF19"
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = java.net.URL("https://colonial-albertine-pepin-5207cd9b.koyeb.app/catalog/status")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                val json = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val totalMatch = Regex("\"totalProducts\":(\\d+)").find(json)
                val lastRunMatch = Regex("\"lastRun\":\"([^\"]+)\"").find(json)
                val total = totalMatch?.groupValues?.get(1) ?: "?"
                val lastRunRaw = lastRunMatch?.groupValues?.get(1) ?: ""
                val lastRun = if (lastRunRaw.isNotBlank()) {
                    try {
                        val utcFormat = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS]")
                        val utcTime = java.time.LocalDateTime.parse(lastRunRaw.take(26), utcFormat)
                        val arTime = utcTime.atZone(java.time.ZoneId.of("UTC"))
                            .withZoneSameInstant(java.time.ZoneId.of("America/Argentina/Buenos_Aires"))
                        arTime.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                    } catch (_: Exception) { lastRunRaw.take(16) }
                } else "?"
                dbStatus = "$total productos \u00B7 $lastRun"
            } catch (_: Exception) { }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(visible = fabExpanded, enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom), exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FabOption("\uD83D\uDCCB Nueva lista") { fabExpanded = false; showCreateDialog = true }
                        FabOption("\uD83D\uDCC2 Nueva categoria") { fabExpanded = false; onCategoryManagementClick() }
                        FabOption("\u2B50 Producto recurrente") { fabExpanded = false; onKnownProductsClick() }
                        FabOption("\uD83D\uDCC4 Duplicar lista") { fabExpanded = false; showDuplicateDialog = true }
                    }
                }
                Spacer(Modifier.height(10.dp))
                FloatingActionButton(
                    modifier = Modifier.padding(bottom = 16.dp).size(60.dp),
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(if (fabExpanded) Icons.Default.Close else Icons.Default.Add, "Acciones", modifier = Modifier.size(28.dp))
                }
            }
        }
    ) { padding ->
        if (fabExpanded) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).padding(padding).clickable { fabExpanded = false })
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 52.dp, bottom = 8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(greeting, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            IconButton(onClick = onPaymentMethodsClick) { Icon(Icons.Default.Settings, "Config", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Box {
                                IconButton(onClick = { showMenu = !showMenu }) { Icon(Icons.Default.MoreVert, "Mas", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(text = { Text("Categorias") }, onClick = { showMenu = false; onCategoryManagementClick() })
                                    DropdownMenuItem(text = { Text("Mis Productos") }, onClick = { showMenu = false; onKnownProductsClick() })
                                    HorizontalDivider()
                                    DropdownMenuItem(text = { Text("Cerrar Sesion") }, onClick = { showMenu = false; onLogout() })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Mis Listas", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    if (dbStatus.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("\uD83D\uDCC8 $dbStatus", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }

            // Quick Actions - pill chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionChip("\uD83D\uDD0D Buscar", Modifier.weight(1f)) { onGlobalSearchClick() }
                    QuickActionChip("\uD83D\uDCB0 Precios", Modifier.weight(1f)) { onPriceCatalogClick() }
                    QuickActionChip("\uD83C\uDFF7\uFE0F Categorias", Modifier.weight(1f)) { onCategoryManagementClick() }
                }
            }

            // Notifications
            if (notifications.isNotEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            notifications.forEach { msg ->
                                Text(msg, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF5D4037))
                                Spacer(Modifier.height(4.dp))
                            }
                            TextButton(onClick = { viewModel.dismissNotifications() }) { Text("Entendido") }
                        }
                    }
                }
            }

            // Pending Invitations
            if (pendingInvitations.isNotEmpty()) {
                item { PendingInvitationsBanner(pendingInvitations, onAccept = { viewModel.acceptInvitation(it) }, onDecline = { viewModel.declineInvitation(it) }) }
            }

            // Empty state
            if (lists.isEmpty() && pendingInvitations.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("\uD83D\uDED2", fontSize = 64.sp)
                            Spacer(Modifier.height(16.dp))
                            Text("No tenes listas todavia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("Toca + para crear tu primera lista", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            } else {
                items(lists, key = { it.id }) { list ->
                    ModernListCard(list = list, onClick = { onListClick(list.id) }, onDelete = { listToDelete = list }, onRename = { listToRename = list }, onEditEmoji = { listToEditEmoji = list })
                }
            }
        }
    }

    if (showCreateDialog) {
        AddEditListDialog(onDismiss = { showCreateDialog = false }, onConfirm = { name -> viewModel.createList(name); showCreateDialog = false }, onConfirmWithEmoji = { name, emoji -> viewModel.createList(name, emoji); showCreateDialog = false })
    }
    listToDelete?.let { list ->
        AlertDialog(onDismissRequest = { listToDelete = null }, title = { Text("Eliminar lista") }, text = { Text("Seguro que queres eliminar \"${list.name}\"?") },
            confirmButton = { TextButton(onClick = { viewModel.deleteList(list.id); listToDelete = null }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { listToDelete = null }) { Text("Cancelar") } })
    }
    if (showDuplicateDialog && lists.isNotEmpty()) {
        DuplicateListDialog(lists = lists, onDismiss = { showDuplicateDialog = false }, onConfirm = { listId, newName -> viewModel.duplicateList(listId, newName); showDuplicateDialog = false })
    }
    listToEditEmoji?.let { list ->
        EditEmojiDialog(currentEmoji = list.emoji, onDismiss = { listToEditEmoji = null },
            onConfirm = { emoji -> viewModel.updateListEmoji(list.id, emoji); listToEditEmoji = null })
    }
    listToRename?.let { list ->
        RenameListDialog(currentName = list.name, onDismiss = { listToRename = null }, onConfirm = { newName -> viewModel.renameList(list.id, newName); listToRename = null })
    }
}

@Composable
private fun QuickActionChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 0.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModernListCard(list: ShoppingList, onClick: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit, onEditEmoji: () -> Unit = {}) {
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())
    val listEmoji = list.emoji.ifBlank { "\uD83D\uDED2" }
    val timeSinceUpdate = Duration.between(list.updatedAt, Instant.now())
    val isRecent = timeSinceUpdate.toHours() < 2

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).combinedClickable(onClick = onClick, onLongClick = onRename),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Emoji avatar
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(
                    if (list.isShared) Color(0xFF4ECDC4).copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                contentAlignment = Alignment.Center
            ) { Text(listEmoji, fontSize = 28.sp) }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val productCount = remember(list.id) { try { AppDatabase.getInstance(ctx).productDao().countByListId(list.id) } catch (_: Exception) { 0 } }
                val pendingCount = remember(list.id) { try { AppDatabase.getInstance(ctx).productDao().countPendingByListId(list.id) } catch (_: Exception) { 0 } }

                Text(list.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (productCount > 0) {
                        Text("$pendingCount/$productCount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (list.isShared) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF4ECDC4).copy(alpha = 0.15f)) {
                            Text("\uD83D\uDC65 Compartida", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E9E96), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                    if (isRecent) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                            Text("\u23F0 Reciente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(dateFormatter.format(list.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun PendingInvitationsBanner(invitations: List<PendingInvitation>, onAccept: (String) -> Unit, onDecline: (String) -> Unit) {
    val label = if (invitations.size == 1) "Tenes 1 invitacion pendiente" else "Tenes ${invitations.size} invitaciones pendientes"
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        elevation = CardDefaults.cardElevation(0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("\uD83D\uDCE9 $label", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
            Spacer(Modifier.height(12.dp))
            invitations.forEach { inv ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(inv.listName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAccept(inv.id) }, Modifier.height(36.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Aceptar", style = MaterialTheme.typography.labelMedium) }
                        OutlinedButton(onClick = { onDecline(inv.id) }, Modifier.height(36.dp), shape = RoundedCornerShape(12.dp)) { Text("Rechazar", style = MaterialTheme.typography.labelMedium) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FabOption(label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun RenameListDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Renombrar lista") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun DuplicateListDialog(lists: List<ShoppingList>, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var selectedListId by rememberSaveable { mutableStateOf(lists.firstOrNull()?.id ?: "") }
    var newName by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Duplicar lista") },
        text = {
            Column {
                Text("Elegir lista a duplicar:", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                lists.forEach { list ->
                    Row(Modifier.fillMaxWidth().clickable { selectedListId = list.id }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedListId == list.id, onClick = { selectedListId = list.id })
                        Spacer(Modifier.width(8.dp))
                        Text(list.name)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nombre de la copia") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = { TextButton(onClick = { if (newName.isNotBlank()) onConfirm(selectedListId, newName.trim()) }, enabled = newName.isNotBlank()) { Text("Duplicar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun EditEmojiDialog(currentEmoji: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(currentEmoji.ifBlank { "\uD83D\uDED2" }) }
    val emojis = listOf("\uD83D\uDED2", "\uD83D\uDCCB", "\uD83C\uDFE0", "\uD83C\uDF7D\uFE0F", "\uD83C\uDF89", "\uD83C\uDF81", "\u2764\uFE0F", "\uD83D\uDC76", "\uD83D\uDC36", "\uD83D\uDC31", "\uD83C\uDFEB", "\uD83C\uDFE2", "\u2708\uFE0F", "\uD83C\uDFD6\uFE0F", "\uD83C\uDFCB\uFE0F", "\uD83D\uDC85", "\uD83D\uDC8A", "\uD83C\uDF53", "\uD83E\uDD6C", "\uD83E\uDDC0", "\uD83C\uDF70", "\uD83C\uDF7A", "\uD83E\uDDF9", "\uD83E\uDDF4")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Cambiar icono") },
        text = {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(6),
                modifier = Modifier.height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(emojis.size) { i ->
                    val e = emojis[i]
                    Surface(onClick = { selected = e }, shape = RoundedCornerShape(12.dp),
                        color = if (e == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) { Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) { Text(e, fontSize = 24.sp) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}