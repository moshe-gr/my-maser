package com.example.mymaser.history

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.example.mymaser.currency.DEFAULT_CURRENCY
import com.example.mymaser.currency.SupportedCurrency
import com.example.mymaser.currency.openingAdjustment
import com.example.mymaser.dataStore
import com.example.mymaser.settings.SettingsKeys
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * One-time move to per-entry currencies and a balance derived from entries:
 * - existing entries take the currency whose symbol they were shown with;
 * - any gap between the old running total and the entries is kept as an opening adjustment,
 *   so nobody's balance changes on upgrade.
 */
suspend fun migrateToMultiCurrency(context: Context) {
    val prefs = context.dataStore.data.first()
    if (prefs[SettingsKeys.MULTI_CURRENCY_MIGRATED] == true) return

    val currency = prefs[SettingsKeys.DISPLAY_CURRENCY]
        ?: SupportedCurrency.fromSymbol(prefs[SettingsKeys.LEGACY_CURRENCY_SYMBOL])?.code
        ?: DEFAULT_CURRENCY
    val dao = HistoryDb.getInstance(context).getHistoryDao()
    if (currency != DEFAULT_CURRENCY) dao.setCurrencyForAll(currency)
    val adjustment = openingAdjustment(prefs[SettingsKeys.LEGACY_TOTAL_MASER], dao.getAll())

    context.dataStore.edit {
        it[SettingsKeys.DISPLAY_CURRENCY] = currency
        if (adjustment != null) {
            it[SettingsKeys.BALANCE_ADJUSTMENT] = adjustment
            it[SettingsKeys.BALANCE_ADJUSTMENT_CURRENCY] = currency
            it[SettingsKeys.BALANCE_ADJUSTMENT_DAY] = LocalDate.now().toEpochDay()
        }
        it[SettingsKeys.MULTI_CURRENCY_MIGRATED] = true
    }
}
