package com.sharedshoppinglists.app.presentation.shoppinglist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val listEmojis = listOf(
    "\uD83D\uDED2", "\uD83D\uDCCB", "\uD83C\uDFE0", "\uD83C\uDF7D\uFE0F",
    "\uD83C\uDF89", "\uD83C\uDF81", "\u2764\uFE0F", "\uD83D\uDC76",
    "\uD83D\uDC36", "\uD83D\uDC31", "\uD83C\uDFEB", "\uD83C\uDFE2",
    "\u2708\uFE0F", "\uD83C\uDFD6\uFE0F", "\uD83C\uDFCB\uFE0F", "\uD83D\uDC85",
    "\uD83D\uDC8A", "\uD83C\uDF53", "\uD83E\uDD6C", "\uD83E\uDDC0",
    "\uD83C\uDF70", "\uD83C\uDF7A", "\uD83E\uDDF9", "\uD83E\uDDF4"
)

@Composable
fun AddEditListDialog(
    initialName: String = "",
    initialEmoji: String = "",
    isEdit: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onConfirmWithEmoji: ((String, String) -> Unit)? = null
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var selectedEmoji by rememberSaveable { mutableStateOf(initialEmoji.ifBlank { "\uD83D\uDED2" }) }
    var showEmojiPicker by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Editar lista" else "Nueva lista") },
        text = {
            Column {
                // Emoji selector
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { showEmojiPicker = !showEmojiPicker },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(selectedEmoji, fontSize = 32.sp, modifier = Modifier.padding(12.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it },
                            label = { Text("Nombre de la lista") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                // Emoji grid
                if (showEmojiPicker) {
                    Spacer(Modifier.height(12.dp))
                    Text("Elegir icono", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.height(160.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(listEmojis) { emoji ->
                            Surface(
                                onClick = { selectedEmoji = emoji; showEmojiPicker = false },
                                shape = RoundedCornerShape(8.dp),
                                color = if (emoji == selectedEmoji) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                                    Text(emoji, fontSize = 24.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (onConfirmWithEmoji != null) onConfirmWithEmoji(name, selectedEmoji)
                    else onConfirm(name)
                },
                enabled = name.isNotBlank()
            ) { Text(if (isEdit) "Guardar" else "Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}