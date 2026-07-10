package com.sharedshoppinglists.app.presentation.discountcard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sharedshoppinglists.app.domain.model.CardType
import com.sharedshoppinglists.app.domain.model.Discount
import com.sharedshoppinglists.app.domain.model.DiscountCard
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun AddEditDiscountCardDialog(
    existingCard: DiscountCard? = null,
    onDismiss: () -> Unit,
    onConfirm: (DiscountCard) -> Unit
) {
    val isEdit = existingCard != null
    var selectedType by rememberSaveable {
        mutableStateOf(existingCard?.type?.name ?: CardType.CREDIT.name)
    }
    var issuer by rememberSaveable { mutableStateOf(existingCard?.issuer ?: "") }
    var supermarketName by rememberSaveable {
        mutableStateOf(existingCard?.applicableSupermarkets?.keys?.firstOrNull() ?: "")
    }
    var discountPercentage by rememberSaveable {
        val existing = existingCard?.applicableSupermarkets?.values?.firstOrNull()
        mutableStateOf(existing?.percentage?.toPlainString() ?: "")
    }
    var discountFixed by rememberSaveable {
        val existing = existingCard?.applicableSupermarkets?.values?.firstOrNull()
        mutableStateOf(existing?.fixedAmount?.toPlainString() ?: "")
    }
    var minimumPurchase by rememberSaveable {
        val existing = existingCard?.applicableSupermarkets?.values?.firstOrNull()
        mutableStateOf(existing?.minimumPurchase?.toPlainString() ?: "")
    }
    var validFromText by rememberSaveable {
        mutableStateOf(
            existingCard?.validFrom?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: ""
        )
    }
    var validUntilText by rememberSaveable {
        mutableStateOf(
            existingCard?.validUntil?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: ""
        )
    }

    val isFormValid = issuer.isNotBlank() && supermarketName.isNotBlank() &&
        (discountPercentage.isNotBlank() || discountFixed.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEdit) "Editar tarjeta" else "Nueva tarjeta de descuento")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Card type selector
                Text(
                    text = "Tipo de tarjeta",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == CardType.CREDIT.name,
                        onClick = { selectedType = CardType.CREDIT.name },
                        label = { Text("Crédito") }
                    )
                    FilterChip(
                        selected = selectedType == CardType.LOYALTY.name,
                        onClick = { selectedType = CardType.LOYALTY.name },
                        label = { Text("Beneficios") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Issuer
                OutlinedTextField(
                    value = issuer,
                    onValueChange = { issuer = it },
                    label = { Text("Emisor") },
                    placeholder = { Text("Ej: Visa, Carrefour") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Supermarket
                OutlinedTextField(
                    value = supermarketName,
                    onValueChange = { supermarketName = it },
                    label = { Text("Supermercado") },
                    placeholder = { Text("Ej: Carrefour, Coto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Discount fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = discountPercentage,
                        onValueChange = { discountPercentage = it },
                        label = { Text("% Descuento") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = discountFixed,
                        onValueChange = { discountFixed = it },
                        label = { Text("$ Fijo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Minimum purchase
                OutlinedTextField(
                    value = minimumPurchase,
                    onValueChange = { minimumPurchase = it },
                    label = { Text("Compra mínima (opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Validity dates
                Text(
                    text = "Vigencia (opcional, formato: AAAA-MM-DD)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = validFromText,
                        onValueChange = { validFromText = it },
                        label = { Text("Desde") },
                        placeholder = { Text("AAAA-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = validUntilText,
                        onValueChange = { validUntilText = it },
                        label = { Text("Hasta") },
                        placeholder = { Text("AAAA-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val discount = Discount(
                        percentage = discountPercentage.toBigDecimalOrNull(),
                        fixedAmount = discountFixed.toBigDecimalOrNull(),
                        minimumPurchase = minimumPurchase.toBigDecimalOrNull()
                    )
                    val card = DiscountCard(
                        id = existingCard?.id ?: UUID.randomUUID().toString(),
                        userId = existingCard?.userId ?: "",
                        type = CardType.valueOf(selectedType),
                        issuer = issuer.trim(),
                        applicableSupermarkets = mapOf(supermarketName.trim() to discount),
                        validFrom = parseDate(validFromText),
                        validUntil = parseDate(validUntilText)
                    )
                    onConfirm(card)
                },
                enabled = isFormValid
            ) {
                Text(if (isEdit) "Guardar" else "Agregar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun parseDate(text: String): LocalDate? {
    return try {
        if (text.isBlank()) null
        else LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: Exception) {
        null
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? {
    return try {
        if (isBlank()) null
        else BigDecimal(trim())
    } catch (_: NumberFormatException) {
        null
    }
}
