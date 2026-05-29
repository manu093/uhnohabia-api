package com.sharedshoppinglists.app.presentation.settings

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.sp
import com.sharedshoppinglists.app.presentation.theme.allDesignStyles
import com.sharedshoppinglists.app.presentation.theme.getDesignStyle
import com.sharedshoppinglists.app.presentation.theme.saveDesignStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentTheme = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE).getString("theme", "dynamic") ?: "dynamic"
    val currentMode = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE).getString("dark_mode", "system") ?: "system"
    val currentStyle = getDesignStyle(context)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Apariencia", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // === COLOR THEME ===
            Text("Color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            val themes = listOf(
                ThemeOption("dynamic", "Auto", "Colores del sistema", Color(0xFF6750A4), Color(0xFFEADDFF)),
                ThemeOption("green", "Fresh", "Verde y teal", Color(0xFF2D8F5E), Color(0xFFDBF5E7)),
                ThemeOption("blue", "Azul", "Azul clasico", Color(0xFF1565C0), Color(0xFFE3F2FD)),
                ThemeOption("orange", "Naranja", "Calido y energico", Color(0xFFE65100), Color(0xFFFFF3E0)),
                ThemeOption("minimal", "Gris", "Neutro y limpio", Color(0xFF424242), Color(0xFFF5F5F5))
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                themes.forEach { theme ->
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
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Color preview circle
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(theme.accentColor),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, "Seleccionado", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(theme.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(theme.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // === DARK MODE ===
            Text("Modo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            val modes = listOf(
                ModeOption("system", "\u2699\uFE0F", "Automatico", "Sigue al sistema"),
                ModeOption("light", "\u2600\uFE0F", "Claro", "Siempre claro"),
                ModeOption("dark", "\uD83C\uDF19", "Oscuro", "Siempre oscuro"),
                ModeOption("amoled", "\u26AB", "AMOLED", "Negro puro"),
                ModeOption("gray", "\uD83E\uDD4A", "Gris", "Oscuro suave")
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { mode ->
                    val isSelected = currentMode == mode.key
                    Surface(
                        onClick = {
                            context.getSharedPreferences("app_theme", Context.MODE_PRIVATE).edit().putString("dark_mode", mode.key).apply()
                            (context as? Activity)?.recreate()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Column(
                            Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(mode.emoji, fontSize = 20.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(mode.label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // === DESIGN STYLE ===
            Text("Estilo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            val styleDescriptions = mapOf(
                "Cl\u00e1sico" to Pair("\uD83D\uDCCB", "Cards con checkbox clasico"),
                "Grilla" to Pair("\uD83D\uDCF1", "Chips grandes tipo Bring!"),
                "Minimalista" to Pair("\u2712\uFE0F", "Ultra limpio, sin bordes"),
                "Compacto" to Pair("\uD83D\uDCCA", "Maximo contenido visible"),
                "Colorido" to Pair("\uD83C\uDF08", "Gradientes y colores"),
                "Moderno" to Pair("\uD83D\uDDBC\uFE0F", "Imagenes + checks circulares")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                allDesignStyles.forEach { style ->
                    val isSelected = currentStyle.name == style.name
                    val (emoji, desc) = styleDescriptions[style.name] ?: Pair("\uD83C\uDFA8", style.name)

                    Surface(
                        onClick = {
                            saveDesignStyle(context, style)
                            (context as? Activity)?.recreate()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(style.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, "Seleccionado", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class ThemeOption(val key: String, val label: String, val description: String, val accentColor: Color, val lightColor: Color)
private data class ModeOption(val key: String, val emoji: String, val label: String, val description: String)