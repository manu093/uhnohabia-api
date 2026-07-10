package com.sharedshoppinglists.app.presentation.manualcomparator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    viewModel: ManualComparatorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scannedBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var scannedProductName by rememberSaveable { mutableStateOf<String?>(null) }
    var lookingUp by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    fun lookupName(code: String) {
        scope.launch {
            lookingUp = true
            val name = withContext(Dispatchers.IO) {
                try {
                    val url = java.net.URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("User-Agent", "UhNoHabia-Android/1.0")
                    conn.connectTimeout = 10000; conn.readTimeout = 10000
                    val body = conn.inputStream.bufferedReader().readText()
                    org.json.JSONObject(body).optJSONObject("product")?.optString("product_name", "")?.ifBlank { null }
                } catch (_: Exception) { null }
            }
            scannedProductName = name
            lookingUp = false
        }
    }

    fun launchScanner() {
        errorMessage = null
        // Escaner en vivo de Google Play Services: no requiere permiso de camara ni CameraX.
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val code = barcode.rawValue ?: ""
                if (code.isBlank()) {
                    errorMessage = "No se leyó ningún código."
                    return@addOnSuccessListener
                }
                scannedBarcode = code
                scannedProductName = null
                lookupName(code)
            }
            .addOnCanceledListener { /* el usuario cerró el escáner */ }
            .addOnFailureListener { errorMessage = "No se pudo abrir el escáner: ${it.message}" }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Escanear Código de Barras") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Apuntá la cámara al código de barras del producto para identificarlo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { launchScanner() }) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Escanear")
            }
            Spacer(Modifier.height(16.dp))
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            scannedBarcode?.let {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Código: $it", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        when {
                            lookingUp -> Text("Buscando nombre del producto...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            scannedProductName != null -> Text("Producto: $scannedProductName", style = MaterialTheme.typography.bodyMedium)
                            else -> Text("No se encontró el nombre. Usá el código para buscarlo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Usá el nombre del producto para buscarlo en el Catálogo de Precios.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
