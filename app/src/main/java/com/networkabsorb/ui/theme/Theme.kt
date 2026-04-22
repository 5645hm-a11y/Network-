package com.networkabsorb.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Minimalist dark-first color palette
private val DarkColors = darkColorScheme(
    primary          = Color(0xFF00E5FF),   // Cyan accent
    onPrimary        = Color(0xFF003640),
    primaryContainer = Color(0xFF004E5C),
    secondary        = Color(0xFF4FC3F7),
    background       = Color(0xFF0A0E14),   // Near-black
    surface          = Color(0xFF141920),
    onBackground     = Color(0xFFE0E8F0),
    onSurface        = Color(0xFFB0BEC5),
    error            = Color(0xFFCF6679)
)

private val LightColors = lightColorScheme(
    primary          = Color(0xFF006478),
    onPrimary        = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2EBF2),
    secondary        = Color(0xFF0277BD),
    background       = Color(0xFFF5F7FA),
    surface          = Color(0xFFFFFFFF),
    onBackground     = Color(0xFF1A1C1E),
    onSurface        = Color(0xFF42474E)
)

@Composable
fun NetworkAbsorbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography  = Typography,
        content     = content
    )
}
