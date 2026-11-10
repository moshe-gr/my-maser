package com.example.mymaser.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.text.BidiFormatter
import com.example.mymaser.settings.DEFAULT_CURRENCY_SYMBOL
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

val LocalCurrencySymbol = staticCompositionLocalOf { DEFAULT_CURRENCY_SYMBOL }

private const val WHOLE_TOLERANCE = 0.005

fun formatCurrency(
    amount: Double,
    trimWholeDecimals: Boolean = false,
    currencySymbol: String = DEFAULT_CURRENCY_SYMBOL
): String {
    val isWhole = abs(amount - round(amount)) < WHOLE_TOLERANCE
    val fractionDigits = if (trimWholeDecimals && isWhole) 0 else 2
    val formattedNumber = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }.format(amount)
    return BidiFormatter.getInstance().unicodeWrap("$currencySymbol$formattedNumber")
}

@Composable
fun formatCurrency(amount: Double, trimWholeDecimals: Boolean = false): String =
    formatCurrency(amount, trimWholeDecimals, LocalCurrencySymbol.current)

fun formatSignedCurrency(
    amount: Double,
    isNegative: Boolean,
    currencySymbol: String = DEFAULT_CURRENCY_SYMBOL
): String {
    val sign = if (isNegative) "−" else "+"
    return BidiFormatter.getInstance()
        .unicodeWrap(sign + formatCurrency(abs(amount), false, currencySymbol))
}

@Composable
fun formatSignedCurrency(amount: Double, isNegative: Boolean): String =
    formatSignedCurrency(amount, isNegative, LocalCurrencySymbol.current)
