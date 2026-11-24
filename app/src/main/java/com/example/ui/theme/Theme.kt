package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTealContainer,
    secondary = AmberAccent,
    onSecondary = Color.White,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = StatusInfo,
    onTertiary = Color.White,
    background = SlateBackgroundLight,
    onBackground = SlateTextPrimary,
    surface = SurfaceWhite,
    onSurface = SlateTextPrimary,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = SlateTextSecondary,
    outline = OutlineLight,
    error = StatusError,
    onError = Color.White,
    errorContainer = StatusErrorBg,
    onErrorContainer = StatusError
)

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = OnTealContainer,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = TealContainer,
    secondary = AmberAccentDark,
    onSecondary = OnAmberContainer,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = AmberContainer,
    tertiary = StatusInfo,
    onTertiary = Color.White,
    background = SlateBackgroundDark,
    onBackground = SlateTextDark,
    surface = SurfaceDark,
    onSurface = SlateTextDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = SlateTextSecondaryDark,
    outline = OutlineDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA)
)

val StoreVisitShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun StoreVisitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StoreVisitTypography,
        shapes = StoreVisitShapes,
        content = content
    )
}
