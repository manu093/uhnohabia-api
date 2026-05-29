package com.sharedshoppinglists.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.activity.enableEdgeToEdge
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.sharedshoppinglists.app.presentation.auth.AuthViewModel
import com.sharedshoppinglists.app.presentation.navigation.AppNavigation
import com.sharedshoppinglists.app.presentation.navigation.Screen
import com.sharedshoppinglists.app.presentation.theme.ProvideAppDesignStyle
import com.sharedshoppinglists.app.presentation.theme.getDesignStyle
import androidx.core.view.WindowCompat
import com.sharedshoppinglists.app.data.sync.UpdateChecker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // Handle App Actions intent (Ok Google, agregá X a mi lista)
        val productFromVoice = intent?.getStringExtra("product_name")

        setContent {
            val designStyle = remember { getDesignStyle(this) }
            var updateAvailable by remember { mutableStateOf<com.sharedshoppinglists.app.data.sync.AppUpdate?>(null) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                updateAvailable = UpdateChecker.checkForUpdate(this@MainActivity)
            }
            if (updateAvailable != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { updateAvailable = null },
                    title = { androidx.compose.material3.Text("Actualizacion disponible") },
                    text = { androidx.compose.material3.Text("Version ${updateAvailable!!.versionName} disponible.\n${updateAvailable!!.releaseNotes}") },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { UpdateChecker.downloadAndInstall(this@MainActivity, updateAvailable!!); updateAvailable = null }) { androidx.compose.material3.Text("Actualizar") } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { updateAvailable = null }) { androidx.compose.material3.Text("Despues") } }
                )
            }
            UhNoHabiaTheme {
                ProvideAppDesignStyle(designStyle) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MainContent(pendingProduct = productFromVoice)
                    }
                }
            }
        }
    }
}

@Composable
fun UhNoHabiaTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val selectedTheme = remember {
        context.getSharedPreferences("app_theme", android.content.Context.MODE_PRIVATE)
            .getString("theme", "dynamic") ?: "dynamic"
    }
    val darkMode = remember {
        context.getSharedPreferences("app_theme", android.content.Context.MODE_PRIVATE)
            .getString("dark_mode", "system") ?: "system"
    }
    val isDark = when (darkMode) {
        "light" -> false
        "dark", "amoled", "gray" -> true
        else -> darkTheme // "system"
    }

    val colorScheme = when (selectedTheme) {
        "minimal" -> if (isDark) darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF9E9E9E),
            onPrimary = androidx.compose.ui.graphics.Color.White,
            primaryContainer = androidx.compose.ui.graphics.Color(0xFF2C2C2C),
            secondary = androidx.compose.ui.graphics.Color(0xFF757575),
            background = androidx.compose.ui.graphics.Color(0xFF121212),
            surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
        ) else lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF424242),
            onPrimary = androidx.compose.ui.graphics.Color.White,
            primaryContainer = androidx.compose.ui.graphics.Color(0xFFF5F5F5),
            secondary = androidx.compose.ui.graphics.Color(0xFF757575),
            background = androidx.compose.ui.graphics.Color.White,
            surface = androidx.compose.ui.graphics.Color.White,
        )
        "green" -> if (isDark) darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF7DDBA3),
            onPrimary = androidx.compose.ui.graphics.Color(0xFF003920),
            primaryContainer = androidx.compose.ui.graphics.Color(0xFF1A4D2E),
            onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFA5F2C8),
            secondary = androidx.compose.ui.graphics.Color(0xFF81C784),
            tertiary = androidx.compose.ui.graphics.Color(0xFF4ECDC4),
            background = androidx.compose.ui.graphics.Color(0xFF0F1F15),
            surface = androidx.compose.ui.graphics.Color(0xFF152A1C),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1E3527),
            onBackground = androidx.compose.ui.graphics.Color(0xFFE0F2E9),
            onSurface = androidx.compose.ui.graphics.Color(0xFFE0F2E9),
            onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB0CCBA),
        ) else lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF2D8F5E),
            onPrimary = androidx.compose.ui.graphics.Color.White,
            primaryContainer = androidx.compose.ui.graphics.Color(0xFFDBF5E7),
            onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF0B3D20),
            secondary = androidx.compose.ui.graphics.Color(0xFF43A047),
            tertiary = androidx.compose.ui.graphics.Color(0xFF4ECDC4),
            background = androidx.compose.ui.graphics.Color(0xFFF7FBF9),
            surface = androidx.compose.ui.graphics.Color.White,
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEDF5F0),
            onBackground = androidx.compose.ui.graphics.Color(0xFF1A2E22),
            onSurface = androidx.compose.ui.graphics.Color(0xFF1A2E22),
            onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF4A6B55),
        )
        "blue" -> if (isDark) darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF42A5F5),
            onPrimary = androidx.compose.ui.graphics.Color.White,
            primaryContainer = androidx.compose.ui.graphics.Color(0xFF0D2137),
            secondary = androidx.compose.ui.graphics.Color(0xFF64B5F6),
            tertiary = androidx.compose.ui.graphics.Color(0xFF1E88E5),
            background = androidx.compose.ui.graphics.Color(0xFF0A1929),
            surface = androidx.compose.ui.graphics.Color(0xFF102A43),
        ) else lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF1565C0),
            onPrimary = androidx.compose.ui.graphics.Color.White,
            primaryContainer = androidx.compose.ui.graphics.Color(0xFFE3F2FD),
            secondary = androidx.compose.ui.graphics.Color(0xFF1976D2),
            tertiary = androidx.compose.ui.graphics.Color(0xFF0D47A1),
            background = androidx.compose.ui.graphics.Color(0xFFF5F9FF),
            surface = androidx.compose.ui.graphics.Color.White,
        )
        "orange" -> if (isDark) darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFFFF9800),
            onPrimary = androidx.compose.ui.graphics.Color.Black,
            primaryContainer = androidx.compose.ui.graphics.Color(0xFF3E2700),
            secondary = androidx.compose.ui.graphics.Color(0xFFFFB74D),
            tertiary = androidx.compose.ui.graphics.Color(0xFFF57C00),
            background = androidx.compose.ui.graphics.Color(0xFF1A1000),
            surface = androidx.compose.ui.graphics.Color(0xFF261A00),
        ) else lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFFE65100),
            onPrimary = androidx.compose.ui.graphics.Color.White,
            primaryContainer = androidx.compose.ui.graphics.Color(0xFFFFF3E0),
            secondary = androidx.compose.ui.graphics.Color(0xFFF57C00),
            tertiary = androidx.compose.ui.graphics.Color(0xFFBF360C),
            background = androidx.compose.ui.graphics.Color(0xFFFFFBF5),
            surface = androidx.compose.ui.graphics.Color.White,
        )
        else -> when { // "dynamic"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            isDark -> darkColorScheme()
            else -> lightColorScheme()
        }
    }
    val designStyle = remember { getDesignStyle(context) }
    // Apply AMOLED or gray overrides
    val finalColorScheme = when (darkMode) {
        "amoled" -> colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF111111)
        )
        "gray" -> colorScheme.copy(
            background = Color(0xFF2A2A2A),
            surface = Color(0xFF333333),
            surfaceVariant = Color(0xFF3D3D3D)
        )
        else -> colorScheme
    }
    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = designStyle.typography,
        shapes = designStyle.shapes,
        content = content
    )
}

@Composable
private fun MainContent(pendingProduct: String? = null) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle(initialValue = null)
    val navController = rememberNavController()

    val startDestination = if (currentUser != null) {
        Screen.ShoppingLists.route
    } else {
        Screen.Login.route
    }

    AppNavigation(
        navController = navController,
        startDestination = startDestination,
        pendingProduct = pendingProduct
    )
}

