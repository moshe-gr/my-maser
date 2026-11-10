package com.example.mymaser.history

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymaser.dataStore
import com.example.mymaser.settings.DEFAULT_MASER_PERCENTAGE
import com.example.mymaser.settings.maserPercentageFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TOTAL_MASER_KEY = floatPreferencesKey("total maser")

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository =
        HistoryRepository(HistoryDb.getInstance(application).getHistoryDao())
    private val dataStore = application.dataStore
    private val percentageFlow = application.maserPercentageFlow()

    /** Null until the first load finishes. */
    val history: StateFlow<List<History>?> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Null until the first load finishes. */
    val totalMaser: StateFlow<Float?> = dataStore.data
        .map { it[TOTAL_MASER_KEY] ?: 0F }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val maserPercentage: StateFlow<Float> = percentageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DEFAULT_MASER_PERCENTAGE)

    val incomeNames: StateFlow<List<String>> = repository.observeNames(isDonation = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val donationNames: StateFlow<List<String>> = repository.observeNames(isDonation = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** The value Home last settled on, so it can count up to changes made on other tabs. */
    var lastShownTotal: Float? = null

    fun addEntry(
        name: String,
        amount: Float,
        isDonation: Boolean,
        onSaved: (History) -> Unit
    ) = runChange {
        val entry = History(
            timeStamp = System.currentTimeMillis(),
            name = name,
            amount = amount,
            isDonation = isDonation,
            maserPercentage = percentageFlow.first()
        )
        val id = repository.insert(entry)
        adjustTotal(entry.maserDelta)
        onSaved(entry.copy(id = id))
    }

    fun editEntry(original: History, name: String, amount: Float) = runChange {
        repository.update(original.copy(name = name, amount = amount))
        adjustTotal(maserDeltaForEdit(original, amount))
    }

    fun deleteEntry(entry: History) = runChange {
        repository.delete(entry)
        adjustTotal(-entry.maserDelta)
    }

    fun restoreEntry(entry: History) = runChange {
        repository.insert(entry)
        adjustTotal(entry.maserDelta)
    }

    fun setAttachment(entry: History, uri: String) = runChange {
        repository.update(entry.copy(attachmentUri = uri))
    }

    suspend fun suggestedAmount(name: String, isDonation: Boolean): Float =
        repository.suggestedAmount(name, isDonation)

    suspend fun loadSummary(entry: History): HistoryRepository.HistorySummary =
        repository.loadSummary(entry)

    /** Runs past ViewModel teardown so an entry and the running total can't get out of sync. */
    private fun runChange(change: suspend () -> Unit) {
        viewModelScope.launch { withContext(NonCancellable) { change() } }
    }

    private suspend fun adjustTotal(delta: Float) {
        dataStore.edit { it[TOTAL_MASER_KEY] = (it[TOTAL_MASER_KEY] ?: 0F) + delta }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
