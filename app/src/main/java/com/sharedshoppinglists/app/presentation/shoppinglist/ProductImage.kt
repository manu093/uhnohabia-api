package com.sharedshoppinglists.app.presentation.shoppinglist

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Memory cache de URLs de imagenes de productos.
 */
private val memoryCache = LruCache<String, String>(300)

/**
 * Obtiene URL de imagen del cache persistente (SharedPreferences).
 */
private fun getDiskCache(context: Context, key: String): String? {
    return context.getSharedPreferences("product_images", Context.MODE_PRIVATE)
        .getString(key, null)
}

/**
 * Guarda URL de imagen en cache persistente.
 */
private fun setDiskCache(context: Context, key: String, url: String) {
    context.getSharedPreferences("product_images", Context.MODE_PRIVATE)
        .edit().putString(key, url).apply()
}

/**
 * Pre-carga imagenes para una lista de productos en paralelo.
 * Llamar al entrar a la pantalla de detalle.
 */
suspend fun preloadProductImages(context: Context, productNames: List<String>) {
    coroutineScope {
        productNames.filter { name ->
            // Solo buscar los que no estan en cache
            memoryCache.get(name) == null && getDiskCache(context, name) == null
        }.take(10).map { name -> // Limitar a 10 en paralelo
            async(Dispatchers.IO) {
                searchProductImageInternal(context, name)
            }
        }.awaitAll()
    }
}

/**
 * Busca imagen de producto. Usa memory cache > disk cache > network.
 */
suspend fun searchProductImage(context: Context, productName: String): String? {
    // 1. Memory cache
    memoryCache.get(productName)?.let { return if (it == "NONE") null else it }

    // 2. Disk cache
    getDiskCache(context, productName)?.let { cached ->
        memoryCache.put(productName, cached)
        return if (cached == "NONE") null else cached
    }

    // 3. Network
    return searchProductImageInternal(context, productName)
}

private suspend fun searchProductImageInternal(context: Context, productName: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val query = productName.lowercase().trim().take(30)
            if (query.length < 3) {
                memoryCache.put(productName, "NONE")
                setDiskCache(context, productName, "NONE")
                return@withContext null
            }

            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$encoded&search_simple=1&action=process&json=1&page_size=3&fields=image_small_url,image_front_small_url,product_name&lc=es"
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "UhNoHabia-App/1.5.3")

            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(response)
            val products = json.optJSONArray("products")
            if (products != null && products.length() > 0) {
                for (i in 0 until products.length()) {
                    val product = products.getJSONObject(i)
                    val imageUrl = product.optString("image_front_small_url", "")
                        .ifBlank { product.optString("image_small_url", "") }
                    if (imageUrl.isNotBlank() && imageUrl.startsWith("http")) {
                        memoryCache.put(productName, imageUrl)
                        setDiskCache(context, productName, imageUrl)
                        return@withContext imageUrl
                    }
                }
            }
            memoryCache.put(productName, "NONE")
            setDiskCache(context, productName, "NONE")
            null
        } catch (_: Exception) {
            memoryCache.put(productName, "NONE")
            setDiskCache(context, productName, "NONE")
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
    val context = LocalContext.current
    var imageUrl by remember(productName) { mutableStateOf<String?>(memoryCache.get(productName)?.takeIf { it != "NONE" } ?: getDiskCache(context, productName)?.takeIf { it != "NONE" }) }
    var searched by remember(productName) { mutableStateOf(imageUrl != null) }

    LaunchedEffect(productName) {
        if (!searched) {
            imageUrl = searchProductImage(context, productName)
            searched = true
        }
    }

    if (imageUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = productName,
            modifier = modifier.size(size).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.size(size).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji.ifBlank { "\uD83D\uDCE6" }, fontSize = (size.value * 0.5f).sp)
        }
    }
}