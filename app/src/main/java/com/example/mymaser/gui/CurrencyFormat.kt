package com.example.mymaser.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.text.BidiFormatter
import com.example.mymaser.currency.DEFAULT_CURRENCY
import com.example.mymaser.currency.SupportedCurrency
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/** ISO 4217 code of the currency amounts are shown in. */
val LocalDisplayCurrency = staticCompositionLocalOf { DEFAULT_CURRENCY }

private const val WHOLE_TOLERANCE = 0.005

fun formatCurrency(
    amount: Double,
    trimWholeDecimals: Boolean = false,
    currencyCode: String = DEFAULT_CURRENCY
): String {
    val isWhole = abs(amount - round(amount)) < WHOLE_TOLERANCE
    val fractionDigits = if (trimWholeDecimals && isWhole) 0 else 2
    val formattedNumber = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }.format(amount)
    val symbol = SupportedCurrency.symbolFor(currencyCode)
    return BidiFormatter.getInstance().unicodeWrap("$symbol$formattedNumber")
}

@Composable
fun formatCurrency(amount: Double, trimWholeDecimals: Boolean = false): String =
    formatCurrency(amount, trimWholeDecimals, LocalDisplayCurrency.current)

fun formatSignedCurrency(
    amount: Double,
    isNegative: Boolean,
    currencyCode: String = DEFAULT_CURRENCY
): String {
    val sign = if (isNegative) "−" else "+"
    return BidiFormatter.getInstance()
        .unicodeWrap(sign + formatCurrency(abs(amount), false, currencyCode))
}

@Composable
fun formatSignedCurrency(amount: Double, isNegative: Boolean): String =
    formatSignedCurrency(amount, isNegative, LocalDisplayCurrency.current)

/** A [fraction] as a locale percentage; [signed] adds + or − except at exactly zero. */
fun formatPercent(fraction: Double, signed: Boolean = false): String {
    val text = NumberFormat.getPercentInstance(Locale.getDefault()).format(abs(fraction))
    return when {
        !signed -> text
        fraction > 0 -> "+$text"
        fraction < 0 -> "−$text"
        else -> text
    }
}
