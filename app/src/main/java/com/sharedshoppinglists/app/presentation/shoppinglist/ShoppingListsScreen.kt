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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Loyalty
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
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
    onMyBankPromosClick: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val lists by viewModel.shoppingLists.collectAsStateWithLifecycle()
    val pendingInvitations by viewModel.pendingInvitations.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val catalogStatus by viewModel.catalogStatus.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var listToDelete by rememberSaveable { mutableStateOf<ShoppingList?>(null) }
    var listToRename by rememberSaveable { mutableStateOf<ShoppingList?>(null) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var showDuplicateDialog by rememberSaveable { mutableStateOf(false) }
    var listToEditEmoji by rememberSaveable { mutableStateOf<ShoppingList?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(visible = fabExpanded, enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom), exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FabOption("Nueva lista", Icons.Filled.PostAdd) { fabExpanded = false; showCreateDialog = true }
                        FabOption("Producto recurrente", Icons.Filled.Star) { fabExpanded = false; onKnownProductsClick() }
                        FabOption("Duplicar lista", Icons.Filled.ContentCopy) { fabExpanded = false; showDuplicateDialog = true }
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
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 48.dp, bottom = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column {
                            val greeting = remember {
                                val hour = java.time.LocalTime.now().hour
                                when {
                                    hour < 12 -> "Buenos dias \u2600\uFE0F"
                                    hour < 19 -> "Buenas tardes \uD83C\uDF24\uFE0F"
                                    else -> "Buenas noches \uD83C\uDF19"
                                }
                            }
                            Text(greeting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            Row {
                                Text("Uh ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color(0xFFFF6B6B))
                                Text("No ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color(0xFF4ECDC4))
                                Text("Hab\u00eda", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color(0xFFFFE66D))
                            }
                            if (lists.isNotEmpty()) {
                                Text("${lists.size} lista${if (lists.size > 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            catalogStatus?.let { status ->
                                if (status.lastRun.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "\uD83D\uDD04 Precios actualizados: ${formatDbDate(status.lastRun)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = !showMenu }) { Icon(Icons.Default.MoreVert, "Más opciones", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Cerrar sesión") },
                                    onClick = { showMenu = false; onLogout() },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) }
                                )
                            }
                        }
                    }
                }
            }

            // Accesos rapidos a herramientas
            item {
                Spacer(Modifier.height(4.dp))
                QuickAccessRow(
                    onKnownProducts = onKnownProductsClick,
                    onCategories = onCategoryManagementClick,
                    onSupermarkets = onMySupermarketsClick,
                    onBankPromos = onMyBankPromosClick,
                    onDiscountCards = onDiscountCardsClick,
                    onBarcode = onBarcodeScannerClick,
                    onAffinityPrograms = onAffinityProgramsClick
                )
                Spacer(Modifier.height(8.dp))
            }

            // Notifications
            if (notifications.isNotEmpty()) {
                item {
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF3E0)) {
                        Column(Modifier.padding(14.dp)) {
                            notifications.forEach { msg -> Text(msg, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF5D4037)) }
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
                    Column(Modifier.fillMaxWidth().padding(vertical = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\uD83D\uDED2", fontSize = 64.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("No tenes listas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Toca + para crear tu primera lista", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(lists, key = { it.id }) { list ->
                    ListCard(list = list, onClick = { onListClick(list.id) }, onDelete = { listToDelete = list }, onRename = { listToRename = list }, onEditEmoji = { listToEditEmoji = list })
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
        EditEmojiDialog(currentEmoji = list.emoji, onDismiss = { listToEditEmoji = null }, onConfirm = { emoji -> viewModel.updateListEmoji(list.id, emoji); listToEditEmoji = null })
    }
    listToRename?.let { list ->
        RenameListDialog(currentName = list.name, onDismiss = { listToRename = null }, onConfirm = { newName -> viewModel.renameList(list.id, newName); listToRename = null })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListCard(list: ShoppingList, onClick: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit, onEditEmoji: () -> Unit = {}) {
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())
    val listEmoji = list.emoji.ifBlank { "\uD83D\uDED2" }
    val timeSinceUpdate = Duration.between(list.updatedAt, Instant.now())
    val isRecent = timeSinceUpdate.toHours() < 2

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).combinedClickable(onClick = onClick, onLongClick = onRename),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Emoji - tappable to change
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)).clickable { onEditEmoji() },
                contentAlignment = Alignment.Center
            ) { Text(listEmoji, fontSize = 24.sp) }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val productCount by androidx.compose.runtime.produceState(initialValue = 0, list.id) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try { AppDatabase.getInstance(ctx).productDao().countByListId(list.id) } catch (_: Exception) { 0 }
                    }
                }
                val pendingCount by androidx.compose.runtime.produceState(initialValue = 0, list.id) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try { AppDatabase.getInstance(ctx).productDao().countPendingByListId(list.id) } catch (_: Exception) { 0 }
                    }
                }

                Text(list.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (productCount > 0) {
                        Text("$pendingCount de $productCount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Vacia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    if (list.isShared) {
                        Text("\u00B7 Compartida", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Arrow
            Text("\u203A", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun PendingInvitationsBanner(invitations: List<PendingInvitation>, onAccept: (String) -> Unit, onDecline: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFFE8F5E9)) {
        Column(Modifier.padding(14.dp)) {
            Text("\uD83D\uDCE9 ${invitations.size} invitacion${if (invitations.size > 1) "es" else ""} pendiente${if (invitations.size > 1) "s" else ""}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
            Spacer(Modifier.height(10.dp))
            invitations.forEach { inv ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(inv.listName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAccept(inv.id) }, Modifier.height(34.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Si", style = MaterialTheme.typography.labelMedium) }
                        OutlinedButton(onClick = { onDecline(inv.id) }, Modifier.height(34.dp), shape = RoundedCornerShape(10.dp)) { Text("No", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FabOption(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

private data class QuickAccess(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
private fun QuickAccessRow(
    onKnownProducts: () -> Unit,
    onCategories: () -> Unit,
    onSupermarkets: () -> Unit,
    onBankPromos: () -> Unit,
    onDiscountCards: () -> Unit,
    onBarcode: () -> Unit,
    onAffinityPrograms: () -> Unit
) {
    val items = listOf(
        QuickAccess("Mis productos", Icons.Filled.Inventory2, onKnownProducts),
        QuickAccess("Categorias", Icons.Filled.Category, onCategories),
        QuickAccess("Super", Icons.Filled.Store, onSupermarkets),
        QuickAccess("Promos banco", Icons.Filled.CreditCard, onBankPromos),
        QuickAccess("Tarjetas", Icons.Filled.Loyalty, onDiscountCards),
        QuickAccess("Afinidad", Icons.Filled.CardMembership, onAffinityPrograms),
        QuickAccess("Escanear", Icons.Filled.QrCodeScanner, onBarcode)
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items) { qa ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(64.dp)
            ) {
                Surface(
                    onClick = qa.onClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(qa.icon, contentDescription = qa.label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    qa.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp
                )
            }
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
                lists.forEach { list ->
                    Row(Modifier.fillMaxWidth().clickable { selectedListId = list.id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(6), modifier = Modifier.height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(emojis.size) { i ->
                    val e = emojis[i]
                    Surface(onClick = { selected = e }, shape = RoundedCornerShape(10.dp),
                        color = if (e == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) { Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) { Text(e, fontSize = 22.sp) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

// Formatea el timestamp de ultima actualizacion de la DB (ej: "2026-07-10 02:50:04.469426")
// a "dd/MM/yyyy HH:mm". Muestra el valor tal cual lo reporta el backend (sin conversion de zona).
private fun formatDbDate(raw: String): String = try {
    val clean = raw.trim().replace(" ", "T").substringBefore(".")
    java.time.LocalDateTime.parse(clean)
        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
} catch (_: Exception) {
    raw.substringBefore(".").replace("T", " ").trim()
}
