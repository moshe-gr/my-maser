package com.example.mymaser.settings

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymaser.currency.CurrencyRepository
import com.example.mymaser.currency.RateStatus
import com.example.mymaser.history.attachmentFolderName
import com.example.mymaser.reminders.ReminderScheduler
import com.example.mymaser.reminders.ReminderSettings
import com.example.mymaser.settings.data.SettingsRepository
import com.example.mymaser.settings.gui.ReminderToggle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

private const val NAME_DEBOUNCE_MS = 400L

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val currencyRepository = CurrencyRepository.getInstance(application)

    private val nameSaveJobs = mutableMapOf<Preferences.Key<String>, Job>()

    /** The attachment folder names as they are on disk; null until loaded. */
    private var savedDonationFolder: String? = null
    private var savedIncomeFolder: String? = null

    var donationDirectoryName by mutableStateOf("")
        private set
    var incomeDirectoryName by mutableStateOf("")
        private set

    init {
        viewModelScope.launch {
            donationDirectoryName = storedFolderName(isDonation = true)
            incomeDirectoryName = storedFolderName(isDonation = false)
            savedDonationFolder = donationDirectoryName
            savedIncomeFolder = incomeDirectoryName
        }
    }

    private fun folderKey(isDonation: Boolean) =
        if (isDonation) SettingsKeys.PUBLIC_DONATION_NAME else SettingsKeys.PUBLIC_INCOME_NAME

    private suspend fun storedFolderName(isDonation: Boolean): String =
        getApplication<Application>().attachmentFolderName(
            isDonation,
            repository.get(folderKey(isDonation), "").first()
        )

    fun <T> get(key: Preferences.Key<T>, defaultValue: T): Flow<T> = repository.get(key, defaultValue)

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { repository.set(key, value) }
    }

    fun setString(key: Preferences.Key<String>, value: String) {
        when (key) {
            SettingsKeys.PUBLIC_DONATION_NAME, SettingsKeys.PUBLIC_INCOME_NAME -> {
                nameSaveJobs[key]?.cancel()
                nameSaveJobs[key] = viewModelScope.launch {
                    delay(NAME_DEBOUNCE_MS)
                    repository.set(key, value)
                }
            }
            SettingsKeys.LANGUAGE -> {
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
            else -> set(key, value)
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
            ReminderToggle.Receipts -> SettingsKeys.REMINDER_RECEIPTS
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

    fun updateDonationName(value: String) {
        donationDirectoryName = value
        setString(SettingsKeys.PUBLIC_DONATION_NAME, value)
    }

    fun updateIncomeName(value: String) {
        incomeDirectoryName = value
        setString(SettingsKeys.PUBLIC_INCOME_NAME, value)
    }

    /** Renames the attachment folders on disk to match the names typed. */
    fun onPause(attachmentDirectoryUri: String) {
        val donationName = donationDirectoryName
        val incomeName = incomeDirectoryName
        viewModelScope.launch {
            nameSaveJobs.values.toList().joinAll()
            savedDonationFolder =
                renameFolder(savedDonationFolder, donationName, isDonation = true, attachmentDirectoryUri)
            savedIncomeFolder =
                renameFolder(savedIncomeFolder, incomeName, isDonation = false, attachmentDirectoryUri)
        }
    }

    private suspend fun renameFolder(
        saved: String?,
        typed: String,
        isDonation: Boolean,
        attachmentDirectoryUri: String
    ): String? {
        if (saved == null) return null
        val target = getApplication<Application>().attachmentFolderName(isDonation, typed)
        if (target != saved) repository.renameDirectory(saved, target, attachmentDirectoryUri)
        return target
    }
}
