package com.example.mymaser.settings.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.mymaser.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(context: Context) {

    private val dataStore = context.dataStore

    fun getString(key: Preferences.Key<String>, defaultValue: String): Flow<String> {
        return dataStore.data.map {
            it[key] ?: defaultValue
        }
    }

    suspend fun setString(key: Preferences.Key<String>, value: String) {
        dataStore.edit {
            it[key] = value
        }
    }
}