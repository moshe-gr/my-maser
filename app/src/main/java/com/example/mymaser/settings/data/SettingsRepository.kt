package com.example.mymaser.settings.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.mymaser.dataStore

class SettingsRepository(context: Context) {

    private val dataStore = context.dataStore

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }
}
