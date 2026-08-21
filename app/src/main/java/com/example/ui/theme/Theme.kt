package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ElegantPurplePrimary,
    onPrimary = ElegantOnPurpleContainer,
    primaryContainer = ElegantPurpleDark,
    onPrimaryContainer = ElegantPurpleContainer,
    secondary = ElegantPenaltiesRed,
    onSecondary = ElegantPenaltiesContainer,
    secondaryContainer = ElegantWarningContainer,
    onSecondaryContainer = ElegantWarningAmber,
    tertiary = ElegantTertiaryTeal,
    onTertiary = Color(0xFF003731),
    tertiaryContainer = ElegantTertiaryContainer,
    onTertiaryContainer = ElegantTertiaryTeal,
    background = ElegantDarkBackground,
    surface = ElegantDarkSurface,
    surfaceVariant = ElegantDarkSurfaceVariant,
    onBackground = ElegantOnBackground,
    onSurface = ElegantOnSurface,
    onSurfaceVariant = ElegantOnSurfaceVariant,
    outline = ElegantDarkOutline,
    outlineVariant = ElegantDarkOutlineVariant,
    error = ElegantPenaltiesRed,
    onError = ElegantPenaltiesContainer,
    errorContainer = ElegantPenaltiesContainer,
    onErrorContainer = ElegantPenaltiesRed
)

private val LightColorScheme = DarkColorScheme // Elegant Dark is the designated primary aesthetic

@Composable
fun WeHabitaTheme(
    darkTheme: Boolean = true, // Default to Elegant Dark
    dynamicColor: Boolean = false, // Use our handcrafted rich palette by default
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
