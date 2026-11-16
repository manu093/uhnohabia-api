package com.sharedshoppinglists.app.presentation.shoppinglist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sharedshoppinglists.app.domain.classifier.ProductClassifier

private const val PREVIEW_LIMIT = 8

@Composable
fun ImportListDialog(onDismiss: () -> Unit, onImport: (List<String>) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val lines = remember(text) { text.lines().filter { it.isNotBlank() } }
    // Vista previa con el clasificador offline (el historial del usuario puede mejorarla al importar)
    val preview = remember(lines) {
        lines.mapNotNull { ProductClassifier.parseLine(it) }.map { it to ProductClassifier.classify(it.name) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar lista") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Pegá tu lista (un producto por línea). Detectamos cantidades, categoría y emoji automáticamente.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 200.dp),
                    placeholder = { Text("Ej:\n- 2 leches\n- 1 kg papa\n- Pan\n- Detergente x2") },
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 15
                )
                if (preview.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${preview.size} productos detectados",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    preview.take(PREVIEW_LIMIT).forEach { (item, suggestion) ->
                        PreviewRow(item, suggestion)
                    }
                    if (preview.size > PREVIEW_LIMIT) {
                        Text(
                            "y ${preview.size - PREVIEW_LIMIT} más…",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(lines) }, enabled = preview.isNotEmpty()) { Text("Importar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PreviewRow(item: ProductClassifier.ParsedItem, suggestion: ProductClassifier.Suggestion?) {
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(suggestion?.emoji ?: "📦")
        Spacer(Modifier.width(8.dp))
        val qty = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
        val amount = if (item.unit == "Unidad" && item.quantity == 1.0) "" else " · $qty ${item.unit}"
        Text(
            item.name + amount,
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            suggestion?.categoryName ?: ProductClassifier.OTROS,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
