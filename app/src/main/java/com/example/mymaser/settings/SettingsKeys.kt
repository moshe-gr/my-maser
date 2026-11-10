package com.example.mymaser.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    val PUBLIC_DONATION_NAME = stringPreferencesKey("public_donation_name")
    val PUBLIC_INCOME_NAME = stringPreferencesKey("public_income_name")
    val ATTACHMENT_DIRECTORY_URI = stringPreferencesKey("attachment_directory_uri")
    val LANGUAGE = stringPreferencesKey("app_language")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val MASER_PERCENTAGE = floatPreferencesKey("maser_percentage")

    /** ISO 4217 code everything is shown in. */
    val DISPLAY_CURRENCY = stringPreferencesKey("display_currency")
    val LAST_RATES_FETCH_DAY = longPreferencesKey("last_rates_fetch_day")

    val BALANCE_ADJUSTMENT = doublePreferencesKey("balance_adjustment")
    val BALANCE_ADJUSTMENT_CURRENCY = stringPreferencesKey("balance_adjustment_currency")
    val BALANCE_ADJUSTMENT_DAY = longPreferencesKey("balance_adjustment_day")
    val MULTI_CURRENCY_MIGRATED = booleanPreferencesKey("multi_currency_migrated")

    /** Before multi-currency: a display-only symbol. Read once during the upgrade. */
    val LEGACY_CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")

    /** Before multi-currency: a running maser total. Read once during the upgrade. */
    val LEGACY_TOTAL_MASER = floatPreferencesKey("total maser")
}
