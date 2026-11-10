package com.example.mymaser.gui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.mymaser.R

private fun heebo(weight: FontWeight) = Font(
    resId = R.font.heebo,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

private val Heebo = FontFamily(
    heebo(FontWeight.Normal),
    heebo(FontWeight.Medium),
    heebo(FontWeight.SemiBold),
    heebo(FontWeight.Bold)
)

private fun style(size: TextUnit, lineHeight: TextUnit, weight: FontWeight) = TextStyle(
    fontFamily = Heebo,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight
)

internal val MaserTypography = Typography(
    displayLarge = style(64.sp, 72.sp, FontWeight.SemiBold),
    displayMedium = style(45.sp, 52.sp, FontWeight.SemiBold),
    displaySmall = style(36.sp, 44.sp, FontWeight.SemiBold),
    headlineLarge = style(32.sp, 40.sp, FontWeight.SemiBold),
    headlineMedium = style(28.sp, 36.sp, FontWeight.SemiBold),
    headlineSmall = style(24.sp, 32.sp, FontWeight.SemiBold),
    titleLarge = style(22.sp, 28.sp, FontWeight.SemiBold),
    titleMedium = style(16.sp, 24.sp, FontWeight.SemiBold),
    titleSmall = style(14.sp, 20.sp, FontWeight.SemiBold),
    bodyLarge = style(16.sp, 24.sp, FontWeight.Normal),
    bodyMedium = style(14.sp, 20.sp, FontWeight.Normal),
    bodySmall = style(12.sp, 16.sp, FontWeight.Normal),
    labelLarge = style(14.sp, 20.sp, FontWeight.Medium),
    labelMedium = style(12.sp, 16.sp, FontWeight.Medium),
    labelSmall = style(11.sp, 16.sp, FontWeight.Medium)
)

/** Equal-width digits, so columns of amounts line up and counting numbers don't jitter. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")
