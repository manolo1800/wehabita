package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// WebHabita Palette:
// #FDFBF7 (Blanco Hueso / Crema) - Fondo principal
// #F4EFE6 (Arena / Beige Claro) - Superficies y tarjetas
// #7A8D7B (Verde Salvia) - Acento primario
// #C37B68 (Terracota Suave) - Acento secundario y alertas
// #3A3530 (Marrón Café Oscuro) - Tipografía y títulos
// #7D756D (Gris Terroso Mediano) - Subtítulos e iconos secundarios
private val WebHabitaColorScheme = lightColorScheme(
    primary = SageGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = SageGreenContainer,
    onPrimaryContainer = OnSageGreenContainer,

    secondary = TerracottaSecondary,
    onSecondary = Color.White,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = OnTerracottaContainer,

    tertiary = EarthyMediumGray,
    onTertiary = Color.White,
    tertiaryContainer = SandSurfaceVariant,
    onTertiaryContainer = EarthyDarkText,

    background = CreamBackground,
    onBackground = EarthyDarkText,

    surface = SandSurface,
    onSurface = EarthyDarkText,
    surfaceVariant = SandSurfaceVariant,
    onSurfaceVariant = EarthyMediumGray,

    surfaceContainer = SandSurfaceContainer,
    surfaceContainerHigh = SandSurfaceContainerHigh,

    outline = SandOutline,
    outlineVariant = SandOutlineVariant,

    error = TerracottaSecondary,
    onError = Color.White,
    errorContainer = TerracottaContainer,
    onErrorContainer = OnTerracottaContainer
)

@Composable
fun WeHabitaTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WebHabitaColorScheme,
        typography = Typography,
        content = content
    )
}
