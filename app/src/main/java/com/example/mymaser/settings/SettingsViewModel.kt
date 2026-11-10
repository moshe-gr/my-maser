package com.example.mymaser.settings

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mymaser.currency.CurrencyRepository
import com.example.mymaser.currency.RateStatus
import com.example.mymaser.settings.data.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val currencyRepository = CurrencyRepository.getInstance(application)

    private var initialDonationName: String? = null
    private var initialIncomeName: String? = null
    private var donationNameUpdateJob: Job? = null
    private var incomeNameUpdateJob: Job? = null
    var donationDirectoryName by mutableStateOf("")
        private set
    var incomeDirectoryName by mutableStateOf("")
        private set

    init {
        viewModelScope.launch {
            donationDirectoryName = repository
                .getString(SettingsKeys.PUBLIC_DONATION_NAME, "")
                .first()

            incomeDirectoryName = repository
                .getString(SettingsKeys.PUBLIC_INCOME_NAME, "")
                .first()

            initialDonationName = donationDirectoryName
            initialIncomeName = incomeDirectoryName
        }
    }

    fun getString(key: Preferences.Key<String>, defaultValue: String): Flow<String> {
        return repository.getString(key, defaultValue)
    }

    fun getFloat(key: Preferences.Key<Float>, defaultValue: Float): Flow<Float> {
        return repository.getFloat(key, defaultValue)
    }

    fun setString(key: Preferences.Key<String>, value: String) {
        when (key) {
            SettingsKeys.PUBLIC_DONATION_NAME -> {
                donationNameUpdateJob?.cancel()
                donationNameUpdateJob = viewModelScope.launch {
                    delay(400L)
                    repository.setString(key, value)
                }
            }
            SettingsKeys.PUBLIC_INCOME_NAME -> {
                incomeNameUpdateJob?.cancel()
                incomeNameUpdateJob = viewModelScope.launch {
                    delay(400L)
                    repository.setString(key, value)
                }
            }
            SettingsKeys.LANGUAGE -> {
                viewModelScope.launch {
                    repository.setString(key, value)
                    val locales = if (value.isBlank()) {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(value)
                    }
                    AppCompatDelegate.setApplicationLocales(locales)
                }
            }
            else -> {
                viewModelScope.launch {
                    repository.setString(key, value)
                }
            }
        }
    }

    val rateStatus: StateFlow<RateStatus> = currencyRepository.status

    /** Switches what everything is shown in, then fetches any exchange rates that requires. */
    fun setDisplayCurrency(code: String) {
        viewModelScope.launch {
            repository.setString(SettingsKeys.DISPLAY_CURRENCY, code)
            currencyRepository.ensureRates()
        }
    }

    fun retryRates() {
        viewModelScope.launch { currencyRepository.ensureRates() }
    }

    fun setFloat(key: Preferences.Key<Float>, value: Float) {
        viewModelScope.launch {
            repository.setFloat(key, value)
        }
    }

    fun updateDonationName(value: String) {
        donationDirectoryName = value
        setString(SettingsKeys.PUBLIC_DONATION_NAME, value)
    }

    fun updateIncomeName(value: String) {
        incomeDirectoryName = value
        setString(SettingsKeys.PUBLIC_INCOME_NAME, value)
    }

    fun onPause(
        currentDonationName: String,
        currentIncomeName: String,
        attachmentDirectoryUri: String
    ) {
        viewModelScope.launch {
            donationNameUpdateJob?.join()
            incomeNameUpdateJob?.join()

            val oldDonation = initialDonationName ?: currentDonationName
            val oldIncome = initialIncomeName ?: currentIncomeName

            if (oldDonation != currentDonationName) {
                renameDirectory(oldDonation, currentDonationName, attachmentDirectoryUri)
                initialDonationName = currentDonationName
            }

            if (oldIncome != currentIncomeName) {
                renameDirectory(oldIncome, currentIncomeName, attachmentDirectoryUri)
                initialIncomeName = currentIncomeName
            }
        }
    }

    private suspend fun renameDirectory(
        oldName: String,
        newName: String,
        attachmentDirectoryUri: String
    ) {
        repository.renameDirectory(oldName, newName, attachmentDirectoryUri)
    }
}

class SettingsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
