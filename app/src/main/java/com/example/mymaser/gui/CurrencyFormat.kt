package com.example.mymaser.gui

import androidx.core.text.BidiFormatter
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

private val ILS: Currency = Currency.getInstance("ILS")
private const val WHOLE_TOLERANCE = 0.005

fun formatCurrency(amount: Double, trimWholeDecimals: Boolean = false): String {
    val isWhole = abs(amount - round(amount)) < WHOLE_TOLERANCE
    val fractionDigits = if (trimWholeDecimals && isWhole) 0 else 2
    val formatted = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        currency = ILS
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }.format(amount)
    return BidiFormatter.getInstance().unicodeWrap(formatted)
}

fun formatSignedCurrency(amount: Double, isNegative: Boolean): String {
    val sign = if (isNegative) "−" else "+"
    return BidiFormatter.getInstance().unicodeWrap(sign + formatCurrency(abs(amount)))
}
