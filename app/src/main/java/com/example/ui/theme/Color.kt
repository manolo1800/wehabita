package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Base Palette (Fondos y Estructura)
// ==========================================
// #FDFBF7 (Blanco Hueso / Crema): Fondo principal acogedor y suave
val CreamBackground = Color(0xFFFDFBF7)

// #F4EFE6 (Arena / Beige Claro): Tarjetas, contenedores y menús secundarios
val SandSurface = Color(0xFFF4EFE6)
val SandSurfaceVariant = Color(0xFFEBE4D8)
val SandSurfaceCard = Color(0xFFF4EFE6)
val SandSurfaceContainer = Color(0xFFEDE6DA)
val SandSurfaceContainerHigh = Color(0xFFE5DDD0)

// Bordes y separadores sutiles en tono arena
val SandOutline = Color(0xFFD3CBC0)
val SandOutlineVariant = Color(0xFFE5DDD0)

// ==========================================
// Colores de Acento
// ==========================================
// #7A8D7B (Verde Salvia): Paz, bienestar, orden (Botones principales, tabs, estados activos)
val SageGreenPrimary = Color(0xFF7A8D7B)
val SageGreenPrimaryDark = Color(0xFF536354)
val SageGreenPrimaryLight = Color(0xFF9FB0A0)
val SageGreenContainer = Color(0xFFD6E0D7)
val OnSageGreenContainer = Color(0xFF263327)

// #C37B68 (Terracota Suave): Calidez, alertas, penalizaciones, botones secundarios
val TerracottaSecondary = Color(0xFFC37B68)
val TerracottaDark = Color(0xFF9E5745)
val TerracottaLight = Color(0xFFDE9987)
val TerracottaContainer = Color(0xFFF5DDD7)
val OnTerracottaContainer = Color(0xFF4A231A)

// ==========================================
// Tipografía e Iconos (Contraste Suave)
// ==========================================
// #3A3530 (Marrón Café Oscuro): Textos largos, títulos, elegancia premium
val EarthyDarkText = Color(0xFF3A3530)
val EarthyOnSurface = Color(0xFF3A3530)

// #7D756D (Gris Terroso Mediano): Textos secundarios, subtítulos, iconos desactivados
val EarthyMediumGray = Color(0xFF7D756D)
val EarthyOnSurfaceVariant = Color(0xFF7D756D)

// ==========================================
// Semantic Accents (Adaptados a la nueva paleta)
// ==========================================
val SuccessGreen = SageGreenPrimary
val SuccessGreenContainer = SageGreenContainer
val SuccessContainer = SageGreenContainer
val SuccessGreenDark = SageGreenPrimaryDark

val WarningTerracotta = TerracottaSecondary
val WarningTerracottaContainer = TerracottaContainer

val ErrorTerracotta = TerracottaSecondary
val ErrorTerracottaContainer = TerracottaContainer

val InfoSlate = Color(0xFF6B8B9E)
val InfoSlateContainer = Color(0xFFD8E5EC)

// ==========================================
// Compatibility Mappings (Mantiene compatibilidad con todas las pantallas)
// ==========================================
val ElegantDarkBackground = CreamBackground
val ElegantDarkSurface = SandSurface
val ElegantDarkSurfaceVariant = SandSurfaceVariant
val ElegantDarkSurfaceCard = SandSurfaceCard
val ElegantDarkOutline = SandOutline
val ElegantDarkOutlineVariant = SandOutlineVariant

val ElegantPurplePrimary = SageGreenPrimary
val ElegantPurpleDark = SageGreenPrimaryDark
val ElegantPurpleContainer = SageGreenContainer
val ElegantOnPurpleContainer = OnSageGreenContainer

val ElegantPenaltiesRed = TerracottaSecondary
val ElegantPenaltiesContainer = TerracottaContainer
val ElegantPenaltiesDark = TerracottaDark

val ElegantSuccessGreen = SageGreenPrimary
val ElegantSuccessContainer = SageGreenContainer

val ElegantWarningAmber = TerracottaSecondary
val ElegantWarningContainer = TerracottaContainer

val ElegantInfoBlue = InfoSlate
val ElegantInfoContainer = InfoSlateContainer

val ElegantTertiaryTeal = EarthyMediumGray
val ElegantTertiaryContainer = SandSurfaceVariant

val ElegantOnBackground = EarthyDarkText
val ElegantOnSurface = EarthyDarkText
val ElegantOnSurfaceVariant = EarthyMediumGray

val IndigoPrimary = SageGreenPrimary
val IndigoPrimaryLight = SageGreenPrimaryLight
val IndigoPrimaryDark = SageGreenPrimaryDark
val IndigoPrimaryContainer = SageGreenContainer
val OnIndigoPrimaryContainer = OnSageGreenContainer

val CoralSecondary = TerracottaSecondary
val CoralSecondaryLight = TerracottaLight
val CoralSecondaryContainer = TerracottaContainer
val OnCoralSecondaryContainer = OnTerracottaContainer

val TealTertiary = EarthyMediumGray
val TealTertiaryLight = EarthyMediumGray
val TealTertiaryContainer = SandSurfaceVariant
val OnTealTertiaryContainer = EarthyDarkText

val ElegantShoppingOrange = TerracottaSecondary
val ElegantShoppingContainer = TerracottaContainer
val WarningOrange = TerracottaSecondary
val WarningContainer = TerracottaContainer
val ErrorRed = TerracottaSecondary
val ErrorContainer = TerracottaContainer
val InfoBlue = InfoSlate
val InfoContainer = InfoSlateContainer
val PurpleAccent = SageGreenPrimary
val PurpleAccentContainer = SageGreenContainer

val BackgroundDark = CreamBackground
val SurfaceDark = SandSurface
val SurfaceVariantDark = SandSurfaceVariant
val OnSurfaceDark = EarthyDarkText
val OnSurfaceVariantDark = EarthyMediumGray
val OutlineDark = SandOutline

val BackgroundLight = CreamBackground
val SurfaceLight = SandSurface
val SurfaceVariantLight = SandSurfaceVariant
val OnSurfaceLight = EarthyDarkText
val OnSurfaceVariantLight = EarthyMediumGray
val OutlineLight = SandOutline
