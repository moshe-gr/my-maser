package com.example.mymaser.settings

import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.mymaser.settings.data.SettingsRepository
import kotlinx.coroutines.flow.Flow

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    fun getString(key: Preferences.Key<String>, defaultValue: String): Flow<String> {
        return repository.getString(key, defaultValue)
    }

    suspend fun setString(key: Preferences.Key<String>, value: String) {
        repository.setString(key, value)
    }
}

class SettingsViewModelFactory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}