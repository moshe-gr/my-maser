package com.example.mymaser.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymaser.currency.CurrencyRepository
import com.example.mymaser.currency.DEFAULT_CURRENCY
import com.example.mymaser.currency.DisplayHistory
import com.example.mymaser.currency.RateStatus
import com.example.mymaser.currency.RateTable
import com.example.mymaser.currency.maserBalance
import com.example.mymaser.currency.toDisplay
import com.example.mymaser.settings.DEFAULT_MASER_PERCENTAGE
import com.example.mymaser.settings.balanceSetupFlow
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

    val displayCurrency: StateFlow<String> = application.displayCurrencyFlow()
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
        onSaved: (History) -> Unit
    ) = runChange {
        val entry = History(
            timeStamp = System.currentTimeMillis(),
            name = name,
            amount = amount,
            isDonation = isDonation,
            maserPercentage = percentageFlow.first(),
            currency = currency
        )
        val id = repository.insert(entry)
        onSaved(entry.copy(id = id))
    }

    fun editEntry(entry: History, name: String, amount: Float) {
        val stored = original(entry)
        runChange { repository.update(stored.copy(name = name, amount = amount)) }
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

    fun setAttachment(entry: History, uri: String) {
        val stored = original(entry)
        runChange { repository.update(stored.copy(attachmentUri = uri)) }
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
