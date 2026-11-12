package com.example.mymaser.ai

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymaser.history.HistoryDb
import com.example.mymaser.history.HistoryRepository
import com.example.mymaser.settings.aiEnabledFlow
import com.example.mymaser.settings.displayCurrencyFlow
import com.example.mymaser.settings.maserBasisFlow
import kotlinx.coroutines.Job
import com.example.mymaser.gmail.PendingImport
import com.example.mymaser.history.Prefill
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ImportState {
    data object Idle : ImportState
    data object Reading : ImportState
    /** [emailIds] are the emails these entries came from, marked done once the user saves. */
    data class Review(
        val entries: List<ExtractedEntry>,
        val skipped: Int,
        val emailIds: List<String> = emptyList()
    ) : ImportState
    data class Empty(val skipped: Int) : ImportState
    data class Failed(val error: AiError) : ImportState
}

val AiError.canRetry: Boolean
    get() = this == AiError.Network || this == AiError.Failed || this == AiError.QuotaExceeded ||
        this == AiError.Busy

/** Reads shared, scanned or picked documents into entries for the review sheet. */
class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val history = HistoryRepository(HistoryDb.getInstance(application).getHistoryDao())
    private val pendingImports = HistoryDb.getInstance(application).getPendingImportDao()

    /** Emails the daily Gmail check found entries in, not yet reviewed. */
    val pendingEmails: StateFlow<List<PendingImport>> = pendingImports.observePending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())
    private val importer = DocumentImporter(GeminiAiClient.getInstance(application)) {
        application.aiEnabledFlow().first()
    }

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _prefills = MutableSharedFlow<Prefill>(extraBufferCapacity = 1)

    /** A single quick-add result, to open in the entry form. */
    val prefills: SharedFlow<Prefill> = _prefills.asSharedFlow()

    val aiEnabled: StateFlow<Boolean> = application.aiEnabledFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Kept only while a retryable failure is on screen. */
    private var retryDocuments: ReadDocuments? = null
    private var job: Job? = null

    fun readShare(intent: Intent) = start { readShare(app, intent) }

    fun readUris(uris: List<Uri>) {
        if (uris.isNotEmpty()) start { readFiles(app, uris) }
    }

    /** Reads a typed or spoken note offline when possible, else with the model. */
    fun quickAdd(text: String) {
        val note = text.trim()
        if (note.isEmpty()) return
        viewModelScope.launch {
            val all = history.observeHistory().first()
            val incomes = all.filterNot { it.isDonation }.map { it.name.trim() }.distinct()
            val donations = all.filter { it.isDonation }.map { it.name.trim() }.distinct()
            when (val local = parseQuickAdd(note, incomes, donations)) {
                is QuickAdd.Parsed -> _prefills.emit(local.prefill)
                QuickAdd.NeedsAi -> start(singleToForm = true) {
                    ReadDocuments(listOf(AiInput.Text(note)), skipped = 0)
                }
            }
        }
    }

    fun retry() {
        val documents = retryDocuments ?: return
        start { documents }
    }

    fun reviewEmails() {
        val emails = pendingEmails.value
        if (emails.isEmpty()) return
        job?.cancel()
        _state.value = ImportState.Review(
            entries = emails.flatMap { entriesFromJson(it.entriesJson) },
            skipped = 0,
            emailIds = emails.map { it.messageId }
        )
    }

    fun dismissEmails() {
        val ids = pendingEmails.value.map { it.messageId }
        viewModelScope.launch { pendingImports.markDone(ids) }
    }

    /** Closes the sheet after the user saved; emails it came from won't be offered again. */
    fun finishReview() {
        val ids = (_state.value as? ImportState.Review)?.emailIds.orEmpty()
        if (ids.isNotEmpty()) viewModelScope.launch { pendingImports.markDone(ids) }
        dismiss()
    }

    fun dismiss() {
        job?.cancel()
        retryDocuments = null
        _state.value = ImportState.Idle
    }

    private fun start(singleToForm: Boolean = false, load: suspend () -> ReadDocuments) {
        job?.cancel()
        retryDocuments = null
        _state.value = ImportState.Reading
        job = viewModelScope.launch {
            val documents = load()
            if (documents.inputs.isEmpty()) {
                _state.value = ImportState.Empty(documents.skipped)
                return@launch
            }
            val result = importer.read(
                inputs = documents.inputs,
                basis = app.maserBasisFlow().first(),
                fallbackCurrency = app.displayCurrencyFlow().first(),
                knownNames = history.observeHistory().first().map { it.name.trim() }.distinct()
            )
            val single = (result as? AiResult.Success)?.value?.singleOrNull()
            if (singleToForm && single != null) {
                _state.value = ImportState.Idle
                _prefills.emit(Prefill(single.name, single.amount, single.currency, single.isDonation))
                return@launch
            }
            _state.value = when (result) {
                is AiResult.Error -> {
                    if (result.reason.canRetry) retryDocuments = documents
                    ImportState.Failed(result.reason)
                }
                is AiResult.Success ->
                    if (result.value.isEmpty()) ImportState.Empty(documents.skipped)
                    else ImportState.Review(result.value, documents.skipped)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
