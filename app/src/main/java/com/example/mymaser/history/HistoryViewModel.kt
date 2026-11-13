package com.example.mymaser.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymaser.ai.ChatData
import com.example.mymaser.currency.CurrencyRepository
import com.example.mymaser.currency.DEFAULT_CURRENCY
import com.example.mymaser.currency.DisplayHistory
import com.example.mymaser.currency.RateStatus
import com.example.mymaser.currency.RateTable
import com.example.mymaser.currency.maserBalance
import com.example.mymaser.currency.toDisplay
import androidx.datastore.preferences.core.edit
import com.example.mymaser.dataStore
import com.example.mymaser.settings.DEFAULT_MASER_PERCENTAGE
import com.example.mymaser.reminders.AppDestination
import com.example.mymaser.reminders.DeepLink
import com.example.mymaser.reminders.ReminderScheduler
import com.example.mymaser.settings.SettingsKeys
import com.example.mymaser.settings.reminderSettingsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.mymaser.settings.balanceSetupFlow
import com.example.mymaser.settings.dismissPrediction
import com.example.mymaser.settings.displayCurrencyFlow
import com.example.mymaser.settings.maserPercentageFlow
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository =
        HistoryRepository(HistoryDb.getInstance(application).getHistoryDao())
    private val currencyRepository = CurrencyRepository.getInstance(application)
    private val percentageFlow = application.maserPercentageFlow()

    /** Entries as stored, each in its own currency. Null until the first load finishes. */
    private val originals: StateFlow<List<History>?> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val displayCurrency: StateFlow<String> = application.displayCurrencyFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_CURRENCY)

    private val rates: StateFlow<RateTable> = currencyRepository.rates
        .stateIn(viewModelScope, SharingStarted.Eagerly, RateTable.EMPTY)

    private val display: StateFlow<DisplayHistory?> =
        combine(originals.filterNotNull(), displayCurrency, rates) { list, currency, table ->
            toDisplay(list, currency, table, ZoneId.systemDefault())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Entries converted to the display currency. Null until the first load finishes. */
    val history: StateFlow<List<History>?> = display
        .map { it?.entries }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** How many entries are still waiting for an exchange rate and left out of totals. */
    val unconvertedCount: StateFlow<Int> = display
        .map { it?.unconverted ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val rateStatus: StateFlow<RateStatus> = currencyRepository.status

    /** Null until the first load finishes. */
    val totalMaser: StateFlow<Float?> = combine(
        display,
        application.balanceSetupFlow(),
        displayCurrency,
        rates
    ) { shown, setup, currency, table ->
        if (shown == null || setup == null) null
        else maserBalance(shown, setup.adjustment, currency, table).toFloat()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val dismissedFlow = application.dataStore.data
        .map { it[SettingsKeys.DISMISSED_PREDICTIONS].orEmpty() }

    /** Projected year-end balance from recurring sources, or null when nothing recurs. */
    val forecast: StateFlow<YearEndForecast?> =
        combine(display, displayCurrency, totalMaser, dismissedFlow) { shown, currency, total, dismissed ->
            if (shown == null || total == null) null
            else forecastYearEnd(shown.countable(currency), total.toDouble(), ZonedDateTime.now(), dismissed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** What the chat may look at right now, or null until everything has loaded. */
    fun chatData(): ChatData? {
        val shown = display.value ?: return null
        val balance = totalMaser.value?.toDouble() ?: return null
        val currency = displayCurrency.value
        return ChatData(shown.countable(currency), currency, balance, forecast.value, ZonedDateTime.now())
    }

    /** A likely mistake in a new entry made at [timeStamp], checked against what's stored. */
    fun checkNewEntry(
        name: String,
        amount: Float,
        currency: String,
        isDonation: Boolean,
        timeStamp: Long = System.currentTimeMillis()
    ): EntryWarning? =
        originals.value?.let { checkEntry(it, name, amount, currency, isDonation, timeStamp) }

    val maserPercentage: StateFlow<Float> = percentageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DEFAULT_MASER_PERCENTAGE)

    val incomeNames: StateFlow<List<String>> = rankedNames(isDonation = false)

    val donationNames: StateFlow<List<String>> = rankedNames(isDonation = true)

    private fun rankedNames(isDonation: Boolean): StateFlow<List<String>> = originals
        .filterNotNull()
        .map { all -> rankSuggestedNames(all.filter { it.isDonation == isDonation }, ZonedDateTime.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** The value Home last settled on, so it can count up to changes made on other tabs. */
    var lastShownTotal: Float? = null

    /** Recurring entries due around today and not logged yet. */
    val predictions: StateFlow<List<Prediction>> =
        combine(originals.filterNotNull(), dismissedFlow) { list, dismissed ->
            predictDueEntries(list, dismissed, ZonedDateTime.now())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _prefill = MutableStateFlow<Prefill?>(null)

    /** A form to open filled in, from a prediction card or a notification. */
    val prefill: StateFlow<Prefill?> = _prefill.asStateFlow()

    fun requestPrefill(prefill: Prefill) {
        _prefill.value = prefill
    }

    fun consumePrefill() {
        _prefill.value = null
    }

    private val _destination = MutableStateFlow<AppDestination?>(null)

    /** A screen a notification asked to open. */
    val destinationRequest: StateFlow<AppDestination?> = _destination.asStateFlow()

    fun openLink(link: DeepLink) {
        when (link) {
            is DeepLink.OpenForm -> requestPrefill(link.prefill)
            is DeepLink.Open -> _destination.value = link.destination
        }
    }

    fun consumeDestination() {
        _destination.value = null
    }

    /**
     * A recurring source to mention when inviting the user to turn on reminders, or null when the
     * invitation shouldn't show (already on, dismissed, or nothing recurs yet).
     */
    val reminderPrompt: StateFlow<String?> = combine(
        originals.filterNotNull(),
        application.reminderSettingsFlow(),
        application.dataStore.data.map { it[SettingsKeys.REMINDER_PROMPT_DISMISSED] == true }
    ) { list, settings, dismissed ->
        if (settings.enabled || dismissed) null
        else recurringSourceNames(list, ZonedDateTime.now()).firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun enableReminders() = runChange {
        val app = getApplication<Application>()
        app.dataStore.edit { it[SettingsKeys.REMINDERS_ENABLED] = true }
        ReminderScheduler.sync(app)
    }

    fun dismissReminderPrompt() = runChange {
        getApplication<Application>().dataStore.edit {
            it[SettingsKeys.REMINDER_PROMPT_DISMISSED] = true
        }
    }

    fun dismissPrediction(prediction: Prediction) = runChange {
        getApplication<Application>().dismissPrediction(prediction.key)
    }

    init {
        viewModelScope.launch {
            migrateToMultiCurrency(application)
            combine(
                displayCurrency,
                originals.filterNotNull().map { list -> list.map { it.currency }.toSet() }
            ) { currency, inUse -> inUse + currency }
                .distinctUntilChanged()
                .collect { currencyRepository.ensureRates() }
        }
    }

    fun retryRates() {
        viewModelScope.launch { currencyRepository.ensureRates() }
    }

    /** The stored entry behind a converted one shown on screen. */
    fun original(entry: History): History =
        originals.value?.firstOrNull { it.id == entry.id } ?: entry

    fun addEntry(
        name: String,
        amount: Float,
        currency: String,
        isDonation: Boolean,
        isTaxOnly: Boolean,
        onSaved: (History) -> Unit
    ) = runChange {
        val entry = History(
            timeStamp = System.currentTimeMillis(),
            name = name,
            amount = amount,
            isDonation = isDonation,
            maserPercentage = percentageFlow.first(),
            currency = currency,
            isTaxOnly = isDonation && isTaxOnly
        )
        val id = repository.insert(entry)
        onSaved(entry.copy(id = id))
    }

    /** Saves several entries at their own times, e.g. from a document import. */
    fun addEntries(entries: List<NewEntry>, onSaved: (List<History>) -> Unit) = runChange {
        val percentage = percentageFlow.first()
        val saved = entries.map {
            val entry = History(
                timeStamp = it.timeStamp,
                name = it.name,
                amount = it.amount,
                isDonation = it.isDonation,
                maserPercentage = percentage,
                currency = it.currency,
                isTaxOnly = it.isDonation && it.isTaxOnly
            )
            entry.copy(id = repository.insert(entry))
        }
        onSaved(saved)
    }

    fun editEntry(entry: History, name: String, amount: Float, isTaxOnly: Boolean) {
        val stored = original(entry)
        runChange {
            repository.update(
                stored.copy(name = name, amount = amount, isTaxOnly = stored.isDonation && isTaxOnly)
            )
        }
    }

    /** Deletes the stored entry and returns it, so it can be restored exactly. */
    fun deleteEntry(entry: History): History {
        val stored = original(entry)
        runChange { repository.delete(stored) }
        return stored
    }

    fun restoreEntry(entry: History) = runChange {
        repository.insert(entry)
    }

    fun suggestedAmount(name: String, isDonation: Boolean): AmountSuggestion? =
        originals.value?.let { suggestAmount(it, name, isDonation, ZonedDateTime.now()) }

    /** Runs past ViewModel teardown so a started change always reaches the database. */
    private fun runChange(change: suspend () -> Unit) {
        viewModelScope.launch { withContext(NonCancellable) { change() } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
