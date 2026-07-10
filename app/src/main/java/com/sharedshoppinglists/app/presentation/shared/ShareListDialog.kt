package com.sharedshoppinglists.app.presentation.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ShareListDialog(
    listId: String,
    viewModel: SharedListViewModel,
    onDismiss: () -> Unit,
    onCopyLink: (String) -> Unit
) {
    val shareResult by viewModel.shareResult.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {
            viewModel.resetShareResult()
            onDismiss()
        },
        title = { Text("Compartir lista") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // --- Invitar por email ---
                Text(
                    text = "Invitar por correo electrónico",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.shareByEmail(listId, email) },
                    enabled = email.isNotBlank() && shareResult !is ShareResult.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Email, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enviar invitación")
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // --- Compartir por enlace ---
                Text(
                    text = "Compartir por enlace",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.shareByLink(listId) },
                    enabled = shareResult !is ShareResult.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generar enlace")
                }

                // --- Estado del resultado ---
                Spacer(modifier = Modifier.height(12.dp))
                when (val result = shareResult) {
                    is ShareResult.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Procesando...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    is ShareResult.Success -> {
                        Text(
                            text = "✓ Invitación enviada correctamente",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is ShareResult.LinkGenerated -> {
                        Column {
                            Text(
                                text = "Enlace generado:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = result.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(onClick = { onCopyLink(result.url) }) {
                                Text("Copiar enlace")
                            }
                        }
                    }
                    is ShareResult.Error -> {
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is ShareResult.Idle -> { /* nada */ }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.resetShareResult()
                onDismiss()
            }) {
                Text("Cerrar")
            }
        }
    )
}
