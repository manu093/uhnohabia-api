package com.sharedshoppinglists.app.presentation.settings

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sharedshoppinglists.app.presentation.theme.allDesignStyles
import com.sharedshoppinglists.app.presentation.theme.getDesignStyle
import com.sharedshoppinglists.app.presentation.theme.saveDesignStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(title = { Text("Apariencia") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Theme color selector
            Text("🎨 Tema de color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val currentTheme = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE).getString("theme", "dynamic")
                val themes = listOf("dynamic" to "Material You", "minimal" to "Minimalista", "green" to "Fresh", "blue" to "Azul", "orange" to "Naranja")
                val colors = mapOf("dynamic" to MaterialTheme.colorScheme.primary, "minimal" to Color(0xFF424242),
                    "green" to Color(0xFF2D8F5E), "blue" to Color(0xFF1565C0), "orange" to Color(0xFFE65100))
                themes.forEach { (key, label) ->
                    val isSelected = currentTheme == key
                    Card(Modifier.width(80.dp).height(48.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { context.getSharedPreferences("app_theme", Context.MODE_PRIVATE).edit().putString("theme", key).apply(); (context as? Activity)?.recreate() }
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) colors[key]!! else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) colors[key]!!.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors[key]!! else MaterialTheme.colorScheme.onSurface)
                    } }
                }
            }

            // Design style selector
            Text("✏️ Estilo de diseño", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val currentStyle = getDesignStyle(context)
                allDesignStyles.forEach { style ->
                    val isSelected = currentStyle.name == style.name
                    val shape = RoundedCornerShape(style.cardCornerRadius)
                    Card(Modifier.width(90.dp).height(56.dp).clip(shape)
                        .clickable { saveDesignStyle(context, style); (context as? Activity)?.recreate() }
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                        shape = shape, elevation = CardDefaults.cardElevation(style.cardElevation)
                    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(style.name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    } }
                }
            }

            // Dark mode selector
            Text("🌙 Modo de pantalla", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val currentMode = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE).getString("dark_mode", "system") ?: "system"
                listOf("system" to "Auto", "light" to "Claro", "dark" to "Oscuro", "amoled" to "AMOLED", "gray" to "Gris").forEach { (key, label) ->
                    val isSelected = currentMode == key
                    Card(Modifier.width(70.dp).height(48.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { context.getSharedPreferences("app_theme", Context.MODE_PRIVATE).edit().putString("dark_mode", key).apply(); (context as? Activity)?.recreate() }
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    } }
                }
            }
        }
    }
}
