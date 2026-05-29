package com.sharedshoppinglists.app.presentation.theme

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design style configuration that changes layout/shape/typography, not colors.
 */
data class AppDesignStyle(
    val name: String,
    val layoutMode: String = "classic", // classic, grid, minimal, colorful, compact
    val cardCornerRadius: Dp,
    val cardElevation: Dp,
    val buttonCornerRadius: Dp,
    val chipCornerRadius: Dp,
    val contentPadding: Dp,
    val itemSpacing: Dp,
    val shapes: Shapes,
    val typography: Typography
)

val LocalAppDesignStyle = staticCompositionLocalOf { defaultStyle }

val defaultStyle = AppDesignStyle(
    name = "Clásico",
    layoutMode = "classic",
    cardCornerRadius = 12.dp,
    cardElevation = 2.dp,
    buttonCornerRadius = 24.dp,
    chipCornerRadius = 8.dp,
    contentPadding = 16.dp,
    itemSpacing = 8.dp,
    shapes = Shapes(
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp)
    ),
    typography = Typography()
)

val roundedStyle = AppDesignStyle(
    name = "Grilla",
    layoutMode = "grid",
    cardCornerRadius = 24.dp,
    cardElevation = 0.dp,
    buttonCornerRadius = 28.dp,
    chipCornerRadius = 20.dp,
    contentPadding = 20.dp,
    itemSpacing = 12.dp,
    shapes = Shapes(
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(28.dp)
    ),
    typography = Typography(
        headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
)

val sharpStyle = AppDesignStyle(
    name = "Minimalista",
    layoutMode = "minimal",
    cardCornerRadius = 0.dp,
    cardElevation = 4.dp,
    buttonCornerRadius = 4.dp,
    chipCornerRadius = 4.dp,
    contentPadding = 12.dp,
    itemSpacing = 6.dp,
    shapes = Shapes(
        small = RoundedCornerShape(2.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(8.dp)
    ),
    typography = Typography(
        headlineLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp),
        titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
        labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    )
)

val compactStyle = AppDesignStyle(
    name = "Compacto",
    layoutMode = "compact",
    cardCornerRadius = 8.dp,
    cardElevation = 1.dp,
    buttonCornerRadius = 12.dp,
    chipCornerRadius = 6.dp,
    contentPadding = 10.dp,
    itemSpacing = 4.dp,
    shapes = Shapes(
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp)
    ),
    typography = Typography(
        headlineLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
        bodyMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontSize = 10.sp),
    )
)

val elegantStyle = AppDesignStyle(
    name = "Colorido",
    layoutMode = "colorful",
    cardCornerRadius = 16.dp,
    cardElevation = 6.dp,
    buttonCornerRadius = 16.dp,
    chipCornerRadius = 12.dp,
    contentPadding = 18.dp,
    itemSpacing = 10.dp,
    shapes = Shapes(
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp)
    ),
    typography = Typography(
        headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
        titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Light),
        titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.3.sp),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.2.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
        labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.5.sp),
    )
)

val modernStyle = AppDesignStyle(
    name = "Moderno",
    layoutMode = "modern",
    cardCornerRadius = 16.dp,
    cardElevation = 0.dp,
    buttonCornerRadius = 24.dp,
    chipCornerRadius = 16.dp,
    contentPadding = 20.dp,
    itemSpacing = 6.dp,
    shapes = Shapes(
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp)
    ),
    typography = Typography(
        headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    )
)

val allDesignStyles = listOf(defaultStyle, roundedStyle, sharpStyle, compactStyle, elegantStyle, modernStyle)

fun getDesignStyle(context: Context): AppDesignStyle {
    val name = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
        .getString("design_style", "Clásico") ?: "Clásico"
    return allDesignStyles.find { it.name == name } ?: defaultStyle
}

fun saveDesignStyle(context: Context, style: AppDesignStyle) {
    context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
        .edit().putString("design_style", style.name).apply()
}

@Composable
fun ProvideAppDesignStyle(style: AppDesignStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppDesignStyle provides style) {
        content()
    }
}
