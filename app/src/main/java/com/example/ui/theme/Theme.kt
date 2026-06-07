package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Standardized Dark Color Scheme for brand-consistent Slate UI
private val SlateDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF0891B2),
    onPrimaryContainer = Color.White,
    secondary = SilveryBlue,
    onSecondary = Color(0xFF0F172A),
    background = SlateBackground,
    onBackground = Color(0xFFF8FAFC),
    surface = SlateSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569),
    error = NeonError,
    onError = Color.White
)

private val SlateLightColorScheme = lightColorScheme(
    primary = Color(0xFF0891B2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECFEFF),
    onPrimaryContainer = Color(0xFF164E63),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFE11D48),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to preserve brand theme consistency
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) SlateDarkColorScheme else SlateLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
