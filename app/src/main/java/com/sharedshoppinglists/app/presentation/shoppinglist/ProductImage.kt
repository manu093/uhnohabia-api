package com.sharedshoppinglists.app.presentation.shoppinglist

import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Cache de URLs de imagenes de productos para no repetir requests.
 */
private val imageCache = LruCache<String, String>(200)

/**
 * Busca una imagen de producto en Open Food Facts por nombre.
 * Retorna URL de imagen o null.
 */
suspend fun searchProductImage(productName: String): String? {
    // Check cache first
    imageCache.get(productName)?.let { return it }

    return withContext(Dispatchers.IO) {
        try {
            // Clean product name for search
            val query = productName.lowercase()
                .replace(Regex("[^a-záéíóúñü0-9 ]"), "")
                .trim()
                .take(40)

            if (query.length < 2) return@withContext null

            val url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$query&search_simple=1&action=process&json=1&page_size=1&fields=image_small_url,image_url,product_name"
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("User-Agent", "UhNoHabia-App/1.3.0")

            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(response)
            val products = json.optJSONArray("products")
            if (products != null && products.length() > 0) {
                val product = products.getJSONObject(0)
                val imageUrl = product.optString("image_small_url", "")
                    .ifBlank { product.optString("image_url", "") }
                if (imageUrl.isNotBlank()) {
                    imageCache.put(productName, imageUrl)
                    return@withContext imageUrl
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Composable que muestra la imagen del producto o un fallback con emoji.
 */
@Composable
fun ProductImage(
    productName: String,
    emoji: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    var imageUrl by remember(productName) { mutableStateOf<String?>(null) }
    var searched by remember(productName) { mutableStateOf(false) }

    LaunchedEffect(productName) {
        if (!searched) {
            imageUrl = searchProductImage(productName)
            searched = true
        }
    }

    if (imageUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = productName,
            modifier = modifier.size(size).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        // Fallback: emoji in a colored box
        Box(
            modifier = modifier.size(size).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji.ifBlank { "\uD83D\uDCE6" }, fontSize = (size.value * 0.45f).sp)
        }
    }
}