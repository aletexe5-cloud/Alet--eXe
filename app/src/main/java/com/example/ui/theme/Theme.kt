package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PremiumDarkColorScheme = darkColorScheme(
    primary = PremiumGold,
    onPrimary = PureBlack,
    primaryContainer = SapphireMedium,
    onPrimaryContainer = Color.White,
    secondary = SilveryGrey,
    onSecondary = PureBlack,
    background = PureBlack,
    onBackground = Color(0xFFF1F5F9),
    surface = DarkGreySurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = DeepSapphireVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334155),
    error = NeonError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark Theme for Premium Luxury TimeFlow feel
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PremiumDarkColorScheme,
        typography = Typography,
        content = content
    )
}
