package com.example.mymaser.gui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

internal val LightColors = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201C),
    inversePrimary = Color(0xFF4FD8C4),
    secondary = Color(0xFF455A64),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E3E9),
    onSecondaryContainer = Color(0xFF101E24),
    tertiary = Color(0xFF455A64),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD5E3E9),
    onTertiaryContainer = Color(0xFF101E24),
    background = Color(0xFFF7F9F9),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE5E2),
    onSurfaceVariant = Color(0xFF3F4947),
    surfaceTint = Color(0xFF00695C),
    inverseSurface = Color(0xFF2D3131),
    inverseOnSurface = Color(0xFFEFF1F0),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD8DBDA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F3),
    surfaceContainer = Color(0xFFECEFEE),
    surfaceContainerHigh = Color(0xFFE6E9E8),
    surfaceContainerHighest = Color(0xFFE0E3E2)
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD8C4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFA7F3E6),
    inversePrimary = Color(0xFF00695C),
    secondary = Color(0xFFB0BEC5),
    onSecondary = Color(0xFF1B2B33),
    secondaryContainer = Color(0xFF324249),
    onSecondaryContainer = Color(0xFFD5E3E9),
    tertiary = Color(0xFFB0BEC5),
    onTertiary = Color(0xFF1B2B33),
    tertiaryContainer = Color(0xFF324249),
    onTertiaryContainer = Color(0xFFD5E3E9),
    background = Color(0xFF101414),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF191D1D),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceTint = Color(0xFF4FD8C4),
    inverseSurface = Color(0xFFE0E3E2),
    inverseOnSurface = Color(0xFF2D3131),
    outline = Color(0xFF899391),
    outlineVariant = Color(0xFF3F4947),
    surfaceBright = Color(0xFF363A3A),
    surfaceDim = Color(0xFF101414),
    surfaceContainerLowest = Color(0xFF0B0F0F),
    surfaceContainerLow = Color(0xFF191D1D),
    surfaceContainer = Color(0xFF1D2121),
    surfaceContainerHigh = Color(0xFF272B2B),
    surfaceContainerHighest = Color(0xFF323535)
)

@Immutable
data class MaserColors(
    val income: Color,
    val incomeContainer: Color,
    val donation: Color,
    val donationContainer: Color
)

internal val LightMaserColors = MaserColors(
    income = Color(0xFF1B6E3C),
    incomeContainer = Color(0xFFD7F2DF),
    donation = Color(0xFF9C4221),
    donationContainer = Color(0xFFFFDBD0)
)

internal val DarkMaserColors = MaserColors(
    income = Color(0xFF7BDB9C),
    incomeContainer = Color(0xFF0F3D22),
    donation = Color(0xFFFFB59E),
    donationContainer = Color(0xFF5C2410)
)
