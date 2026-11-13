package com.example.mymaser.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

object SettingsKeys {
    val LANGUAGE = stringPreferencesKey("app_language")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val MASER_PERCENTAGE = floatPreferencesKey("maser_percentage")
    val MASER_BASIS = stringPreferencesKey("maser_basis")

    /** Off until the user agrees to send documents to Gemini. */
    val AI_ENABLED = booleanPreferencesKey("ai_enabled")

    val GMAIL_CONNECTED = booleanPreferencesKey("gmail_connected")

    /** Set when the saved Gmail permission stopped working and the user must connect again. */
    val GMAIL_NEEDS_RECONNECT = booleanPreferencesKey("gmail_needs_reconnect")

    /** Comma-separated words the daily email search looks for. */
    val GMAIL_KEYWORDS = stringPreferencesKey("gmail_keywords")

    /** JSON object of renames the user made when reviewing imports. */
    val NAME_ALIASES = stringPreferencesKey("name_aliases")

    /** Encrypted, see [savePdfPassword]. */
    val PDF_PASSWORD = stringPreferencesKey("pdf_password")

    /** ISO 4217 code everything is shown in. */
    val DISPLAY_CURRENCY = stringPreferencesKey("display_currency")
    val LAST_RATES_FETCH_DAY = longPreferencesKey("last_rates_fetch_day")

    val BALANCE_ADJUSTMENT = doublePreferencesKey("balance_adjustment")
    val BALANCE_ADJUSTMENT_CURRENCY = stringPreferencesKey("balance_adjustment_currency")
    val BALANCE_ADJUSTMENT_DAY = longPreferencesKey("balance_adjustment_day")
    val MULTI_CURRENCY_MIGRATED = booleanPreferencesKey("multi_currency_migrated")

    /** Prediction keys the user chose to skip, see [com.example.mymaser.history.Prediction.key]. */
    val DISMISSED_PREDICTIONS = stringSetPreferencesKey("dismissed_predictions")

    val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
    val REMINDER_EXPECTED = booleanPreferencesKey("reminder_expected")
    val REMINDER_BALANCE = booleanPreferencesKey("reminder_balance")
    val REMINDER_REVIEW = booleanPreferencesKey("reminder_review")
    val REMINDER_MINUTE_OF_DAY = intPreferencesKey("reminder_minute_of_day")
    val SENT_REMINDERS = stringSetPreferencesKey("sent_reminders")
    val REMINDER_PROMPT_DISMISSED = booleanPreferencesKey("reminder_prompt_dismissed")

    /** Before multi-currency: a display-only symbol. Read once during the upgrade. */
    val LEGACY_CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")

    /** Before multi-currency: a running maser total. Read once during the upgrade. */
    val LEGACY_TOTAL_MASER = floatPreferencesKey("total maser")
}
