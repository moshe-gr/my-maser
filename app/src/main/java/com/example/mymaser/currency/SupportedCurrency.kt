package com.example.mymaser.currency

import androidx.annotation.StringRes
import com.example.mymaser.R

const val DEFAULT_CURRENCY = "ILS"

/** Rates are stored against the euro, the base currency of the ECB reference rates. */
const val BASE_CURRENCY = "EUR"

enum class SupportedCurrency(val code: String, val symbol: String, @StringRes val nameRes: Int) {
    ILS("ILS", "₪", R.string.currency_ils),
    USD("USD", "$", R.string.currency_usd),
    EUR("EUR", "€", R.string.currency_eur),
    GBP("GBP", "£", R.string.currency_gbp);

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code }

        fun fromSymbol(symbol: String?) = entries.firstOrNull { it.symbol == symbol }

        fun symbolFor(code: String) = fromCode(code)?.symbol ?: code
    }
}
