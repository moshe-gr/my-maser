package com.example.mymaser.settings

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymaser.currency.CurrencyRepository
import com.example.mymaser.currency.RateStatus
import com.example.mymaser.gmail.GmailScheduler
import com.example.mymaser.reminders.ReminderScheduler
import com.example.mymaser.reminders.ReminderSettings
import com.example.mymaser.settings.data.SettingsRepository
import com.example.mymaser.settings.gui.ReminderToggle
import com.example.mymaser.dataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Every value the settings screen shows, read from one preferences snapshot. */
data class SettingsState(
    val language: String,
    val themeMode: ThemeMode,
    val maserPercentage: Float,
    val aiEnabled: Boolean,
    val maserBasis: MaserBasis,
    val gmail: GmailSettings,
    val reminders: ReminderSettings,
    val reminderMinuteOfDay: Int
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val currencyRepository = CurrencyRepository.getInstance(application)

    /** Null until the stored values load, so the screen never shows defaults first. */
    val state: StateFlow<SettingsState?> = application.dataStore.data
        .map { prefs ->
            SettingsState(
                language = prefs[SettingsKeys.LANGUAGE].orEmpty(),
                themeMode = ThemeMode.fromKey(prefs[SettingsKeys.THEME_MODE]),
                maserPercentage = prefs.maserPercentage(),
                aiEnabled = prefs.aiEnabled(),
                maserBasis = prefs.maserBasis(),
                gmail = prefs.gmailSettings(),
                reminders = prefs.reminderSettings(),
                reminderMinuteOfDay = prefs.reminderMinuteOfDay()
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    fun setAiEnabled(on: Boolean) {
        viewModelScope.launch {
            repository.set(SettingsKeys.AI_ENABLED, on)
            GmailScheduler.sync(getApplication())
        }
    }

    fun gmailConnected() {
        viewModelScope.launch {
            repository.set(SettingsKeys.GMAIL_CONNECTED, true)
            repository.set(SettingsKeys.GMAIL_NEEDS_RECONNECT, false)
            GmailScheduler.sync(getApplication())
            GmailScheduler.runNow(getApplication())
        }
    }

    fun disconnectGmail() {
        viewModelScope.launch {
            repository.set(SettingsKeys.GMAIL_CONNECTED, false)
            GmailScheduler.sync(getApplication())
        }
    }

    fun setGmailKeywords(keywords: String) = set(SettingsKeys.GMAIL_KEYWORDS, keywords.trim())

    fun checkGmailNow() = GmailScheduler.runNow(getApplication())

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
