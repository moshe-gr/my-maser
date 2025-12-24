package com.example.mymaser.settings

import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    val PUBLIC_DONATION_NAME = stringPreferencesKey("public_donation_name")
    val PUBLIC_INCOME_NAME = stringPreferencesKey("public_income_name")
    val ATTACHMENT_DIRECTORY_URI = stringPreferencesKey("attachment_directory_uri")
}
