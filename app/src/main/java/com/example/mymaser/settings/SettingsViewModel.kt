package com.example.mymaser.settings

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymaser.currency.CurrencyRepository
import com.example.mymaser.currency.RateStatus
import com.example.mymaser.reminders.ReminderScheduler
import com.example.mymaser.reminders.ReminderSettings
import com.example.mymaser.settings.data.SettingsRepository
import com.example.mymaser.settings.gui.ReminderToggle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val currencyRepository = CurrencyRepository.getInstance(application)

    fun <T> get(key: Preferences.Key<T>, defaultValue: T): Flow<T> = repository.get(key, defaultValue)

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { repository.set(key, value) }
    }

    fun setString(key: Preferences.Key<String>, value: String) {
        if (key != SettingsKeys.LANGUAGE) {
            set(key, value)
            return
        }
        viewModelScope.launch {
            repository.set(key, value)
            val locales = if (value.isBlank()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(value)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    val rateStatus: StateFlow<RateStatus> = currencyRepository.status

    /** Switches what everything is shown in, then fetches any exchange rates that requires. */
    fun setDisplayCurrency(code: String) {
        viewModelScope.launch {
            repository.set(SettingsKeys.DISPLAY_CURRENCY, code)
            currencyRepository.ensureRates()
        }
    }

    fun retryRates() {
        viewModelScope.launch { currencyRepository.ensureRates() }
    }

    val reminderSettings: Flow<ReminderSettings> = application.reminderSettingsFlow()
    val reminderMinuteOfDay: Flow<Int> = application.reminderMinuteOfDayFlow()

    fun setReminder(toggle: ReminderToggle, on: Boolean) {
        val key = when (toggle) {
            ReminderToggle.Master -> SettingsKeys.REMINDERS_ENABLED
            ReminderToggle.Expected -> SettingsKeys.REMINDER_EXPECTED
            ReminderToggle.Balance -> SettingsKeys.REMINDER_BALANCE
            ReminderToggle.Review -> SettingsKeys.REMINDER_REVIEW
        }
        viewModelScope.launch {
            repository.set(key, on)
            if (toggle == ReminderToggle.Master) ReminderScheduler.sync(getApplication())
        }
    }

    fun setReminderTime(minuteOfDay: Int) {
        viewModelScope.launch {
            repository.set(SettingsKeys.REMINDER_MINUTE_OF_DAY, minuteOfDay)
            ReminderScheduler.sync(getApplication(), rescheduleTime = true)
        }
    }

    fun runRemindersNow() {
        ReminderScheduler.runNow(getApplication())
    }
}
