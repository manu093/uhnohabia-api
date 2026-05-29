package com.sharedshoppinglists.app.presentation.settings

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedshoppinglists.app.presentation.theme.allDesignStyles
import com.sharedshoppinglists.app.presentation.theme.getDesignStyle
import com.sharedshoppinglists.app.presentation.theme.saveDesignStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentTheme = remember { context.getSharedPreferences("app_theme", Context.MODE_PRIVATE).getString("theme", "dynamic") ?: "dynamic" }
    val currentMode = remember { context.getSharedPreferences("app_theme", Context.MODE_PRIVATE).getString("dark_mode", "system") ?: "system" }
    val currentStyle = remember { getDesignStyle(context) }

    val themes = remember { listOf(
        ThemeOption("dynamic", "Auto", "Colores del sistema", Color(0xFF6750A4), Color(0xFFEADDFF)),
        ThemeOption("green", "Fresh", "Verde y teal", Color(0xFF2D8F5E), Color(0xFFDBF5E7)),
        ThemeOption("blue", "Azul", "Azul clasico", Color(0xFF1565C0), Color(0xFFE3F2FD)),
        ThemeOption("orange", "Naranja", "Calido y energico", Color(0xFFE65100), Color(0xFFFFF3E0)),
        ThemeOption("minimal", "Gris", "Neutro y limpio", Color(0xFF424242), Color(0xFFF5F5F5))
    ) }

    val modes = remember { listOf(
        ModeOption("system", "\u2699\uFE0F", "Auto"),
        ModeOption("light", "\u2600\uFE0F", "Claro"),
        ModeOption("dark", "\uD83C\uDF19", "Oscuro"),
        ModeOption("amoled", "\u26AB", "AMOLED"),
        ModeOption("gray", "\uD83E\uDD4A", "Gris")
    ) }

    val styleDescriptions = remember { mapOf(
        "Cl\u00e1sico" to Pair("\uD83D\uDCCB", "Checkbox clasico"),
        "Grilla" to Pair("\uD83D\uDCF1", "Chips tipo Bring!"),
        "Minimalista" to Pair("\u2712\uFE0F", "Sin bordes"),
        "Compacto" to Pair("\uD83D\uDCCA", "Maximo visible"),
        "Colorido" to Pair("\uD83C\uDF08", "Gradientes"),
        "Moderno" to Pair("\uD83D\uDDBC\uFE0F", "Imagenes + circular")
    ) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Apariencia", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // === COLOR ===
            item {
                Text("Color", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
            items(themes) { theme ->
                val isSelected = currentTheme == theme.key
                Surface(
                    onClick = {
                        context.getSharedPreferences("app_theme", Context.MODE_PRIVATE).edit().putString("theme", theme.key).apply()
                        (context as? Activity)?.recreate()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) theme.lightColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, theme.accentColor) else null
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(theme.accentColor), contentAlignment = Alignment.Center) {
                            if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(theme.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(theme.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // === MODO ===
            item {
                Spacer(Modifier.height(8.dp))
                Text("Modo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEach { mode ->
                        val isSelected = currentMode == mode.key
                        Surface(
                            onClick = {
                                context.getSharedPreferences("app_theme", Context.MODE_PRIVATE).edit().putString("dark_mode", mode.key).apply()
                                (context as? Activity)?.recreate()
                            },
                            modifier = Modifier.weight(1f).height(64.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(mode.emoji, fontSize = 18.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(mode.label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // === ESTILO ===
            item {
                Spacer(Modifier.height(8.dp))
                Text("Estilo de productos", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
            }
            items(allDesignStyles) { style ->
                val isSelected = currentStyle.name == style.name
                val (emoji, desc) = styleDescriptions[style.name] ?: Pair("\uD83C\uDFA8", style.name)
                Surface(
                    onClick = { saveDesignStyle(context, style); (context as? Activity)?.recreate() },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(emoji, fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(style.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private data class ThemeOption(val key: String, val label: String, val description: String, val accentColor: Color, val lightColor: Color)
private data class ModeOption(val key: String, val emoji: String, val label: String)