package com.sharedshoppinglists.app.presentation.manualcomparator

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    viewModel: ManualComparatorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var scannedBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var scannedProductName by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri != null) {
            errorMessage = null
            try {
                val image = InputImage.fromFilePath(context, photoUri!!)
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                    .build()
                BarcodeScanning.getClient(options).process(image)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            val code = barcodes.first().rawValue ?: ""
                            scannedBarcode = code
                            // Lookup product name from Open Food Facts
                            Thread {
                                try {
                                    val url = java.net.URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name")
                                    val conn = url.openConnection() as java.net.HttpURLConnection
                                    conn.setRequestProperty("User-Agent", "UhNoHabia-Android/1.0")
                                    conn.connectTimeout = 10000; conn.readTimeout = 10000
                                    val body = conn.inputStream.bufferedReader().readText()
                                    val name = org.json.JSONObject(body).optJSONObject("product")?.optString("product_name", "") ?: ""
                                    scannedProductName = name.ifBlank { null }
                                } catch (_: Exception) { scannedProductName = null }
                            }.start()
                        } else { errorMessage = "No se detectó ningún código de barras." }
                    }
                    .addOnFailureListener { errorMessage = "Error al escanear: ${it.message}" }
            } catch (e: Exception) { errorMessage = "Error: ${e.message}" }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "barcode_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            photoUri = uri; cameraLauncher.launch(uri)
        } else { errorMessage = "Se necesita permiso de cámara." }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val file = File(context.cacheDir, "barcode_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            photoUri = uri; cameraLauncher.launch(uri)
        } else { permissionLauncher.launch(Manifest.permission.CAMERA) }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Escanear Código de Barras") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Text("Escaneá el código de barras de un producto para identificarlo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { launchCamera() }) { Text("📷 Tomar foto del código de barras") }
            Spacer(Modifier.height(16.dp))
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            scannedBarcode?.let {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Código: $it", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        scannedProductName?.let { name -> Text("Producto: $name", style = MaterialTheme.typography.bodyMedium) }
                            ?: Text("Buscando nombre del producto...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Usá el nombre del producto para buscarlo en el Catálogo de Precios.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
