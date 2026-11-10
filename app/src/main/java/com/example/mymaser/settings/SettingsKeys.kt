package com.example.mymaser.settings

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    val PUBLIC_DONATION_NAME = stringPreferencesKey("public_donation_name")
    val PUBLIC_INCOME_NAME = stringPreferencesKey("public_income_name")
    val ATTACHMENT_DIRECTORY_URI = stringPreferencesKey("attachment_directory_uri")
    val LANGUAGE = stringPreferencesKey("app_language")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
    val MASER_PERCENTAGE = floatPreferencesKey("maser_percentage")
}
